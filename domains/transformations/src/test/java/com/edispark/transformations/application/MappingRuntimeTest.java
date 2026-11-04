package com.edispark.transformations.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import com.edispark.transformations.domain.MappingRevision;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MappingRuntimeTest {
    private final ObjectMapper mapper = new ObjectMapper();

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
}
