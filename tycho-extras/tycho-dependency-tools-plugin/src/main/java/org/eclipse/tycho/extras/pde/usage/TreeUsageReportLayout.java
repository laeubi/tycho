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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.tycho.targetplatform.TargetDefinition;

/**
 * Tree-structured report layout that organizes units by target file and location.
 */
@Component(role = ReportLayout.class, hint = "tree")
final class TreeUsageReportLayout implements ReportLayout {

    private final int lineWrapLimit;

    /**
     * Creates a tree layout with the specified line wrap limit.
     * 
     * @param lineWrapLimit
     *            maximum line length before wrapping (default 200)
     */
    public TreeUsageReportLayout(int lineWrapLimit) {
        this.lineWrapLimit = lineWrapLimit;
    }

    /**
     * Creates a tree layout with default line wrap limit of 200 characters.
     */
    public TreeUsageReportLayout() {
        this(200);
    }

    @Override
    public void generateReport(UsageReport report, Consumer<String> reportConsumer) {
        reportConsumer.accept("###### DEPENDENCIES USAGE REPORT #######");
        reportConsumer.accept("Your build uses " + report.usedUnits.size() + " dependencies.");
        reportConsumer.accept("Your build uses " + report.targetFiles.size() + " target file(s).");
        reportConsumer.accept("");

        // Group units by target file and location
        Map<TargetDefinition, Map<String, List<UnitInfo>>> targetStructure = buildTargetStructure(report);

        // Only report on root units (defined directly in target files)
        Set<IInstallableUnit> allUnits = report.providedBy.keySet();
        Set<IInstallableUnit> rootUnits = allUnits.stream().filter(report::isRootUnit).collect(Collectors.toSet());

        // Track which units have been covered by reporting their parent
        Set<IInstallableUnit> reportedUnits = new HashSet<>();

        // Generate tree output for each target file
        for (TargetDefinition targetFile : targetStructure.keySet().stream()
                .sorted(Comparator.comparing(TargetDefinition::getOrigin)).toList()) {
            reportConsumer.accept("Target: " + targetFile.getOrigin());
            int totalUnits = report.targetFileUnits.get(targetFile).query(QueryUtil.ALL_UNITS, null).toSet().size();
            reportConsumer.accept(
                    "  Total units: " + totalUnits + " from " + targetFile.getLocations().size() + " locations");

            Map<String, List<UnitInfo>> locations = targetStructure.get(targetFile);

            // Sort locations by name
            List<String> sortedLocations = new ArrayList<>(locations.keySet());
            sortedLocations.sort(String::compareTo);

            for (String location : sortedLocations) {
                List<UnitInfo> units = locations.get(location);

                // Filter to only include root units from this location
                List<UnitInfo> rootUnitsInLocation = units.stream().filter(ui -> rootUnits.contains(ui.unit))
                        .filter(ui -> !reportedUnits.contains(ui.unit))
                        .sorted(Comparator.comparing(ui -> ui.unit.toString())).toList();

                if (rootUnitsInLocation.isEmpty()) {
                    continue;
                }

                reportConsumer.accept("  Location: " + wrapLine(location, "    ", lineWrapLimit));

                for (UnitInfo unitInfo : rootUnitsInLocation) {
                    IInstallableUnit unit = unitInfo.unit;

                    // Skip if already reported
                    if (reportedUnits.contains(unit)) {
                        continue;
                    }

                    // Determine usage status
                    if (report.usedUnits.contains(unit)) {
                        // USED status - include project count in brackets
                        List<String> projects = report.projectUsage.entrySet().stream()
                                .filter(entry -> entry.getValue().contains(unit))
                                .map(project -> project.getKey().getId()).toList();
                        String status = "USED (" + projects.size() + " project" + (projects.size() == 1 ? "" : "s") + ")";
                        String unitLine = "    • " + unit + " [" + status + "]";
                        reportConsumer.accept(unitLine);
                    } else if (report.isUsedIndirectly(unit)) {
                        // INDIRECTLY USED status - show as tree
                        String unitLine = "    • " + unit + " [INDIRECTLY USED]";
                        reportConsumer.accept(unitLine);
                        
                        // Display indirect usage chain as a tree structure
                        displayIndirectUsageTree(unit, report, reportConsumer);
                    } else {
                        // UNUSED status
                        String unitLine = "    • " + unit + " [UNUSED]";
                        reportConsumer.accept(unitLine);
                        reportConsumer.accept("      Can potentially be removed");
                    }

                    // Mark this unit and all its transitive dependencies as reported
                    reportedUnits.add(unit);
                    reportedUnits.addAll(report.getAllChildren(unit));
                }
            }

            reportConsumer.accept("");
        }
    }

