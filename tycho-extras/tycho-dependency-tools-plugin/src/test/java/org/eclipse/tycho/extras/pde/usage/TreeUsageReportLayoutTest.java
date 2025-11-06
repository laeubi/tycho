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
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
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
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
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
        layout.generateReport(report, false, reportLines::add);
        
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
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
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
        report.usedUnits.add(unit);
        for (int i = 1; i <= 29; i++) {
            MavenProject project = createMockProject("project" + i);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unit);
        }
        
        List<String> reportLines = new ArrayList<>();
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
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
        report.usedUnits.add(unitC);
        for (int i = 1; i <= 5; i++) {
            MavenProject project = createMockProject("project" + i);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitC);
        }
        
        List<String> reportLines = new ArrayList<>();
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
        String fullReport = String.join("\n", reportLines);
        
        // Verify the new tree format with └─ connectors
        assertTrue(fullReport.contains("[INDIRECTLY USED]"), "Should show INDIRECTLY USED status");
        assertTrue(fullReport.contains("└─"), "Should use tree connector");
        assertTrue(fullReport.contains("(5 projects)"), "Should show project count on final node");
        assertFalse(fullReport.contains("Via:"), "Should not use old 'Via:' format");
    }

    /**
     * Tests that duplicate units are not reported multiple times.
     * A unit should only appear once even if it's in multiple dependency chains.
     */
    @Test
    void testNoDuplicateUnits() {
        UsageReport report = new UsageReport();
        
        // Create three root units that all lead to the same used unit
        IInstallableUnit root1 = createMockUnit("root1", "1.0.0");
        IInstallableUnit root2 = createMockUnit("root2", "1.0.0");
        IInstallableUnit intermediate1 = createMockUnit("intermediate1", "1.0.0");
        IInstallableUnit intermediate2 = createMockUnit("intermediate2", "1.0.0");
        IInstallableUnit sharedUnit = createMockUnit("ch.qos.logback.classic", "1.5.18");
        
        // Set up requirements
        IRequirement reqInt1 = createRequirement("intermediate1", "1.0.0");
        IRequirement reqInt2 = createRequirement("intermediate2", "1.0.0");
        IRequirement reqShared = createRequirement("ch.qos.logback.classic", "1.5.18");
        
        when(root1.getRequirements()).thenReturn(Arrays.asList(reqInt1));
        when(root2.getRequirements()).thenReturn(Arrays.asList(reqInt2));
        when(intermediate1.getRequirements()).thenReturn(Arrays.asList(reqShared));
        when(intermediate2.getRequirements()).thenReturn(Arrays.asList(reqShared));
        when(intermediate1.satisfies(reqInt1)).thenReturn(true);
        when(intermediate2.satisfies(reqInt2)).thenReturn(true);
        when(sharedUnit.satisfies(reqShared)).thenReturn(true);
        
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(root1, root2, intermediate1, intermediate2, sharedUnit);
        
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        // Report the dependency chains
        report.reportProvided(root1, targetDef, "Location1", null);
        report.reportProvided(intermediate1, targetDef, "Location1", root1);
        report.reportProvided(sharedUnit, targetDef, "Location1", intermediate1);
        
        report.reportProvided(root2, targetDef, "Location2", null);
        report.reportProvided(intermediate2, targetDef, "Location2", root2);
        report.reportProvided(sharedUnit, targetDef, "Location2", intermediate2);
        
        // Mark the shared unit as used
        report.usedUnits.add(sharedUnit);
        MavenProject project = createMockProject("project1");
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(sharedUnit);
        
        List<String> reportLines = new ArrayList<>();
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
        String fullReport = String.join("\n", reportLines);
        
        // Count occurrences of the shared unit
        long sharedUnitCount = reportLines.stream()
                .filter(line -> line.contains("ch.qos.logback.classic"))
                .count();
        
        // The shared unit should appear only once (in the shortest path)
        assertTrue(sharedUnitCount == 1, 
            "ch.qos.logback.classic should appear only once, but appeared " + sharedUnitCount + " times");
    }

    /**
     * Tests that synthetic JRE units (a.jre.javase) are filtered out.
     */
    @Test
    void testSyntheticJreUnitsFiltered() {
        UsageReport report = new UsageReport();
        
        IInstallableUnit rootUnit = createMockUnit("org.eclipse.equinox.launcher", "1.7.0.v20250519-0528");
        IInstallableUnit jreUnit = createMockUnit("a.jre.javase", "21.0.0");
        IInstallableUnit realUnit = createMockUnit("org.eclipse.core.runtime", "3.29.0");
        
        // Set up requirements
        IRequirement reqJre = createRequirement("a.jre.javase", "21.0.0");
        IRequirement reqReal = createRequirement("org.eclipse.core.runtime", "3.29.0");
        
        when(rootUnit.getRequirements()).thenReturn(Arrays.asList(reqJre, reqReal));
        when(jreUnit.satisfies(reqJre)).thenReturn(true);
        when(realUnit.satisfies(reqReal)).thenReturn(true);
        
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(rootUnit, jreUnit, realUnit);
        
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        report.reportProvided(rootUnit, targetDef, "Location1", null);
        report.reportProvided(jreUnit, targetDef, "Location1", rootUnit);
        report.reportProvided(realUnit, targetDef, "Location1", rootUnit);
        
        // Mark both as used
        report.usedUnits.add(jreUnit);
        report.usedUnits.add(realUnit);
        MavenProject project = createMockProject("project1");
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(jreUnit);
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(realUnit);
        
        List<String> reportLines = new ArrayList<>();
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
        String fullReport = String.join("\n", reportLines);
        
        // JRE units should not appear in the report
        assertFalse(fullReport.contains("a.jre.javase"), 
            "Synthetic JRE unit a.jre.javase should be filtered out");
        assertTrue(fullReport.contains("org.eclipse.core.runtime"), 
            "Real unit should still appear in report");
    }

    /**
     * Tests that locations are sorted by project count (highest to lowest).
     */
    @Test
    void testLocationsSortedByProjectCount() {
        UsageReport report = new UsageReport();
        
        // Create units in different locations with different usage counts
        IInstallableUnit unit1 = createMockUnit("unit1", "1.0.0");
        IInstallableUnit unit2 = createMockUnit("unit2", "1.0.0");
        IInstallableUnit unit3 = createMockUnit("unit3", "1.0.0");
        
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unit1, unit2, unit3);
        
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        // LocationA: 2 projects use unit1
        report.reportProvided(unit1, targetDef, "LocationA", null);
        report.usedUnits.add(unit1);
        for (int i = 1; i <= 2; i++) {
            MavenProject project = createMockProject("projectA" + i);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unit1);
        }
        
        // LocationB: 5 projects use unit2
        report.reportProvided(unit2, targetDef, "LocationB", null);
        report.usedUnits.add(unit2);
        for (int i = 1; i <= 5; i++) {
            MavenProject project = createMockProject("projectB" + i);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unit2);
        }
        
        // LocationC: 3 projects use unit3
        report.reportProvided(unit3, targetDef, "LocationC", null);
        report.usedUnits.add(unit3);
        for (int i = 1; i <= 3; i++) {
            MavenProject project = createMockProject("projectC" + i);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unit3);
        }
        
        List<String> reportLines = new ArrayList<>();
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
        // Find the order of locations in the output
        int indexB = -1, indexC = -1, indexA = -1;
        for (int i = 0; i < reportLines.size(); i++) {
            String line = reportLines.get(i);
            if (line.contains("Location: LocationB")) indexB = i;
            if (line.contains("Location: LocationC")) indexC = i;
            if (line.contains("Location: LocationA")) indexA = i;
        }
        
        // LocationB (5 projects) should come before LocationC (3 projects)
        // LocationC (3 projects) should come before LocationA (2 projects)
        assertTrue(indexB > 0 && indexC > 0 && indexA > 0, "All locations should be present");
        assertTrue(indexB < indexC, "LocationB (5 projects) should come before LocationC (3 projects)");
        assertTrue(indexC < indexA, "LocationC (3 projects) should come before LocationA (2 projects)");
    }

    /**
     * Tests that root units within a location are sorted by usage count.
     */
    @Test
    void testUnitsSortedByUsageCount() {
        UsageReport report = new UsageReport();
        
        // Create units with different usage counts
        IInstallableUnit unit1 = createMockUnit("unitAlpha", "1.0.0");
        IInstallableUnit unit2 = createMockUnit("unitBeta", "1.0.0");
        IInstallableUnit unit3 = createMockUnit("unitGamma", "1.0.0");
        
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unit1, unit2, unit3);
        
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        String location = "LocationX";
        
        // unitAlpha: used by 3 projects
        report.reportProvided(unit1, targetDef, location, null);
        report.usedUnits.add(unit1);
        for (int i = 1; i <= 3; i++) {
            MavenProject project = createMockProject("projectAlpha" + i);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unit1);
        }
        
        // unitBeta: used by 7 projects
        report.reportProvided(unit2, targetDef, location, null);
        report.usedUnits.add(unit2);
        for (int i = 1; i <= 7; i++) {
            MavenProject project = createMockProject("projectBeta" + i);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unit2);
        }
        
        // unitGamma: used by 1 project
        report.reportProvided(unit3, targetDef, location, null);
        report.usedUnits.add(unit3);
        MavenProject project = createMockProject("projectGamma1");
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unit3);
        
        List<String> reportLines = new ArrayList<>();
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
        // Find the order of units in the output
        int indexAlpha = -1, indexBeta = -1, indexGamma = -1;
        for (int i = 0; i < reportLines.size(); i++) {
            String line = reportLines.get(i);
            if (line.contains("unitAlpha")) indexAlpha = i;
            if (line.contains("unitBeta")) indexBeta = i;
            if (line.contains("unitGamma")) indexGamma = i;
        }
        
        // unitBeta (7 projects) should come first
        // unitAlpha (3 projects) should come second
        // unitGamma (1 project) should come last
        assertTrue(indexAlpha > 0 && indexBeta > 0 && indexGamma > 0, "All units should be present");
        assertTrue(indexBeta < indexAlpha, "unitBeta (7 projects) should come before unitAlpha (3 projects)");
        assertTrue(indexAlpha < indexGamma, "unitAlpha (3 projects) should come before unitGamma (1 project)");
    }

    /**
     * Tests that location includes project count in the output.
     */
    @Test
    void testLocationShowsProjectCount() {
        UsageReport report = new UsageReport();
        
        IInstallableUnit unit = createMockUnit("test.unit", "1.0.0");
        
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unit);
        
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        report.reportProvided(unit, targetDef, "TestLocation", null);
        report.usedUnits.add(unit);
        
        // Add 3 projects using this unit
        for (int i = 1; i <= 3; i++) {
            MavenProject project = createMockProject("project" + i);
            report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unit);
        }
        
        List<String> reportLines = new ArrayList<>();
        new TreeUsageReportLayout().generateReport(report, false, reportLines::add);
        
        String fullReport = String.join("\n", reportLines);
        
        // Verify location shows project count
        assertTrue(fullReport.contains("Location: TestLocation (3 projects)"), 
            "Location should show project count");
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
