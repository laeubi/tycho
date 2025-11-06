package org.eclipse.tycho.extras.pde;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.tycho.targetplatform.TargetDefinition;
import org.eclipse.tycho.targetplatform.TargetDefinition.InstallableUnitLocation;
import org.eclipse.tycho.targetplatform.TargetDefinition.Location;
import org.eclipse.tycho.targetplatform.TargetDefinition.Unit;
import org.eclipse.tycho.targetplatform.TargetDefinitionContent;
import org.eclipse.tycho.targetplatform.TargetDefinitionFile;

final class UsageReport {
    /**
     * Maximum number of indirect usage examples to show in the report
     */
    private static final int MAX_INDIRECT_USAGE_EXAMPLES = 3;

    /**
     * Maps a project to the units it uses (or at least is resolved to)
     */
    final Map<MavenProject, Set<IInstallableUnit>> projectUsage = new HashMap<>();
    /**
     * A collection of units used by all projects in the reactor
     */
    final Set<IInstallableUnit> usedUnits = new HashSet<>();
    /**
     * A collection of all used target files in the reactor
     */
    final Set<TargetDefinitionFile> targetFiles = new HashSet<>();
    /**
     * Maps a target file to its actual content
     */
    final Map<TargetDefinitionFile, TargetDefinitionContent> targetFileUnits = new HashMap<>();
    /**
     * Maps a unit to the set of definition files this unit is defined in
     */
    final Map<IInstallableUnit, Set<TargetDefinitionLocationReference>> providedBy = new HashMap<>();

    private final Map<IInstallableUnit, Set<IInstallableUnit>> parentMap = new HashMap<>();

    private final Map<IInstallableUnit, Set<IInstallableUnit>> childMap = new HashMap<>();

    void reportProvided(IInstallableUnit iu, TargetDefinition file, String location, IInstallableUnit parent) {
        if (parent != null) {
            parentMap.computeIfAbsent(iu, nil -> new HashSet<>()).add(parent);
            childMap.computeIfAbsent(parent, nil -> new HashSet<>()).add(iu);
        }
        providedBy.computeIfAbsent(iu, nil -> new HashSet<>())
                .add(new TargetDefinitionLocationReference(parent, file, location));
    }

    static record TargetDefinitionLocationReference(IInstallableUnit parent, TargetDefinition file, String location) {

    }

    /**
     * Returns true if this unit is a root unit (defined directly in the target file)
     */
    boolean isRootUnit(IInstallableUnit unit) {
        Set<TargetDefinitionLocationReference> refs = providedBy.get(unit);
        if (refs == null) {
            return false;
        }
        return refs.stream().anyMatch(ref -> ref.parent() == null);
    }

    /**
     * Gets all transitive children of a unit
     */
    Set<IInstallableUnit> getAllChildren(IInstallableUnit unit) {
        Set<IInstallableUnit> result = new HashSet<>();
        collectChildren(unit, result);
        return result;
    }

    private void collectChildren(IInstallableUnit unit, Set<IInstallableUnit> result) {
        Set<IInstallableUnit> children = childMap.get(unit);
        if (children != null) {
            for (IInstallableUnit child : children) {
                if (result.add(child)) {
                    collectChildren(child, result);
                }
            }
        }
    }

    /**
     * Checks if any child in the dependency tree of this unit is used
     */
    boolean hasUsedChildren(IInstallableUnit unit) {
        Set<IInstallableUnit> allChildren = getAllChildren(unit);
        return allChildren.stream().anyMatch(usedUnits::contains);
    }

