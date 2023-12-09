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
package org.eclipse.tycho.core.osgitools;

import java.io.File;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.tycho.ArtifactKey;
import org.eclipse.tycho.DefaultArtifactKey;
import org.eclipse.tycho.DependencyArtifacts;
import org.eclipse.tycho.PackagingType;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.TychoConstants;
import org.eclipse.tycho.core.ArtifactDependencyVisitor;
import org.eclipse.tycho.core.ArtifactDependencyWalker;
import org.eclipse.tycho.core.TychoProject;
import org.eclipse.tycho.core.osgitools.targetplatform.DefaultDependencyArtifacts;
import org.eclipse.tycho.model.Feature;
import org.eclipse.tycho.model.ProductConfiguration;
import org.eclipse.tycho.model.UpdateSite;

@Component(role = TychoProject.class, hint = PackagingType.TYPE_ECLIPSE_TARGET_DEFINITION)
public class TargetPlatformProject extends AbstractTychoProject {

    @Override
    public ArtifactDependencyWalker getDependencyWalker(ReactorProject project) {
        return new ArtifactDependencyWalker() {

            @Override
            public void walk(ArtifactDependencyVisitor visitor) {

            }

            @Override
            public void traverseUpdateSite(UpdateSite site, ArtifactDependencyVisitor visitor) {

            }

            @Override
            public void traverseProduct(ProductConfiguration productConfiguration, ArtifactDependencyVisitor visitor) {

            }

            @Override
            public void traverseFeature(File location, Feature feature, ArtifactDependencyVisitor visitor) {

            }
        };
    }

    @Override
    public ArtifactKey getArtifactKey(ReactorProject project) {
        return new DefaultArtifactKey("target", project.getArtifactId(), project.getVersion());
    }

    @Override
    public DependencyArtifacts getDependencyArtifacts(ReactorProject project) {
        synchronized (project) {
            DependencyArtifacts resolvedDependencies = (DependencyArtifacts) project
                    .getContextValue(TychoConstants.CTX_DEPENDENCY_ARTIFACTS);
            if (resolvedDependencies != null) {
                return resolvedDependencies;
            }
            DefaultDependencyArtifacts artifacts = new DefaultDependencyArtifacts(project);
            return artifacts;
        }
    }

}
