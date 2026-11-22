package com.edispark.wireup.mcp;

import com.edispark.integrationapplication.mig.MigGeneration;
import static com.edispark.integrationapplication.mig.MigGeneration.*;
import java.util.*;

final class McpMigTools {
    private static final Map<String, Object> ID = Map.of("type", "string", "format", "uuid",
            "pattern", "^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    private static final Map<String, Object> OFFSET = Map.of("type", "integer", "minimum", 0, "maximum", 1500);

    private McpMigTools() { }

    static void register(McpTools tools, MigGeneration service) {
        tools.register("get_mig_job", "Read the state and credit totals of a specific MIG generation job.", McpTools.MIG_READ,
                schema(Map.of("jobId", ID)), "state", (args, caller) -> {
                    var job = job(args, service);
                    var result = new LinkedHashMap<String, Object>();
                    result.put("state", job.state().name());
                    result.put("estimatedCredits", job.estimatedCredits());
                    result.put("reservedCredits", job.reservedCredits());
                    result.put("settledCredits", job.settledCredits());
                    result.put("evidenceDigest", job.evidenceDigest());
                    result.put("target", job.target());
                    return result;
                });
        tools.register("read_mig_evidence", "Read a page of untrusted document excerpts and exact citation anchors from a MIG job.",
                McpTools.MIG_READ, schema(Map.of("jobId", ID, "offset", OFFSET)), "matches",
                (args, caller) -> page(job(args, service).sections(), offset(args), 12));
        tools.register("get_mig_discrepancies", "Read disagreements and requirements from a completed MIG comparison.",
                McpTools.MIG_READ, schema(Map.of("jobId", ID, "offset", OFFSET)), "matches",
                (args, caller) -> page(job(args, service).differences(), offset(args), 8));
        tools.register("find_mig_fields", "Search the verified source and destination pointers for this job's pinned format.",
                McpTools.MIG_READ, schema(Map.of("jobId", ID, "query", Map.of("type", "string", "minLength", 1, "maxLength", 120))),
                "matches", (args, caller) -> {
                    UUID id = jobId(args);
                    var fields = service.findFields(id, (String) args.get("query"));
                    return Map.of("matches", fields.entrySet().stream().map(entry ->
                            Map.of("side", entry.getKey(), "pointers", entry.getValue())).toList());
                });
        tools.register("get_mig_preview", "Read an existing candidate's side-effect-free fixture preview and validation results.",
                McpTools.MIG_READ, schema(Map.of("jobId", ID, "candidate", Map.of("type", "string", "enum", List.of("SONNET", "NOVA", "JEV")),
                        "recheck", Map.of("type", "boolean"))),
                "findings", (args, caller) -> {
                    var job = job(args, service);
                    var candidate = Objects.toString(args.get("candidate"), "");
                    var fixture = Boolean.TRUE.equals(args.get("recheck")) ? service.checkCandidate(job.id(), Candidate.valueOf(candidate))
                            : job.proposals().stream().filter(proposal -> proposal.candidate().name().equals(candidate))
                                    .map(Proposal::fixture).filter(Objects::nonNull).findFirst().orElseThrow(
                                            () -> new IllegalArgumentException("Preview unavailable"));
                    var result = new LinkedHashMap<String, Object>();
                    result.put("findings", fixture.findings().stream().limit(50).toList());
                    result.put("referenceFindings", fixture.referenceFindings() == null ? List.of()
                            : fixture.referenceFindings().stream().limit(40).toList());
                    result.put("check", fixture.check());
                    result.put("expectedMatch", fixture.expectedMatch());
                    return result;
                });
    }

    private static Job job(Map<String, Object> args, MigGeneration service) {
        return service.status(jobId(args));
    }

    private static UUID jobId(Map<String, Object> args) {
        return UUID.fromString((String) args.get("jobId"));
    }

    private static Map<String, Object> schema(Map<String, Object> fields) {
        return Map.of("type", "object", "properties", fields, "required",
                fields.containsKey("candidate") ? List.of("jobId", "candidate") : fields.containsKey("query")
                        ? List.of("jobId", "query") : List.of("jobId"), "additionalProperties", false);
    }

    private static int offset(Map<String, Object> args) {
        return args.get("offset") instanceof Number n ? n.intValue() : 0;
    }

    private static Map<String, Object> page(List<?> items, int offset, int limit) {
        int end = Math.min(items.size(), offset + limit);
        return Map.of("matches", items.subList(Math.min(offset, end), end), "nextOffset", end < items.size() ? end : -1);
    }
}
