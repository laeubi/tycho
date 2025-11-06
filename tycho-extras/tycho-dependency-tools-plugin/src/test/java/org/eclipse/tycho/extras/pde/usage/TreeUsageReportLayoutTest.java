/*******************************************************************************
 * Copyright (c) 2025 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tycho.extras.pde.usage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.tycho.extras.pde.usage.TreeUsageReportLayout;
import org.eclipse.tycho.extras.pde.usage.UsageReport;
import org.eclipse.tycho.targetplatform.TargetDefinition;
import org.eclipse.tycho.targetplatform.TargetDefinitionContent;
import org.junit.jupiter.api.Test;

/**
 * Tests for the TreeUsageReportLayout implementation.
 */
public class TreeUsageReportLayoutTest {

    /**
     * Tests that the tree layout generates structured output with proper indentation.
     */
    @Test
    void testTreeStructure() {
        UsageReport report = new UsageReport();
        
        // Create mock units
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitB = createMockUnit("unitB", "1.0.0");
        
        // Create mock target definition
        TargetDefinition targetDef = createMockTargetDefinition("my-target.target");
        TargetDefinitionContent content = createMockContent(unitA, unitB);
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        // Report units
        report.reportProvided(unitA, targetDef, "LocationL1", null);
        report.reportProvided(unitB, targetDef, "LocationL2", null);
        
        // Mark A as used
        MavenProject project = createMockProject("project1");
        report.usedUnits.add(unitA);
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitA);
        
        // Collect report output using TreeLayout
        List<String> reportLines = new ArrayList<>();
        report.generateReport(reportLines::add, new TreeUsageReportLayout());
        
        // Verify structure
        String fullReport = String.join("\n", reportLines);
        
        // Check for header
        assertTrue(fullReport.contains("DEPENDENCIES USAGE REPORT"), "Should contain corrected header");
        
        // Check for tree structure
        assertTrue(fullReport.contains("Target: my-target.target"), "Should contain target name");
        assertTrue(fullReport.contains("Location: LocationL1"), "Should contain location L1");
        assertTrue(fullReport.contains("Location: LocationL2"), "Should contain location L2");
        
        // Check for unit status indicators
        assertTrue(fullReport.contains("[USED (1 project)]"), "Should show USED status with project count");
        assertTrue(fullReport.contains("[UNUSED]"), "Should show UNUSED status");
        
