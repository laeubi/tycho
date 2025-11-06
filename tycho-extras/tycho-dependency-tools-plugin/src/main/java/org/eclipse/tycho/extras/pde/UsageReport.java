package org.eclipse.tycho.extras.pde;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
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

    void reportProvided(IInstallableUnit iu, TargetDefinitionFile file, String location, IInstallableUnit parent) {
        if (parent != null) {
            parentMap.computeIfAbsent(iu, nil -> new HashSet<>()).add(parent);
            childMap.computeIfAbsent(parent, nil -> new HashSet<>()).add(iu);
        }
        providedBy.computeIfAbsent(iu, nil -> new HashSet<>())
                .add(new TargetDefinitionLocationReference(parent, file, location));
    }

    static record TargetDefinitionLocationReference(IInstallableUnit parent, TargetDefinitionFile file,
            String location) {

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
        return refs.stream()
                .map(ref -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(ref.file().getOrigin());
                    sb.append(" > ");
                    sb.append(ref.location());
                    
                    // If this has a parent, show the nesting
                    if (ref.parent() != null) {
                        List<IInstallableUnit> path = getShortestPathFromRoot(unit);
                        if (path.size() > 1) {
                            sb.append(" > ");
                            sb.append(path.stream()
                                    .map(IInstallableUnit::toString)
                                    .collect(Collectors.joining(" > ")));
                        }
                    }
                    
                    return sb.toString();
                })
                .collect(Collectors.joining("; "));
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
        Set<IInstallableUnit> usedChildren = allChildren.stream()
                .filter(usedUnits::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        
        if (usedChildren.isEmpty()) {
            return "";
        }
        
        // For each used child, find the shortest path from this unit to it
        return usedChildren.stream()
                .limit(MAX_INDIRECT_USAGE_EXAMPLES)
                .map(usedChild -> {
                    List<IInstallableUnit> path = findPathBetween(unit, usedChild);
                    return path.stream()
                            .map(IInstallableUnit::toString)
                            .collect(Collectors.joining(" > "));
                })
                .collect(Collectors.joining("; "));
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

}
