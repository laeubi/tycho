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

import java.io.File;
import java.util.List;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.artifacts.DependencyArtifacts;
import org.eclipse.tycho.artifacts.TargetPlatform;
import org.eclipse.tycho.core.DependencyResolver;
import org.eclipse.tycho.core.DependencyResolverConfiguration;
import org.eclipse.tycho.core.TychoConstants;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.osgitools.targetplatform.DefaultDependencyArtifacts;

// TODO romove me as part of TYCHO-527
@Component(role = DependencyResolver.class, hint = "p2", instantiationStrategy = "per-lookup")
public class StubP2DependencyResolver extends LocalDependencyResolver {

    @Override
    public DependencyArtifacts resolveDependencies(MavenSession session, MavenProject project,
            TargetPlatform resolutionContext, List<ReactorProject> reactorProjects,
            DependencyResolverConfiguration resolverConfiguration) {
        ReactorProject reactorProject = DefaultReactorProject.adapt(project);
        DefaultDependencyArtifacts platform = new DefaultDependencyArtifacts(reactorProject);
        Properties properties = (Properties) reactorProject.getContextValue(TychoConstants.CTX_MERGED_PROPERTIES);
        String property = properties.getProperty("tycho.test.targetPlatform ");
        if (property != null) {
            File location = new File(property);
            if (!location.exists() || !location.isDirectory()) {
                throw new RuntimeException("Invalid target platform location: " + property);
            }
            setLocation(new File(property));
        }
        return super.resolveDependencies(session, project, resolutionContext, reactorProjects, resolverConfiguration);
    }
}
