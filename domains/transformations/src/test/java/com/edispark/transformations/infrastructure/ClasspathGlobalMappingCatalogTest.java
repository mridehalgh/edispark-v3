package com.edispark.transformations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.transformations.application.MappingRuntime;
import com.edispark.transformations.domain.MappingScope;
import com.edispark.transformations.domain.MappingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ClasspathGlobalMappingCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ClasspathGlobalMappingCatalog catalog = new ClasspathGlobalMappingCatalog();

    @Test
    void vendsEightImmutableComprehensiveMappingDirectionsWithSamples() throws Exception {
        assertThat(catalog.list()).hasSize(8).allSatisfy(mapping -> {
            assertThat(mapping.scope()).isEqualTo(MappingScope.GLOBAL);
            assertThat(mapping.status()).isEqualTo(MappingStatus.PUBLISHED);
            assertThat(mapping.vendor()).isEqualTo("EdiSpark");
            assertThat(mapping.nodes()).hasSizeGreaterThanOrEqualTo(40);
            assertThat(mapping.sample()).isNotNull();
            assertThat(mapping.sample().sourceFormat()).isNotBlank();
            assertThat(mapping.sample().targetFormat()).isNotBlank();
            assertThat(mapping.sample().sourceDocument()).isNotBlank();
            assertThat(mapping.sample().targetDocument()).isNotBlank();
            String parsedExamples = mapping.sample().sourceDocument() + mapping.sample().targetDocument();
            assertThat(parsedExamples).contains("parties", "lines", "totals");
            String rawEdi = mapping.sample().sourceRaw() != null
                    ? mapping.sample().sourceRaw()
                    : mapping.sample().targetRaw();
            assertThat(rawEdi).isNotBlank();
            assertThat(rawEdi.lines()).hasSizeGreaterThanOrEqualTo(16);
            assertThat(rawEdi).containsAnyOf("LIN+2", "OLD=2", "ILD=2");
        });
        assertThat(catalog.list()).extracting(mapping -> mapping.id()).doesNotHaveDuplicates();
        for (var mapping : catalog.list()) {
            assertThat(mapper.readTree(mapping.sample().sourceDocument())).isNotNull();
            assertThat(mapper.readTree(mapping.sample().targetDocument())).isNotNull();
        }
    }

    @Test
    void outboundSamplesExecuteToTheirPublishedParsedEdiExamples() throws Exception {
        var runtime = new MappingRuntime(mapper);
        for (var mapping : catalog.list().subList(0, 4)) {
            byte[] output = runtime.execute(mapping,
                    mapping.sample().sourceDocument().getBytes(StandardCharsets.UTF_8));
            assertThat(mapper.readTree(output)).isEqualTo(mapper.readTree(mapping.sample().targetDocument()));
            assertThat(mapping.sample().targetRaw()).isNotBlank();
        }
    }

    @Test
    void inboundSampleMaterializesUblArraysInsteadOfNumericObjectKeys() throws Exception {
        var mapping = catalog.list().get(4);
        byte[] output = new MappingRuntime(mapper).execute(mapping,
                mapping.sample().sourceDocument().getBytes(StandardCharsets.UTF_8));
        var ubl = mapper.readTree(output);

        assertThat(ubl.path("Order").isArray()).isTrue();
        assertThat(ubl.at("/Order/0/ID/0/_").asText()).isEqualTo("PO-2026-0042");
        assertThat(ubl.at("/Order/0/OrderLine/0/LineItem/0/Item/0/StandardItemIdentification/0/ID/0/_").asText())
                .isEqualTo("05012345000017");
    }

    @Test
    void everyPublishedFieldIsPopulatedAndLargeSamplesContainEightMeaningfulLines() throws Exception {
        var runtime = new MappingRuntime(mapper);
        for (var mapping : catalog.list()) {
            var source = mapper.readTree(mapping.sample().sourceDocument());
            assertThat(mapping.nodes()).allSatisfy(node -> {
                if (node.operation() == com.edispark.transformations.domain.MappingOperation.CONSTANT) return;
                assertThat(!source.at(node.sourcePointers().getFirst()).isMissingNode()
                        && !source.at(node.sourcePointers().getFirst()).isNull())
                        .as("source value for %s", node.sourcePointers().getFirst()).isTrue();
            });

            var output = mapper.readTree(runtime.execute(mapping,
                    mapping.sample().sourceDocument().getBytes(StandardCharsets.UTF_8)));
            assertThat(mapping.nodes()).allSatisfy(node -> assertThat(
                    !output.at(node.targetPointer()).isMissingNode() && !output.at(node.targetPointer()).isNull())
                    .as("target value for %s", node.targetPointer()).isTrue());

            String rawEdi = mapping.sample().sourceRaw() != null
                    ? mapping.sample().sourceRaw() : mapping.sample().targetRaw();
            assertThat(rawEdi.lines()).hasSizeGreaterThanOrEqualTo(20);
            var target = mapper.readTree(mapping.sample().targetDocument());
            var ubl = source.has("Order") || source.has("Invoice") ? source : target;
            assertThat(ubl.at(mapping.name().contains("Invoice")
                    ? "/Invoice/0/InvoiceLine" : "/Order/0/OrderLine").size()).isGreaterThanOrEqualTo(8);
        }
    }
}
