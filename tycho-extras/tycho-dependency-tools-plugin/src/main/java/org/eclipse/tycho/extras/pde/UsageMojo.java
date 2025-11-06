/*******************************************************************************
 * Copyright (c) 2025 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.tycho.extras.pde;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.tycho.DependencyArtifacts;
import org.eclipse.tycho.ExecutionEnvironment;
import org.eclipse.tycho.TargetEnvironment;
import org.eclipse.tycho.core.TargetPlatformConfiguration;
import org.eclipse.tycho.core.TychoProjectManager;
import org.eclipse.tycho.core.ee.impl.StandardEEResolutionHints;
import org.eclipse.tycho.core.resolver.shared.IncludeSourceMode;
import org.eclipse.tycho.core.resolver.shared.ReferencedRepositoryMode;
import org.eclipse.tycho.p2maven.repository.P2RepositoryManager;
import org.eclipse.tycho.p2resolver.TargetDefinitionResolverService;
import org.eclipse.tycho.targetplatform.TargetDefinition.InstallableUnitLocation;
import org.eclipse.tycho.targetplatform.TargetDefinition.Location;
import org.eclipse.tycho.targetplatform.TargetDefinition.Unit;
import org.eclipse.tycho.targetplatform.TargetDefinitionContent;
import org.eclipse.tycho.targetplatform.TargetDefinitionFile;

/**
 * This mojos compares the actual content of the target against what is used in the projects to
 * allow for getting rid of seldom or unused dependencies
 * 
 * Example <code>org.eclipse.tycho.extras:tycho-dependency-tools-plugin:6.0.0-SNAPSHOT:usage</code>
 */
@Mojo(name = "usage", defaultPhase = LifecyclePhase.NONE, requiresProject = true, threadSafe = true, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, aggregator = true)
public class UsageMojo extends AbstractMojo {

    @Component
    private TychoProjectManager projectManager;

    @Component
    private MavenSession mavenSession;

//    @Component
//    private MavenProject mavenProject;

    @Component
    private LegacySupport legacySupport;

    @Component
    private P2RepositoryManager repositoryManager;

    @Component
    private TargetDefinitionResolverService definitionResolverService;

