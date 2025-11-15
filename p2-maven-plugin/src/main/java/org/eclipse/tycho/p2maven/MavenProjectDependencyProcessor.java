/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.equinox.internal.p2.metadata.IRequiredCapability;
import org.eclipse.equinox.internal.p2.metadata.InstallableUnit;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.expression.IMatchExpression;
import org.eclipse.equinox.p2.query.CollectionResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.tycho.p2maven.io.MetadataIO;
import org.eclipse.tycho.p2maven.tmp.BundlesAction;

/**
 * THis component computes dependencies between projects
 *
 */
@Named
@Singleton
public class MavenProjectDependencyProcessor {

	private static final ProjectDependencies EMPTY_DEPENDENCIES = new ProjectDependencies(Map.of(), Set.of());

	private static final boolean DUMP_DATA = Boolean.getBoolean("tycho.p2.dump")
			|| Boolean.getBoolean("tycho.p2.dump.dependencies");

	@Inject
	private InstallableUnitGenerator generator;

	@Inject
	private InstallableUnitSlicer slicer;

	/**
	 * Computes the {@link ProjectDependencyClosure} of the given collection of
	 * projects.
	 * 
	 * @param projects                  the projects to include in the closure
	 * @param session                   the maven session for this request
	 * @param profilePropertiesSupplier supplier of context IUs for a project that
	 *                                  represent the the profile properties to
	 *                                  consider during resolution, can be empty in
	 *                                  which case a filter is always considered a
	 *                                  match
	 * @return the computed {@link ProjectDependencyClosure}
	 * @throws CoreException if computation failed
	 */
	public ProjectDependencyClosure computeProjectDependencyClosure(Collection<MavenProject> projects,
			MavenSession session)
			throws CoreException {
		Objects.requireNonNull(session);
		Map<MavenProject, Collection<IInstallableUnit>> projectIUMap = generator.getInstallableUnits(projects, session);
		Collection<IInstallableUnit> availableIUs = projectIUMap.values().stream().flatMap(Collection::stream)
				.collect(Collectors.toSet());
		Map<MavenProject, ProjectDependencies> projectDependenciesMap = computeProjectDependencies(projects,
				new CollectionResult<>(availableIUs), projectIUMap);
		Map<IInstallableUnit, MavenProject> iuProjectMap = new HashMap<>();
		for (var entry : projectIUMap.entrySet()) {
			MavenProject mavenProject = entry.getKey();
			for (IInstallableUnit iu : entry.getValue()) {
				iuProjectMap.put(iu, mavenProject);
			}
		}
		return new ProjectDependencyClosure() {

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
				return projectDependenciesMap.entrySet().stream()
						.map(pd -> new SimpleEntry<>(pd.getKey(),
								pd.getValue().getDependencies(contextIuSupplier.apply(pd.getKey()))));
			}

			@Override
			public boolean isFragment(MavenProject mavenProject) {

				return getProjectUnits(mavenProject).stream().anyMatch(MavenProjectDependencyProcessor::isFragment);
			}

			@Override
			public Collection<IInstallableUnit> getProjectUnits(MavenProject mavenProject) {
				Collection<IInstallableUnit> collection = projectIUMap.get(mavenProject);
				if (collection != null) {
					return collection;
				}
				return Collections.emptyList();
			}

		};
	}

	/**
	 * Given a set of projects, compute the mapping of a project to its dependencies
	 * 
	 * @param projects    the projects to investigate
	 * @param avaiableIUs all available units that should be used to fulfill project
	 *                    requirements
	 * @return a Map from the passed projects to their dependencies
	 * @throws CoreException if computation failed
	 */
	private Map<MavenProject, ProjectDependencies> computeProjectDependencies(Collection<MavenProject> projects,
			IQueryable<IInstallableUnit> avaiableIUs, Map<MavenProject, Collection<IInstallableUnit>> projectIUMap)
			throws CoreException {
		List<CoreException> errors = new CopyOnWriteArrayList<>();
		Map<MavenProject, ProjectDependencies> result = new ConcurrentHashMap<>();
		projects.parallelStream().unordered().takeWhile(nil -> errors.isEmpty()).forEach(project -> {
			try {
				ProjectDependencies projectDependencies = computeProjectDependencies(
						Set.copyOf(projectIUMap.get(project)),
						avaiableIUs);
				result.put(project, projectDependencies);
				if (DUMP_DATA) {
					File file = new File(project.getBasedir(), "project-dependencies.xml");
					try {
						new MetadataIO().writeXML(
								Collections.unmodifiableCollection(projectDependencies.getDependencies(List.of())),
								file);
					} catch (IOException e) {
					}
				}
			} catch (CoreException e) {
				errors.add(e);
			}
		});
		if (errors.isEmpty()) {
			return result;
		}
		if (errors.size() == 1) {
			throw errors.get(0);
		}
		MultiStatus multiStatus = new MultiStatus(InstallableUnitGenerator.class, IStatus.ERROR,
				"computing installable unit units failed");
		errors.forEach(e -> multiStatus.add(e.getStatus()));
		throw new CoreException(multiStatus);
	}

	/**
	 * Given a {@link MavenProject} and a collection of {@link IInstallableUnit},
	 * compute the collection of dependencies that fulfill the projects requirements
	 * 
	 * @param project     the project to query for requirements
	 * @param avaiableIUs all available units that should be used to fulfill project
	 *                    requirements
	 * @return the collection of dependent {@link InstallableUnit}s
	 * @throws CoreException if computation failed
	 */
	private ProjectDependencies computeProjectDependencies(Set<IInstallableUnit> projectUnits,
			IQueryable<IInstallableUnit> avaiableIUs)
			throws CoreException {
		if (projectUnits.isEmpty()) {
			return EMPTY_DEPENDENCIES;
		}
		Map<IRequirement, Collection<IInstallableUnit>> dependencies = slicer.computeDirectDependencies(projectUnits,
				avaiableIUs);
		return new ProjectDependencies(dependencies, projectUnits);
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

	private static boolean isFragment(IInstallableUnit installableUnit) {
		return getFragmentCapability(installableUnit).findAny().isPresent();
	}

	private static Stream<IProvidedCapability> getFragmentCapability(IInstallableUnit installableUnit) {

		return installableUnit.getProvidedCapabilities().stream()
				.filter(cap -> BundlesAction.CAPABILITY_NS_OSGI_FRAGMENT.equals(cap.getNamespace()));
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

	private static boolean isMatch(IRequirement requirement, Collection<IInstallableUnit> contextIUs) {
		IMatchExpression<IInstallableUnit> filter = requirement.getFilter();
		if (filter == null || contextIUs.isEmpty()) {
			return true;
		}
		return contextIUs.stream().anyMatch(contextIU -> filter.isMatch(contextIU));
	}

	public static final class ProjectDependencies {

		final Map<IRequirement, Collection<IInstallableUnit>> requirementsMap;
		private final Set<IInstallableUnit> projectUnits;

		ProjectDependencies(Map<IRequirement, Collection<IInstallableUnit>> requirementsMap,
				Set<IInstallableUnit> projectUnits) {
			this.requirementsMap = requirementsMap;
			this.projectUnits = projectUnits;
		}

		public Collection<IInstallableUnit> getDependencies(Collection<IInstallableUnit> contextIUs) {
			return requirementsMap.entrySet().stream().filter(entry -> isMatch(entry.getKey(), contextIUs))
					.flatMap(entry -> entry.getValue().stream().filter(unit -> !projectUnits.contains(unit))
							.limit(entry.getKey().getMax()))
					.distinct().toList();
		}


	}

	/**
	 * Simple graph structure for dependency cycle detection
	 */
	public static class ProjectDependencyGraph {
		private final Map<MavenProject, List<DependencyEdge>> adjacencyList;
		
		public ProjectDependencyGraph() {
			this.adjacencyList = new HashMap<>();
		}
		
		public void addEdge(MavenProject from, MavenProject to, String requirementInfo, String capabilityInfo) {
			adjacencyList.computeIfAbsent(from, k -> new ArrayList<>())
				.add(new DependencyEdge(to, requirementInfo, capabilityInfo));
		}
		
		public List<MavenProject> getDirectDependencies(MavenProject project) {
			return adjacencyList.getOrDefault(project, List.of()).stream()
				.map(edge -> edge.target)
				.toList();
		}
		
		/**
		 * Detect all cycles in the dependency graph
		 * @return list of cycles, where each cycle is a list of projects forming the cycle
		 */
		public List<List<DependencyEdge>> detectCycles() {
			List<List<DependencyEdge>> cycles = new ArrayList<>();
			Set<MavenProject> visited = new HashSet<>();
			Set<MavenProject> recursionStack = new HashSet<>();
			LinkedHashMap<MavenProject, DependencyEdge> path = new LinkedHashMap<>();
			
			for (MavenProject project : adjacencyList.keySet()) {
				if (!visited.contains(project)) {
					detectCyclesDFS(project, visited, recursionStack, path, cycles);
				}
			}
			
			return cycles;
		}
		
		private void detectCyclesDFS(MavenProject current, Set<MavenProject> visited, 
				Set<MavenProject> recursionStack, LinkedHashMap<MavenProject, DependencyEdge> path,
				List<List<DependencyEdge>> cycles) {
			
			visited.add(current);
			recursionStack.add(current);
			
			List<DependencyEdge> edges = adjacencyList.getOrDefault(current, List.of());
			for (DependencyEdge edge : edges) {
				MavenProject next = edge.target;
				
				if (recursionStack.contains(next)) {
					// Cycle detected! Extract the cycle from the path
					List<DependencyEdge> cycle = new ArrayList<>();
					boolean inCycle = false;
					
					// Extract cycle from path - find where the cycle starts
					for (Map.Entry<MavenProject, DependencyEdge> entry : path.entrySet()) {
						MavenProject pathNode = entry.getKey();
						if (pathNode.equals(next)) {
							inCycle = true;
						}
						if (inCycle && entry.getValue() != null) {
							cycle.add(entry.getValue());
						}
					}
					// Add the edge that completes the cycle
					cycle.add(edge);
					cycles.add(cycle);
				} else if (!visited.contains(next)) {
					path.put(current, edge); // Map from source to edge going to target
					detectCyclesDFS(next, visited, recursionStack, path, cycles);
					path.remove(current);
				}
			}
			
			recursionStack.remove(current);
		}
		
		/**
		 * Represents an edge in the dependency graph
		 */
		public static class DependencyEdge {
			public final MavenProject target;
			public final String requirementInfo;
			public final String capabilityInfo;
			
			public DependencyEdge(MavenProject target, String requirementInfo, String capabilityInfo) {
				this.target = target;
				this.requirementInfo = requirementInfo;
				this.capabilityInfo = capabilityInfo;
			}
		}
	}

	public static interface ProjectDependencyClosure {

		/**
		 * 
		 * @param dependency
		 * @return the project that provides the given {@link IInstallableUnit} or an
		 *         empty Optional if no project in this closure provides this unit.
		 */
		Optional<MavenProject> getProject(IInstallableUnit dependency);

		/**
		 * @param mavenProject
		 * @return the dependencies of this project inside the closure
		 */
		ProjectDependencies getProjectDependecies(MavenProject mavenProject);

		Collection<IInstallableUnit> getProjectUnits(MavenProject mavenProject);

		/**
		 * @return a stream of all contained maven projects with dependecies
		 */
		Stream<Entry<MavenProject, Collection<IInstallableUnit>>> dependencies(
				Function<MavenProject, Collection<IInstallableUnit>> contextIuSupplier);

		/**
		 * Build a dependency graph for cycle detection
		 */
		default ProjectDependencyGraph buildDependencyGraph(Collection<IInstallableUnit> contextIUs) {
			ProjectDependencyGraph graph = new ProjectDependencyGraph();
			Set<MavenProject> processed = new HashSet<>();
			
			// Build graph by traversing all projects
			dependencies(always -> contextIUs).forEach(entry -> {
				MavenProject project = entry.getKey();
				if (processed.add(project)) {
					buildGraphForProject(project, contextIUs, graph);
				}
			});
			
			return graph;
		}
		
		/**
		 * Build graph edges for a single project
		 */
		default void buildGraphForProject(MavenProject project, Collection<IInstallableUnit> contextIUs, 
				ProjectDependencyGraph graph) {
			ProjectDependencies projectDependencies = getProjectDependecies(project);
			List<MavenProject> directDeps = projectDependencies.getDependencies(contextIUs).stream()
					.flatMap(dependency -> getProject(dependency).stream()).distinct().toList();
			
			for (MavenProject depProject : directDeps) {
				String requirementInfo = buildRequirementInfo(project, depProject, projectDependencies, contextIUs);
				String capabilityInfo = getProjectProvidesInfo(depProject);
				graph.addEdge(project, depProject, requirementInfo, capabilityInfo);
			}
		}
		
		/**
		 * Build requirement information for an edge
		 */
		default String buildRequirementInfo(MavenProject fromProject, MavenProject toProject,
				ProjectDependencies fromDependencies, Collection<IInstallableUnit> contextIUs) {
			Collection<IInstallableUnit> toUnits = getProjectUnits(toProject);
			
			List<String> requirementInfos = new ArrayList<>();
			fromDependencies.requirementsMap.entrySet().stream()
					.filter(entry -> isMatch(entry.getKey(), contextIUs))
					.forEach(entry -> {
						IRequirement req = entry.getKey();
						Collection<IInstallableUnit> satisfyingUnits = entry.getValue();
						
						boolean satisfiedByTarget = satisfyingUnits.stream()
								.anyMatch(iu -> toUnits.contains(iu));
						
						if (satisfiedByTarget) {
							String reqInfo = getRequirementDescription(req);
							requirementInfos.add("[requires " + reqInfo + "]");
						}
					});
			
			return requirementInfos.isEmpty() ? "" : String.join("", requirementInfos);
		}

		/**
		 * Get information about what capabilities a project provides
		 */
		default String getProjectProvidesInfo(MavenProject project) {
			Collection<IInstallableUnit> projectUnits = getProjectUnits(project);
			List<String> provides = new ArrayList<>();
			
			for (IInstallableUnit iu : projectUnits) {
				for (IProvidedCapability cap : iu.getProvidedCapabilities()) {
					// Only include interesting capabilities (exclude standard OSGi metadata)
					String namespace = cap.getNamespace();
					if (!"osgi.identity".equals(namespace) && 
						!"org.eclipse.equinox.p2.eclipse.type".equals(namespace) &&
						!"org.eclipse.equinox.p2.iu".equals(namespace) &&
						!("osgi.bundle".equals(namespace) && cap.getName().equals(project.getArtifactId()))) {
						provides.add("[provides " + namespace + ":" + cap.getName() + "]");
					}
				}
			}
			
			return provides.isEmpty() ? "" : String.join("", provides);
		}

		/**
		 * Get a human-readable description of a requirement
		 */
		default String getRequirementDescription(IRequirement requirement) {
			if (requirement instanceof IRequiredCapability requiredCapability) {
				return requiredCapability.getNamespace() + ":" + requiredCapability.getName();
			}
			return requirement.toString();
		}

		/**
		 * Format a cycle as a human-readable error message
		 */
		default String formatCycle(List<ProjectDependencyGraph.DependencyEdge> cycle) {
			if (cycle.isEmpty()) {
				return "Empty cycle";
			}
			
			StringBuilder message = new StringBuilder("Dependency cycle detected: ");
			List<String> steps = new ArrayList<>();
			
			// Each edge in the cycle represents: (implicit source) -> target
			// The cycle edges form a chain where each edge's target becomes the next edge's source
			for (ProjectDependencyGraph.DependencyEdge edge : cycle) {
				MavenProject project = edge.target;
				
				String projectInfo = project.getGroupId() + ":" + project.getArtifactId();
				
				// Add capability info for this project
				if (edge.capabilityInfo != null && !edge.capabilityInfo.isEmpty()) {
					projectInfo += edge.capabilityInfo;
				}
				
				// Add requirement info from this edge
				if (edge.requirementInfo != null && !edge.requirementInfo.isEmpty()) {
					projectInfo += edge.requirementInfo;
				}
				
				steps.add(projectInfo);
			}
			
			message.append(String.join(" -> ", steps));
			return message.toString();
		}

		/**
		 * Given a maven project returns all other maven projects this one (directly)
		 * depends on
		 * 
		 * @param mavenProject the maven project for which all direct dependent projects
		 *                     should be collected
		 * @param contextIUs   the context IUs to filter dependencies
		 * @return the collection of projects this maven project depend on in this
		 *         closure
		 */
		default Collection<MavenProject> getDependencyProjects(MavenProject mavenProject,
				Collection<IInstallableUnit> contextIUs) {
			// Build dependency graph and check for cycles
			ProjectDependencyGraph graph = buildDependencyGraph(contextIUs);
			List<List<ProjectDependencyGraph.DependencyEdge>> cycles = graph.detectCycles();
			
			if (!cycles.isEmpty()) {
				// Format and throw exception for the first cycle
				String cycleMessage = formatCycle(cycles.get(0));
				throw new ProjectDependencyCycleException(cycleMessage);
			}
			
			// If no cycle detected, return dependencies as before
			ProjectDependencies projectDependecies = getProjectDependecies(mavenProject);
			List<MavenProject> list = projectDependecies.getDependencies(contextIUs).stream()
					.flatMap(dependency -> getProject(dependency).stream()).distinct().toList();
			if (isFragment(mavenProject)) {
				// for projects that are fragments don't do any special processing...
				return list;
			}
			// for regular projects we must check if they have any fragment requirements
			// that must be attached here, example is SWT that defines a requirement to its
			// fragments and if build inside the same reactor with a consumer (e.g. JFace)
			// has to be applied
			return list.stream().flatMap(project -> {
				ProjectDependencies dependecies = getProjectDependecies(project);
				return Stream.concat(Stream.of(project), dependecies.getDependencies(contextIUs).stream()
						.filter(dep -> hasAnyHost(dep, dependecies.projectUnits))
						.flatMap(dependency -> getProject(dependency).stream()));
			}).toList();
		}

		/**
		 * Check if the given unit is a fragment
		 * 
		 * @param installableUnit the unit to check
		 * @return <code>true</code> if this is a fragment, <code>false</code> otherwise
		 */
		boolean isFragment(MavenProject mavenProject);

	}

	/**
	 * Exception thrown when a cycle is detected in project dependencies
	 */
	public static class ProjectDependencyCycleException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public ProjectDependencyCycleException(String message) {
			super(message);
		}
	}
}
