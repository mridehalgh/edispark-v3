package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

class LondonMigModelTest {
    @Test
    void scopesModelToolsToThePinnedGuideAndJournalsEveryRemoteTurn() {
        var calls = new ArrayList<ConverseRequest>();
        var phases = new ArrayList<String>();
        BedrockRuntimeClient client = (BedrockRuntimeClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BedrockRuntimeClient.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("converse")) throw new UnsupportedOperationException(method.getName());
                    calls.add((ConverseRequest) args[0]);
                    return calls.size() == 1 ? toolRequest() : proposal();
                });
        var rate = new MigCandidateModel.Rate(1_000_000, 2_000_000);
        var models = new LondonMigModel(client, "synthetic-test-guardrail", "1", Map.of(Candidate.NOVA, rate));
        var pack = new MigEvidence.Pack(1, new Coordinates("INBOUND", "ORDER", "EDIFACT", "D01B", "ORDERS"),
                null, List.of(), List.of(new Section("PRIMARY/test/line 1", "Cited order identifier.")), List.of(), List.of(), "digest");
        var job = new MigJob(UUID.randomUUID(), State.RUNNING, 0L, 1, pack, List.of(), 0, Map.of(), List.of(), List.of(),
                List.of(), null, null, null, null, null, null, ModelMode.LOCAL_FIXTURE, null, null);
        var tools = new MigCandidateTools(job, new MigReview(), null);
        var result = models.propose(Candidate.NOVA, UUID.randomUUID(), pack, "{}".getBytes(StandardCharsets.UTF_8), rate,
                tools, (turn, phase, actual, receipt) -> phases.add(turn + ":" + phase));

        assertThat(calls).hasSize(2).allSatisfy(call -> {
            assertThat(call.modelId()).isEqualTo(LondonMigModel.NOVA);
            assertThat(call.guardrailConfig().trace()).isEqualTo(GuardrailTrace.DISABLED);
        });
        assertThat(calls.getFirst().toolConfig().tools()).hasSize(4);
        assertThat(calls.getLast().toolConfig()).isNull();
        var followup = calls.getLast().messages().getFirst().content();
        assertThat(followup.getFirst().guardContent().text().text()).isEqualTo("{}");
        assertThat(followup.getLast().guardContent().text().text()).contains("Cited order identifier.");
        assertThat(phases).containsExactly("1:DISPATCHED", "1:CONFIRMED", "2:DISPATCHED", "2:CONFIRMED");
        assertThat(result.proposal().requirements()).extracting(MigCandidateModel.Requirement::description)
                .containsExactly("Order number");
        assertThat(result.actualMicros()).isEqualTo(24L);
    }

    private ConverseResponse toolRequest() {
        var input = Document.fromMap(Map.of("anchors", Document.fromList(
                List.of(Document.fromString("PRIMARY/test/line 1"), Document.fromString("OTHER/test/line 3")))));
        var content = ContentBlock.fromToolUse(use -> use.toolUseId("call-1").name("read_mig_sections").input(input));
        return response(StopReason.TOOL_USE, content);
    }

    private ConverseResponse proposal() {
        return response(StopReason.END_TURN, ContentBlock.fromText("""
                {"requirements":[{"description":"Order number","citations":["PRIMARY/test/line 1"],"unsupported":"No runtime"}]}
                """));
    }

    private ConverseResponse response(StopReason reason, ContentBlock block) {
        return ConverseResponse.builder().stopReason(reason).usage(u -> u.inputTokens(6).outputTokens(3))
                .output(ConverseOutput.fromMessage(message -> message.role(ConversationRole.ASSISTANT).content(block))).build();
    }
}
