/*******************************************************************************
 * Copyright (c) 2011, 2020 SAP SE and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP SE - initial API and implementation
 *    Christoph Läubrich - Bug 567098 - pomDependencies=consider should wrap non-osgi jars
 *                         Bug 567639 - wrapAsBundle fails when dealing with esoteric versions
 *                         Bug 567957 - wrapAsBundle must check if artifact has a classifier
 *                         Bug 568729 - Support new "Maven" Target location
 *******************************************************************************/
package org.eclipse.tycho.p2.target;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.PublisherResult;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.shared.MavenContext;
import org.eclipse.tycho.core.shared.MavenLogger;
import org.eclipse.tycho.p2.artifact.provider.IRawArtifactFileProvider;
import org.eclipse.tycho.p2.impl.publisher.MavenPropertiesAdvice;
import org.eclipse.tycho.p2.impl.publisher.repo.TransientArtifactRepository;
import org.eclipse.tycho.p2.metadata.IArtifactFacade;
import org.eclipse.tycho.p2.repository.MavenRepositoryCoordinates;
import org.eclipse.tycho.p2.resolver.WrappedArtifact;
import org.eclipse.tycho.p2.target.repository.MavenBundlesArtifactRepository;
import org.eclipse.tycho.repository.util.StatusTool;
import org.osgi.framework.BundleException;

@SuppressWarnings("restriction")
public class TargetPlatformBundlePublisher {

    private final MavenLogger logger;
    private final MavenBundlesArtifactRepository publishedArtifacts;
    private final ReactorProject project;
    private final MavenContext mavenContext;

    public TargetPlatformBundlePublisher(ReactorProject project, MavenContext mavenContext) {
        this.project = project;
        this.mavenContext = mavenContext;
        this.publishedArtifacts = new MavenBundlesArtifactRepository(mavenContext);
        this.logger = mavenContext.getLogger();
    }

    /**
     * Generate p2 data for an artifact, if the artifact is an OSGI bundle.
     * <p>
     * The p2 metadata produced by this method is only determined by the artifact, and the function
     * used for this conversion must not change (significantly) even in future versions. This is
     * required because the resulting metadata can be included in p2 repositories built by Tycho,
     * and hence may be propagated into the p2 universe. Therefore the metadata generated by this
     * method shall fulfill the basic assumption of p2 that ID+version uniquely identifies a
     * unit/artifact. Assuming that distinct bundle artifacts specify unique ID+versions in their
     * manifest (which should be mostly true), and the p2 BundlesAction used in the implementation
     * doesn't change significantly (which can also be assumed), these conditions specified above a
     * met.
     * </p>
     * <p>
     * In slight deviation on the principles described in the previous paragraph, the implementation
     * adds GAV properties to the generated IU. This is justified by the potential benefits of
     * tracing the origin of artifact.
     * </p>
     * 
     * @param mavenArtifact
     *            An artifact in local file system.
     * @return the p2 metadata of the artifact, or <code>null</code> if the artifact isn't a valid
     *         OSGi bundle.
     */
    MavenBundleInfo attemptToPublishBundle(IArtifactFacade mavenArtifact, boolean wrapIfNessesary) {
        if (!isAvailableAsLocalFile(mavenArtifact)) {
            // this should have been ensured by the caller
            throw new IllegalArgumentException(
                    mavenArtifact + " is not a local artifact file @ location " + mavenArtifact.getLocation());
        }
        PublisherRun publisherRun = new PublisherRun(mavenArtifact, project, publishedArtifacts.getBaseDir(),
                mavenContext, wrapIfNessesary);
        IStatus status = publisherRun.execute();
        if (!status.isOK()) {
            /**
             * If publishing of a jar fails, it is simply not added to the resolution context. The
             * BundlesAction already ignores non-bundle JARs silently, so an error status here
             * indicates a caught exception that we at least want to see.
             */
            logger.warn(StatusTool.collectProblems(status), status.getException());
        }

        MavenBundleInfo publishedIU = publisherRun.getPublishedUnitIfExists();
        if (publishedIU != null) {
            publishedArtifacts.addPublishedArtifact(publishedIU.getDescriptor(), publishedIU.getArtifact());
        }

        return publishedIU;
    }

    private boolean isAvailableAsLocalFile(IArtifactFacade artifact) {
        File localLocation = artifact.getLocation();
        return localLocation != null && localLocation.isFile();
    }

    IRawArtifactFileProvider getArtifactRepoOfPublishedBundles() {
        return publishedArtifacts;
    }

    private static class PublisherRun {

        private static final String EXCEPTION_CONTEXT = "Error while adding Maven artifact to the target platform: ";

        private final IArtifactFacade mavenArtifact;

        private PublisherInfo publisherInfo;
        private TransientArtifactRepository collectedDescriptors;
        private PublisherResult publisherResult;
        private IArtifactFacade publishedArtifact;

        private ReactorProject project;

        private File basedir;

        private MavenLogger logger;

        private boolean wrapIfNessesary;

        private MavenContext mavenContext;

        PublisherRun(IArtifactFacade artifact, ReactorProject project, File basedir, MavenContext mavenContext,
                boolean wrapIfNessesary) {
            this.mavenArtifact = artifact;
            this.project = project;
            this.basedir = basedir;
            this.mavenContext = mavenContext;
            this.logger = mavenContext.getLogger();
            this.wrapIfNessesary = wrapIfNessesary;
        }

