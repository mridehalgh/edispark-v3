package com.edispark.wireup.mcp;

import static com.edispark.wireup.mcp.McpResponseFields.*;

import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.application.RetainedMappingSource;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
final class McpMappings {
    private static final int FINDING_LIMIT = 40;
    private final MappingService mappings;
    private final RetainedMappingSource sources;

    McpMappings(MappingService mappings, RetainedMappingSource sources) {
        this.mappings = mappings;
        this.sources = sources;
    }

    Map<String, Object> mappingRevision(Map<String, Object> args) {
        var revision = mappings.getPersisted(new McpArguments(args).id("mappingRevisionId"));
        var compatibility = revision.compatibility();
        return fields("mappingRevisionId", revision.id(), "familyId", revision.familyId(), "revision", revision.revision(),
                "status", revision.status(), "origin", revision.scope(), "sourceSchemaId", revision.sourceSchemaId(),
                "targetSchemaId", revision.targetSchemaId(), "publishedAt", revision.publishedAt(), "scheduledAt", revision.scheduledAt(),
                "documentType", compatibility == null ? null : safeCode(compatibility.documentType()),
                "direction", compatibility == null ? null : safeCode(compatibility.direction()),
                "standard", compatibility == null ? null : safeCode(compatibility.standard()),
                "release", compatibility == null ? null : safePath(compatibility.release()),
                "workspacePath", "/organisation/document-rules/" + revision.id());
    }

    Map<String, Object> compareMappings(Map<String, Object> values) {
        var args = new McpArguments(values);
        var baseline = args.id("baselineRevisionId");
        var candidate = args.id("candidateRevisionId");
        var comparison = mappings.comparePersisted(baseline, candidate);
        return fields("candidateRevisionId", candidate, "baselineRevisionId", baseline,
                "added", comparison.added().stream().limit(FINDING_LIMIT).toList(), "addedCount", comparison.added().size(),
                "removed", comparison.removed().stream().limit(FINDING_LIMIT).toList(), "removedCount", comparison.removed().size(),
                "changed", comparison.changed().stream().limit(FINDING_LIMIT).toList(), "changedCount", comparison.changed().size());
    }

    Map<String, Object> preview(Map<String, Object> args) {
        var revision = new McpArguments(args).id("mappingRevisionId");
        var source = args.containsKey("representationId") ? pin(args) : null;
        if (source == null && !args.keySet().equals(java.util.Set.of("mappingRevisionId", "input", "expected"))
                || source != null && !args.keySet().equals(java.util.Set.of("mappingRevisionId", "documentSetId",
                        "documentId", "representationId", "contentHash"))) throw new IllegalArgumentException();
        var preview = source == null
                ? mappings.preview(revision, bytes(args, "input"), bytes(args, "expected"))
                : mappings.previewRetained(revision, source, sources);
        var findings = Stream.concat(preview.warnings().stream().map(warning -> finding(warning.code(), warning.sourcePath())),
                preview.findings().stream().map(finding -> finding(finding.code(), finding.path()))).limit(FINDING_LIMIT).toList();
        return fields("mappingRevisionId", revision, "mode", source == null ? "EXPECTED_OUTPUT" : "RETAINED_SOURCE",
                "passed", preview.passed(), "expectedMatch", preview.expectedMatch(), "sourceDocument", source == null ? null : fields(
                        "documentSetId", source.documentSetId(), "documentId", source.documentId(),
                        "representationId", source.representationId(), "contentHash", source.contentHash()),
                "findings", findings, "findingsTruncated", preview.warnings().size() + preview.findings().size() > FINDING_LIMIT);
    }

    private static RetainedMappingSource.Reference pin(Map<String, Object> values) {
        var args = new McpArguments(values);
        return new RetainedMappingSource.Reference(args.id("documentSetId"), args.id("documentId"), args.id("representationId"), args.text("contentHash"));
    }

    private static Map<String, Object> finding(String code, String path) {
        return fields("code", safeCode(code), "path", safePath(path));
    }

    private static byte[] bytes(Map<String, Object> args, String key) {
        byte[] bytes = new McpArguments(args).text(key).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32_768) throw new IllegalArgumentException("Mapping fixture exceeds the byte limit");
        return bytes;
    }
}
