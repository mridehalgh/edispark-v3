package com.edispark.transformations.application;

import com.edispark.transformations.domain.MappingRevision;
import java.util.ArrayList;
import java.util.Optional;

/** Resolves direct target validation locations back to business input, including aligned repeats. */
public final class MappingFindingLocation {
    private MappingFindingLocation() { }

    public static Optional<String> sourcePath(MappingRevision mapping, String targetPath) {
        for (var node : mapping.nodes()) {
            if (node.sourcePointers().size() != 1) continue;
            String[] pattern = node.targetPointer().split("/"), actual = targetPath.split("/");
            if (pattern.length != actual.length) continue;
            var indexes = new ArrayList<String>(); boolean matches = true;
            for (int index = 0; index < pattern.length; index++) {
                if (pattern[index].equals("*") && actual[index].matches("[0-9]+")) indexes.add(actual[index]);
                else if (!pattern[index].equals(actual[index])) { matches = false; break; }
            }
            if (!matches) continue;
            String source = node.sourcePointers().getFirst();
            for (String index : indexes) source = source.replaceFirst("\\*", index);
            return Optional.of(source);
        }
        return Optional.empty();
    }
}
