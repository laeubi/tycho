/*******************************************************************************
 * Copyright (c) 2008, 2011 Sonatype Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.core.testcomponents;

import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.artifacts.DependencyArtifacts;
import org.eclipse.tycho.artifacts.TargetPlatform;
import org.eclipse.tycho.core.DependencyResolver;
import org.eclipse.tycho.core.DependencyResolverConfiguration;
import org.eclipse.tycho.core.osgitools.AbstractTychoProject;
import org.eclipse.tycho.p2.target.facade.PomDependencyCollector;

@Component(role = DependencyResolver.class, hint = "p2", instantiationStrategy = "per-lookup")
public class StubDependencyResolver extends LocalDependencyResolver implements DependencyResolver {

    @Override
    public void setupProjects(MavenSession session, MavenProject project, ReactorProject reactorProject) {
        // nothing to do

    }

    @Override
    public PomDependencyCollector resolvePomDependencies(MavenSession session, MavenProject project) {
        throw new UnsupportedOperationException("StubDependencyResolver.enclosing_method()");
    }

    @Override
    public TargetPlatform computePreliminaryTargetPlatform(MavenSession session, MavenProject project,
            List<ReactorProject> reactorProjects) {
        //throw new UnsupportedOperationException("StubDependencyResolver.computePreliminaryTargetPlatform()");
        return null; // null is a valid value... ???
    }

    @Override
    public DependencyArtifacts resolveDependencies(MavenSession session, MavenProject project,
            TargetPlatform targetPlatform, List<ReactorProject> reactorProjects,
            DependencyResolverConfiguration resolverConfiguration) {
        // TODO Auto-generated method stub
        //throw new UnsupportedOperationException("StubDependencyResolver.resolveDependencies()");
        return super.resolveDependencies(session, project, targetPlatform, reactorProjects, resolverConfiguration);

    }

    @Override
    public void injectDependenciesIntoMavenModel(MavenProject project, AbstractTychoProject projectType,
            DependencyArtifacts resolvedDependencies, DependencyArtifacts testDepedencyArtifacts, Logger logger) {
        // TODO Auto-generated method stub
        //throw new UnsupportedOperationException("StubDependencyResolver.injectDependenciesIntoMavenModel()");
        super.injectDependenciesIntoMavenModel(project, projectType, resolvedDependencies, testDepedencyArtifacts,
                logger);

    }

}
