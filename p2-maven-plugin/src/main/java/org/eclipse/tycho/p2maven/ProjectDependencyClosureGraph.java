/*******************************************************************************
 * Copyright (c) 2025 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2maven;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.tycho.p2maven.MavenProjectDependencyProcessor.ProjectDependencies;
import org.eclipse.tycho.p2maven.MavenProjectDependencyProcessor.ProjectDependencyClosure;
import org.eclipse.tycho.p2maven.tmp.BundlesAction;

class ProjectDependencyClosureGraph implements ProjectDependencyClosure {

	/**
	 * Represents a requirement from an installable unit
	 */
	record Requirement(IInstallableUnit installableUnit, IRequirement requirement) {
	}

	/**
	 * Represents a capability provided by an installable unit
	 */
	record Capability(MavenProject project, IInstallableUnit installableUnit) {
	}

	/**
	 * Represents a directional edge in the dependency graph
	 */
	record Edge(Requirement requirement, Capability capability) {
	}

	private static final ProjectDependencies EMPTY_DEPENDENCIES = new ProjectDependencies(Map.of(), Set.of());

	private static final boolean DUMP_DATA = Boolean.getBoolean("tycho.p2.dump")
			|| Boolean.getBoolean("tycho.p2.dump.dependencies");

	private final Map<IInstallableUnit, MavenProject> iuProjectMap = new HashMap<>();

	private Map<MavenProject, ProjectDependencies> projectDependenciesMap;

	private Map<MavenProject, Collection<IInstallableUnit>> projectIUMap;

	// Graph structure: maps each project to its outgoing edges
	private Map<MavenProject, List<Edge>> projectEdgesMap = new HashMap<>();

	ProjectDependencyClosureGraph(Map<MavenProject, Collection<IInstallableUnit>> projectIUMap) throws CoreException {
		this.projectIUMap = projectIUMap;
		// Build IU to project mapping
		for (var entry : projectIUMap.entrySet()) {
			MavenProject mavenProject = entry.getKey();
			for (IInstallableUnit iu : entry.getValue()) {
				iuProjectMap.put(iu, mavenProject);
			}
		}
		// Build the graph structure
		buildGraph();
		// Compute project dependencies
		projectDependenciesMap = computeProjectDependenciesFromGraph();
		// Write DOT file if requested
		if (DUMP_DATA) {
			try {
				File dotFile = new File("project-dependencies.dot");
				dump(dotFile);
			} catch (IOException e) {
				// Ignore dump errors
			}
		}
	}

	/**
	 * Build the internal graph structure based on projectIUMap.
	 * For each project, we collect all requirements from all IUs provided by that project,
	 * then create edges to the capabilities that satisfy those requirements.
	 */
	private void buildGraph() {
		// Pre-create Capability objects for all IUs to prevent multiple object creation
		List<Capability> allCapabilities = projectIUMap.entrySet().stream()
				.flatMap(entry -> entry.getValue().stream()
						.map(iu -> new Capability(entry.getKey(), iu)))
				.collect(Collectors.toList());

		// Build edges for each project in parallel
		Map<MavenProject, List<Edge>> result = new java.util.concurrent.ConcurrentHashMap<>();
		
		projectIUMap.entrySet().parallelStream().unordered().forEach(entry -> {
			MavenProject project = entry.getKey();
			List<Edge> edges = new ArrayList<>();
			Collection<IInstallableUnit> projectUnits = entry.getValue();

			// Collect all requirements from all IUs of this project (excluding MetaRequirements)
			// Include all requirements, even self-satisfied ones (allowing cyclic dependencies)
			Set<Requirement> requirements = new LinkedHashSet<>();
			for (IInstallableUnit iu : projectUnits) {
				for (IRequirement req : iu.getRequirements()) {
					requirements.add(new Requirement(iu, req));
				}
			}

			// For each requirement, find matching capabilities and create edges
			for (Requirement requirement : requirements) {
				// Search through all capabilities to find those that satisfy the requirement
				for (Capability capability : allCapabilities) {
					if (capability.installableUnit.satisfies(requirement.requirement)) {
						edges.add(new Edge(requirement, capability));
					}
				}
			}

			result.put(project, edges);
		});
		
		projectEdgesMap.putAll(result);
	}

	/**
	 * Compute project dependencies from the built graph structure
	 */
	private Map<MavenProject, ProjectDependencies> computeProjectDependenciesFromGraph() {
		Map<MavenProject, ProjectDependencies> result = new LinkedHashMap<>();
		
		for (var entry : projectIUMap.entrySet()) {
			MavenProject project = entry.getKey();
			Set<IInstallableUnit> projectUnits = Set.copyOf(entry.getValue());
			
			// Build requirements map from edges
			// Group edges by requirement and collect all satisfying IUs
			Map<IRequirement, Collection<IInstallableUnit>> requirementsMap = new LinkedHashMap<>();
			List<Edge> edges = projectEdgesMap.getOrDefault(project, List.of());
			
			for (Edge edge : edges) {
				// Only add if not from the same project (use project from capability)
				if (!edge.capability.project.equals(project)) {
					requirementsMap.computeIfAbsent(edge.requirement.requirement, k -> new ArrayList<>())
							.add(edge.capability.installableUnit);
				}
			}
			
			result.put(project, new ProjectDependencies(requirementsMap, projectUnits));
		}
		
		return result;
	}

	/**
	 * Dump the graph to a DOT file for visualization
	 * 
	 * @param file the file to write the DOT representation to
	 * @throws IOException if writing fails
	 */
	public void dump(File file) throws IOException {
		try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
			writer.println("digraph ProjectDependencies {");
			writer.println("  rankdir=LR;");
			writer.println("  node [shape=box];");
			writer.println();
			
			// Create a mapping of projects to short names for the graph
			Map<MavenProject, String> projectNames = new HashMap<>();
			int counter = 0;
			for (MavenProject project : projectIUMap.keySet()) {
				String nodeName = "p" + counter++;
				projectNames.put(project, nodeName);
				String label = project.getArtifactId();
				writer.println("  " + nodeName + " [label=\"" + escapeLabel(label) + "\"];");
			}
			writer.println();
			
			// Write edges
			for (var entry : projectEdgesMap.entrySet()) {
				MavenProject sourceProject = entry.getKey();
				String sourceName = projectNames.get(sourceProject);
				
				// Collect target projects from edges
				Set<MavenProject> targetProjects = new HashSet<>();
				for (Edge edge : entry.getValue()) {
					MavenProject targetProject = edge.capability.project;
					if (!targetProject.equals(sourceProject)) {
						targetProjects.add(targetProject);
					}
				}
				
				// Write edge for each target project
				for (MavenProject targetProject : targetProjects) {
					String targetName = projectNames.get(targetProject);
					if (targetName != null) {
						writer.println("  " + sourceName + " -> " + targetName + ";");
					}
				}
			}
			
			writer.println("}");
		}
	}
	
	private String escapeLabel(String label) {
		return label.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	@Override
	public Optional<MavenProject> getProject(IInstallableUnit installableUnit) {
		return Optional.ofNullable(iuProjectMap.get(installableUnit));
	}

	@Override
	public ProjectDependencies getProjectDependecies(MavenProject mavenProject) {
		return projectDependenciesMap.getOrDefault(mavenProject, EMPTY_DEPENDENCIES);
	}

	@Override
	public Stream<Entry<MavenProject, Collection<IInstallableUnit>>> dependencies(
			Function<MavenProject, Collection<IInstallableUnit>> contextIuSupplier) {
		return projectDependenciesMap.entrySet().stream().map(pd -> new SimpleEntry<>(pd.getKey(),
				pd.getValue().getDependencies(contextIuSupplier.apply(pd.getKey()))));
	}

	@Override
	public boolean isFragment(MavenProject mavenProject) {

		return getProjectUnits(mavenProject).stream().anyMatch(ProjectDependencyClosureGraph::isFragment);
	}

	@Override
	public Collection<IInstallableUnit> getProjectUnits(MavenProject mavenProject) {
		Collection<IInstallableUnit> collection = projectIUMap.get(mavenProject);
		if (collection != null) {
			return collection;
		}
		return Collections.emptyList();
	}

	private static boolean isFragment(IInstallableUnit installableUnit) {
		return getFragmentCapability(installableUnit).findAny().isPresent();
	}

	private static Stream<IProvidedCapability> getFragmentCapability(IInstallableUnit installableUnit) {

		return installableUnit.getProvidedCapabilities().stream()
				.filter(cap -> BundlesAction.CAPABILITY_NS_OSGI_FRAGMENT.equals(cap.getNamespace()));
	}

}