    /**
     * Gets the shortest path from a root unit to the target unit
     */
    List<IInstallableUnit> getShortestPathFromRoot(IInstallableUnit unit) {
        // BFS to find shortest path from any root to this unit
        Set<IInstallableUnit> visited = new HashSet<>();
        Deque<List<IInstallableUnit>> queue = new ArrayDeque<>();

        // Start from the unit itself
        List<IInstallableUnit> initialPath = new ArrayList<>();
        initialPath.add(unit);
        queue.add(initialPath);
        visited.add(unit);

        List<IInstallableUnit> shortestPath = null;

        while (!queue.isEmpty()) {
            List<IInstallableUnit> currentPath = queue.poll();
            IInstallableUnit current = currentPath.get(currentPath.size() - 1);

            // Check if we've reached a root
            if (isRootUnit(current)) {
                if (shortestPath == null || currentPath.size() < shortestPath.size()) {
                    shortestPath = new ArrayList<>(currentPath);
                }
                continue; // Keep searching for potentially shorter paths
            }

            // Explore parents
            Set<IInstallableUnit> parents = parentMap.get(current);
            if (parents != null) {
                for (IInstallableUnit parent : parents) {
                    if (!visited.contains(parent)) {
                        visited.add(parent);
                        List<IInstallableUnit> newPath = new ArrayList<>(currentPath);
                        newPath.add(parent);
                        queue.add(newPath);
                    }
                }
            }
        }

        // Reverse the path so it goes from root to unit
        if (shortestPath != null) {
            List<IInstallableUnit> reversed = new ArrayList<>(shortestPath);
            java.util.Collections.reverse(reversed);
            return reversed;
        }

        return List.of(unit);
    }

    /**
     * Returns formatted string showing where the unit is provided from, including nesting
     */
    String getProvidedBy(IInstallableUnit unit) {
        Set<TargetDefinitionLocationReference> refs = providedBy.get(unit);
        if (refs == null || refs.isEmpty()) {
            return "unknown";
        }

        // Find the reference with the shortest path to a root
        return refs.stream().map(ref -> {
            StringBuilder sb = new StringBuilder();
            sb.append(ref.file().getOrigin());
            sb.append(" > ");
            sb.append(ref.location());

            // If this has a parent, show the nesting
            if (ref.parent() != null) {
                List<IInstallableUnit> path = getShortestPathFromRoot(unit);
                if (path.size() > 1) {
                    sb.append(" > ");
                    sb.append(path.stream().map(IInstallableUnit::toString).collect(Collectors.joining(" > ")));
                }
            }

            return sb.toString();
        }).collect(Collectors.joining("; "));
    }

    /**
     * Checks if a unit is used indirectly (used unit is a descendant, not the unit itself)
     */
    boolean isUsedIndirectly(IInstallableUnit unit) {
        if (usedUnits.contains(unit)) {
            return false; // Used directly
        }
        return hasUsedChildren(unit);
    }

