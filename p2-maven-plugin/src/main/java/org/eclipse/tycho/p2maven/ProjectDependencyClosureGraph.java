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
	record Capability(IInstallableUnit installableUnit, IProvidedCapability providedCapability) {
	}

	/**
	 * Represents a directional edge in the dependency graph
	 */
	record Edge(Requirement requirement, Set<Capability> capabilities) {
	}

	private static final ProjectDependencies EMPTY_DEPENDENCIES = new ProjectDependencies(Map.of(), Set.of());

	private static final boolean DUMP_DATA = Boolean.getBoolean("tycho.p2.dump")
			|| Boolean.getBoolean("tycho.p2.dump.dependencies");

	private final Map<IInstallableUnit, MavenProject> iuProjectMap = new HashMap<>();

	private Map<MavenProject, ProjectDependencies> projectDependenciesMap;

	private Map<MavenProject, Collection<IInstallableUnit>> projectIUMap;

	// Graph structure: maps each project to its outgoing edges
	private Map<MavenProject, Set<Edge>> projectEdgesMap = new HashMap<>();

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
		// Collect all IUs from all projects for capability matching
		List<IInstallableUnit> allIUs = projectIUMap.values().stream()
				.flatMap(Collection::stream)
				.collect(Collectors.toList());

		// For each project, build edges based on requirements
		for (var entry : projectIUMap.entrySet()) {
			MavenProject project = entry.getKey();
			Set<Edge> edges = new LinkedHashSet<>();
			Collection<IInstallableUnit> projectUnits = entry.getValue();

			// Collect all requirements from all IUs of this project (excluding MetaRequirements)
			Set<Requirement> requirements = new LinkedHashSet<>();
			for (IInstallableUnit iu : projectUnits) {
				for (IRequirement req : iu.getRequirements()) {
					// Don't include self-satisfied requirements
					boolean selfSatisfied = false;
					for (IInstallableUnit projectIU : projectUnits) {
						if (projectIU.satisfies(req)) {
							selfSatisfied = true;
							break;
						}
					}
					if (!selfSatisfied) {
						requirements.add(new Requirement(iu, req));
					}
				}
			}

			// For each requirement, find matching capabilities
			for (Requirement requirement : requirements) {
				Set<Capability> matchingCapabilities = new LinkedHashSet<>();
				
				// Search through all IUs to find those that satisfy the requirement
				for (IInstallableUnit iu : allIUs) {
					if (iu.satisfies(requirement.requirement)) {
						// Collect all capabilities from this IU
						for (IProvidedCapability cap : iu.getProvidedCapabilities()) {
							matchingCapabilities.add(new Capability(iu, cap));
						}
					}
				}
				
				// Create edge even if no capabilities match (empty set indicates unsatisfied)
				edges.add(new Edge(requirement, matchingCapabilities));
			}

			projectEdgesMap.put(project, edges);
		}
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
			Map<IRequirement, Collection<IInstallableUnit>> requirementsMap = new LinkedHashMap<>();
			Set<Edge> edges = projectEdgesMap.getOrDefault(project, Set.of());
			
			for (Edge edge : edges) {
				List<IInstallableUnit> satisfyingUnits = edge.capabilities.stream()
						.map(cap -> cap.installableUnit)
						.filter(iu -> !projectUnits.contains(iu)) // Exclude own units
						.distinct()
						.collect(Collectors.toList());
				
				// Always add the requirement to the map, even if it has no satisfying units
				// This maintains the same behavior as the old implementation
				requirementsMap.put(edge.requirement.requirement, satisfyingUnits);
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
					for (Capability capability : edge.capabilities) {
						MavenProject targetProject = iuProjectMap.get(capability.installableUnit);
						if (targetProject != null && !targetProject.equals(sourceProject)) {
							targetProjects.add(targetProject);
						}
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
