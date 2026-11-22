package com.edispark.wireup.mcp;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

record DeskGuardrail(String identifier, String version) implements ExecutionInterceptor {
    DeskGuardrail {
        if (!(identifier.isBlank() && version.isBlank()) && (identifier.isBlank() || !version.matches("[1-9][0-9]*")))
            throw new IllegalArgumentException("Use a published Bedrock guardrail and its numeric version");
    }

    @Override
    public SdkRequest modifyRequest(Context.ModifyRequest context, ExecutionAttributes attributes) {
        if (!(context.request() instanceof ConverseStreamRequest stream)) throw new IllegalStateException("Unexpected Desk model operation");
        if (identifier.isBlank()) throw new IllegalStateException("A Bedrock guardrail is required");
        return stream.toBuilder().guardrailConfig(g -> g.guardrailIdentifier(identifier).guardrailVersion(version)
                .streamProcessingMode(GuardrailStreamProcessingMode.SYNC).trace(GuardrailTrace.DISABLED)).build();
    }

    // Converse does not screen toolResult blocks. Screen evidence from the current turn before inference.
    String newEvidence(List<Message> messages) {
        if (identifier.isBlank()) return "";
        var evidenceBlocks = new ArrayList<String>();
        for (var message : messages.reversed()) {
            if (message instanceof UserMessage) break;
            if (message instanceof ToolResponseMessage tool) {
                tool.getResponses().forEach(response -> evidenceBlocks.add(response.responseData()));
            }
        }
        String evidence = String.join("\n", evidenceBlocks);
        if (evidence.length() > 96_000)
            throw new DeskUsage.Refusal("AI_CONTEXT_LIMIT", "The tool returned too much evidence. Narrow the question to one record.");
        return evidence;
    }

    long estimatedUnits(List<Message> messages, String evidence) {
        if (identifier.isBlank()) return 0;
        // Reserve per text block, including separate evidence screening and room for the bounded response.
        return 8 + units(evidence) + messages.stream().filter(m -> !(m instanceof ToolResponseMessage))
                .mapToLong(m -> units(m.getText())).sum();
    }

    private static long units(String text) { return text == null ? 0 : (text.length() + 999L) / 1000; }

    void checkEvidence(BedrockRuntimeClient client, String evidence) {
        if (evidence.isEmpty()) return;
        var response = client.applyGuardrail(r -> r.guardrailIdentifier(identifier).guardrailVersion(version)
                .source(GuardrailContentSource.INPUT).content(GuardrailContentBlock.fromText(
                        GuardrailTextBlock.builder().text(evidence).build())));
        if (response.action() != GuardrailAction.NONE) throw blocked();
    }

    static DeskUsage.Refusal blocked() {
        return new DeskUsage.Refusal("AI_POLICY_BLOCK", "The workspace AI policy blocked this request. Rephrase the question or open the record.");
    }
}
