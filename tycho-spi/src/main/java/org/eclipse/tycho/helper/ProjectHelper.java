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
package org.eclipse.tycho.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.MojoDescriptorCreator;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Component(role = ProjectHelper.class)
public class ProjectHelper {

    @Requirement
    private MojoDescriptorCreator mojoDescriptorCreator;

    private Map<String, Plugin> cliPlugins = new ConcurrentHashMap<String, Plugin>();

    /**
     * Get all plugins for a project, either configured directly or specified on the commandline
     * 
     * @param project
     * @param mavenSession
     * @return
     */
    public List<Plugin> getPlugins(MavenProject project, MavenSession mavenSession) {
        List<Plugin> plugins = new ArrayList<Plugin>(project.getBuildPlugins());
        for (String goal : mavenSession.getGoals()) {
            if (goal.indexOf(':') >= 0) {
                Plugin plugin = cliPlugins.computeIfAbsent(goal, cli -> {
                    try {
                        MojoDescriptor mojoDescriptor = mojoDescriptorCreator.getMojoDescriptor(goal, mavenSession,
                                project);
                        PluginDescriptor pluginDescriptor = mojoDescriptor.getPluginDescriptor();
                        Plugin p = pluginDescriptor.getPlugin();
                        PluginExecution execution = new PluginExecution();
                        execution.setId("default-cli");
                        execution.addGoal(mojoDescriptor.getGoal());
                        p.addExecution(execution);
                        return p;
                    } catch (Exception e) {
                        return null;
                    }
                });
                if (plugin != null) {
                    plugins.add(plugin);
                }
            }
        }
        return plugins;
    }

    /**
     * Check if there is at least one plugin execution configured for the specified plugin and goal
     * 
     * @param pluginGroupId
     * @param pluginArtifactId
     * @param goal
     * @param project
     * @param mavenSession
     * @return <code>true</code> if an execution was found or <code>false</code> otherwhise.
     */
    public boolean hasPluginExecution(String pluginGroupId, String pluginArtifactId, String goal, MavenProject project,
            MavenSession mavenSession) {
        MavenSession clone = mavenSession.clone();
        clone.setCurrentProject(project);
        List<Plugin> plugins = getPlugins(project, clone);
        for (Plugin plugin : plugins) {
            if (plugin.getGroupId().equals(pluginGroupId) && plugin.getArtifactId().equals(pluginArtifactId)) {
                for (PluginExecution execution : plugin.getExecutions()) {
                    if (execution.getGoals().contains(goal)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Xpp3Dom getPluginConfiguration(String pluginGroupId, String pluginArtifactId, String goal,
            MavenProject project, MavenSession mavenSession) {
        MavenSession clone = mavenSession.clone();
        clone.setCurrentProject(project);
        List<Plugin> plugins = getPlugins(project, clone);
        for (Plugin plugin : plugins) {
            if (plugin.getGroupId().equals(pluginGroupId) && plugin.getArtifactId().equals(pluginArtifactId)) {
                if (goal == null) {
                    return (Xpp3Dom) plugin.getConfiguration();
                }
                for (PluginExecution execution : plugin.getExecutions()) {
                    if (execution.getGoals().contains(goal)) {
                        return (Xpp3Dom) execution.getConfiguration();
                    }
                }
            }
        }
        return null;
    }
}