    @Component
    private IProvisioningAgent agent;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        log.info("Scan reactor for dependencies...");
        List<MavenProject> projects = mavenSession.getProjects();
        UsageReport usageReport = new UsageReport();
        for (MavenProject project : projects) {
            Set<IInstallableUnit> projectUnits = projectManager.getDependencyArtifacts(project)
                    .map(DependencyArtifacts::getNonReactorUnits).stream().flatMap(Collection::stream).filter(iu -> {
                        if (iu.getId().endsWith(".source")) {
                            return false;
                        }
                        if (iu.getId().endsWith(".feature.jar")) {
                            return false;
                        }
                        return true;
                    }).collect(Collectors.toSet());
            usageReport.projectUsage.put(project, projectUnits);
            usageReport.usedUnits.addAll(projectUnits);
            TargetPlatformConfiguration tpconfig = projectManager.getTargetPlatformConfiguration(project);
            List<TargetDefinitionFile> targets = tpconfig.getTargets();
            ExecutionEnvironment specification = projectManager.getExecutionEnvironmentConfiguration(project)
                    .getFullSpecification();
            for (TargetDefinitionFile definitionFile : targets) {
                if (usageReport.targetFiles.add(definitionFile)) {
                    TargetDefinitionContent content = definitionResolverService.getTargetDefinitionContent(
                            definitionFile, List.of(TargetEnvironment.getRunningEnvironment()),
                            new StandardEEResolutionHints(specification), IncludeSourceMode.ignore,
                            ReferencedRepositoryMode.include, agent);
                    usageReport.targetFileUnits.put(definitionFile, content);
                    for (Location location : definitionFile.getLocations()) {
                        try {
                            if (location instanceof InstallableUnitLocation iu) {
                                analyzeIULocation(definitionFile, iu, usageReport);
                            }
                        } catch (RuntimeException e) {
                            log.warn("Can't analyze location " + location, e);
                        }
                    }
                }
            }
        }
        generateReport(log, usageReport);
    }

    private static void generateReport(Log log, UsageReport usageReport) {
        log.info("###### DEPENDECIES USAGE REPORT #######");
        log.info("Your build uses " + usageReport.usedUnits.size() + " dependencies.");
        log.info("Your build uses " + usageReport.targetFiles.size() + " target file(s).");
        for (TargetDefinitionFile targetFile : usageReport.targetFiles) {
            log.info(targetFile.getOrigin() + " contains "
                    + usageReport.targetFileUnits.get(targetFile).query(QueryUtil.ALL_UNITS, null).toSet().size()
                    + " units from " + targetFile.getLocations().size() + " locations");
        }
        
        // Only report on root units (defined directly in target files)
        Set<IInstallableUnit> allUnits = usageReport.providedBy.keySet();
        Set<IInstallableUnit> rootUnits = allUnits.stream()
                .filter(usageReport::isRootUnit)
                .collect(Collectors.toSet());
        
        // Track which units have been covered by reporting their parent
        Set<IInstallableUnit> reportedUnits = new HashSet<>();
        
        for (IInstallableUnit unit : rootUnits) {
            // Skip if this unit was already covered by a parent report
            if (reportedUnits.contains(unit)) {
                continue;
            }
            
            String by = usageReport.getProvidedBy(unit);
            
            if (usageReport.usedUnits.contains(unit)) {
                // Unit is directly used - report it and mark all its children as reported
                List<String> list = usageReport.projectUsage.entrySet().stream()
                        .filter(entry -> entry.getValue().contains(unit)).map(project -> project.getKey().getId())
                        .toList();
                log.info("The unit " + unit + " is used by " + list.size() + " projects and currently provided by "
                        + by);
                
                // Mark this unit and all its transitive dependencies as reported
                reportedUnits.add(unit);
                reportedUnits.addAll(usageReport.getAllChildren(unit));
            } else if (usageReport.isUsedIndirectly(unit)) {
                // Unit is indirectly used (one of its dependencies is used but not the unit itself)
                String chain = usageReport.getIndirectUsageChain(unit);
                log.info("The unit " + unit + " is INDIRECTLY used through: " + chain 
                        + " and currently provided by " + by);
                
                // Mark this unit and all its transitive dependencies as reported
                reportedUnits.add(unit);
                reportedUnits.addAll(usageReport.getAllChildren(unit));
            } else {
                // Unit and all its dependencies are unused
                log.info("The unit " + unit + " is UNUSED and currently provided by " + by);
                
                // Mark this unit and all its transitive dependencies as reported
                // (so we don't report them separately as unused)
                reportedUnits.add(unit);
                reportedUnits.addAll(usageReport.getAllChildren(unit));
            }
        }
    }

    private void analyzeIULocation(TargetDefinitionFile file, InstallableUnitLocation location,
            UsageReport usageReport) {
        List<? extends Unit> units = location.getUnits();
        String ref = location.getRepositories().stream().map(r -> r.getLocation()).collect(Collectors.joining(", "));
        TargetDefinitionContent content = usageReport.targetFileUnits.get(file);
        for (Unit unit : units) {
            String id = unit.getId();
            String version = unit.getVersion();
            Optional<IInstallableUnit> found;
            if (version == null || version.isBlank() || version.equals("0.0.0")) {
                found = content.query(QueryUtil.createIUQuery(id), null).stream().findFirst();
            } else if (version.startsWith("[") || version.startsWith("(")) {
                found = content
                        .query(QueryUtil.createLatestQuery(QueryUtil.createIUQuery(id, VersionRange.create(version))),
                                null)
                        .stream().findFirst();
            } else {
                found = content.query(QueryUtil.createIUQuery(id, Version.create(version)), null).stream().findFirst();
            }
            if (found.isPresent()) {
                IInstallableUnit iu = found.get();
                reportUsage(usageReport, iu, null, file, ref, content, new HashSet<>());
            }
        }
    }

    private void reportUsage(UsageReport usageReport, IInstallableUnit iu, IInstallableUnit parent,
            TargetDefinitionFile file, String location, TargetDefinitionContent content, Set<IInstallableUnit> seen) {
        if (seen.add(iu)) {
            usageReport.reportProvided(iu, file, location, parent);
            Collection<IRequirement> requirements = iu.getRequirements();
            Set<IInstallableUnit> units = content.query(QueryUtil.ALL_UNITS, null).toSet();
            for (IRequirement requirement : requirements) {
                for (IInstallableUnit provider : units) {
                    if (provider.satisfies(requirement)) {
                        reportUsage(usageReport, provider, iu, file, location, content, seen);
                    }
                }
            }
        }
    }

}
