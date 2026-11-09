package com.edispark.integrationapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.integrationapplication.acl.EdiPayloadWriter;
import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import com.edispark.partnerships.domain.model.AcknowledgementPolicy;
import com.edispark.partnerships.domain.model.BatchingPolicy;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.transformations.application.MappingRuntime;
import com.edispark.transformations.domain.MappingRevision;
import com.edispark.transformations.infrastructure.ClasspathGlobalMappingCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UblEdiLifecycleE2ETest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EdiPayloadWriter writer = new EdiPayloadWriter(MAPPER,
            Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC));
    private final InboundEdiTranslator reader = new InboundEdiTranslator(MAPPER);
    private final ClasspathGlobalMappingCatalog mappings = new ClasspathGlobalMappingCatalog();
    private final MappingRuntime mappingRuntime = new MappingRuntime(MAPPER);

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("lifecycleDocuments")
    void historicalMappingsExposeCoverageGapsBeforeSending(EdiStandard standard, BusinessDocumentType type) throws Exception {
        var result = mappingRuntime.executeDetailed(mapping("OUTBOUND", standard, type), ublDocument(type));
        assertThat(result.warnings()).as("Historical mappings must not silently approve richer source fixtures").isNotEmpty();
        assertThat(MAPPER.readTree(result.report()).path("warnings")).hasSize(result.warnings().size());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(EdiStandard.class)
    void aReviewedRepeatedMappingCarriesAllTwelveLinesIntoTheTransportRepresentation(EdiStandard standard) throws Exception {
        var source = MAPPER.createObjectNode();
        var document = source.putArray("OrderResponse").addObject();
        document.putArray("ID").addObject().put("_", "OR-repeat-12");
        document.putArray("IssueDate").addObject().put("_", "2026-11-04");
        document.putArray("OrderReference").addObject().putArray("ID").addObject().put("_", "PO-repeat-12");
        document.putArray("SellerSupplierParty").addObject().putArray("Party").addObject().putArray("EndpointID").addObject().put("_", "5012345678901");
        document.putArray("BuyerCustomerParty").addObject().putArray("Party").addObject().putArray("EndpointID").addObject().put("_", "5098765432109");
        var lines = document.putArray("OrderLine");
        for (int index = 1; index <= 12; index++) {
            var line = lines.addObject().putArray("LineItem").addObject();
            line.putArray("ID").addObject().put("_", String.valueOf(index));
            line.putArray("Quantity").addObject().put("_", index).put("unitCode", "EA");
        }
        var original = mapping("OUTBOUND", standard, BusinessDocumentType.ORDER_RESPONSE);
        var repeatedNodes = original.nodes().stream().map(node -> new com.edispark.transformations.domain.MappingNode(
                node.id(), node.operation(), node.sourcePointers().stream().map(path -> path.replace("/OrderLine/0/", "/OrderLine/*/")).toList(),
                node.targetPointer().replace("/lines/0/", "/lines/*/"), node.constant(), node.separator(), node.codeMap(),
                node.codeList(), node.sourceSyntax(), node.targetSyntax())).toList();
        var reviewed = original.forkForTenant("test-tenant", Instant.now()).revise(repeatedNodes);
        var result = mappingRuntime.executeDetailed(reviewed, MAPPER.writeValueAsBytes(source));
        assertThat(result.warnings()).isEmpty();
        assertThat(MAPPER.readTree(result.output()).path("lines")).hasSize(12);
        byte[] sent = writer.write(capability(standard, BusinessDocumentType.ORDER_RESPONSE, "ORDRSP"), result.output());
        var received = reader.translate(sent);
        assertThat(MAPPER.readTree(received.mappingInputJson()).path("lines")).hasSize(12);
        assertThat(original.nodes()).noneMatch(node -> node.sourcePointers().stream().anyMatch(path -> path.contains("/*/")));
    }

    private static Stream<Arguments> lifecycleDocuments() {
        return Stream.of(EdiStandard.values()).flatMap(standard -> Stream.of(BusinessDocumentType.values())
                .map(type -> Arguments.of(standard, type)));
    }

    private byte[] ublDocument(BusinessDocumentType type) throws Exception {
        String resource = "/seed/documents/" + switch (type) {
            case ORDER -> "ubl-order-po-2026-0042.json";
            case ORDER_RESPONSE -> "ubl-order-response-2026-0042.json";
            case DESPATCH_ADVICE -> "ubl-despatch-advice-2026-0042.json";
            case INVOICE -> "ubl-invoice-100045.json";
            case REMITTANCE_ADVICE -> "ubl-remittance-advice-2026-0042.json";
            case RECEIPT_ADVICE -> "ubl-receipt-advice-2026-0042.json";
        };
        try (InputStream input = UblEdiLifecycleE2ETest.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing UBL lifecycle fixture: " + resource);
            return input.readAllBytes();
        }
    }

    private MappingRevision mapping(String direction, EdiStandard standard, BusinessDocumentType type) {
        return mappings.list().stream().filter(item -> item.compatibility() != null)
                .filter(item -> item.compatibility().direction().equals(direction))
                .filter(item -> item.compatibility().standard().equals(standard.name()))
                .filter(item -> item.compatibility().documentType().equals(type.name()))
                .findFirst().orElseThrow();
    }

    private ExchangeCapability capability(EdiStandard standard, BusinessDocumentType type, String messageType) {
        return new ExchangeCapability(UUID.randomUUID(), ExchangeDirection.OUTBOUND, type, standard,
                standard == EdiStandard.EDIFACT ? "D96A" : "9", messageType,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
    }

    private String documentId(BusinessDocumentType type) {
        return switch (type) {
            case ORDER -> "PO-2026-0042";
            case ORDER_RESPONSE -> "OR-2026-0042";
            case DESPATCH_ADVICE -> "DES-2026-0042";
            case INVOICE -> "INV-100045";
            case REMITTANCE_ADVICE -> "REM-2026-0042";
            case RECEIPT_ADVICE -> "REC-2026-0042";
        };
    }

    private String messageType(BusinessDocumentType type) {
        return switch (type) {
            case ORDER -> "ORDERS";
            case ORDER_RESPONSE -> "ORDRSP";
            case DESPATCH_ADVICE -> "DESADV";
            case INVOICE -> "INVOIC";
            case REMITTANCE_ADVICE -> "REMADV";
            case RECEIPT_ADVICE -> "RECADV";
        };
    }

    private String rootName(BusinessDocumentType type) {
        return switch (type) {
            case ORDER -> "Order";
            case ORDER_RESPONSE -> "OrderResponse";
            case DESPATCH_ADVICE -> "DespatchAdvice";
            case INVOICE -> "Invoice";
            case REMITTANCE_ADVICE -> "RemittanceAdvice";
            case RECEIPT_ADVICE -> "ReceiptAdvice";
        };
    }
}
