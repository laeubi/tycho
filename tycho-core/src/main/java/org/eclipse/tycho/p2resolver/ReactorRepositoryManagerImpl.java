/*******************************************************************************
 * Copyright (c) 2012, 2021 SAP SE and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP SE - initial API and implementation
 *    Christoph Läubrich - Issue #697 - Failed to resolve dependencies with Tycho 2.7.0 for custom repositories
 *******************************************************************************/
package org.eclipse.tycho.p2resolver;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.ReactorProjectIdentities;
import org.eclipse.tycho.TargetPlatform;
import org.eclipse.tycho.core.DependencyResolver;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.resolver.P2ResolverFactory;
import org.eclipse.tycho.p2.repository.PublishingRepository;
import org.eclipse.tycho.p2.repository.module.PublishingRepositoryImpl;
import org.eclipse.tycho.p2.target.facade.PomDependencyCollector;
import org.eclipse.tycho.p2.target.facade.TargetPlatformFactory;
import org.eclipse.tycho.repository.registry.facade.ReactorRepositoryManager;

@Component(role = ReactorRepositoryManager.class)
public class ReactorRepositoryManagerImpl implements ReactorRepositoryManager {

    @Requirement
    IProvisioningAgent agent;
    @Requirement
    P2ResolverFactory p2ResolverFactory;

    @Requirement(hint = P2DependencyResolver.ROLE_HINT)
    DependencyResolver p2Resolver;

    @Requirement
    LegacySupport legacySupport;
    private TargetPlatformFactory tpFactory;

    @Override
    public PublishingRepository getPublishingRepository(ReactorProjectIdentities project) {
        return new PublishingRepositoryImpl(agent, project);
    }

    @Override
    public TargetPlatform computeFinalTargetPlatform(ReactorProject project,
            List<? extends ReactorProjectIdentities> upstreamProjects, PomDependencyCollector pomDependencyCollector) {
        synchronized (project) {
            TargetPlatform preliminaryTargetPlatform = p2Resolver.computePreliminaryTargetPlatform(
                    project.adapt(MavenSession.class), project.adapt(MavenProject.class),
                    DefaultReactorProject.adapt(project.adapt(MavenSession.class)));
            List<PublishingRepository> upstreamProjectResults = getBuildResults(upstreamProjects);
            TargetPlatform result = getTpFactory().createTargetPlatformWithUpdatedReactorContent(
                    preliminaryTargetPlatform, upstreamProjectResults, pomDependencyCollector);
            project.setContextValue(TargetPlatform.FINAL_TARGET_PLATFORM_KEY, result);
            return result;
        }
    }

    private List<PublishingRepository> getBuildResults(List<? extends ReactorProjectIdentities> projects) {
        List<PublishingRepository> results = new ArrayList<>(projects.size());
        for (ReactorProjectIdentities project : projects) {
            results.add(getPublishingRepository(project));
        }
        return results;
    }

    @Override
    public TargetPlatform getFinalTargetPlatform(ReactorProject project) {
        TargetPlatform targetPlatform = (TargetPlatform) project
                .getContextValue(TargetPlatform.FINAL_TARGET_PLATFORM_KEY);
        if (targetPlatform == null) {
            throw new IllegalStateException("Target platform is missing");
        }
        return targetPlatform;
    }

    public synchronized TargetPlatformFactory getTpFactory() {
        if (tpFactory == null) {
            tpFactory = p2ResolverFactory.getTargetPlatformFactory();
        }
        return tpFactory;
    }

}