    /**
     * Gets the chain showing how this unit is indirectly used
     */
    String getIndirectUsageChain(IInstallableUnit unit) {
        Set<IInstallableUnit> allChildren = getAllChildren(unit);
        Set<IInstallableUnit> usedChildren = allChildren.stream().filter(usedUnits::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (usedChildren.isEmpty()) {
            return "";
        }

        // For each used child, find the shortest path from this unit to it
        return usedChildren.stream().limit(MAX_INDIRECT_USAGE_EXAMPLES).map(usedChild -> {
            List<IInstallableUnit> path = findPathBetween(unit, usedChild);
            return path.stream().map(IInstallableUnit::toString).collect(Collectors.joining(" > "));
        }).collect(Collectors.joining("; "));
    }

    /**
     * Finds shortest path between two units using BFS
     */
    private List<IInstallableUnit> findPathBetween(IInstallableUnit start, IInstallableUnit end) {
        Set<IInstallableUnit> visited = new HashSet<>();
        Deque<List<IInstallableUnit>> queue = new ArrayDeque<>();

        List<IInstallableUnit> initialPath = new ArrayList<>();
        initialPath.add(start);
        queue.add(initialPath);
        visited.add(start);

        while (!queue.isEmpty()) {
            List<IInstallableUnit> currentPath = queue.poll();
            IInstallableUnit current = currentPath.get(currentPath.size() - 1);

            if (current.equals(end)) {
                return currentPath;
            }

            Set<IInstallableUnit> children = childMap.get(current);
            if (children != null) {
                for (IInstallableUnit child : children) {
                    if (!visited.contains(child)) {
                        visited.add(child);
                        List<IInstallableUnit> newPath = new ArrayList<>(currentPath);
                        newPath.add(child);
                        queue.add(newPath);
                    }
                }
            }
        }

        return List.of(start, end); // Fallback
    }

    void generateReport(Consumer<String> reportConsumer) {
        reportConsumer.accept("###### DEPENDECIES USAGE REPORT #######");
        reportConsumer.accept("Your build uses " + usedUnits.size() + " dependencies.");
        reportConsumer.accept("Your build uses " + targetFiles.size() + " target file(s).");
        for (TargetDefinitionFile targetFile : targetFiles) {
            reportConsumer.accept(targetFile.getOrigin() + " contains "
                    + targetFileUnits.get(targetFile).query(QueryUtil.ALL_UNITS, null).toSet().size() + " units from "
                    + targetFile.getLocations().size() + " locations");
        }

        // Only report on root units (defined directly in target files)
        Set<IInstallableUnit> allUnits = providedBy.keySet();
        Set<IInstallableUnit> rootUnits = allUnits.stream().filter(this::isRootUnit).collect(Collectors.toSet());

        // Track which units have been covered by reporting their parent
        Set<IInstallableUnit> reportedUnits = new HashSet<>();

        for (IInstallableUnit unit : rootUnits) {
            // Skip if this unit was already covered by a parent report
            if (reportedUnits.contains(unit)) {
                continue;
            }

            String by = getProvidedBy(unit);

            if (usedUnits.contains(unit)) {
                // Unit is directly used - report it and mark all its children as reported
                List<String> list = projectUsage.entrySet().stream().filter(entry -> entry.getValue().contains(unit))
                        .map(project -> project.getKey().getId()).toList();
                reportConsumer.accept("The unit " + unit + " is used by " + list.size()
                        + " projects and currently provided by " + by);

                // Mark this unit and all its transitive dependencies as reported
                reportedUnits.add(unit);
                reportedUnits.addAll(getAllChildren(unit));
            } else if (isUsedIndirectly(unit)) {
                // Unit is indirectly used (one of its dependencies is used but not the unit itself)
                String chain = getIndirectUsageChain(unit);
                reportConsumer.accept("The unit " + unit + " is INDIRECTLY used through: " + chain
                        + " and currently provided by " + by);

                // Mark this unit and all its transitive dependencies as reported
                reportedUnits.add(unit);
                reportedUnits.addAll(getAllChildren(unit));
            } else {
                // Unit and all its dependencies are unused
                reportConsumer.accept("The unit " + unit + " is UNUSED and currently provided by " + by);

                // Mark this unit and all its transitive dependencies as reported
                // (so we don't report them separately as unused)
                reportedUnits.add(unit);
                reportedUnits.addAll(getAllChildren(unit));
            }
        }
    }

    void analyzeLocations(TargetDefinition definitionFile, BiConsumer<Location, RuntimeException> exceptionConsumer) {
        for (Location location : definitionFile.getLocations()) {
            try {
                if (location instanceof InstallableUnitLocation iu) {
                    analyzeIULocation(definitionFile, iu);
                }
            } catch (RuntimeException e) {
                exceptionConsumer.accept(location, e);
            }
        }
    }

    private void analyzeIULocation(TargetDefinition file, InstallableUnitLocation location) {
        List<? extends Unit> units = location.getUnits();
        String ref = location.getRepositories().stream().map(r -> r.getLocation()).collect(Collectors.joining(", "));
        TargetDefinitionContent content = targetFileUnits.get(file);
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
                reportUsage(iu, null, file, ref, content, new HashSet<>());
            }
        }
    }

    private void reportUsage(IInstallableUnit iu, IInstallableUnit parent, TargetDefinition file, String location,
            TargetDefinitionContent content, Set<IInstallableUnit> seen) {
        if (seen.add(iu)) {
            reportProvided(iu, file, location, parent);
            Collection<IRequirement> requirements = iu.getRequirements();
            Set<IInstallableUnit> units = content.query(QueryUtil.ALL_UNITS, null).toSet();
            for (IRequirement requirement : requirements) {
                for (IInstallableUnit provider : units) {
                    if (provider.satisfies(requirement)) {
                        reportUsage(provider, iu, file, location, content, seen);
                    }
                }
            }
        }
    }

}
