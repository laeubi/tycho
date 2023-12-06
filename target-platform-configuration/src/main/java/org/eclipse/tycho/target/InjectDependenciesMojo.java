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
package org.eclipse.tycho.target;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.tycho.ArtifactKey;
import org.eclipse.tycho.ExecutionEnvironmentConfiguration;
import org.eclipse.tycho.IArtifactFacade;
import org.eclipse.tycho.MavenArtifactKey;
import org.eclipse.tycho.PackagingType;
import org.eclipse.tycho.TargetPlatform;
import org.eclipse.tycho.core.DependencyResolver;
import org.eclipse.tycho.core.TargetPlatformConfiguration;
import org.eclipse.tycho.core.TychoProjectManager;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.osgitools.targetplatform.DefaultDependencyArtifacts;
import org.eclipse.tycho.core.resolver.shared.IncludeSourceMode;
import org.eclipse.tycho.core.resolver.target.ArtifactTypeHelper;
import org.eclipse.tycho.p2.target.facade.TargetPlatformConfigurationStub;
import org.eclipse.tycho.p2resolver.P2DependencyResolver;
import org.eclipse.tycho.repository.registry.facade.ReactorRepositoryManager;
import org.eclipse.tycho.targetplatform.P2TargetPlatform;
import org.eclipse.tycho.targetplatform.TargetDefinitionFile;
import org.eclipse.tycho.targetplatform.TargetPlatformArtifactResolver;
import org.eclipse.tycho.targetplatform.TargetResolveException;

@Mojo(name = "inject-dependencies", threadSafe = true, defaultPhase = LifecyclePhase.INITIALIZE)
public class InjectDependenciesMojo extends AbstractMojo {

    @Parameter(property = "project", readonly = true)
    private MavenProject project;
    @Component(hint = P2DependencyResolver.ROLE_HINT)
    private DependencyResolver resolver;

    @Component
    private TychoProjectManager projectManager;
    @Component
    private Logger logger;

    @Component
    private ReactorRepositoryManager reactorRepositoryManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (PackagingType.TYPE_ECLIPSE_TARGET_DEFINITION.equals(project.getPackaging())) {
            try {
                TargetPlatformConfiguration configuration = projectManager.getTargetPlatformConfiguration(project);
                TargetDefinitionFile file = TargetDefinitionFile
                        .read(TargetPlatformArtifactResolver.getMainTargetFile(project));
                TargetPlatformConfigurationStub tpConfiguration = new TargetPlatformConfigurationStub();
                tpConfiguration.addTargetDefinition(file);
                tpConfiguration.addFilters(configuration.getFilters());
                tpConfiguration.setIncludeSourceMode(IncludeSourceMode.ignore);
                tpConfiguration.setIgnoreLocalArtifacts(true);
                tpConfiguration.setReferencedRepositoryMode(configuration.getReferencedRepositoryMode());
                ExecutionEnvironmentConfiguration ee = projectManager.getExecutionEnvironmentConfiguration(project);
                TargetPlatform targetPlatform = reactorRepositoryManager.computePreliminaryTargetPlatform(
                        DefaultReactorProject.adapt(project), tpConfiguration, ee, List.of());
                DefaultDependencyArtifacts da = new DefaultDependencyArtifacts();
                if (targetPlatform instanceof P2TargetPlatform p2) {
                    IArtifactRepository artifactRepository = p2.getArtifactRepository();
                    System.out.println(artifactRepository);
                    Set<IInstallableUnit> installableUnits = p2.getInstallableUnits();
                    for (IInstallableUnit iu : installableUnits) {
                        //System.out.println(iu);
                        IArtifactFacade mavenArtifact = p2.getOriginalMavenArtifactMap().get(iu);
                        if (mavenArtifact != null) {
//                            addExternalMavenArtifact(result, mavenArtifact, iu);
//                            return;
                            System.out.println("A maven artifact!! " + mavenArtifact);
                            continue;
                        }

                        for (IArtifactKey key : iu.getArtifacts()) {
                            //addArtifactFile(result, iu, key, targetPlatform);
                            System.out.println("Something else: " + key.getId() + " " + key.getVersion());
                            ArtifactKey artifactKey = ArtifactTypeHelper.toTychoArtifactKey(iu, key);
                            System.out.println("tycho = " + artifactKey.getType() + " / " + artifactKey.getId() + " / "
                                    + artifactKey.getVersion());
                            if (artifactKey instanceof MavenArtifactKey) {
                                System.out.println("MavenKey?!?");
                            }
                            if (key.getId().contains("bnd")) {
                                System.out.println("bnd...");
                            }
                            IArtifactDescriptor[] artifactDescriptors = artifactRepository.getArtifactDescriptors(key);
                            for (IArtifactDescriptor descriptor : artifactDescriptors) {
                                System.out.println(descriptor);
                            }
                            //p2.getLocalArtifactFile(key)
                            File dummy = null;
                            da.addArtifactFile(artifactKey, dummy, installableUnits);
                        }
                    }
                }
//                IMetadataRepository metadataRepository = targetPlatform.getMetadataRepository();
//                IQueryResult<IInstallableUnit> result = metadataRepository.query(QueryUtil.ALL_UNITS, null);
//                for (IInstallableUnit iu : result) {
//                    System.out.println(iu);
//                }
            } catch (TargetResolveException e) {
                new MojoFailureException("Can't fetch main target file", e);
            }
        }

//        ReactorProject reactorProject = DefaultReactorProject.adapt(project);
//        TychoProject tychoProject = projectManager.getTychoProject(project).orElse(null);
//        if (tychoProject instanceof AbstractTychoProject dr) {
//            resolver.injectDependenciesIntoMavenModel(project, dr, dr.getDependencyArtifacts(reactorProject),
//                    dr.getTestDependencyArtifacts(reactorProject), logger);
//        }
    }

}
