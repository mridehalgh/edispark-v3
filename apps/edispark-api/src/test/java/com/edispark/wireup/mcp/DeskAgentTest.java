package com.edispark.wireup.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edispark.identifiers.tenant.TenantId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

class DeskAgentTest {
    private final ObjectMapper json = new ObjectMapper();
    private final UUID thread = UUID.randomUUID();

    @Test
    void deskToolsRespectBothMemberPermissionsAndGrantedScopes() {
        var actor = new McpAccess.Principal(TenantId.fromString("2OnHVzeAJKwm0ThFLcy1HfLHGPm"), "operator",
                "desk", Set.of(McpTools.READ, McpTools.PREVIEW), UUID.randomUUID());
        try (var tools = new McpTools(json, new SimpleMeterRegistry())) {
            Map.of("find_documents", McpTools.READ, "trace_exchange", McpTools.READ, "trace_transfer", McpTools.MFT_READ)
                    .forEach((name, scope) -> tools.register(name, name, scope, Map.of("type", "object"), "matches",
                            (args, user) -> Map.of("matches", List.of())));
            assertThat(tools.callbacks(actor, true, name -> {}, (name, result) -> {}))
                    .extracting(tool -> tool.getToolDefinition().name()).containsExactly("find_documents");
            assertThat(tools.callbacks(actor, false, name -> {}, (name, result) -> {}))
                    .extracting(tool -> tool.getToolDefinition().name()).containsExactlyInAnyOrder("find_documents", "trace_exchange");
        }
    }

    @Test
    void attachedInvoiceRequestOffersTheReviewActionOnlyWithPreviewScope() throws Exception {
        var set = UUID.randomUUID();
        var document = UUID.randomUUID();
        var path = "/documents/" + set + "/" + document;
        var review = "/documents/new?type=INVOICE&fromSet=" + set + "&fromDocument=" + document;
        var model = mock(ChatModel.class);
        var docs = mock(McpDocuments.class);
        var guard = mock(DeskGuardrail.class);
        when(guard.identifier()).thenReturn("test");
        try (var tools = new McpTools(json, new SimpleMeterRegistry())) {
            var input = Map.<String, Object>of("type", "object", "properties", Map.of("documentSetId", Map.of("type", "string"),
                    "documentId", Map.of("type", "string")), "required", List.of("documentSetId", "documentId"));
            tools.register("get_document_context", "Read the source", McpTools.READ, input, "documentId",
                    (args, principal) -> Map.of("documentSetId", set, "documentId", document));
            tools.register("find_exchanges", "Read exchanges", McpTools.READ, input, "matches",
                    (args, principal) -> Map.of("matches", List.of()));
            tools.register("prepare_invoice_draft", "Prepare invoice", McpTools.PREVIEW, input, "workspacePath",
                    (args, principal) -> Map.of("workspacePath", review, "guidance", "Check the copied details."));
            var desk = new DeskAgent(model, tools, docs, json, null, null, "", mock(DeskUsage.class), guard, null);
            assertThat(run(desk, member("viewer"), UUID.randomUUID(), "Create an invoice off this please", path))
                    .contains("Your access doesn’t allow invoice preparation").doesNotContain(review);
            assertThat(run(desk, member("author", McpTools.PREVIEW), UUID.randomUUID(), "Create an invoice off this please", path))
                    .contains("deskArtifacts", "Review invoice draft", review, "Check the copied details.");
            org.mockito.Mockito.verifyNoInteractions(model);
        }
    }

