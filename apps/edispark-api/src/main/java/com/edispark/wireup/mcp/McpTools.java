package com.edispark.wireup.mcp;

import com.edispark.exchange.application.ExchangeService;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.transformations.application.RetainedSourceChanged;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

final class McpTools implements AutoCloseable {
    static final String READ = "edi:evidence";
    static final String PREVIEW = "edi:preview";
    static final String PRINCIPAL = "principal";
    private static final Logger log = LoggerFactory.getLogger(McpTools.class);
    private static final int RESULT_LIMIT = 96 * 1024;
    private static final Duration DEADLINE = Duration.ofSeconds(8);
    private static final Map<String, Object> ID = Map.of("type", "string", "format", "uuid",
            "pattern", "^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    private static final Map<String, Object> INSTANT = Map.of("type", "string", "format", "date-time", "maxLength", 40);
    private static final Map<String, Object> PINS = Map.of("documentSetId", ID, "documentId", ID, "representationId", ID,
            "contentHash", Map.of("type", "string", "pattern", "^sha-256:[0-9a-fA-F]{64}$"));
    private static final Map<String, Object> ROUTE = Map.of("agreementId", ID, "capabilityId", ID);

    private final Map<String, McpSyncTool> tools = new LinkedHashMap<>();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 16, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("edi-mcp-", 0).factory());
    private final Semaphore[] tenantSlots = new Semaphore[256];
    private final ObjectMapper json;
    private final MeterRegistry registry;

    McpTools(McpEvidence evidence, McpCursor cursor, ObjectMapper json, MeterRegistry registry) {
        this.json = json;
        this.registry = registry;
        Arrays.setAll(tenantSlots, slot -> new Semaphore(2));
        var filters = Map.<String, Object>of("exchangeId", ID, "from", INSTANT, "to", INSTANT, "partnerId", ID,
                "sourceMessageReference", Map.of("type", "string", "maxLength", 128, "pattern", "^[^\\p{Cntrl}]+$"),
                "direction", enumValues(ExchangeDirection.values()), "status", enumValues(ExchangeStatus.values()),
                "cursor", boundedString(McpCursor.TOKEN_LENGTH));
        add("find_exchanges", "Find an exchange by ID or page through a date window of up to 31 days.", READ,
                schema(filters), "matches", (args, principal) -> evidence.find(args, cursor, principal.tenant().asString()));
        add("trace_exchange", "Read persisted document, mapping, transfer and acknowledgement evidence.", READ,
                schema(Map.of("exchangeId", ID), "exchangeId"), "exchangeId", (args, principal) -> evidence.trace(args));
        add("get_route_context", "Read a persisted trading agreement capability and its qualification and revision pins.", READ,
                schema(ROUTE, "agreementId", "capabilityId"), "capabilityId", (args, principal) -> evidence.route(args));
        var mapping = new LinkedHashMap<>(PINS);
        mapping.putAll(Map.of("mappingRevisionId", ID, "input", boundedString(32_768), "expected", boundedString(32_768)));
        add("preview_mapping", "Test a mapping revision using synthetic input and expected JSON strings, or an exact retained outbound representation.",
                PREVIEW, schema(mapping, "mappingRevisionId"), "passed", (args, principal) -> evidence.preview(args));
        var readiness = new LinkedHashMap<>(PINS);
        readiness.putAll(ROUTE);
        add("check_outbound_readiness", "Check whether a pinned document is ready for the selected outbound route.", PREVIEW,
                schema(readiness, "documentSetId", "documentId", "representationId", "contentHash", "agreementId", "capabilityId"),
                "ready", (args, principal) -> evidence.readiness(args));
    }

    McpSyncToolRepository repository() {
        return new McpSyncToolRepository() {
            @Override
            public ToolsPage list(McpRequestContext context, String cursor) {
                if (cursor != null) throw new IllegalArgumentException("Unexpected cursor");
                return new ToolsPage(tools.values().stream().map(McpSyncTool::definition).toList(), null, 0L,
                        McpSchema.CacheScope.PRIVATE);
            }

            @Override
            public McpSyncTool find(McpRequestContext context, String name) {
                return tools.get(name);
            }
        };
    }

