package com.edispark.transformations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.transformations.application.MappingRuntime;
import com.edispark.transformations.domain.MappingScope;
import com.edispark.transformations.domain.MappingStatus;
import com.edispark.transformations.domain.MappingOperation;
import com.edispark.transformations.domain.BusinessCodeSyntax;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ClasspathGlobalMappingCatalogTest {
    @Test void bqAcknowledgementMapsAllEightLinesAndRecordsExplicitOmissions() throws Exception {
        var mapping = catalog.find(BqAcknowledgementMapping.ID).orElseThrow();
        var result = new MappingRuntime(mapper).executeDetailed(mapping, mapping.sample().sourceDocument().getBytes(StandardCharsets.UTF_8));
        assertThat(result.warnings()).isEmpty();
        assertThat(mapper.readTree(result.output())).isEqualTo(mapper.readTree(mapping.sample().targetDocument()));
        assertThat(mapper.readTree(result.report()).path("reviewedOmissions")).hasSize(7);
        var source = mapper.readTree(mapping.sample().sourceDocument());
        ((com.fasterxml.jackson.databind.node.ObjectNode) source.at("/OrderResponse/0")).put("UnexpectedValue", "preserve me");
        assertThat(new MappingRuntime(mapper).executeDetailed(mapping, mapper.writeValueAsBytes(source)).warnings())
                .anySatisfy(warning -> assertThat(warning.sourcePath()).endsWith("/UnexpectedValue"));
    }
    private final ObjectMapper mapper = new ObjectMapper();
    private final ClasspathGlobalMappingCatalog catalog = new ClasspathGlobalMappingCatalog();

    @Test
    void vendsAllSixLifecycleDocumentsAndKeepsComprehensiveOrderInvoiceSamples() throws Exception {
        assertThat(catalog.list()).hasSize(29).allSatisfy(mapping -> {
            assertThat(mapping.scope()).isEqualTo(MappingScope.GLOBAL);
            assertThat(mapping.status()).isEqualTo(MappingStatus.PUBLISHED);
            assertThat(mapping.vendor()).isEqualTo("EdiSpark");
            assertThat(mapping.compatibility()).isNotNull();
        });
        assertThat(catalog.list()).filteredOn(mapping -> mapping.sample() != null && mapping.revision() == 1).hasSize(8).allSatisfy(mapping -> {
            assertThat(mapping.nodes()).hasSizeGreaterThanOrEqualTo(40);
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
        for (var mapping : catalog.list().stream().filter(item -> item.sample() != null && item.revision() == 1).toList()) {
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
        for (var mapping : catalog.list().stream().filter(item -> item.sample() != null && item.revision() == 1).toList()) {
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

    @Test
    void publishedMappingsUseControlledLookupsForCodedBusinessFields() {
        assertThat(catalog.list()).filteredOn(mapping -> mapping.revision() == 1
                && !java.util.Set.of(TescoUkMappings.ORDER, TescoUkMappings.INVOICE, TescoUkMappings.DESPATCH).contains(mapping.id()))
                .allSatisfy(mapping -> {
            assertThat(mapping.nodes()).anySatisfy(node -> {
                assertThat(node.operation()).isEqualTo(MappingOperation.CODE_LOOKUP);
                assertThat(node.codeMap()).isNotEmpty();
            });
            assertThat(mapping.nodes().stream()
                    .filter(node -> node.targetPointer().endsWith("/unitCode"))
                    .toList()).allSatisfy(node -> assertThat(node.operation()).isEqualTo(MappingOperation.CODE_LOOKUP));
        });
    }

    @Test
    void publishedCodeLookupsDeclareTheirSourceAndTargetStandards() {
        assertThat(catalog.list()).filteredOn(mapping -> mapping.revision() == 1).allSatisfy(mapping -> mapping.nodes().stream()
                .filter(node -> node.operation() == MappingOperation.CODE_LOOKUP)
                .forEach(node -> {
                    assertThat(node.codeList()).isNotNull();
                    if (mapping.name().startsWith("UBL")) {
                        assertThat(node.sourceSyntax()).isEqualTo(BusinessCodeSyntax.UBL_2_1);
                        assertThat(node.targetSyntax()).isIn(
                                BusinessCodeSyntax.EDIFACT_D96A, BusinessCodeSyntax.TRADACOMS_9);
                    } else {
                        assertThat(node.sourceSyntax()).isIn(
                                BusinessCodeSyntax.EDIFACT_D96A, BusinessCodeSyntax.TRADACOMS_9);
                        assertThat(node.targetSyntax()).isEqualTo(BusinessCodeSyntax.UBL_2_1);
                    }
                }));
    }

    @Test void replacementResponseMappingCoversEveryLineAndPreservesTheLegacyRevision() throws Exception {
        var mapping = catalog.find(OrderResponseCoreMapping.ID).orElseThrow();
        var runtime = new MappingRuntime(mapper);
        var source = mapper.readTree(mapping.sample().sourceDocument());
        var result = runtime.executeDetailed(mapping, mapper.writeValueAsBytes(source));
        assertThat(result.warnings()).isEmpty();
        assertThat(mapper.readTree(result.output())).isEqualTo(mapper.readTree(mapping.sample().targetDocument()));
        var lines = (com.fasterxml.jackson.databind.node.ArrayNode) source.at("/OrderResponse/0/OrderLine");
        for (int index = 9; index <= 12; index++) {
            var line = lines.get(0).deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) line.at("/LineItem/0/ID/0")).put("_", "" + index);
            lines.add(line);
        }
        var extended = runtime.executeDetailed(mapping, mapper.writeValueAsBytes(source));
        assertThat(extended.warnings()).isEmpty();
        assertThat(mapper.readTree(extended.output()).path("lines")).hasSize(12);
        assertThat(catalog.find(java.util.UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac40")).orElseThrow().revision()).isEqualTo(1);
    }
}
