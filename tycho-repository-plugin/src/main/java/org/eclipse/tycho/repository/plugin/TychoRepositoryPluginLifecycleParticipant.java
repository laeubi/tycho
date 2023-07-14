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
package org.eclipse.tycho.repository.plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.tycho.helper.PluginConfigurationHelper;
import org.eclipse.tycho.helper.PluginRealmHelper;
import org.eclipse.tycho.helper.ProjectHelper;
import org.eclipse.tycho.packaging.RepositoryGenerator;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class TychoRepositoryPluginLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	@Requirement
	PluginRealmHelper pluginRealmHelper;

	@Requirement
	Logger logger;

	@Requirement
	PluginConfigurationHelper configurationHelper;

	@Requirement
	ProjectHelper projectHelper;

	@Override
	public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
		List<MavenProject> projects = session.getProjects();
		for (MavenProject project : projects) {
			Plugin plugin = project.getPlugin("org.eclipse.tycho:tycho-repository-plugin");
			if ("repository".equals(project.getPackaging()) && plugin != null) {
				Set<MavenProject> added = new HashSet<MavenProject>();
				for (PluginExecution execution : plugin.getExecutions()) {
					for (String goal : execution.getGoals()) {
						addInterestingProjects(project, projects, session, goal, added);
					}
				}
			}
		}
	}

	private void addInterestingProjects(MavenProject project, List<MavenProject> projects, MavenSession session,
			String goal, Set<MavenProject> added) {
		try {
			Xpp3Dom configuration = projectHelper.getPluginConfiguration("org.eclipse.tycho", "tycho-repository-plugin",
					goal);
			String repoType = configurationHelper.getConfiguration(configuration)
					.getString(PackageRepositoryMojo.PARAMETER_REPOSITORY_TYPE)
					.orElse(PackageRepositoryMojo.DEFAULT_REPOSITORY_TYPE);
			pluginRealmHelper.visitExtensions(project, session, RepositoryGenerator.class, repoType,
					generator -> {
						for (MavenProject other : projects) {
							if (other == project || added.contains(other)) {
								continue;
							}
							if (generator.isInteresting(other)) {
								Dependency dependency = new Dependency();
								dependency.setGroupId(other.getGroupId());
								dependency.setArtifactId(other.getArtifactId());
								dependency.setVersion(other.getVersion());
								project.getModel().addDependency(dependency);
								added.add(other);
							}
						}
						return false; // only one will be used ...
					});
		} catch (PluginVersionResolutionException | PluginDescriptorParsingException | InvalidPluginDescriptorException
				| PluginResolutionException | PluginManagerException e) {
			logger.warn("Can't determine projects that should be declared as automatic discovered dependencies!", e);
		}

	}

}