    private void add(String name, String description, String scope, Map<String, Object> input, String outputField,
                     BiFunction<Map<String, Object>, McpAccess.Principal, Map<String, Object>> handler) {
        String outputType = switch (outputField) {
            case "matches" -> "array";
            case "ready", "passed" -> "boolean";
            default -> "string";
        };
        var definition = McpSchema.Tool.builder(name, input).description(description)
                .outputSchema(Map.of("type", "object", "required", List.of(outputField),
                        "properties", Map.of(outputField, Map.of("type", outputType))))
                .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false)
                        .idempotentHint(true).openWorldHint(false).build()).build();
        tools.put(name, McpSyncTool.of(definition, (context, request) -> {
            var principal = (McpAccess.Principal) context.transportContext().get(PRINCIPAL);
            return McpSyncResponse.result(execute(name, scope, principal, request.arguments(), handler));
        }));
    }

    private McpSchema.CallToolResult execute(String name, String scope, McpAccess.Principal principal,
            Map<String, Object> args, BiFunction<Map<String, Object>, McpAccess.Principal, Map<String, Object>> handler) {
        long started = System.nanoTime();
        String outcome = "OK";
        try {
            if (principal == null || !principal.scopes().contains(scope)) throw new ToolFailure("INSUFFICIENT_SCOPE");
            var slot = tenantSlots[Math.floorMod(principal.tenant().hashCode(), tenantSlots.length)];
            var result = run(slot, principal, () -> handler.apply(args, principal));
            String content = json.writeValueAsString(result);
            if (content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > RESULT_LIMIT) {
                throw new ToolFailure("RESULT_LIMIT");
            }
            return McpSchema.CallToolResult.builder().structuredContent(result).addContent(new McpSchema.TextContent(null, content, null)).build();
        } catch (RuntimeException exception) {
            outcome = switch (exception) {
                case ToolFailure failure -> failure.code;
                case ExchangeService.SourceChanged ignored -> "STALE_SOURCE";
                case RetainedSourceChanged ignored -> "STALE_SOURCE";
                case McpEvidence.EvidenceMissing ignored -> "EVIDENCE_NOT_FOUND";
                case IllegalArgumentException ignored -> "INVALID_OR_UNAVAILABLE_INPUT";
                case DateTimeParseException ignored -> "INVALID_OR_UNAVAILABLE_INPUT";
                default -> "EVIDENCE_UNAVAILABLE";
            };
            return McpSchema.CallToolResult.builder().isError(true).structuredContent(Map.of("code", outcome))
                    .addContent(new McpSchema.TextContent(null, outcome, null)).build();
        } finally {
            registry.timer("edispark.mcp.tool", "tool", name, "outcome", outcome)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            if (principal != null) {
                var ids = args.entrySet().stream().filter(entry -> entry.getKey().endsWith("Id"))
                        .filter(entry -> entry.getValue() instanceof String value && value.matches("[a-fA-F0-9-]{36}"))
                        .map(Map.Entry::getValue).toList();
                log.info("mcp_tool tool={} outcome={} tenant={} actor={} client={} correlationId={} records={}",
                        name, outcome, principal.tenant(), principal.subject(), principal.client(), principal.correlation(), ids);
            }
        }
    }

    private Map<String, Object> run(Semaphore slot, McpAccess.Principal principal,
                                    java.util.function.Supplier<Map<String, Object>> action) {
        Future<Map<String, Object>> task;
        try {
            task = workers.submit(() -> {
                if (!slot.tryAcquire()) throw new ToolFailure("BUSY");
                try {
                    return TenantContext.getWhere(TenantContextHolder.of(principal.tenant()), action);
                } finally {
                    slot.release();
                }
            });
        } catch (RejectedExecutionException exception) {
            throw new ToolFailure("BUSY");
        }
        try {
            return task.get(DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            throw new ToolFailure("DEADLINE_EXCEEDED");
        } catch (InterruptedException exception) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new ToolFailure("CANCELLED");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new ToolFailure("EVIDENCE_UNAVAILABLE");
        }
    }

    private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        return Map.of("type", "object", "properties", properties, "required", List.of(required), "additionalProperties", false);
    }

    private static Map<String, Object> boundedString(int length) {
        return Map.of("type", "string", "minLength", 1, "maxLength", length);
    }

    private static Map<String, Object> enumValues(Enum<?>[] values) {
        return Map.of("type", "string", "enum", Arrays.stream(values).map(Enum::name).toList());
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }

    private static final class ToolFailure extends RuntimeException {
        private final String code;

        private ToolFailure(String code) {
            this.code = code;
        }
    }
}
