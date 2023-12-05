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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.DependencyResolver;
import org.eclipse.tycho.core.TychoProject;
import org.eclipse.tycho.core.TychoProjectManager;
import org.eclipse.tycho.core.osgitools.AbstractTychoProject;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.p2resolver.P2DependencyResolver;

@Mojo(name = "inject-dependencies", threadSafe = true, defaultPhase = LifecyclePhase.INITIALIZE)
public class InjectDependenciesMojo extends AbstractMojo {

    @Parameter(property = "project", readonly = true)
    private MavenProject project;
    @Component(hint = P2DependencyResolver.ROLE_HINT)
    private DependencyResolver resolver;

    @Component
    TychoProjectManager projectManager;
    @Component
    private Logger logger;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        ReactorProject reactorProject = DefaultReactorProject.adapt(project);
        TychoProject tychoProject = projectManager.getTychoProject(project).orElse(null);
        if (tychoProject instanceof AbstractTychoProject dr) {
            resolver.injectDependenciesIntoMavenModel(project, dr, dr.getDependencyArtifacts(reactorProject),
                    dr.getTestDependencyArtifacts(reactorProject), logger);
        }
    }

}