        IStatus execute() {
            try {
                BundleDescription bundleDescription = BundlesAction
                        .createBundleDescription(mavenArtifact.getLocation());
                if (bundleDescription == null) {
                    return new Status(IStatus.OK, TargetPlatformBundlePublisher.class.getName(),
                            "artifact file " + mavenArtifact.getLocation() + " is certainly not a bundle/jar file");
                }
                if (bundleDescription.getSymbolicName() == null) {
                    if (wrapIfNessesary) {
                        try {
                            MavenRepositoryCoordinates repositoryCoordinates = new MavenRepositoryCoordinates(
                                    mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(),
                                    mavenArtifact.getVersion(),
                                    WrappedArtifact.createClassifierFromArtifact(mavenArtifact), null);
                            File wrappedFile = new File(basedir,
                                    repositoryCoordinates.getLocalRepositoryPath(mavenContext));
                            WrappedArtifact wrappedArtifact = WrappedArtifact.createWrappedArtifact(mavenArtifact,
                                    project.getGroupId(), wrappedFile);
                            publishedArtifact = wrappedArtifact;
                            logger.warn("Maven Artifact " + mavenArtifact.getGroupId() + ":"
                                    + mavenArtifact.getArtifactId() + ":" + mavenArtifact.getVersion()
                                    + " is not a bundle and was automatically wrapped with bundle-symbolic name "
                                    + wrappedArtifact.getWrappedBsn()
                                    + ", ignoring such artifacts can be enabled with <pomDependencies>consider</pomDependencies> in target platform configuration.");
                            logger.info(wrappedArtifact.getReferenceHint());
                            if (logger.isDebugEnabled()) {
                                logger.debug("The follwoing manifest was generated for this artifact:\r\n"
                                        + wrappedArtifact.getGeneratedManifest());
                            }
                        } catch (Exception e) {
                            return new Status(IStatus.ERROR, TargetPlatformBundlePublisher.class.getName(),
                                    "wrapping file " + mavenArtifact.getLocation() + " failed", e);
                        }
                    } else {
                        logger.info("Maven Artifact " + mavenArtifact.getGroupId() + ":" + mavenArtifact.getArtifactId()
                                + ":" + mavenArtifact.getVersion()
                                + " is not a bundle and will be ignored, automatic wrapping of such artifacts can be enabled with "
                                + "<pomDependencies>wrapAsBundle</pomDependencies> in target platform configuration.");
                        return new Status(IStatus.OK, TargetPlatformBundlePublisher.class.getName(), "Nothing to do");
                    }

                } else {
                    publishedArtifact = mavenArtifact;
                }
            } catch (IOException e) {
                return new Status(IStatus.WARNING, TargetPlatformBundlePublisher.class.getName(),
                        "reading file " + mavenArtifact.getLocation() + " failed", e);
            } catch (BundleException e) {
                return new Status(IStatus.WARNING, TargetPlatformBundlePublisher.class.getName(),
                        "reading maven manifest from file: " + mavenArtifact.getLocation() + " failed", e);
            }

            publisherInfo = new PublisherInfo();
            enableArtifactDescriptorCollection();
            enableUnitAnnotationWithGAV();

            BundlesAction bundlesAction = new BundlesAction(new File[] { publishedArtifact.getLocation() });
            IStatus status = executePublisherAction(bundlesAction);
            return status;
        }

        private void enableArtifactDescriptorCollection() {
            publisherInfo.setArtifactOptions(IPublisherInfo.A_INDEX);
            collectedDescriptors = new TransientArtifactRepository();
            publisherInfo.setArtifactRepository(collectedDescriptors);
        }

        private void enableUnitAnnotationWithGAV() {
            MavenPropertiesAdvice advice = new MavenPropertiesAdvice(publishedArtifact, mavenContext);
            publisherInfo.addAdvice(advice);
        }

        private IStatus executePublisherAction(BundlesAction action) {
            IPublisherAction[] actions = new IPublisherAction[] { action };
            publisherResult = new PublisherResult();
            return new Publisher(publisherInfo, publisherResult).publish(actions, null);
        }

        MavenBundleInfo getPublishedUnitIfExists() {
            if (publisherResult == null) {
                return null;
            }
            Collection<IInstallableUnit> units = publisherResult.getIUs(null, null);
            if (units.isEmpty()) {
                // the BundlesAction simply does not create any IUs if the JAR is not a bundle
                return null;
            } else if (units.size() == 1) {
                IInstallableUnit unit = units.iterator().next();
                IArtifactDescriptor artifactDescriptor = getPublishedArtifactDescriptor();
                return new MavenBundleInfo(unit, artifactDescriptor, publishedArtifact);
            } else {
                throw new AssertionFailedException(EXCEPTION_CONTEXT + "BundlesAction produced more than one IU for "
                        + mavenArtifact.getLocation());
            }
        }

        IArtifactDescriptor getPublishedArtifactDescriptor() {
            Set<IArtifactDescriptor> descriptors = collectedDescriptors.getArtifactDescriptors();
            if (descriptors.isEmpty()) {
                throw new AssertionFailedException(EXCEPTION_CONTEXT
                        + "BundlesAction did not create an artifact entry for " + mavenArtifact.getLocation());
            } else if (descriptors.size() == 1) {
                return descriptors.iterator().next();
            } else {
                throw new AssertionFailedException(EXCEPTION_CONTEXT
                        + "BundlesAction created more than one artifact entry for " + mavenArtifact.getLocation());
            }
        }

    }

}