        // Check for indentation (units should be indented under locations)
        assertTrue(fullReport.contains("    • unitA"), "Units should be indented with bullet");
    }
    
    /**
     * Tests that the tree layout correctly identifies and displays indirect usage.
     */
    @Test
    void testTreeIndirectUsage() {
        UsageReport report = new UsageReport();
        
        // Create mock units
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitB = createMockUnit("unitB", "1.0.0");
        
        // Set up requirements: A requires B
        IRequirement reqB = createRequirement("unitB", "1.0.0");
        when(unitA.getRequirements()).thenReturn(Arrays.asList(reqB));
        when(unitB.satisfies(reqB)).thenReturn(true);
        
        // Create mock target definition
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unitA, unitB);
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        // Report units
        report.reportProvided(unitA, targetDef, "LocationL", null);
        report.reportProvided(unitB, targetDef, "LocationL", unitA);
        
        // Mark only B as used
        report.usedUnits.add(unitB);
        MavenProject project = createMockProject("project1");
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitB);
        
        // Collect report output using TreeLayout
        List<String> reportLines = new ArrayList<>();
        report.generateReport(reportLines::add, new TreeUsageReportLayout());
        
        // Verify indirect usage is shown
        String fullReport = String.join("\n", reportLines);
        assertTrue(fullReport.contains("[INDIRECTLY USED]"), "Should show INDIRECTLY USED status");
        assertTrue(fullReport.contains("└─"), "Should show tree structure for indirect usage chain");
    }
    
    /**
     * Tests that line wrapping works correctly for long lines.
     */
    @Test
    void testLineWrapping() {
        UsageReport report = new UsageReport();
        
        // Create a unit with a very long repository location
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        
        // Create target with a long location name
        String longLocation = "https://download.eclipse.org/releases/2024-09/202409111000/plugins/repository";
        
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unitA);
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        report.reportProvided(unitA, targetDef, longLocation, null);
        report.usedUnits.add(unitA);
        MavenProject project = createMockProject("project1");
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitA);
        
        // Use a small line wrap limit to test wrapping
        TreeUsageReportLayout layout = new TreeUsageReportLayout(80);
        
        List<String> reportLines = new ArrayList<>();
        report.generateReport(reportLines::add, layout);
        
        String fullReport = String.join("\n", reportLines);
        
        // Verify report was generated (basic check)
        assertTrue(fullReport.contains("unitA"), "Should contain unit A");
        assertTrue(fullReport.contains("[USED (1 project)]"), "Should show USED status with project count");
    }
    
    /**
     * Tests that multiple target files are properly separated in the output.
     */
    @Test
    void testMultipleTargetFiles() {
        UsageReport report = new UsageReport();
        
        // Create mock units for two different targets
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitB = createMockUnit("unitB", "1.0.0");
        
        // Create two target definitions
        TargetDefinition targetDef1 = createMockTargetDefinition("target1.target");
        TargetDefinition targetDef2 = createMockTargetDefinition("target2.target");
        
        TargetDefinitionContent content1 = createMockContent(unitA);
        TargetDefinitionContent content2 = createMockContent(unitB);
        
        report.targetFiles.add(targetDef1);
        report.targetFiles.add(targetDef2);
        report.targetFileUnits.put(targetDef1, content1);
        report.targetFileUnits.put(targetDef2, content2);
        
        // Report units from different targets
        report.reportProvided(unitA, targetDef1, "Location1", null);
        report.reportProvided(unitB, targetDef2, "Location2", null);
        
        // Mark both as used
        MavenProject project = createMockProject("project1");
        report.usedUnits.add(unitA);
        report.usedUnits.add(unitB);
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitA);
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitB);
        
        // Collect report
        List<String> reportLines = new ArrayList<>();
        report.generateReport(reportLines::add, new TreeUsageReportLayout());
        
        String fullReport = String.join("\n", reportLines);
        
        // Verify both targets are shown
        assertTrue(fullReport.contains("Target: target1.target"), "Should contain target1");
        assertTrue(fullReport.contains("Target: target2.target"), "Should contain target2");
        assertTrue(fullReport.contains("Location: Location1"), "Should contain Location1");
        assertTrue(fullReport.contains("Location: Location2"), "Should contain Location2");
    }
    
    /**
     * Tests the new format for USED status with project count in brackets.
     */
    @Test
    void testUsedFormatWithProjectCount() {
        UsageReport report = new UsageReport();
        
        IInstallableUnit unit = createMockUnit("org.eclipse.wst.common.emf", "1.2.800.v202508180220");
        
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unit);
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        report.reportProvided(unit, targetDef, 
            "https://download.eclipse.org/webtools/downloads/drops/R3.39.0/R-3.39.0-20250902093744/repository/", 
            null);
        
        // Mark unit as used by 29 projects
        for (int i = 1; i <= 29; i++) {
            MavenProject project = createMockProject("project" + i);
            report.usedUnits.add(unit);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unit);
        }
        
        List<String> reportLines = new ArrayList<>();
        report.generateReport(reportLines::add, new TreeUsageReportLayout());
        
        String fullReport = String.join("\n", reportLines);
        
        // Verify the new format: [USED (29 projects)] instead of [USED]\n      Used by 29 project(s)
        assertTrue(fullReport.contains("[USED (29 projects)]"), 
            "Should show project count in brackets on same line as status");
        assertFalse(fullReport.contains("Used by 29 project(s)"), 
            "Should not show old format on separate line");
    }
    
    /**
     * Tests the new tree format for INDIRECTLY USED status.
     */
    @Test
    void testIndirectlyUsedTreeFormat() {
        UsageReport report = new UsageReport();
        
        IInstallableUnit unitA = createMockUnit("org.eclipse.emf.ecore.edit.feature.group", "2.17.0.v20240604-0832");
        IInstallableUnit unitB = createMockUnit("org.eclipse.emf.edit", "2.23.0.v20250330-0741");
        IInstallableUnit unitC = createMockUnit("org.eclipse.emf.ecore.change", "2.17.0.v20240604-0832");
        
        // Set up requirements: A > B > C
        IRequirement reqB = createRequirement("org.eclipse.emf.edit", "2.23.0.v20250330-0741");
        IRequirement reqC = createRequirement("org.eclipse.emf.ecore.change", "2.17.0.v20240604-0832");
        when(unitA.getRequirements()).thenReturn(Arrays.asList(reqB));
        when(unitB.getRequirements()).thenReturn(Arrays.asList(reqC));
        when(unitB.satisfies(reqB)).thenReturn(true);
        when(unitC.satisfies(reqC)).thenReturn(true);
        
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unitA, unitB, unitC);
        
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        report.reportProvided(unitA, targetDef, 
            "https://download.eclipse.org/modeling/emf/emf/builds/release/2.43.0", 
            null);
        report.reportProvided(unitB, targetDef, 
            "https://download.eclipse.org/modeling/emf/emf/builds/release/2.43.0", 
            unitA);
        report.reportProvided(unitC, targetDef, 
            "https://download.eclipse.org/modeling/emf/emf/builds/release/2.43.0", 
            unitB);
        
        // Mark unitC as used by 5 projects (makes A and B indirectly used)
        for (int i = 1; i <= 5; i++) {
            MavenProject project = createMockProject("project" + i);
            report.usedUnits.add(unitC);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitC);
        }
        
        List<String> reportLines = new ArrayList<>();
        report.generateReport(reportLines::add, new TreeUsageReportLayout());
        
        String fullReport = String.join("\n", reportLines);
        
        // Verify the new tree format with └─ connectors
        assertTrue(fullReport.contains("[INDIRECTLY USED]"), "Should show INDIRECTLY USED status");
        assertTrue(fullReport.contains("└─"), "Should use tree connector");
        assertTrue(fullReport.contains("(5 projects)"), "Should show project count on final node");
        assertFalse(fullReport.contains("Via:"), "Should not use old 'Via:' format");
    }

    // Helper methods for creating mock objects

    private IInstallableUnit createMockUnit(String id, String version) {
        IInstallableUnit unit = mock(IInstallableUnit.class);
        when(unit.getId()).thenReturn(id);
        when(unit.getVersion()).thenReturn(Version.create(version));
        when(unit.toString()).thenReturn(id + "/" + version);
        when(unit.getRequirements()).thenReturn(Arrays.asList());
        return unit;
    }

    private IRequirement createRequirement(String id, String version) {
        return MetadataFactory.createRequirement(
                IInstallableUnit.NAMESPACE_IU_ID,
                id,
                new VersionRange(Version.create(version), true, Version.create(version), true),
                null,
                false,
                false,
                true
        );
    }

    private TargetDefinition createMockTargetDefinition(String origin) {
        TargetDefinition targetDef = mock(TargetDefinition.class);
        when(targetDef.getOrigin()).thenReturn(origin);
        when(targetDef.getLocations()).thenReturn(Arrays.asList());
        return targetDef;
    }

    private TargetDefinitionContent createMockContent(IInstallableUnit... units) {
        TargetDefinitionContent content = mock(TargetDefinitionContent.class);
        Set<IInstallableUnit> unitSet = new HashSet<>(Arrays.asList(units));
        
        IQueryResult<IInstallableUnit> queryResult = mock(IQueryResult.class);
        when(queryResult.toSet()).thenReturn(unitSet);
        when(content.query(QueryUtil.ALL_UNITS, null)).thenReturn(queryResult);
        
        return content;
    }

    private MavenProject createMockProject(String id) {
        MavenProject project = mock(MavenProject.class);
        when(project.getId()).thenReturn(id);
        return project;
    }
}
