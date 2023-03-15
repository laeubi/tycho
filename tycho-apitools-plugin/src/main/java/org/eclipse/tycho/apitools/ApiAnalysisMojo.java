/*******************************************************************************
 * Copyright (c) 2023 Christoph Läubrich and others.
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
package org.eclipse.tycho.apitools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.core.runtime.internal.adaptor.EclipseAppLauncher;
import org.eclipse.osgi.internal.framework.EquinoxConfiguration;
import org.eclipse.osgi.service.environment.EnvironmentInfo;
import org.eclipse.osgi.service.runnable.ApplicationLauncher;
import org.eclipse.tycho.ArtifactType;
import org.eclipse.tycho.IllegalArtifactReferenceException;
import org.eclipse.tycho.MavenRepositoryLocation;
import org.eclipse.tycho.TargetEnvironment;
import org.eclipse.tycho.TargetPlatform;
import org.eclipse.tycho.core.TychoProjectManager;
import org.eclipse.tycho.core.ee.shared.ExecutionEnvironmentConfiguration;
import org.eclipse.tycho.core.resolver.P2ResolutionResult;
import org.eclipse.tycho.core.resolver.P2ResolutionResult.Entry;
import org.eclipse.tycho.core.resolver.P2Resolver;
import org.eclipse.tycho.core.resolver.P2ResolverFactory;
import org.eclipse.tycho.core.resolver.TargetPlatformConfigurationException;
import org.eclipse.tycho.p2.target.facade.TargetPlatformConfigurationStub;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Performs a PDE-API Tools analysis of this project.
 */
