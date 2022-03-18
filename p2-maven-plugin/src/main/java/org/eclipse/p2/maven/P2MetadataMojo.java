/*******************************************************************************
 * Copyright (c) 2008, 2013 Sonatype Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.p2.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherResult;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.p2.maven.repository.TransientArtifactRepository;
import org.eclipse.p2.maven.xmlio.ArtifactsIO;
import org.eclipse.p2.maven.xmlio.MetadataIO;
import org.eclipse.tycho.TychoConstants;

@Mojo(name = "p2-metadata", threadSafe = true)
public class P2MetadataMojo extends AbstractMojo {

	@Parameter(property = "project")
	protected MavenProject project;

	@Parameter(property = "mojoExecution", readonly = true)
	protected MojoExecution execution;

	@Parameter(defaultValue = "true")
	protected boolean attachP2Metadata;

	/**
	 * Project types which this plugin supports.
	 */
	@Parameter
	private List<String> supportedProjectTypes = Arrays.asList("eclipse-plugin", "eclipse-test-plugin", "jar",
			"bundle");

	/**
	 * Whether to generate a 'download.stats' property for artifact metadata. See
	 * https://wiki.eclipse.org/Equinox_p2_download_stats
	 */
	@Parameter(property = "tycho.generateDownloadStatsProperty", defaultValue = "false")
	private boolean generateDownloadStatsProperty;

	@Component
	protected MavenProjectHelper projectHelper;

	@Component
	private MetadataIO metadataIO;

	@Component
	private ArtifactsIO artifactsIO;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (!attachP2Metadata || !supportedProjectTypes.contains(project.getPackaging())) {
			return;
		}

		File file = project.getArtifact().getFile();

		if (file == null || !file.canRead()) {
			throw new IllegalStateException();
		}

		File targetDir = new File(project.getBuild().getDirectory());

		Stream<Artifact> artifacts = Stream.concat(//
				Stream.of(project.getArtifact()), //
				project.getAttachedArtifacts().stream()//
						.filter(artifact -> artifact.getFile() != null
								&& artifact.getFile().getName().toLowerCase().endsWith(".jar"))//
		);
		PublisherInfo publisherInfo = new PublisherInfo();
		publisherInfo.setArtifactOptions(IPublisherInfo.A_INDEX | IPublisherInfo.A_PUBLISH);
		TransientArtifactRepository artifactRepository = new TransientArtifactRepository();
		publisherInfo.setArtifactRepository(artifactRepository);
		List<IPublisherAction> actions = new ArrayList<>();
		artifacts.forEach(artifact -> {

			actions.add(new BundlesAction(new File[] { artifact.getFile() }));

		});

		PublisherResult result = new PublisherResult();

		Publisher publisher = new Publisher(publisherInfo, result);

		IStatus status = publisher.publish(actions.toArray(new IPublisherAction[actions.size()]),
				new NullProgressMonitor());

		if (!status.isOK()) {
			throw new RuntimeException(status.toString(), status.getException());
		}
		List<IInstallableUnit> units = new ArrayList<IInstallableUnit>();
		// TODO distinguish between "seed" and "resolve" ?
		result.everything().forEachRemaining(units::add);
		Set<IArtifactDescriptor> descriptors = artifactRepository.getArtifactDescriptors();

		File contentsXml = new File(targetDir, TychoConstants.FILE_NAME_P2_METADATA);
		File artifactsXml = new File(targetDir, TychoConstants.FILE_NAME_P2_ARTIFACTS);
		try {
			metadataIO.writeXML(units, contentsXml);
			artifactsIO.writeXML(descriptors, artifactsXml);
			projectHelper.attachArtifact(project, TychoConstants.EXTENSION_P2_METADATA,
					TychoConstants.CLASSIFIER_P2_METADATA, contentsXml);
			projectHelper.attachArtifact(project, TychoConstants.EXTENSION_P2_ARTIFACTS,
					TychoConstants.CLASSIFIER_P2_ARTIFACTS, artifactsXml);
		} catch (IOException e) {
			throw new MojoExecutionException("Could not generate P2 metadata", e);
		}
//            List<IArtifactFacade> artifacts = new ArrayList<>();
//
//            artifacts.add(projectDefaultArtifact);
//
//            for (Artifact attachedArtifact : project.getAttachedArtifacts()) {
//                if (attachedArtifact.getFile() != null && (attachedArtifact.getFile().getName().endsWith(".jar") {
//                }
//            }

