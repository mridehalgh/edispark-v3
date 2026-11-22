package com.edispark.wireup.mcp;

import com.edispark.exchange.application.ExchangeService;
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
    static final String MFT_READ = "mft:evidence";
    static final String COMMERCIAL = "edi:commercial";
    static final java.util.Set<String> SUPPORTED_SCOPES = java.util.Set.of(READ, PREVIEW, MFT_READ, COMMERCIAL);
    static final String PRINCIPAL = "principal";
    private static final Logger log = LoggerFactory.getLogger(McpTools.class);
    private static final int RESULT_LIMIT = 96 * 1024;
    private static final Duration DEADLINE = Duration.ofSeconds(8);
    private record Entry(String scope, Map<String, Object> input, String description,
                         BiFunction<Map<String, Object>, McpAccess.Principal, Map<String, Object>> handler) {}
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, McpSyncTool> tools = new LinkedHashMap<>();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 16, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("edi-mcp-", 0).factory());
    private final Semaphore[] tenantSlots = new Semaphore[256];
    private final ObjectMapper json;
    private final MeterRegistry registry;

    McpTools(ObjectMapper json, MeterRegistry registry) {
        this.json = json;
        this.registry = registry;
        Arrays.setAll(tenantSlots, slot -> new Semaphore(2));
    }

    McpSyncToolRepository repository() {
        return new McpSyncToolRepository() {
            @Override
            public ToolsPage list(McpRequestContext context, String cursor) {
                if (cursor != null) throw new IllegalArgumentException("Unexpected cursor");
                var principal = (McpAccess.Principal) context.transportContext().get(PRINCIPAL);
                var definitions = available(principal).stream().map(tools::get).map(McpSyncTool::definition).toList();
                return new ToolsPage(definitions, null, 0L, McpSchema.CacheScope.PRIVATE);
            }

            @Override
            public McpSyncTool find(McpRequestContext context, String name) {
                var principal = (McpAccess.Principal) context.transportContext().get(PRINCIPAL);
                return available(principal).contains(name) ? tools.get(name) : null;
            }
        };
    }

    void register(String name, String description, String scope, Map<String, Object> input, String outputField,
                     BiFunction<Map<String, Object>, McpAccess.Principal, Map<String, Object>> handler) {
        String outputType = switch (outputField) {
            case "matches", "findings" -> "array";
            case "ready", "passed" -> "boolean";
            case "messageCount" -> "integer";
            default -> "string";
        };
        var definition = McpSchema.Tool.builder(name, input).description(description)
                .outputSchema(Map.of("type", "object", "required", List.of(outputField),
                        "properties", Map.of(outputField, Map.of("type", outputType))))
                .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false)
                        .idempotentHint(true).openWorldHint(false).build()).build();
        if (tools.containsKey(name)) throw new IllegalArgumentException("Duplicate MCP tool: " + name);
        entries.put(name, new Entry(scope, input, description, handler));
        tools.put(name, McpSyncTool.of(definition, (context, request) -> {
            var principal = (McpAccess.Principal) context.transportContext().get(PRINCIPAL);
            return McpSyncResponse.result(execute(name, scope, principal, request.arguments(), handler));
        }));
    }

    private List<String> available(McpAccess.Principal principal) {
        if (principal == null) return List.of();
        return entries.entrySet().stream().filter(entry -> principal.scopes().contains(entry.getValue().scope()))
                .map(Map.Entry::getKey).toList();
    }

    List<org.springframework.ai.tool.ToolCallback> callbacks(McpAccess.Principal principal, boolean member,
            java.util.function.Consumer<String> progress, java.util.function.BiConsumer<String,Object> finished) {
        var validator = McpConfiguration.schemaValidator();
        return available(principal).stream().filter(name -> !member || switch (name) {
            case "find_documents", "get_document_context", "get_document_workflow", "find_exchanges",
                 "find_document_exceptions", "list_eligible_routes", "check_outbound_readiness",
                 "prepare_related_document_draft", "prepare_invoice_draft", "prepare_document_correction", "validate_document_preview",
                 "list_business_codes" -> true;
            default -> false;
        }).map(name -> {
            var entry = entries.get(name);
            var definition = org.springframework.ai.tool.definition.ToolDefinition.builder().name(name)
                    .description(entry.description()).inputSchema(json.writeValueAsString(entry.input())).build();
            return (org.springframework.ai.tool.ToolCallback) new org.springframework.ai.tool.ToolCallback() {
                public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() { return definition; }
                public String call(String input) {
                    if (input.length() > 65536 || !validator.validate(entry.input(), input).valid()) {
                        return "INVALID_OR_UNAVAILABLE_INPUT";
                    }
                    progress.accept(name);
                    var arguments = json.readValue(input, new tools.jackson.core.type.TypeReference<Map<String,Object>>() {});
                    var result = execute(name, entry.scope(), principal, arguments, (args, actor) ->
                            searchForMatch(entry, args, actor)).structuredContent();
                    finished.accept(name, result);
                    return json.writeValueAsString(result);
                }
            };
        }).toList();
    }

    private Map<String, Object> searchForMatch(Entry entry, Map<String, Object> args, McpAccess.Principal actor) {
        var pageArgs = new LinkedHashMap<>(args);
        var result = entry.handler().apply(pageArgs, actor);
        // Filtered searches can scan a whole page of records without a single match.
        for (int page = 1; page < 48 && result.get("matches") instanceof List<?> matches && matches.isEmpty()
                && result.get("nextCursor") instanceof String next; page++) {
            if (Thread.currentThread().isInterrupted()) throw new ToolFailure("CANCELLED");
            if (next.equals(pageArgs.put("cursor", next))) break;
            result = entry.handler().apply(pageArgs, actor);
        }
        return result;
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
                case com.edispark.documents.application.query.DocumentContentQueryService.CanonicalSourceChanged ignored -> "STALE_SOURCE";
                case com.edispark.documents.application.handler.DocumentNotFoundException ignored -> "EVIDENCE_NOT_FOUND";
                case RetainedSourceChanged ignored -> "STALE_SOURCE";
                case McpResponseFields.EvidenceMissing ignored -> "EVIDENCE_NOT_FOUND";
                case com.edispark.mft.application.MftNotFoundException ignored -> "EVIDENCE_NOT_FOUND";
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
