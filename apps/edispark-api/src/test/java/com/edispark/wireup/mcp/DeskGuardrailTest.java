package com.edispark.wireup.mcp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

class DeskGuardrailTest {
    private final DeskGuardrail guard = new DeskGuardrail("guardrail-id", "3");

    @Test
    void usesPublishedVersionAndSynchronousFilteringForEveryStream() {
        assertThatThrownBy(() -> new DeskGuardrail("guardrail-id", "DRAFT")).isInstanceOf(IllegalArgumentException.class);
        var context = mock(Context.ModifyRequest.class);
        when(context.request()).thenReturn(ConverseStreamRequest.builder().modelId("model").build());
        var configured = (ConverseStreamRequest) guard.modifyRequest(context, null);
        assertThat(configured.guardrailConfig().guardrailVersion()).isEqualTo("3");
        assertThat(configured.guardrailConfig().guardrailIdentifier()).isEqualTo("guardrail-id");
        assertThat(configured.guardrailConfig().streamProcessingMode()).isEqualTo(GuardrailStreamProcessingMode.SYNC);
        assertThat(configured.guardrailConfig().trace()).isEqualTo(GuardrailTrace.DISABLED);
    }

    @Test
    void requiresExplicitClearanceBeforeToolEvidenceReachesInference() {
        var result = ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse("id","find_documents","sample EDI record"))).build();
        var evidence = guard.newEvidence(List.of(new UserMessage("Find my order"), result));
        assertThat(evidence).contains("sample EDI record");
        var client = mock(BedrockRuntimeClient.class);
        when(client.applyGuardrail(org.mockito.ArgumentMatchers.<Consumer<ApplyGuardrailRequest.Builder>>any()))
                .thenReturn(ApplyGuardrailResponse.builder().action(GuardrailAction.GUARDRAIL_INTERVENED).build());
        assertThatThrownBy(() -> guard.checkEvidence(client, evidence)).isInstanceOf(DeskUsage.Refusal.class);
        assertThat(guard.newEvidence(List.of(new UserMessage("Find my order")))).isEmpty();
    }
}