    @Test
    void investigatesThroughAnAuthorisedToolAndKeepsFollowUpsPrivate() throws Exception {
        var user = member("operator");
        var set = UUID.randomUUID();
        var doc = UUID.randomUUID();
        var documents = mock(McpDocuments.class);
        var model = mock(ChatModel.class);
        var calls = new AtomicInteger();
        try (var tools = new McpTools(json, new SimpleMeterRegistry())) {
            tools.register("find_documents", "Find orders", McpTools.READ,
                    Map.of("type", "object", "properties", Map.of()), "matches",
                    (args, principal) -> "second-page".equals(args.get("cursor"))
                            ? Map.of("matches", List.of(Map.of("documentSetId", set, "documentId", doc)))
                            : Map.of("matches", List.of(), "nextCursor", "second-page"));
            var guardrail = mock(DeskGuardrail.class);
            when(guardrail.identifier()).thenReturn("test");
            var usage = mock(DeskUsage.class);
            var assistant = new DeskAgent(model, tools, documents, json, null, null, "", usage, guardrail, null);
            when(model.stream(any(Prompt.class))).thenAnswer(invocation -> {
                Prompt prompt = invocation.getArgument(0);
                var message = switch (calls.incrementAndGet()) {
                    case 1 -> AssistantMessage.builder().content("").toolCalls(List.of(
                            new AssistantMessage.ToolCall("lookup", "function", "find_documents", "{}"))).build();
                    case 2 -> {
                        assertThat(prompt.getInstructions().getLast()).isInstanceOf(ToolResponseMessage.class);
                        yield new AssistantMessage("The order has arrived.");
                    }
                    default -> {
                        assertThat(prompt.getContents()).contains("The order has arrived.");
                        yield new AssistantMessage("Open the order to see its details.");
                    }
                };
                return Flux.fromIterable(List.of(new AssistantMessage("<thi"), new AssistantMessage("nking>private notes</think"),
                        new AssistantMessage("ing>"), message)).map(part -> new ChatResponse(List.of(new Generation(part))));
            });

            var question = UUID.randomUUID();
            assertThat(run(assistant, user, question, "Which orders arrived?"))
                    .doesNotContain("private notes", "<thinking>")
                    .contains("STEP_STARTED", "find_documents", "STEP_FINISHED", "The order has arrived.",
                            "/documents/" + set + "/" + doc, "RUN_FINISHED");
            assertThat(run(assistant, user, question, "Which orders arrived?")).contains("RUN_FINISHED");
            assertThat(calls).hasValue(2);
            assertThat(run(assistant, user, UUID.randomUUID(), "Where are its details?")).contains("Open the order");
            org.mockito.Mockito.verify(usage, org.mockito.Mockito.times(3)).reserve(org.mockito.ArgumentMatchers.anyLong());
            when(usage.reserve(org.mockito.ArgumentMatchers.anyLong())).thenThrow(new DeskUsage.Refusal("AI_MONTHLY_LIMIT","Allowance exhausted."));
            assertThat(run(assistant, user, UUID.randomUUID(), "Look again")).contains("RUN_ERROR", "AI_MONTHLY_LIMIT");
            assertThat(calls).hasValue(3);
            assertThat(assistant.history(user, thread)).hasSize(2);
            assertThat(assistant.threads(user)).hasSize(1);
            assertThat(assistant.history(member("another-operator"), thread)).isEmpty();
            when(documents.context(set, doc)).thenThrow(new IllegalArgumentException("record unavailable"));
            assertThat(assistant.history(user, thread)).isEmpty();
        }
    }

    private String run(DeskAgent desk, JwtAuthenticationToken user, UUID id, String question) throws Exception {
        return run(desk, user, id, question, "");
    }

    private String run(DeskAgent desk, JwtAuthenticationToken user, UUID id, String question, String record) throws Exception {
        var response = new MockHttpServletResponse();
        desk.run(user, json.valueToTree(Map.of("threadId", thread, "runId", UUID.randomUUID(), "messages",
                List.of(Map.of("role", "user", "content", question, "id", id)), "forwardedProps", Map.of("record", record))), response);
        return response.getContentAsString();
    }

    private JwtAuthenticationToken member(String subject, String... scopes) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "test")
                .subject(subject).claim("tenant_id", "2OnHVzeAJKwm0ThFLcy1HfLHGPm")
                .claim("organization_role", "member").claim("agent_scopes", scopes.length == 0 ? List.of(McpTools.READ) : List.of(scopes))
                .expiresAt(Instant.now().plusSeconds(300)).build());
    }
}
