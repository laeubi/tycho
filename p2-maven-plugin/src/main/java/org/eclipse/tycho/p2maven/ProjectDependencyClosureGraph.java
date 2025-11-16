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
import java.io.IOException;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.equinox.internal.p2.metadata.IRequiredCapability;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.tycho.p2maven.MavenProjectDependencyProcessor.ProjectDependencies;
import org.eclipse.tycho.p2maven.MavenProjectDependencyProcessor.ProjectDependencyClosure;
import org.eclipse.tycho.p2maven.tmp.BundlesAction;

/**
 * Simple logger interface for debugging dependency resolution
 */
interface DependencyLogger {
	void debug(String message);
	void info(String message);
	void error(String message);
	
	/**
	 * No-op logger that discards all messages
	 */
	static DependencyLogger NONE = new DependencyLogger() {
		@Override
		public void debug(String message) {}
		@Override
		public void info(String message) {}
		@Override
		public void error(String message) {}
	};
}

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
	 * Types of edges in the dependency graph
	 */
	enum EdgeType {
		/** Edge representing an attached fragment */
		ATTACHED_FRAGMENT,
		/** Edge representing a mandatory compile-time requirement (osgi.bundle or java.package) */
		COMPILE_REQUIRED,
		/** Edge representing an optional requirement */
		OPTIONAL,
		/** Edge representing any other type of requirement */
		OTHER
	}
	
	/**
	 * Represents a directional edge in the dependency graph
	 */
	record Edge(Requirement requirement, Capability capability, EdgeType type) {
		
		/**
		 * Determine the edge type based on the requirement
		 */
		static EdgeType determineType(IRequirement requirement, boolean isFragment) {
			if (isFragment) {
				return EdgeType.ATTACHED_FRAGMENT;
			}
			if (requirement.getMin() == 0) {
				return EdgeType.OPTIONAL;
			}
			// Check if it's a compile-required dependency
			if (requirement instanceof IRequiredCapability) {
				IRequiredCapability reqCap = (IRequiredCapability) requirement;
				String namespace = reqCap.getNamespace();
				if (BundlesAction.CAPABILITY_NS_OSGI_BUNDLE.equals(namespace)
						|| "java.package".equals(namespace)) {
					return EdgeType.COMPILE_REQUIRED;
				}
			}
			return EdgeType.OTHER;
		}
	}

	private static final ProjectDependencies EMPTY_DEPENDENCIES = new ProjectDependencies(Map.of(), Set.of());

	private static final boolean DUMP_DATA = Boolean.getBoolean("tycho.p2.dump")
			|| Boolean.getBoolean("tycho.p2.dump.dependencies");

	private final Map<IInstallableUnit, MavenProject> iuProjectMap = new HashMap<>();

	private final Map<MavenProject, ProjectDependencies> projectDependenciesMap = new ConcurrentHashMap<>();

	Map<MavenProject, Collection<IInstallableUnit>> projectIUMap;

	// Graph structure: maps each project to its outgoing edges
	Map<MavenProject, List<Edge>> projectEdgesMap = new HashMap<>();

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
		// Write DOT file if requested
		if (DUMP_DATA) {
			try {
				DotDump.dump(new File("project-dependencies.dot"), this);
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
						// Determine edge type (fragment status is determined later in getDependencyProjects)
						EdgeType type = Edge.determineType(requirement.requirement, false);
						edges.add(new Edge(requirement, capability, type));
					}
				}
			}

			result.put(project, edges);
		});
		
		projectEdgesMap.putAll(result);
	}

	
	/**
	 * Detect all cycles in the dependency graph using Tarjan's algorithm for strongly connected components
	 * 
	 * @return a set of sets, where each inner set represents a cycle (strongly connected component with more than one node)
	 */
	Set<Set<MavenProject>> detectCycles() {
		Set<Set<MavenProject>> cycles = new HashSet<>();
		Map<MavenProject, Integer> index = new HashMap<>();
		Map<MavenProject, Integer> lowLink = new HashMap<>();
		Map<MavenProject, Boolean> onStack = new HashMap<>();
		List<MavenProject> stack = new ArrayList<>();
		int[] indexCounter = {0};
		
		for (MavenProject project : projectIUMap.keySet()) {
			if (!index.containsKey(project)) {
				strongConnect(project, index, lowLink, onStack, stack, indexCounter, cycles);
			}
		}
		
		return cycles;
	}
	
	/**
	 * Tarjan's strongly connected components algorithm
	 */
	private void strongConnect(MavenProject v, Map<MavenProject, Integer> index, 
			Map<MavenProject, Integer> lowLink, Map<MavenProject, Boolean> onStack,
			List<MavenProject> stack, int[] indexCounter, Set<Set<MavenProject>> cycles) {
		
		index.put(v, indexCounter[0]);
		lowLink.put(v, indexCounter[0]);
		indexCounter[0]++;
		stack.add(v);
		onStack.put(v, true);
		
		// Consider successors of v
		List<Edge> edges = projectEdgesMap.getOrDefault(v, List.of());
		for (Edge edge : edges) {
			MavenProject w = edge.capability.project;
			
			if (!index.containsKey(w)) {
				// Successor w has not yet been visited; recurse on it
				strongConnect(w, index, lowLink, onStack, stack, indexCounter, cycles);
				lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(w)));
			} else if (onStack.getOrDefault(w, false)) {
				// Successor w is in stack and hence in the current SCC
				lowLink.put(v, Math.min(lowLink.get(v), index.get(w)));
			}
		}
		
		// If v is a root node, pop the stack and generate an SCC
		if (lowLink.get(v).equals(index.get(v))) {
			Set<MavenProject> scc = new HashSet<>();
			MavenProject w;
			do {
				w = stack.remove(stack.size() - 1);
				onStack.put(w, false);
				scc.add(w);
			} while (!w.equals(v));
			
			// Only add if it's a real cycle (more than one node, or has self-edge)
			if (scc.size() > 1 || hasSelfEdge(v)) {
				cycles.add(scc);
			}
		}
	}
	
	/**
	 * Check if a project has a self-referencing edge
	 */
	private boolean hasSelfEdge(MavenProject project) {
		List<Edge> edges = projectEdgesMap.getOrDefault(project, List.of());
		for (Edge edge : edges) {
			if (edge.capability.project.equals(project)) {
				return true;
			}
		}
		return false;
	}
	

	@Override
	public Optional<MavenProject> getProject(IInstallableUnit installableUnit) {
		return Optional.ofNullable(iuProjectMap.get(installableUnit));
	}

	@Override
	public ProjectDependencies getProjectDependecies(MavenProject mavenProject) {
		return projectDependenciesMap.computeIfAbsent(mavenProject, project -> computeProjectDependencies(project));
	}

	private ProjectDependencies computeProjectDependencies(MavenProject project) {
		Collection<IInstallableUnit> projectUnits = projectIUMap.get(project);
		if (projectUnits != null) {
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
			return new ProjectDependencies(requirementsMap, Set.copyOf(projectUnits));
		}
		return EMPTY_DEPENDENCIES;
	}

	@Override
	public Stream<Entry<MavenProject, Collection<IInstallableUnit>>> dependencies(
			Function<MavenProject, Collection<IInstallableUnit>> contextIuSupplier) {
		return projectIUMap.keySet().stream().map(mavenProject -> {
			ProjectDependencies projectDependencies = getProjectDependecies(mavenProject);
			Collection<IInstallableUnit> dependentUnits = projectDependencies
					.getDependencies(contextIuSupplier.apply(mavenProject));
			return new SimpleEntry<>(mavenProject, dependentUnits);
		});
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

	@Override
	public Collection<MavenProject> getDependencyProjects(MavenProject mavenProject,
			Collection<IInstallableUnit> contextIUs) {
		return getDependencyProjects(mavenProject, contextIUs, DependencyLogger.NONE);
	}
	
	/**
	 * Get the dependency projects for a given project with optional logging for debugging.
	 * This is the enhanced version that handles cycles properly.
	 * 
	 * @param mavenProject the project to analyze
	 * @param contextIUs the context IUs for filtering
	 * @param logger optional logger for debugging (use DependencyLogger.NONE for no logging)
	 * @return collection of cycle-free dependency projects
	 */
	public Collection<MavenProject> getDependencyProjects(MavenProject mavenProject,
			Collection<IInstallableUnit> contextIUs, DependencyLogger logger) {
		
		// Use the original logic to get base dependencies
		ProjectDependencies projectDependecies = getProjectDependecies(mavenProject);
		List<MavenProject> baseDeps = projectDependecies.getDependencies(contextIUs).stream()
				.flatMap(dependency -> getProject(dependency).stream()).distinct().toList();
		
		// Step 1: Handle fragment attachment uniformly for all projects
		List<MavenProject> depsWithFragments = new ArrayList<>();
		Set<MavenProject> processedProjects = new HashSet<>();
		Collection<IInstallableUnit> projectUnits = getProjectUnits(mavenProject);
		
		for (MavenProject depProject : baseDeps) {
			if (!processedProjects.contains(depProject)) {
				processedProjects.add(depProject);
				depsWithFragments.add(depProject);
				
				// Find and attach fragments for this dependency
				ProjectDependencies depDependencies = getProjectDependecies(depProject);
				depDependencies.getDependencies(contextIUs).stream()
						.filter(dep -> isFragment(dep) && hasAnyHost(dep, getProjectUnits(depProject)))
						.flatMap(dependency -> getProject(dependency).stream())
						.forEach(fragmentProject -> {
							if (!depsWithFragments.contains(fragmentProject)) {
								depsWithFragments.add(fragmentProject);
								logger.debug("Attaching fragment " + fragmentProject.getArtifactId() 
										+ " to " + depProject.getArtifactId());
							}
						});
			}
		}
		
		// Also check if the project itself has fragments that should be attached
		projectDependecies.getDependencies(contextIUs).stream()
				.filter(dep -> isFragment(dep) && hasAnyHost(dep, projectUnits))
				.flatMap(dependency -> getProject(dependency).stream())
				.forEach(fragmentProject -> {
					if (!depsWithFragments.contains(fragmentProject)) {
						depsWithFragments.add(fragmentProject);
						logger.debug("Attaching fragment " + fragmentProject.getArtifactId() 
								+ " to " + mavenProject.getArtifactId());
					}
				});
		
		// Step 2: Filter out cyclic dependencies
		List<MavenProject> filtered = new ArrayList<>();
		Set<MavenProject> removed = new HashSet<>();
		List<Edge> keptEdges = new ArrayList<>();
		Set<Edge> removedEdges = new HashSet<>();
		
		for (MavenProject depProject : depsWithFragments) {
			// Check if including this dependency creates a cycle
			if (createsCycleWithProject(mavenProject, depProject)) {
				// Determine how to handle this based on the edge type
				boolean isFragmentDep = isFragment(depProject);
				boolean shouldRemove = false;
				
				if (isFragmentDep) {
					// Fragment in cycle - remove it
					shouldRemove = true;
					removed.add(depProject);
					logger.debug("Removing fragment edge in cycle: " + depProject.getArtifactId());
				} else {
					// Check the edge type
					List<Edge> edgesToDep = getEdges(mavenProject, depProject);
					boolean hasCompileRequired = edgesToDep.stream()
							.anyMatch(e -> e.type == EdgeType.COMPILE_REQUIRED);
					
					if (hasCompileRequired) {
						// Compile required - check for alternative
						boolean hasAlternative = hasAlternativeProviderForProject(
								depProject, depsWithFragments, mavenProject, contextIUs);
						
						if (hasAlternative) {
							shouldRemove = true;
							removed.add(depProject);
							removedEdges.addAll(edgesToDep);
							logger.info("Removing compile-required edge with alternative provider: " 
									+ depProject.getArtifactId());
						} else {
							// No alternative - this is an error
							String errorMsg = "Cyclic compile-required dependency detected: " 
									+ mavenProject.getArtifactId() + " -> " + depProject.getArtifactId();
							logger.error(errorMsg);
							throw new IllegalStateException(errorMsg);
						}
					} else {
						// Other type - can be removed
						shouldRemove = true;
						removed.add(depProject);
						removedEdges.addAll(edgesToDep);
						logger.info("Removing edge in cycle: " + depProject.getArtifactId());
					}
				}
				
				if (!shouldRemove) {
					filtered.add(depProject);
					keptEdges.addAll(getEdges(mavenProject, depProject));
				}
			} else {
				// No cycle - keep it
				filtered.add(depProject);
				keptEdges.addAll(getEdges(mavenProject, depProject));
			}
		}
		
		// Step 3: Write filtered DOT file if DUMP_DATA is enabled
		if (DUMP_DATA && mavenProject.getBasedir() != null) {
			try {
				File dotFile = new File(mavenProject.getBasedir(), "project-dependencies-filtered.dot");
				DotDump.dumpFiltered(dotFile, this, mavenProject, keptEdges, removedEdges);
			} catch (IOException e) {
				// Ignore dump errors
			}
		}
		
		return filtered;
	}
	
	/**
	 * Get all edges from source project to target project
	 */
	private List<Edge> getEdges(MavenProject source, MavenProject target) {
		List<Edge> result = new ArrayList<>();
		List<Edge> sourceEdges = projectEdgesMap.getOrDefault(source, List.of());
		for (Edge edge : sourceEdges) {
			if (edge.capability.project.equals(target)) {
				result.add(edge);
			}
		}
		return result;
	}
	
	/**
	 * Check if adding a dependency from source to target would create a cycle
	 */
	private boolean createsCycleWithProject(MavenProject source, MavenProject target) {
		// If source equals target, it's definitely a cycle
		if (source.equals(target)) {
			return true;
		}
		
		// Use BFS to check if there's a path from target back to source in the graph
		Set<MavenProject> visited = new HashSet<>();
		List<MavenProject> queue = new ArrayList<>();
		queue.add(target);
		visited.add(target);
		
		while (!queue.isEmpty()) {
			MavenProject current = queue.remove(0);
			
			// Get all edges from current project
			List<Edge> currentEdges = projectEdgesMap.getOrDefault(current, List.of());
			for (Edge edge : currentEdges) {
				MavenProject next = edge.capability.project;
				
				// If we reached source, we have a cycle
				if (next.equals(source)) {
					return true;
				}
				
				// Continue BFS
				if (!visited.contains(next)) {
					visited.add(next);
					queue.add(next);
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Check if there's an alternative provider for the capabilities provided by a project
	 */
	private boolean hasAlternativeProviderForProject(MavenProject project, 
			List<MavenProject> allDeps, MavenProject excludeProject, Collection<IInstallableUnit> contextIUs) {
		// Get capabilities provided by the project
		Collection<IInstallableUnit> projectUnits = getProjectUnits(project);
		
		// Check if any other project in allDeps provides the same capabilities
		for (MavenProject altProject : allDeps) {
			if (altProject.equals(project) || altProject.equals(excludeProject)) {
				continue;
			}
			
			// Check if altProject provides any of the same capabilities
			Collection<IInstallableUnit> altUnits = getProjectUnits(altProject);
			
			// Check if there's overlap in provided capabilities
			for (IInstallableUnit projectUnit : projectUnits) {
				for (IInstallableUnit altUnit : altUnits) {
					// If they have the same ID, it's an alternative
					if (projectUnit.getId().equals(altUnit.getId())) {
						return true;
					}
				}
			}
		}
		
		return false;
	}

	private static boolean isFragment(IInstallableUnit installableUnit) {
		return getFragmentCapability(installableUnit).findAny().isPresent();
	}

	private static Stream<IProvidedCapability> getFragmentCapability(IInstallableUnit installableUnit) {

		return installableUnit.getProvidedCapabilities().stream()
				.filter(cap -> BundlesAction.CAPABILITY_NS_OSGI_FRAGMENT.equals(cap.getNamespace()));
	}

	private static boolean hasAnyHost(IInstallableUnit unit, Iterable<IInstallableUnit> collection) {
		return getFragmentHostRequirement(unit).anyMatch(req -> {
			for (IInstallableUnit iu : collection) {
				if (req.isMatch(iu)) {
					return true;
				}
			}
			return false;
		});
	}
	
	private static Stream<IRequirement> getFragmentHostRequirement(IInstallableUnit installableUnit) {
		return getFragmentCapability(installableUnit).map(provided -> {
			String hostName = provided.getName();
			for (IRequirement requirement : installableUnit.getRequirements()) {
				if (requirement instanceof IRequiredCapability requiredCapability) {
					if (hostName.equals(requiredCapability.getName())) {
						return requirement;
					}
				}
			}
			return null;
		}).filter(Objects::nonNull);
	}

}
