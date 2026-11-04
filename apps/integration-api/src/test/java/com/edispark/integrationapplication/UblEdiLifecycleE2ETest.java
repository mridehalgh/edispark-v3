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
    void sendsAndReceivesEveryOrderToInvoiceLifecycleFile(EdiStandard standard, BusinessDocumentType type) throws Exception {
        String messageType = messageType(type);
        MappingRevision outbound = mapping("OUTBOUND", standard, type);
        byte[] sent = writer.write(capability(standard, type, messageType),
                mappingRuntime.execute(outbound, ublDocument(type)));

        InboundEdiTranslator.Translation received = reader.translate(sent);
        byte[] receivedUbl = mappingRuntime.execute(mapping("INBOUND", standard, type), received.mappingInputJson());

        assertThat(received.standard()).isEqualTo(standard);
        assertThat(received.release()).isEqualTo(standard == EdiStandard.EDIFACT ? "D96A" : "9");
        assertThat(received.messageType()).isEqualTo(messageType);
        assertThat(received.documentType()).isEqualTo(type);
        assertThat(new String(sent, StandardCharsets.US_ASCII))
                .contains(new String(received.rawMessage(), StandardCharsets.US_ASCII));
        assertThat(MAPPER.readTree(receivedUbl).at("/" + rootName(type) + "/0/ID/0/_").asText())
                .isEqualTo(documentId(type));
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
