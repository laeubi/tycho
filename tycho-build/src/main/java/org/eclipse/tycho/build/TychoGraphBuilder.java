/*******************************************************************************
 * Copyright (c) 2021 Christoph Läubrich and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Christoph Läubrich - initial API and implementation based on 
 *    		org.eclipse.tycho.p2.util.resolution.AbstractSlicerResolutionStrategy
 *    		org.eclipse.tycho.p2.util.resolution.ProjectorResolutionStrategy
 *******************************************************************************/
package org.eclipse.tycho.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.graph.DefaultGraphBuilder;
import org.apache.maven.graph.GraphBuilder;
import org.apache.maven.model.building.Result;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.director.Explanation;
import org.eclipse.equinox.internal.p2.director.Explanation.HardRequirement;
import org.eclipse.equinox.internal.p2.director.Explanation.IUToInstall;
import org.eclipse.equinox.internal.p2.director.Explanation.MissingIU;
import org.eclipse.equinox.internal.p2.director.Projector;
import org.eclipse.equinox.internal.p2.director.QueryableArray;
import org.eclipse.equinox.internal.p2.director.SimplePlanner;
import org.eclipse.equinox.internal.p2.director.Slicer;
import org.eclipse.equinox.internal.p2.metadata.IRequiredCapability;
import org.eclipse.equinox.internal.p2.metadata.RequiredCapability;
import org.eclipse.equinox.internal.p2.metadata.RequiredPropertiesMatch;
import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.metadata.expression.ExpressionUtil;
import org.eclipse.equinox.p2.metadata.expression.IExpression;
import org.eclipse.equinox.p2.metadata.expression.IMatchExpression;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherResult;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeaturesAction;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.tycho.p2.QueryableCollection;
import org.osgi.framework.BundleException;

@Component(role = GraphBuilder.class, hint = GraphBuilder.HINT)
public class TychoGraphBuilder extends DefaultGraphBuilder {

	public static final String TYPE_ECLIPSE_PLUGIN = "eclipse-plugin";
	public static final String TYPE_ECLIPSE_TEST_PLUGIN = "eclipse-test-plugin";
	public static final String TYPE_ECLIPSE_FEATURE = "eclipse-feature";
	public static final String TYPE_ECLIPSE_REPOSITORY = "eclipse-repository";
	public static final String TYPE_ECLIPSE_TARGET_DEFINITION = "eclipse-target-definition";

	@Requirement
	private ToolchainManager toolchainManager;

	@Requirement
	private Logger log;

	@Requirement
	private IProvisioningAgent agent;

