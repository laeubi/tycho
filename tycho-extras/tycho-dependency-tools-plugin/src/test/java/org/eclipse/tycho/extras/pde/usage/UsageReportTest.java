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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import org.eclipse.tycho.extras.pde.usage.SimpleUsageReportLayout;
import org.eclipse.tycho.extras.pde.usage.UsageReport;
import org.eclipse.tycho.targetplatform.TargetDefinition;
import org.eclipse.tycho.targetplatform.TargetDefinitionContent;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UsageReport} that verify high-level use cases for dependency
 * usage reporting in target definitions. These tests mock locations and units to
 * assert that the report text contains expected outputs without testing implementation
 * details.
 */
public class UsageReportTest {

    /**
     * Tests that a unit directly used by a project is reported as "used".
     * 
     * <pre>
     * Target Definition
     *   Location L
     *     ├─ Unit A (directly used by project)
     * </pre>
     */
    @Test
    void testDirectlyUsedUnit() {
        UsageReport report = new UsageReport();
        
        // Create mock units
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        
        // Create mock target definition
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unitA);
        
        // Report unit A as provided by location L
        report.reportProvided(unitA, targetDef, "LocationL", null);
        
        // Mark unit A as used
        MavenProject project = createMockProject("project1");
        report.usedUnits.add(unitA);
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitA);
        
        // Verify unit A is recognized as directly used (not indirectly)
        assertFalse(report.isUsedIndirectly(unitA), "Unit A should be directly used, not indirectly");
        assertTrue(report.isRootUnit(unitA), "Unit A should be a root unit");
    }

    /**
     * Tests that when a transitive dependency is used, the root unit is reported
     * as "INDIRECTLY used" rather than unused.
     * 
     * <pre>
     * Target Definition
     *   Location L
     *     ├─ Unit A
     *        ├─ Requires X
     *        ├─ Requires Y (used by project)
     *        └─ Requires Z
     * </pre>
     */
    @Test
    void testIndirectlyUsedUnit() {
        UsageReport report = new UsageReport();
        
        // Create mock units
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitY = createMockUnit("unitY", "1.0.0");
        
        // Set up requirements: A requires Y
        IRequirement reqY = createRequirement("unitY", "1.0.0");
        when(unitA.getRequirements()).thenReturn(Arrays.asList(reqY));
        when(unitY.satisfies(reqY)).thenReturn(true);
        
        // Create mock target definition
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unitA, unitY);
        
        // Report A as root unit and Y as child of A
        report.reportProvided(unitA, targetDef, "LocationL", null);
        report.reportProvided(unitY, targetDef, "LocationL", unitA);
        
        // Mark only Y as used (not A)
        report.usedUnits.add(unitY);
        MavenProject project = createMockProject("project1");
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitY);
        
        // Verify A is recognized as indirectly used
        assertTrue(report.isUsedIndirectly(unitA), "Unit A should be indirectly used because Y is used");
        assertFalse(report.isUsedIndirectly(unitY), "Unit Y should be directly used");
        
        // Verify indirect usage chain is reported
        String chain = report.getIndirectUsageChain(unitA);
        assertTrue(chain.contains("unitA") && chain.contains("unitY"), 
                "Indirect usage chain should show path from A to Y");
    }

    /**
     * Tests that when no dependencies are used, only the root unit is reported
     * as unused, not the transitive dependencies.
     * 
     * <pre>
     * Target Definition
     *   Location L
     *     ├─ Unit A (unused)
     *        ├─ Requires X (should not be reported separately)
     *        ├─ Requires Y (should not be reported separately)
     *        └─ Requires Z (should not be reported separately)
     * </pre>
     */
    @Test
    void testUnusedUnitWithUnusedDependencies() {
        UsageReport report = new UsageReport();
        
        // Create mock units
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitX = createMockUnit("unitX", "1.0.0");
        IInstallableUnit unitY = createMockUnit("unitY", "1.0.0");
        IInstallableUnit unitZ = createMockUnit("unitZ", "1.0.0");
        
        // Set up requirements: A requires X, Y, Z
        IRequirement reqX = createRequirement("unitX", "1.0.0");
        IRequirement reqY = createRequirement("unitY", "1.0.0");
        IRequirement reqZ = createRequirement("unitZ", "1.0.0");
        when(unitA.getRequirements()).thenReturn(Arrays.asList(reqX, reqY, reqZ));
        when(unitX.satisfies(reqX)).thenReturn(true);
        when(unitY.satisfies(reqY)).thenReturn(true);
        when(unitZ.satisfies(reqZ)).thenReturn(true);
        
        // Create mock target definition
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        
        // Report A as root unit and X, Y, Z as children
        report.reportProvided(unitA, targetDef, "LocationL", null);
        report.reportProvided(unitX, targetDef, "LocationL", unitA);
        report.reportProvided(unitY, targetDef, "LocationL", unitA);
        report.reportProvided(unitZ, targetDef, "LocationL", unitA);
        
        // Nothing is used
        // Verify A is a root unit but X, Y, Z are not
        assertTrue(report.isRootUnit(unitA), "Unit A should be a root unit");
        assertFalse(report.isRootUnit(unitX), "Unit X should not be a root unit");
        assertFalse(report.isRootUnit(unitY), "Unit Y should not be a root unit");
        assertFalse(report.isRootUnit(unitZ), "Unit Z should not be a root unit");
        
        // Verify A is not indirectly used
        assertFalse(report.isUsedIndirectly(unitA), "Unit A should not be indirectly used");
    }

    /**
     * Tests deeper nesting where a unit requires another unit which has
     * its own requirements.
     * 
     * <pre>
     * Target Definition
     *   Location L
     *     ├─ Unit A
     *        └─ Requires Unit B
     *           ├─ Requires X (used by project)
     *           ├─ Requires Y
     *           └─ Requires Z
     * </pre>
     */
    @Test
    void testDeeperNesting() {
        UsageReport report = new UsageReport();
        
        // Create mock units
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitB = createMockUnit("unitB", "1.0.0");
        IInstallableUnit unitX = createMockUnit("unitX", "1.0.0");
        
        // Set up requirements: A requires B, B requires X
        IRequirement reqB = createRequirement("unitB", "1.0.0");
        IRequirement reqX = createRequirement("unitX", "1.0.0");
        when(unitA.getRequirements()).thenReturn(Arrays.asList(reqB));
        when(unitB.getRequirements()).thenReturn(Arrays.asList(reqX));
        when(unitB.satisfies(reqB)).thenReturn(true);
        when(unitX.satisfies(reqX)).thenReturn(true);
        
        // Create mock target definition
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        
        // Report units with proper parent relationships
        report.reportProvided(unitA, targetDef, "LocationL", null);
        report.reportProvided(unitB, targetDef, "LocationL", unitA);
        report.reportProvided(unitX, targetDef, "LocationL", unitB);
        
        // Mark only X as used
        report.usedUnits.add(unitX);
        MavenProject project = createMockProject("project1");
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitX);
        
        // Verify both A and B are indirectly used
        assertTrue(report.isUsedIndirectly(unitA), "Unit A should be indirectly used");
        assertTrue(report.isUsedIndirectly(unitB), "Unit B should be indirectly used");
        
        // Verify children collection works transitively
        Set<IInstallableUnit> childrenOfA = report.getAllChildren(unitA);
        assertTrue(childrenOfA.contains(unitB), "Children of A should include B");
        assertTrue(childrenOfA.contains(unitX), "Children of A should include X (transitively)");
    }

    /**
     * Tests that the provenance string includes proper nesting information
     * showing the origin, location, and dependency chain.
     * 
     * <pre>
     * Target Definition (my-target.target)
     *   Location L
     *     ├─ Unit A
     *        └─ Requires Unit B
     * </pre>
     */
    @Test
    void testProvidedByNesting() {
        UsageReport report = new UsageReport();
        
        // Create mock units
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitB = createMockUnit("unitB", "1.0.0");
        
        // Set up requirements
        IRequirement reqB = createRequirement("unitB", "1.0.0");
        when(unitA.getRequirements()).thenReturn(Arrays.asList(reqB));
        when(unitB.satisfies(reqB)).thenReturn(true);
        
        // Create mock target definition with specific origin
        TargetDefinition targetDef = createMockTargetDefinition("my-target.target");
        
        // Report units
        report.reportProvided(unitA, targetDef, "LocationL", null);
        report.reportProvided(unitB, targetDef, "LocationL", unitA);
        
        // Verify provenance for root unit
        String providedByA = report.getProvidedBy(unitA);
        assertTrue(providedByA.contains("my-target.target"), "Should contain origin");
        assertTrue(providedByA.contains("LocationL"), "Should contain location");
        
        // Verify provenance for nested unit includes path
        String providedByB = report.getProvidedBy(unitB);
        assertTrue(providedByB.contains("my-target.target"), "Should contain origin");
        assertTrue(providedByB.contains("LocationL"), "Should contain location");
    }

    /**
     * Tests that the shortest path from root to a unit is correctly computed.
     * 
     * <pre>
     * Target Definition
     *   Location L
     *     ├─ Unit A
     *        ├─ Requires B
     *        └─ Requires C
     *           └─ Requires B (longer path)
     * </pre>
     */
    @Test
    void testShortestPathFromRoot() {
        UsageReport report = new UsageReport();
        
        // Create mock units
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitB = createMockUnit("unitB", "1.0.0");
        IInstallableUnit unitC = createMockUnit("unitC", "1.0.0");
        
        // Set up requirements: A requires B and C, C also requires B
        IRequirement reqB = createRequirement("unitB", "1.0.0");
        IRequirement reqC = createRequirement("unitC", "1.0.0");
        when(unitA.getRequirements()).thenReturn(Arrays.asList(reqB, reqC));
        when(unitC.getRequirements()).thenReturn(Arrays.asList(reqB));
        when(unitB.satisfies(reqB)).thenReturn(true);
        when(unitC.satisfies(reqC)).thenReturn(true);
        
        // Create mock target definition
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        
        // Report units - B has two paths: A->B (length 2) and A->C->B (length 3)
        report.reportProvided(unitA, targetDef, "LocationL", null);
        report.reportProvided(unitB, targetDef, "LocationL", unitA);
        report.reportProvided(unitC, targetDef, "LocationL", unitA);
        // Don't add B as child of C to keep this test focused on direct path
        
        // Get shortest path from root to B
        List<IInstallableUnit> path = report.getShortestPathFromRoot(unitB);
        
        // Should be [A, B] (shortest path)
        assertEquals(2, path.size(), "Shortest path should have 2 units");
        assertEquals(unitA, path.get(0), "First unit should be A (root)");
        assertEquals(unitB, path.get(1), "Second unit should be B");
    }

    /**
     * Tests the complete report generation to ensure text output is correct.
     * 
     * <pre>
     * Target Definition
     *   Location L
     *     ├─ Unit A (used directly)
     *     ├─ Unit B (indirectly used via C)
     *        └─ Requires Unit C (used)
     *     └─ Unit D (unused)
     * </pre>
     */
    @Test
    void testReportGeneration() {
        UsageReport report = new UsageReport();
        
        // Create mock units
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitB = createMockUnit("unitB", "1.0.0");
        IInstallableUnit unitC = createMockUnit("unitC", "1.0.0");
        IInstallableUnit unitD = createMockUnit("unitD", "1.0.0");
        
        // Set up requirements: B requires C
        IRequirement reqC = createRequirement("unitC", "1.0.0");
        when(unitB.getRequirements()).thenReturn(Arrays.asList(reqC));
        when(unitC.satisfies(reqC)).thenReturn(true);
        
        // Create mock target definition
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unitA, unitB, unitC, unitD);
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        // Report units
        report.reportProvided(unitA, targetDef, "LocationL", null);
        report.reportProvided(unitB, targetDef, "LocationL", null);
        report.reportProvided(unitC, targetDef, "LocationL", unitB);
        report.reportProvided(unitD, targetDef, "LocationL", null);
        
        // Mark A and C as used
        MavenProject project = createMockProject("project1");
        report.usedUnits.add(unitA);
        report.usedUnits.add(unitC);
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitA);
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitC);
        
        // Collect report output
        List<String> reportLines = new ArrayList<>();
        report.generateReport(reportLines::add, new SimpleUsageReportLayout());
        
        // Verify report contains expected elements
        String fullReport = String.join("\n", reportLines);
        assertTrue(fullReport.contains("DEPENDENCIES USAGE REPORT"), "Should contain report header");
        assertTrue(fullReport.contains("unitA") && fullReport.contains("is used by"), 
                "Should report unitA as used");
        assertTrue(fullReport.contains("unitB") && fullReport.contains("INDIRECTLY used"), 
                "Should report unitB as indirectly used");
        assertTrue(fullReport.contains("unitD") && fullReport.contains("UNUSED"), 
                "Should report unitD as unused");
    }

    /**
     * Tests that a unit providing already-available transitive dependencies
     * is reported as UNUSED when those dependencies are already provided by
     * another used unit.
     * 
     * <pre>
     * Location L1
     *   ├─ Unit A
     *      └─ Requires Unit B
     *         ├─ Requires X (used by project)
     *         ├─ Requires Y (used by project)
     *         └─ Requires Z
     * 
     * Location L2
     *   └─ Unit Q
     *      ├─ Requires P
     *      └─ Requires Y (used by project)
     * </pre>
     * 
     * In this case Q should be reported as UNUSED because Y is already
     * provided transitively through A (which is indirectly used), making Q redundant.
     */
    @Test
    void testRedundantUnitWithSharedTransitiveDependency() {
        UsageReport report = new UsageReport();
        
        // Create mock units for Location L1
        IInstallableUnit unitA = createMockUnit("unitA", "1.0.0");
        IInstallableUnit unitB = createMockUnit("unitB", "1.0.0");
        IInstallableUnit unitX = createMockUnit("unitX", "1.0.0");
        IInstallableUnit unitY = createMockUnit("unitY", "1.0.0");
        IInstallableUnit unitZ = createMockUnit("unitZ", "1.0.0");
        
        // Create mock units for Location L2
        IInstallableUnit unitQ = createMockUnit("unitQ", "1.0.0");
        IInstallableUnit unitP = createMockUnit("unitP", "1.0.0");
        
        // Set up requirements: A requires B, B requires X, Y, Z
        IRequirement reqB = createRequirement("unitB", "1.0.0");
        IRequirement reqX = createRequirement("unitX", "1.0.0");
        IRequirement reqY = createRequirement("unitY", "1.0.0");
        IRequirement reqZ = createRequirement("unitZ", "1.0.0");
        when(unitA.getRequirements()).thenReturn(Arrays.asList(reqB));
        when(unitB.getRequirements()).thenReturn(Arrays.asList(reqX, reqY, reqZ));
        when(unitB.satisfies(reqB)).thenReturn(true);
        when(unitX.satisfies(reqX)).thenReturn(true);
        when(unitY.satisfies(reqY)).thenReturn(true);
        when(unitZ.satisfies(reqZ)).thenReturn(true);
        
        // Set up requirements: Q requires P and Y
        IRequirement reqP = createRequirement("unitP", "1.0.0");
        IRequirement reqYforQ = createRequirement("unitY", "1.0.0");
        when(unitQ.getRequirements()).thenReturn(Arrays.asList(reqP, reqYforQ));
        when(unitP.satisfies(reqP)).thenReturn(true);
        when(unitY.satisfies(reqYforQ)).thenReturn(true);
        
        // Create mock target definitions for two locations
        TargetDefinition targetDef = createMockTargetDefinition("target.target");
        TargetDefinitionContent content = createMockContent(unitA, unitB, unitX, unitY, unitZ, unitQ, unitP);
        report.targetFiles.add(targetDef);
        report.targetFileUnits.put(targetDef, content);
        
        // Report units from Location L1
        report.reportProvided(unitA, targetDef, "LocationL1", null);
        report.reportProvided(unitB, targetDef, "LocationL1", unitA);
        report.reportProvided(unitX, targetDef, "LocationL1", unitB);
        report.reportProvided(unitY, targetDef, "LocationL1", unitB);
        report.reportProvided(unitZ, targetDef, "LocationL1", unitB);
        
        // Report units from Location L2
        report.reportProvided(unitQ, targetDef, "LocationL2", null);
        report.reportProvided(unitP, targetDef, "LocationL2", unitQ);
        // Note: Y is already reported from L1, so Q also references it
        
        // Mark X and Y as used
        MavenProject project = createMockProject("project1");
        report.usedUnits.add(unitX);
        report.usedUnits.add(unitY);
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitX);
        report.projectUsage.computeIfAbsent(project, k -> new HashSet<>()).add(unitY);
        
        // Collect report output
        List<String> reportLines = new ArrayList<>();
        report.generateReport(reportLines::add, new SimpleUsageReportLayout());
        
        // Verify report contains expected elements
        String fullReport = String.join("\n", reportLines);
        
        // A should be INDIRECTLY used because X and Y are used
        assertTrue(fullReport.contains("unitA") && fullReport.contains("INDIRECTLY used"), 
                "Unit A should be indirectly used through X and Y");
        
        // Q should be UNUSED because Y is already provided transitively through A
        // and A is not unused (it's indirectly used)
        assertTrue(fullReport.contains("unitQ") && fullReport.contains("UNUSED"), 
                "Unit Q should be reported as UNUSED because Y is already available through A");
        
        // Verify Q is a root unit
        assertTrue(report.isRootUnit(unitQ), "Unit Q should be a root unit");
        
        // Verify Q is not indirectly used (even though Y is used, Y comes from A's tree)
        assertFalse(report.isUsedIndirectly(unitQ), 
                "Unit Q should not be indirectly used since Y is provided by A");
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
