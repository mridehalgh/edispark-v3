package com.edispark.transformations.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public record MappingRevisionComparison(List<UUID> added, List<UUID> removed, List<UUID> changed) {
    public static MappingRevisionComparison between(MappingRevision baseline, MappingRevision candidate) {
        if (!baseline.sourceSchemaId().equals(candidate.sourceSchemaId())
                || !baseline.targetSchemaId().equals(candidate.targetSchemaId())) {
            throw new IllegalArgumentException("Compare revisions that use the same source and target schemas");
        }
        var before = byId(baseline);
        var after = byId(candidate);
        var added = after.keySet().stream().filter(id -> !before.containsKey(id)).sorted().toList();
        var removed = before.keySet().stream().filter(id -> !after.containsKey(id)).sorted().toList();
        var changed = after.keySet().stream().filter(id -> before.containsKey(id) && !after.get(id).equals(before.get(id))).sorted().toList();
        return new MappingRevisionComparison(added, removed, changed);
    }

    private static Map<UUID, MappingNode> byId(MappingRevision revision) {
        return revision.nodes().stream().collect(Collectors.toMap(MappingNode::id, Function.identity()));
    }
}