	@Override
	public Result<ProjectDependencyGraph> build(MavenSession session) {
		session.getUserProperties().put("tycho.mode", "extension");
		System.out.println("TychoGraphBuilder.build()");
		MavenExecutionRequest request = session.getRequest();
		ProjectDependencyGraph dependencyGraph = session.getProjectDependencyGraph();
		System.out.println("  - SelectedProjects: " + request.getSelectedProjects());
		System.out.println("  - ExcludedProjects: " + request.getExcludedProjects());
		System.out.println("  - MakeBehavior:     " + request.getMakeBehavior());
		System.out.println("  - DependencyGraph:  " + dependencyGraph);
		List<Toolchain> jdks = toolchainManager.getToolchains(session, "jdk", null);
		System.out.println("  - Toolchain JDKs:   " + jdks);

		Result<ProjectDependencyGraph> build = super.build(session);
		if (dependencyGraph != null) {
			return build;
		}

		List<MavenProject> projects = build.get().getAllProjects();
		Map<MavenProject, Collection<IInstallableUnit>> rootIus = new ConcurrentHashMap<>();
		FeatureParser parser = new FeatureParser();
		PublisherInfo publisherInfo = new PublisherInfo();
		publisherInfo.setArtifactOptions(IPublisherInfo.A_INDEX);
		for (MavenProject project : projects) {
			if (TYPE_ECLIPSE_PLUGIN.equals(project.getPackaging())
					|| TYPE_ECLIPSE_TEST_PLUGIN.equals(project.getPackaging())) {

				File basedir = project.getBasedir();
				System.out.print(basedir);
				File mf = new File(basedir, JarFile.MANIFEST_NAME);
				if (mf.exists()) {
					System.out.println(" --> Bundle");
					try {
						BundleDescription bundleDescription = BundlesAction.createBundleDescription(basedir);
						IArtifactKey descriptor = BundlesAction.createBundleArtifactKey(
								bundleDescription.getSymbolicName(), bundleDescription.getVersion().toString());
						IInstallableUnit iu = BundlesAction.createBundleIU(bundleDescription, descriptor,
								publisherInfo);
						System.out.println("   iu = " + iu);
						rootIus.put(project, Collections.singletonList(iu));
					} catch (IOException | BundleException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					System.out.println("---> ?");
				}
			} else if (TYPE_ECLIPSE_FEATURE.equals(project.getPackaging())) {
				File basedir = project.getBasedir();
				System.out.print(basedir);
				System.out.println(" --> Feature");
				Feature feature = parser.parse(basedir);
				Map<IInstallableUnit, Feature> featureMap = new HashMap<>();
				FeaturesAction action = new FeaturesAction(new Feature[] { feature }) {
					@Override
					protected void publishFeatureArtifacts(Feature feature, IInstallableUnit featureIU,
							IPublisherInfo publisherInfo) {
						// so not call super as we don't wan't to copy anything --> Bug in P2 with
						// IPublisherInfo.A_INDEX option
						// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=578380
					}

					@Override
					protected IInstallableUnit generateFeatureJarIU(Feature feature, IPublisherInfo publisherInfo) {
						IInstallableUnit iu = super.generateFeatureJarIU(feature, publisherInfo);
						featureMap.put(iu, feature);
						return iu;
					}
				};
				PublisherResult results = new PublisherResult();
				action.perform(publisherInfo, results, null);
				Set<IInstallableUnit> result = results.query(QueryUtil.ALL_UNITS, null).toSet();
				for (IInstallableUnit unit : result) {
					System.out.println("   iu = " + unit);
				}
				rootIus.put(project, result);

			}
		}
		Map<String, String> properties = new HashMap<String, String>();

		Map<MavenProject, Collection<IInstallableUnit>> projectDependecies = new HashMap<MavenProject, Collection<IInstallableUnit>>();
		Collection<IInstallableUnit> availableIUs = rootIus.values().stream().flatMap(Collection::stream)
				/* .filter(u -> !seedUnits.contains(u)) */.collect(Collectors.toSet());
		for (Entry<MavenProject, Collection<IInstallableUnit>> entry : rootIus.entrySet()) {
			List<IInstallableUnit> additionalUnits = new ArrayList<>();
			Set<IRequirement> missingReq = new HashSet<IRequirement>();
			Collection<IInstallableUnit> missingUnits = new HashSet<IInstallableUnit>();
			System.out.println("#### ---- resolving [" + entry.getKey() + "] ---- ####");
			try {
				Collection<IInstallableUnit> seedUnits = entry.getValue();
				Collection<IInstallableUnit> resolve = resolveInternal(properties, additionalUnits, missingReq,
						missingUnits, seedUnits, availableIUs);
				resolve.removeAll(seedUnits);
				resolve.removeAll(additionalUnits);
//				for (IInstallableUnit resolved : resolve) {
//					if (additionalUnits.contains(resolved)) {
//						continue;
//					}
//					System.out.println("\t require " + resolved);
//
//				}
//				System.out.println("");
				projectDependecies.put(entry.getKey(), resolve);
				System.out.println("::: the following requirements are missing (" + missingReq.size() + ") ::::");
				for (IRequirement requirement : missingReq) {
					System.out.println(requirement);
				}
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
//		System.out.println("::: the following units are missing (" + missingUnits.size() + ") ::::");
//		for (IInstallableUnit missing : missingUnits) {
//			System.out.println(missing);
//		}
		for (Entry<MavenProject, Collection<IInstallableUnit>> dependecies : projectDependecies.entrySet()) {
			System.out.println("#### ---- [" + dependecies.getKey().getName() + "] ---- ####");
			for (IInstallableUnit ius : dependecies.getValue()) {
				System.out.println("\t require " + ius);
			}
		}

		// projects

//			resolveProjects(session, projects);
		ProjectDependencyGraph graph = new ProjectDependencyGraph() {

			@Override
			public List<MavenProject> getUpstreamProjects(MavenProject project, boolean transitive) {
				System.out.println(
						"TychoGraphBuilder.build(...).new ProjectDependencyGraph() {...}.getUpstreamProjects()");
				// TODO Auto-generated method stub
				return build.get().getDownstreamProjects(project, transitive);
			}

			@Override
			public List<MavenProject> getSortedProjects() {
				System.out
						.println("TychoGraphBuilder.build(...).new ProjectDependencyGraph() {...}.getSortedProjects()");
				// TODO Auto-generated method stub
				return build.get().getAllProjects();
			}

			@Override
			public List<MavenProject> getDownstreamProjects(MavenProject project, boolean transitive) {
				System.out.println(
						"TychoGraphBuilder.build(...).new ProjectDependencyGraph() {...}.getDownstreamProjects()");
				// TODO Auto-generated method stub
				return build.get().getDownstreamProjects(project, transitive);
			}

			@Override
			public List<MavenProject> getAllProjects() {
				System.out.println("TychoGraphBuilder.build(...).new ProjectDependencyGraph() {...}.getAllProjects()");
				// TODO Auto-generated method stub
				return projects;
			}
		};
		// TODO add problems here!
		return Result.newResult(graph, build.getProblems());
	}

	protected Collection<IInstallableUnit> resolveInternal(Map<String, String> properties,
			List<IInstallableUnit> additionalUnits, Collection<IRequirement> missingReq,
			Collection<IInstallableUnit> unitsToInstall, Collection<IInstallableUnit> seedUnits,
			Collection<IInstallableUnit> availableIUs) throws CoreException {
		System.out.println("resolve...");
		Map<String, String> newSelectionContext = SimplePlanner.createSelectionContext(properties);

		IQueryable<IInstallableUnit> slice = slice(properties, additionalUnits, availableIUs, seedUnits);

		List<IRequirement> seedRequires = new ArrayList<>();
//		if (data.getAdditionalRequirements() != null) {
//			seedRequires.addAll(data.getAdditionalRequirements());
//		}

		// force profile UIs to be used during resolution
//		seedUnits.addAll(data.getEEResolutionHints().getMandatoryUnits());
//		seedRequires.addAll(data.getEEResolutionHints().getMandatoryRequires());

		Projector projector = new Projector(slice, newSelectionContext, new HashSet<IInstallableUnit>(), false);
		projector.encode(createUnitRequiring("tycho", seedUnits, seedRequires),
				new IInstallableUnit[0] /* alreadyExistingRoots */,
				new QueryableArray(new IInstallableUnit[0]) /* installedIUs */, seedUnits /* newRoots */,
				new NullProgressMonitor());
		IStatus s = projector.invokeSolver(new NullProgressMonitor());
		if (s.getSeverity() == IStatus.ERROR) {
			Set<Explanation> explanation = projector.getExplanation(new NullProgressMonitor()); // suppress "Cannot
																								// complete the request.
																								// Generating details."
			List<IRequirement> missingRequirements = new ArrayList<>();
			List<IInstallableUnit> missingIUs = new ArrayList<>();
			Set<IInstallableUnit> incompleteUnits = new HashSet<>();
			outer: for (Explanation exp : explanation) {
				if (exp instanceof MissingIU) {
					MissingIU missingIU = (MissingIU) exp;
					if (incompleteUnits.contains(missingIU.iu)) {
						log.info("Ignore missing IU that is incomplete > " + missingIU.iu);
					} else {
						log.info("Recording missing MissingIU requirement for IU " + missingIU.iu + ": "
								+ missingIU.req);
						missingRequirements.add(missingIU.req);
						missingReq.add(missingIU.req);
						incompleteUnits.add(missingIU.iu);
					}
				} else if (exp instanceof HardRequirement) {
					HardRequirement hardRequirement = (HardRequirement) exp;

					incompleteUnits.add(hardRequirement.iu);
					for (IInstallableUnit incomplete : incompleteUnits) {
						if (hardRequirement.req.isMatch(incomplete)) {
							log.info("Ignore missing HardRequirement for incomplete IU " + hardRequirement.iu);
							continue outer;
						}
					}
					log.info("Recording missing HardRequirement for IU " + hardRequirement.iu + ": "
							+ hardRequirement.req);
					missingRequirements.add(hardRequirement.req);
					missingReq.add(hardRequirement.req);
				} else if (exp instanceof IUToInstall) {
//this is one of the root IUs and we don't need to mind (here)
				} else {
					log.info("Ignoring Explanation of type " + exp.getClass()
							+ " in computation of missing requirements: " + exp);
				}
			}
			if (missingRequirements.size() > 0) {
				// only start a new resolve if we have collected additional requirements...
				IInstallableUnit providing = createUnitProviding("tycho.unresolved.requirements", missingRequirements);
				if (providing.getProvidedCapabilities().size() > 0) {
					// ... and we could provide additional capabilities
					missingIUs.add(providing);
				}
			}
			if (missingIUs.size() > 0) {
				additionalUnits.addAll(missingIUs);
				return resolveInternal(properties, additionalUnits, missingRequirements, unitsToInstall, seedUnits,
						availableIUs);
			}
			throw new CoreException(s);
		}
		return projector.extractSolution();
	}

	protected final IQueryable<IInstallableUnit> slice(Map<String, String> properties,
			List<IInstallableUnit> additionalUnits, Collection<IInstallableUnit> availableIUs,
			Collection<IInstallableUnit> seedIUs) throws CoreException {

		availableIUs.addAll(additionalUnits);

		Slicer slicer = newSlicer(new QueryableCollection(availableIUs), properties);
		IQueryable<IInstallableUnit> slice = slicer.slice(seedIUs.toArray(new IInstallableUnit[0]),
				new NullProgressMonitor());
		MultiStatus slicerStatus = slicer.getStatus();
		if (slice == null || isSlicerError(slicerStatus)) {
			throw new CoreException(slicerStatus);
		}

		return slice;
	}

	protected Slicer newSlicer(IQueryable<IInstallableUnit> availableUnits, Map<String, String> properties) {
		return new Slicer(availableUnits, properties, false) {
			@Override
			protected boolean isApplicable(IInstallableUnit iu) {
				// we don't care...
				return true;
			}
		};
	}

	protected static IInstallableUnit createUnitRequiring(String name, Collection<IInstallableUnit> units,
			Collection<IRequirement> additionalRequirements) {

		InstallableUnitDescription result = new MetadataFactory.InstallableUnitDescription();
		String time = Long.toString(System.currentTimeMillis());
		result.setId(name + "-" + time);
		result.setVersion(Version.createOSGi(0, 0, 0, time));

		ArrayList<IRequirement> requirements = new ArrayList<>();
		if (units != null) {
			for (IInstallableUnit unit : units) {
				requirements.add(createStrictRequirementTo(unit));
			}
		}
		if (additionalRequirements != null) {
			requirements.addAll(additionalRequirements);
		}

		result.addRequirements(requirements);
		return MetadataFactory.createInstallableUnit(result);
	}

	private static IRequirement createStrictRequirementTo(IInstallableUnit unit) {
		VersionRange strictRange = new VersionRange(unit.getVersion(), true, unit.getVersion(), true);
		int min = 1;
		int max = Integer.MAX_VALUE;
		boolean greedy = true;
		IRequirement requirement = MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, unit.getId(),
				strictRange, unit.getFilter(), min, max, greedy);
		return requirement;
	}

	protected boolean isSlicerError(MultiStatus slicerStatus) {
		return slicerStatus.matches(IStatus.ERROR | IStatus.CANCEL);
	}

	protected IInstallableUnit createUnitProviding(String name, Collection<IRequirement> requirements) {

		InstallableUnitDescription result = new MetadataFactory.InstallableUnitDescription();
		String time = Long.toString(System.currentTimeMillis());
		result.setId(name + "-" + time);
		result.setVersion(Version.createOSGi(0, 0, 0, time));
		for (IRequirement requirement : requirements) {
			if (requirement instanceof IRequiredCapability) {
				try {
					IRequiredCapability capability = (IRequiredCapability) requirement;
					String namespace = capability.getNamespace();
					IMatchExpression<IInstallableUnit> matches = capability.getMatches();
					String extractName = RequiredCapability.extractName(matches);
					Version version = RequiredCapability.extractRange(matches).getMinimum();
					IProvidedCapability providedCapability = MetadataFactory.createProvidedCapability(namespace,
							extractName, version);
					result.addProvidedCapabilities(Collections.singleton(providedCapability));
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			} else if (requirement instanceof RequiredPropertiesMatch) {
				try {
					RequiredPropertiesMatch propertiesMatch = (RequiredPropertiesMatch) requirement;
					IMatchExpression<IInstallableUnit> matches = propertiesMatch.getMatches();
					String namespace = RequiredPropertiesMatch.extractNamespace(matches);
					// TODO only on osgi.ee namespace ...
//					System.out.println("ns: " + namespace + ", prop " + property);
					Map<String, Object> properties = new HashMap<>();
					Object[] params = matches.getParameters();
					// TODO then simply use second? Or use ExpressionUtil
					for (Object p : params) {
						if (p instanceof IExpression) {

							IExpression expression = (IExpression) p;
							IExpression operand = ExpressionUtil.getOperand(expression);
							IExpression[] operands = ExpressionUtil.getOperands(operand);
							for (IExpression eq : operands) {
								IExpression lhs = ExpressionUtil.getLHS(eq);
								IExpression rhs = ExpressionUtil.getRHS(eq);
								Object value = ExpressionUtil.getValue(rhs);
								if (IProvidedCapability.PROPERTY_VERSION.equals(lhs.toString())) {
									properties.put(lhs.toString(), Version.create(value.toString()));
								} else {
									properties.put(lhs.toString(), value.toString());
								}
							}
						}
					}
					IProvidedCapability providedCapability = MetadataFactory.createProvidedCapability(namespace,
							properties);
					result.addProvidedCapabilities(Collections.singleton(providedCapability));
				} catch (RuntimeException e) {
					e.printStackTrace();
				}

			} else {
				System.out.println("??? " + requirement);
			}
		}
		return MetadataFactory.createInstallableUnit(result);
	}

}
