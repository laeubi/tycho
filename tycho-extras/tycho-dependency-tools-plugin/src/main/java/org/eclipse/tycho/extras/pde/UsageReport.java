package org.eclipse.tycho.extras.pde;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.project.MavenProject;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.tycho.targetplatform.TargetDefinitionContent;
import org.eclipse.tycho.targetplatform.TargetDefinitionFile;

final class UsageReport {
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

    String getProvidedBy(IInstallableUnit unit) {
        Set<TargetDefinitionLocationReference> set = providedBy.get(unit);
        return set.stream().map(ref -> ref.file().getOrigin()).collect(Collectors.joining(","));
    }

}
