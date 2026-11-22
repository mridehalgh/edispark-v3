package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JevMigModelTest {
    private static final String MODEL = "jev-1.14.0";
    private static final String ANCHOR = "PRIMARY/fixture/page 1, row 3";

    @Test
    void acceptsOnlyAConfirmedTypedChoiceAndReusesTheVerifiedOperationAndCitation() {
        var phases = new ArrayList<String>();
        var pack = pack();
        var model = new JevMigModel(MODEL, body -> {
            var request = new JsonMapper().readTree(body);
            assertThat(request.path("model").asText()).isEqualTo(MODEL);
            assertThat(request.path("state").path("guideExcerpts").path("section_0").asText()).contains("order identifier");
            assertThat(request.path("questions").path("section_0").path("criteria").has("option_0")).isTrue();
            return new JevMigModel.Reply(200, response("option_0", 0.95), "synthetic-receipt");
        });

        var result = model.propose(pack, JevMigModel.RATE, (turn, phase, cost, receipt) -> phases.add(phase));

        assertThat(phases).containsExactly("DISPATCHED", "CONFIRMED");
        assertThat(result.actualMicros()).isEqualTo(42L);
        var proposal = new MigReview().compile(Candidate.JEV, result.proposal(), job(pack),
                ignored -> new FixtureResult("CATALOGUE_STRUCTURE", null, List.of()));
        assertThat(proposal.findings()).isEmpty();
        assertThat(proposal.rules().getFirst().operation().targetPointer()).isEqualTo("/document/id");
        assertThat(proposal.rules().getFirst().citations()).containsExactly(ANCHOR);
    }

    @Test
    void reportsUnknownOrUncertainChoicesAsCitedGapsWhileKeepingConfirmedUsage() {
        for (String choice : List.of("unknown_pointer", "option_0")) {
            var model = new JevMigModel(MODEL, ignored -> new JevMigModel.Reply(200, response(choice, 0.2), "synthetic-receipt"));

            var result = model.propose(pack(), JevMigModel.RATE, (turn, phase, cost, receipt) -> { });

            assertThat(result.actualMicros()).isEqualTo(42L);
            assertThat(result.proposal().requirements()).singleElement().satisfies(rule -> {
                assertThat(rule.operation()).isNull();
                assertThat(rule.citations()).containsExactly(ANCHOR);
                assertThat(rule.unsupported()).isNotBlank();
            });
        }
    }

    private MigJob job(MigEvidence.Pack pack) {
        return new MigJob(UUID.randomUUID(), State.REVIEW, 0L, 1, pack, List.of(), 0, Map.of(), List.of(),
                List.of(), List.of(), null, null, null, null, null, null, ModelMode.BEDROCK_AND_JEV, null, null, null);
    }

    private MigEvidence.Pack pack() {
        String source = "/Order/0/ID/0/_";
        String destination = "/document/id";
        var node = new MappingNode(UUID.randomUUID(), MappingOperation.COPY, List.of(source), destination,
                null, null, Map.of(), null, null, null, null);
        var profile = new MigEvidence.Profile(UUID.randomUUID(), UUID.randomUUID(), "schema", "runtime",
                List.of(new MigEvidence.Reference(UUID.randomUUID(), 1, "digest", null, List.of(node))),
                Set.of(source), Set.of(destination), null, List.of(), List.of(), Map.of(), null);
        return new MigEvidence.Pack(2, new Coordinates("OUTBOUND", "ORDER", "EDIFACT", "D96A", "ORDERS"),
                profile, List.of(), List.of(new Section(ANCHOR, "Copy the UBL Order ID to the BGM order identifier.")),
                List.of(), List.of(), "digest");
    }

    private String response(String option, double confidence) {
        return """
                {"model":"jev-1.14.0","answers":{
                  "section_0":{"type":"choice","choice":"%s","confidence":%s},
                  "section_0_rule":{"type":"noul","noul":0.97}
                },"usage":{"input_tokens":1000,"output_tokens":100}}
                """.formatted(option, confidence);
    }
}