    /**
     * Wraps a line to fit within the specified limit, continuing on the next line with the
     * specified indent.
     */
    private String wrapLine(String text, String indent, int limit) {
        if (text.length() + indent.length() <= limit) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        String[] parts = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String part : parts) {
            if (currentLine.length() > 0 && currentLine.length() + part.length() + 1 + indent.length() > limit) {
                // Start a new line
                if (result.length() > 0) {
                    result.append("\n").append(indent);
                }
                result.append(currentLine);
                currentLine = new StringBuilder();
            }

            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(part);
        }

        // Append the last line
        if (currentLine.length() > 0) {
            if (result.length() > 0) {
                result.append("\n").append(indent);
            }
            result.append(currentLine);
        }

        return result.toString();
    }

    /**
     * Displays the indirect usage chain as a tree structure.
     */
    private void displayIndirectUsageTree(IInstallableUnit unit, UsageReport report, Consumer<String> reportConsumer) {
        Set<IInstallableUnit> allChildren = report.getAllChildren(unit);
        Set<IInstallableUnit> usedChildren = allChildren.stream()
                .filter(report.usedUnits::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        
        if (usedChildren.isEmpty()) {
            return;
        }
        
        // For each used child, find the shortest path from this unit to it
        // We'll display the first path as a tree
        for (IInstallableUnit usedChild : usedChildren.stream().limit(1).toList()) {
            List<IInstallableUnit> path = report.findPathBetween(unit, usedChild);
            
            // Skip the first element as it's the unit itself
            for (int i = 1; i < path.size(); i++) {
                IInstallableUnit pathUnit = path.get(i);
                boolean isLast = (i == path.size() - 1);
                
                // Create the tree connector
                String connector = "└─";
                String indent = "      ";
                for (int j = 1; j < i; j++) {
                    indent += "   ";
                }
                
                String line = indent + connector + " " + pathUnit;
                
                // If this is the last node and it's used, add project count
                if (isLast && report.usedUnits.contains(pathUnit)) {
                    List<String> projects = report.projectUsage.entrySet().stream()
                            .filter(entry -> entry.getValue().contains(pathUnit))
                            .map(project -> project.getKey().getId()).toList();
                    line += " (" + projects.size() + " project" + (projects.size() == 1 ? "" : "s") + ")";
                }
                
                reportConsumer.accept(line);
            }
        }
    }

    /**
     * Builds a structure mapping target files to locations to units.
     */
    private Map<TargetDefinition, Map<String, List<UnitInfo>>> buildTargetStructure(UsageReport report) {
        Map<TargetDefinition, Map<String, List<UnitInfo>>> structure = new LinkedHashMap<>();

        // Iterate through all units and organize by target and location
        for (Map.Entry<IInstallableUnit, Set<UsageReport.TargetDefinitionLocationReference>> entry : report.providedBy
                .entrySet()) {
            IInstallableUnit unit = entry.getKey();

            for (UsageReport.TargetDefinitionLocationReference ref : entry.getValue()) {
                TargetDefinition targetFile = ref.file();
                String location = ref.location();

                Map<String, List<UnitInfo>> locations = structure.computeIfAbsent(targetFile,
                        k -> new LinkedHashMap<>());
                List<UnitInfo> units = locations.computeIfAbsent(location, k -> new ArrayList<>());

                // Only add root units (those with no parent in the dependency tree)
                if (ref.parent() == null) {
                    units.add(new UnitInfo(unit, ref.parent()));
                }
            }
        }

        return structure;
    }

    private static class UnitInfo {
        final IInstallableUnit unit;
        final IInstallableUnit parent;

        UnitInfo(IInstallableUnit unit, IInstallableUnit parent) {
            this.unit = unit;
            this.parent = parent;
        }
    }
}