//            P2Generator p2generator = getService(P2Generator.class);
//
//            Map<String, IP2Artifact> generatedMetadata = p2generator.generateMetadata(artifacts,
//                    new PublisherOptions(generateDownloadStatsProperty), targetDir);
//
//            if (baselineMode != BaselineMode.disable) {
//                generatedMetadata = baselineValidator.validateAndReplace(project, execution, generatedMetadata,
//                        baselineRepositories, baselineMode, baselineReplace);
//            }
//
//            File contentsXml = new File(targetDir, TychoConstants.FILE_NAME_P2_METADATA);
//            File artifactsXml = new File(targetDir, TychoConstants.FILE_NAME_P2_ARTIFACTS);
//            p2generator.persistMetadata(generatedMetadata, contentsXml, artifactsXml);
//            projectHelper.attachArtifact(project, TychoConstants.EXTENSION_P2_METADATA, TychoConstants.CLASSIFIER_P2_METADATA, contentsXml);
//            projectHelper.attachArtifact(project, TychoConstants.EXTENSION_P2_ARTIFACTS, TychoConstants.CLASSIFIER_P2_ARTIFACTS, artifactsXml);
//
//            ReactorProject reactorProject = DefaultReactorProject.adapt(project);
//
//            Set<Object> installableUnits = new LinkedHashSet<>();
//            for (Map.Entry<String, IP2Artifact> entry : generatedMetadata.entrySet()) {
//                String classifier = entry.getKey();
//                IP2Artifact p2artifact = entry.getValue();
//
//                installableUnits.addAll(p2artifact.getInstallableUnits());
//
//                // attach any new classified artifacts, like feature root files for example
//                if (classifier != null && !hasAttachedArtifact(project, classifier)) {
//                    projectHelper.attachArtifact(project, getExtension(p2artifact.getLocation()), classifier,
//                            p2artifact.getLocation());
//                }
//            }
//
//            // TODO 353889 distinguish between dependency resolution seed units ("primary") and other units of the project
//            reactorProject.setDependencyMetadata(DependencyMetadataType.SEED, installableUnits);
//            reactorProject.setDependencyMetadata(DependencyMetadataType.RESOLVE, Collections.emptySet());

//        File localArtifactsFile = new File(project.getBuild().getDirectory(), TychoConstants.FILE_NAME_LOCAL_ARTIFACTS);
//        writeArtifactLocations(localArtifactsFile, getAllProjectArtifacts(project));
	}

	private static boolean hasAttachedArtifact(MavenProject project, String classifier) {
		for (Artifact artifact : project.getAttachedArtifacts()) {
			if (classifier.equals(artifact.getClassifier())) {
				return true;
			}
		}
		return false;
	}

	private static String getExtension(File file) {
		String fileName = file.getName();
		int separator = fileName.lastIndexOf('.');
		if (separator < 0) {
			throw new IllegalArgumentException("No file extension in \"" + fileName + "\"");
		}
		return fileName.substring(separator + 1);
	}

	/**
	 * Returns a map from classifiers to artifact files of the given project. The
	 * classifier <code>null</code> is mapped to the project's main artifact.
	 */
	private static Map<String, File> getAllProjectArtifacts(MavenProject project) {
		Map<String, File> artifacts = new HashMap<>();
		Artifact mainArtifact = project.getArtifact();
		if (mainArtifact != null) {
			artifacts.put(null, mainArtifact.getFile());
		}
		for (Artifact attachedArtifact : project.getAttachedArtifacts()) {
			artifacts.put(attachedArtifact.getClassifier(), attachedArtifact.getFile());
		}
		return artifacts;
	}

//	static void writeArtifactLocations(File outputFile, Map<String, File> artifactLocations)
//			throws MojoExecutionException {
//		Properties outputProperties = new Properties();
//
//		for (Entry<String, File> entry : artifactLocations.entrySet()) {
//			if (entry.getKey() == null) {
//				outputProperties.put(TychoConstants.KEY_ARTIFACT_MAIN, entry.getValue().getAbsolutePath());
//			} else {
//				outputProperties.put(TychoConstants.KEY_ARTIFACT_ATTACHED + entry.getKey(),
//						entry.getValue().getAbsolutePath());
//			}
//		}
//
//		writeProperties(outputProperties, outputFile);
//	}

	private static void writeProperties(Properties properties, File outputFile) throws MojoExecutionException {
		FileOutputStream outputStream;
		try {
			outputStream = new FileOutputStream(outputFile);

			try {
				properties.store(outputStream, null);
			} finally {
				outputStream.close();
			}
		} catch (IOException e) {
			throw new MojoExecutionException("I/O exception while writing " + outputFile, e);
		}
	}
}
