/*******************************************************************************
 * Copyright (c) 2008, 2021 Sonatype Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *    Christoph Läubrich - Bug 532575
 *    Christoph Läubrich - Issue #460 - Delay classpath resolution to the compile phase 
 *******************************************************************************/
package org.eclipse.tycho.core.resolver;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.tycho.DependencyArtifacts;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.DependencyResolver;
import org.eclipse.tycho.core.TychoProject;
import org.eclipse.tycho.core.TychoProjectManager;
import org.eclipse.tycho.core.osgitools.AbstractTychoProject;
import org.eclipse.tycho.core.osgitools.DebugUtils;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.resolver.TychoResolver;

@Component(role = TychoResolver.class)
public class DefaultTychoResolver implements TychoResolver {

    private static final String SETUP_MARKER = "DefaultTychoResolver/Setup";
    private static final String RESOLVE_MARKER = "DefaultTychoResolver/Resolve";

    @Requirement
    private Logger logger;

    @Requirement(hint = "p2")
    private DependencyResolver dependencyResolver;

    @Requirement()
    TychoProjectManager projectManager;

    @Override
    public void setupProject(MavenSession session, MavenProject project) {
        //This will bootstrap the project and init it with the session
        ReactorProject reactorProject = DefaultReactorProject.adapt(project, session);
        synchronized (reactorProject) {
            TychoProject tychoProject = projectManager.getTychoProject(project).orElse(null);
            if (tychoProject instanceof AbstractTychoProject dr) {
                if (reactorProject.getContextValue(SETUP_MARKER) != null) {
                    return;
                }
                reactorProject.setContextValue(SETUP_MARKER, true);
                //FIXME this should actually happen lazy on first access so we do not require more here than bootstrap the project with a session above 
                dr.setupProject(session, project);
                dependencyResolver.setupProjects(session, project, reactorProject);
            }
        }
    }

    @Override
    public void resolveProject(MavenSession session, MavenProject project) {
        TychoProject tychoProject = projectManager.getTychoProject(project).orElse(null);
        if (tychoProject instanceof AbstractTychoProject dr) {
            ReactorProject reactorProject = DefaultReactorProject.adapt(project);
            synchronized (reactorProject) {
                if (reactorProject.getContextValue(RESOLVE_MARKER) != null) {
                    return;
                }
                reactorProject.setContextValue(RESOLVE_MARKER, true);
                String threadMarker;
                if (logger.isDebugEnabled()) {
                    threadMarker = "[" + Thread.currentThread().getName().replaceAll("^ForkJoinPool-(\\d+)-", "")
                            + "] ";
                } else {
                    threadMarker = "";
                }
                logger.debug(threadMarker + "Computing preliminary target platform for " + project);
                logger.info(threadMarker + "Resolving dependencies of " + project);
                DependencyArtifacts dependencyArtifacts = tychoProject.getDependencyArtifacts(reactorProject);
                logger.info(threadMarker + "Resolving test dependencies of " + project);
                DependencyArtifacts testDependencyArtifacts = tychoProject.getDependencyArtifacts(reactorProject);
                dependencyResolver.injectDependenciesIntoMavenModel(project, tychoProject, dependencyArtifacts,
                        testDependencyArtifacts, logger);
                if (logger.isDebugEnabled() && DebugUtils.isDebugEnabled(session, project)) {
                    StringBuilder sb = new StringBuilder(threadMarker);
                    sb.append("Injected dependencies for ").append(project.toString()).append("\n");
                    for (Dependency dependency : project.getDependencies()) {
                        sb.append("  ").append(dependency.toString());
                    }
                    logger.debug(sb.toString());
                }
            }
        }
    }
}