@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ApiAnalysisMojo extends AbstractMojo {

	private static final Map<String, Collection<File>> RESOLVER_CACHE = new ConcurrentHashMap<>();

	private static final Map<String, File> WORKDIR_CACHE = new ConcurrentHashMap<>();

	@Parameter(property = "plugin.artifacts")
	protected List<Artifact> pluginArtifacts;

	@Parameter(property = "project", readonly = true)
	private MavenProject project;

	@Parameter(defaultValue = "eclipse-plugin")
	private Set<String> supportedPackagingTypes;

	@Parameter(defaultValue = "false", property = "tycho.apitools.verify.skip")
	private boolean skip;

	@Parameter(property = "baselines", name = "baselines")
	private List<Repository> baselines;

	@Parameter(defaultValue = "${project.build.directory}/dependencies-list.txt")
	private String dependencyList;

	@Parameter(defaultValue = "https://download.eclipse.org/eclipse/updates/4.28-I-builds")
	private String apiToolsRepo;

	@Component
	private Logger logger;

	@Component
	private ToolchainManager toolchainManager;

	@Parameter(property = "session", readonly = true, required = true)
	private MavenSession session;

	@Component
	private TychoProjectManager projectManager;

	@Component
	private P2ResolverFactory resolverFactory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
			return;
		}
		if (supportedPackagingTypes.contains(project.getPackaging())) {
			StringBuffer sb = new StringBuffer();
			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\r\n");
			sb.append("<?pde version=\"3.8\"?>\r\n");
			sb.append("<target name=\"");
			sb.append(project.getArtifactId());
			sb.append("-apiBaseline\" sequenceNumber=\"1\">\r\n");
			sb.append("<locations>");
			// TODO could/should there actually not be many repositories or only one
			for (Repository repository : baselines) {
				sb.append(
						"<location includeAllPlatforms=\"false\" includeConfigurePhase=\"false\" includeMode=\"planner\" includeSource=\"false\" type=\"InstallableUnit\">\r\n");
				sb.append("<repository location=\"");
				sb.append(repository.getUrl());
				sb.append("\"/>\r\n");
				sb.append("<unit id=\"");
				// FIXME actually it must be the bundle symbolic name!
				sb.append(project.getArtifactId());
				sb.append("\" version=\"0.0.0\"/>\r\n");
				sb.append("</location>\r\n");
			}
			sb.append("</locations>");
			sb.append("</target>");
			Path targetFile = Path.of(project.getBuild().getDirectory(),
					project.getArtifactId() + "-apiBaseline.target");
			try {
				Files.writeString(targetFile, sb, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new MojoExecutionException("Writing target file failed!", e);
			}
			Map<String, String> frameworkProperties = getFrameworkProperties();
//			List<String> args = new ArrayList<>();
//			frameworkProperties.put("-application", "org.eclipse.pde.api.tools.apiAnalyzer");
//			frameworkProperties.put("-project", project.getBasedir().getAbsolutePath());
//			frameworkProperties.put("-baseline", targetFile.toAbsolutePath().toString());
//			frameworkProperties.put("-dependencyList", dependencyList);
//			frameworkProperties.put("-failOnError", "");
			var loader = ServiceLoader.load(ConnectFrameworkFactory.class, getClass().getClassLoader());
			ConnectFrameworkFactory factory = loader.findFirst()
					.orElseThrow(() -> new NoSuchElementException("No ConnectFrameworkFactory found"));
			Framework framework = factory.newFramework(frameworkProperties, new MojoModuleConnector());
			try {
				framework.init();
			} catch (BundleException e) {
				throw new MojoExecutionException("Init framework failed!", e);
			}
			BundleContext systemBundleContext = framework.getBundleContext();
			ServiceTracker<EnvironmentInfo, EnvironmentInfo> environmentInfoTracker = new ServiceTracker<EnvironmentInfo, EnvironmentInfo>(
					systemBundleContext, EnvironmentInfo.class, null);
			environmentInfoTracker.open();
			EquinoxConfiguration configuration = (EquinoxConfiguration) environmentInfoTracker.getService();
//                <args>-application</args>
//                <args>org.eclipse.pde.api.tools.apiAnalyzer</args>
//                <args>-project</args>
//                <args>${project.basedir}</args>
//                <args>-baseline</args>
//                <args>${project.build.directory}/${project.artifactId}-apiBaseline.target</args>
//                <args>-dependencyList</args>
//                <args>${project.build.directory}/dependencies-list.txt</args>
//                <args>-failOnError</args>
			List<String> args = new ArrayList<>();
			args.add("-application");
			args.add("org.eclipse.pde.api.tools.apiAnalyzer");
			args.add("-project");
			args.add(project.getBasedir().getAbsolutePath());
			args.add("-baseline");
			args.add(targetFile.toAbsolutePath().toString());
			args.add("-dependencyList");
			args.add(dependencyList);
			args.add("-failOnError");
			configuration.setAppArgs(args.toArray(String[]::new));
			environmentInfoTracker.close();
			for (File bundleFile : getBundles()) {
				try {
					Bundle bundle = systemBundleContext.installBundle(bundleFile.toURI().toString(),
							new FileInputStream(bundleFile));
					try {
						bundle.start();
					} catch (BundleException e) {
						// not relevant...
					}
				} catch (FileNotFoundException | BundleException e) {
					getLog().warn("Can't install " + bundleFile + ": " + e);
				}
			}
			FrameworkWiring wiring = framework.adapt(FrameworkWiring.class);
			wiring.resolveBundles(Collections.emptyList());
			try {
				framework.start();
			} catch (BundleException e) {
				throw new MojoExecutionException("Start framework failed!", e);
			}

//			ServiceTracker<ParameterizedRunnable, ParameterizedRunnable> runnables = new ServiceTracker<ParameterizedRunnable, ParameterizedRunnable>(
//					systemBundleContext, ParameterizedRunnable.class, null);
//			runnables.open(true);
//			Collection<ParameterizedRunnable> collection = runnables.getTracked().values();
//			for (ParameterizedRunnable parameterizedRunnable : collection) {
//				System.out.println(parameterizedRunnable);
//			}
//			System.out.println("....");
			// context.getServiceReferences(ParameterizedRunnable.class.getName(),
			// "(&(objectClass=" + appClass + ")(eclipse.application=*))");

			EclipseAppLauncher appLauncher = new EclipseAppLauncher(systemBundleContext, false, true, null,
					configuration);
			systemBundleContext.registerService(ApplicationLauncher.class, appLauncher, null);
////			appLauncherRegistration = context.registerService(ApplicationLauncher.class.getName(), appLauncher, null);
//			// must start the launcher AFTER service restration because this method
//			// blocks and runs the application on the current thread. This method
//			// will return only after the application has stopped.
			try {
				Object returnValue = appLauncher.start(null);
				System.out.println(returnValue);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			try {
				framework.stop();
				framework.waitForStop(0);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BundleException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println();
		}
	}

	private Map<String, String> getFrameworkProperties() {
//		addProgramArgs(cli, "-install", runtime.getLocation().getAbsolutePath(), "-configuration",
//				new File(work, "configuration").getAbsolutePath());
//
//		File workspace = new File(work, "data");
//		addProgramArgs(cli, "-data", workspace.getAbsolutePath());

		Map<String, String> map = new LinkedHashMap<String, String>();
		File workDir = WORKDIR_CACHE.computeIfAbsent("apiTools-work-" + Thread.currentThread().getId(), dir -> {
			map.put("osgi.clean", "true");
			try {
				return Files.createTempDirectory("apiAnalyzer-workspace").toFile();
			} catch (IOException e) {
				throw new RuntimeException("Can't create temp directory!", e);
			}
		});
		map.put("osgi.configuration.area", new File(workDir, "configuration").getAbsolutePath());
		map.put("osgi.instance.area", new File(workDir, "data").getAbsolutePath());
		return map;
	}

	private Collection<File> getBundles() {
		return RESOLVER_CACHE.computeIfAbsent(apiToolsRepo, repo -> {
			getLog().info("Resolve API tools runtime from " + repo + "...");

			// TODO actually it would be good to simply have this as plain maven
			// dependencies and then use the pluginArtifacts ...
			// But the maven dependencies are a mess...
			TargetPlatformConfigurationStub tpConfiguration = new TargetPlatformConfigurationStub();
			tpConfiguration.setIgnoreLocalArtifacts(true);
			try {
				tpConfiguration.addP2Repository(new MavenRepositoryLocation("apitools", new URI(repo)));
			} catch (URISyntaxException e) {
				throw new TargetPlatformConfigurationException("Can't resolve API tools repo", e);
			}
			ExecutionEnvironmentConfiguration eeConfiguration = projectManager
					.getExecutionEnvironmentConfiguration(project);
			TargetPlatform targetPlatform = resolverFactory.getTargetPlatformFactory()
					.createTargetPlatform(tpConfiguration, eeConfiguration, null);
			P2Resolver resolver = resolverFactory
					.createResolver(Collections.singletonList(TargetEnvironment.getRunningEnvironment()));
			try {
				resolver.addDependency(ArtifactType.TYPE_INSTALLABLE_UNIT, "org.eclipse.pde.api.tools", "0.0.0");
				resolver.addDependency(ArtifactType.TYPE_INSTALLABLE_UNIT, "javax.annotation", "0.0.0");
				resolver.addDependency(ArtifactType.TYPE_INSTALLABLE_UNIT, "org.eclipse.equinox.p2.transport.ecf",
						"0.0.0");
				resolver.addDependency(ArtifactType.TYPE_INSTALLABLE_UNIT, "org.eclipse.ecf.provider.filetransfer.ssl",
						"0.0.0");
				resolver.addDependency(ArtifactType.TYPE_INSTALLABLE_UNIT, "org.eclipse.equinox.p2.touchpoint.natives",
						"0.0.0");
				resolver.addDependency(ArtifactType.TYPE_INSTALLABLE_UNIT, "org.eclipse.osgi.compatibility.state",
						"0.0.0");
				resolver.addDependency(ArtifactType.TYPE_INSTALLABLE_UNIT, "org.eclipse.core.runtime", "0.0.0");
				resolver.addDependency(ArtifactType.TYPE_INSTALLABLE_UNIT, "org.eclipse.equinox.launcher", "0.0.0");

			} catch (IllegalArtifactReferenceException e) {
				throw new TargetPlatformConfigurationException("Can't add API tools requirement", e);
			}
			List<File> resolvedBundles = new ArrayList<File>();
			for (P2ResolutionResult result : resolver.resolveTargetDependencies(targetPlatform, null).values()) {
				for (Entry entry : result.getArtifacts()) {
					if (ArtifactType.TYPE_ECLIPSE_PLUGIN.equals(entry.getType())
							&& !"org.eclipse.osgi".equals(entry.getId())) {
						resolvedBundles.add(entry.getLocation(true));
					}
				}
			}
			getLog().info("API Runtime resolved with " + resolvedBundles.size() + " bundles.");
			return resolvedBundles;
		});

	}

}
