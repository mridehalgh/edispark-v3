package com.edispark.transformations.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import com.edispark.transformations.domain.MappingRevision;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MappingRuntimeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void repeatsEveryLineAndKeepsUnexpectedFieldsVisible() throws Exception {
        var mapping = MappingRevision.draft("tenant", "Repeated lines", UUID.randomUUID(), UUID.randomUUID(),
                List.of(new MappingNode(UUID.randomUUID(), MappingOperation.COPY, List.of("/items/*/id"), "/lines/*/id", null, null),
                        new MappingNode(UUID.randomUUID(), MappingOperation.OPTIONAL_COPY, List.of("/items/*/name"), "/lines/*/name", null, null)), Instant.now());
        var source = mapper.createObjectNode();
        var items = source.putArray("items");
        for (int i = 0; i < 12; i++) items.addObject().put("id", String.valueOf(i)).put("unreviewed", "value");
        var result = new MappingRuntime(mapper).executeDetailed(mapping, mapper.writeValueAsBytes(source));
        assertThat(mapper.readTree(result.output()).path("lines")).hasSize(12);
        assertThat(mapper.readTree(result.output()).at("/lines/11/id").asText()).isEqualTo("11");
        assertThat(result.warnings()).hasSize(12);
    }

    @Test
    void intentionalOmissionsRequireAReasonAndRemainInEvidence() throws Exception {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MappingNode(UUID.randomUUID(), MappingOperation.OMIT,
                List.of("/note"), "/reviewed", "", null)).hasMessageContaining("business reason");
        var mapping = MappingRevision.draft("tenant", "Reviewed omission", UUID.randomUUID(), UUID.randomUUID(),
                List.of(new MappingNode(UUID.randomUUID(), MappingOperation.OMIT, List.of("/note"), "/reviewed",
                        "Partner receives this note separately under the agreement", null)), Instant.now());
        var result = new MappingRuntime(mapper).executeDetailed(mapping, "{\"note\":\"value\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(result.warnings()).isEmpty();
        assertThat(mapper.readTree(result.report()).path("reviewedOmissions")).hasSize(1);
    }

    @Test
    void reportsPopulatedSourceLeavesThatThePublishedRevisionDoesNotConsume() throws Exception {
        var node = new MappingNode(UUID.randomUUID(), MappingOperation.COPY, List.of("/document/id"),
                "/message/reference", null, null);
        var mapping = MappingRevision.draft("tenant", "Order to ORDERS", UUID.randomUUID(), UUID.randomUUID(),
                List.of(node), Instant.parse("2026-11-01T12:00:00Z")).publish("sha256", Instant.parse("2026-11-01T12:01:00Z"));

        var result = new MappingRuntime(mapper).executeDetailed(mapping,
                "{\"document\":{\"id\":\"PO-42\",\"note\":\"Handle carefully\"},\"_D\":\"namespace\"}"
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(mapper.readTree(result.output()).at("/message/reference").asText()).isEqualTo("PO-42");
        assertThat(result.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo("SOURCE_FIELD_UNMAPPED");
            assertThat(warning.sourcePath()).isEqualTo("/document/note");
            assertThat(warning.disposition()).isEqualTo("SOURCE_ONLY");
        });
        var report = mapper.readTree(result.report());
        assertThat(report.path("mappingRevisionId").asText()).isEqualTo(mapping.id().toString());
        assertThat(report.path("warnings")).hasSize(1);
    }

    @Test
    void translatesControlledBusinessCodesWhenProducingPartnerEdi() throws Exception {
        var node = new MappingNode(UUID.randomUUID(), MappingOperation.CODE_LOOKUP, List.of("/line/unitCode"),
                "/detail/uom", null, null, Map.of("EA", "EACH", "KGM", "KG"));
        var mapping = MappingRevision.draft("tenant", "Order units to partner codes", UUID.randomUUID(),
                UUID.randomUUID(), List.of(node), Instant.parse("2026-11-01T12:00:00Z"))
                .publish("sha256", Instant.parse("2026-11-01T12:01:00Z"));

        byte[] output = new MappingRuntime(mapper).execute(mapping,
                "{\"line\":{\"unitCode\":\"KGM\"}}".getBytes(StandardCharsets.UTF_8));

        assertThat(mapper.readTree(output).at("/detail/uom").asText()).isEqualTo("KG");
    }

    @Test
    void rejectsUnmappedBusinessCodesRatherThanWritingInvalidEdi() {
        var node = new MappingNode(UUID.randomUUID(), MappingOperation.CODE_LOOKUP, List.of("/line/unitCode"),
                "/detail/uom", null, null, Map.of("EA", "EACH"));
        var mapping = MappingRevision.draft("tenant", "Order units to partner codes", UUID.randomUUID(),
                UUID.randomUUID(), List.of(node), Instant.parse("2026-11-01T12:00:00Z"))
                .publish("sha256", Instant.parse("2026-11-01T12:01:00Z"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MappingRuntime(mapper).execute(mapping,
                "{\"line\":{\"unitCode\":\"BOX\"}}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No code mapping for BOX");
    }

    @Test
    void explainsHowToRepairAMissingRequiredSourceValue() {
        var node = new MappingNode(UUID.randomUUID(), MappingOperation.COPY,
                List.of("/document/orderReference"), "/OrderResponse/0/OrderReference/0/ID/0/_", null, null);
        var mapping = MappingRevision.draft("tenant", "Order response", UUID.randomUUID(), UUID.randomUUID(),
                List.of(node), Instant.parse("2026-11-01T12:00:00Z"))
                .publish("sha256", Instant.parse("2026-11-01T12:01:00Z"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MappingRuntime(mapper).execute(mapping,
                "{\"document\":{\"id\":\"OR-42\"}}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target '/OrderResponse/0/OrderReference/0/ID/0/_'")
                .hasMessageContaining("source fields contain a value: [/document/orderReference]")
                .hasMessageContaining("Add the missing value to the source document or update mapping node")
                .hasMessageContaining("(COPY)");
    }
}
