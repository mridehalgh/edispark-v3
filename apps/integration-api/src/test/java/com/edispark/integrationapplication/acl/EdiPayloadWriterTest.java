package com.edispark.integrationapplication.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.partnerships.domain.model.AcknowledgementPolicy;
import com.edispark.partnerships.domain.model.BatchingPolicy;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EdiPayloadWriterTest {
    private final EdiPayloadWriter writer = new EdiPayloadWriter(new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-11-01T12:34:00Z"), ZoneOffset.UTC));

    @Test
    void createsAnEdifactD96aEnvelopeFromTheConstrainedMappingModel() {
        String result = new String(writer.write(capability(EdiStandard.EDIFACT, "D96A", "ORDERS"),
                json()), StandardCharsets.US_ASCII);

        assertThat(result).startsWith("UNB+UNOC:3+5012345678901+5098765432109+261101:1234+");
        assertThat(result).contains("UNH+1+ORDERS:D:96A:UN'", "BGM+220+PO-1001+9'", "DTM+137:20261101:102'");
        assertThat(result).contains("CUX+2:GBP:9'", "LIN+1++05012345000017:EN'", "QTY+21:12:KGM'",
                "PRI+AAA:4.5'", "MOA+203:54.0'");
        assertThat(result).containsPattern("UNZ\\+1\\+[A-F0-9]{12}'$");
        assertThat(result).doesNotContain("[name]", "[delivery_location]", "TOD+5++DAP", "PAT+1", "DTM+2:");
    }

    @Test
    void createsATradacomsOrdersEnvelope() {
        String result = new String(writer.write(capability(EdiStandard.TRADACOMS, "9", "ORDERS"),
                targetJson(BusinessDocumentType.ORDER)), StandardCharsets.US_ASCII);

        assertThat(result).startsWith("STX=ANA:1+5012345678901+5098765432109+261101:123400+");
        assertThat(result).contains("MHD=1+ORDHDR:9'", "FIL=PO-1001+1'", "ORD=PO-1001++GBP'",
                "OLD=1+05012345000017+BUY-1:SELL-1+Coffee beans+12:KGM+4.5'").endsWith("END=2'");
    }

    @ParameterizedTest(name = "EDIFACT D96A {0}")
    @EnumSource(value = BusinessDocumentType.class, names = "ORDER_RESPONSE", mode = EnumSource.Mode.EXCLUDE)
    void createsEveryLifecycleDocumentAsEdifactD96a(BusinessDocumentType type) {
        String messageType = messageType(type);

        String result = new String(writer.write(capability(type, EdiStandard.EDIFACT, "D96A", messageType),
                json()), StandardCharsets.US_ASCII);

        assertThat(result).contains("UNH+1+" + messageType + ":D:96A:UN'")
                .contains("BGM+" + bgmCode(type) + "+PO-1001+9'")
                .contains("UNT+").endsWith("'");
    }

    @ParameterizedTest(name = "TRADACOMS 9 {0}")
    @EnumSource(value = BusinessDocumentType.class, names = "ORDER_RESPONSE", mode = EnumSource.Mode.EXCLUDE)
    void createsEveryLifecycleDocumentAsTradacoms9(BusinessDocumentType type) {
        String messageType = messageType(type);

        String result = new String(writer.write(capability(type, EdiStandard.TRADACOMS, "9", messageType),
                targetJson(type)), StandardCharsets.US_ASCII);

        assertThat(result).startsWith("STX=").contains("MHD=2+" + messageType + ":9'")
                .contains("MTR=").endsWith("END=2'");
    }

    @Test void delegatesResponseSemanticsToTheStandardsContractAndBlocksUnspecifiedTradacoms() throws Exception {
        var mapper = new ObjectMapper();
        var model = new com.edispark.edifact.OrderResponse(com.edispark.edifact.OrderResponse.CONTRACT,
                new com.edispark.edifact.OrderResponse.Header("OR-42", "2026-11-01", "PO-42", "29", "GBP", null),
                new com.edispark.edifact.OrderResponse.Parties("SUPPLIER", "BUYER", "91", "92", null, null, null, null), null,
                java.util.stream.IntStream.rangeClosed(1, 8).mapToObj(i -> new com.edispark.edifact.OrderResponse.Line(
                        "" + i, "5", java.math.BigDecimal.valueOf(i), "EA", "Product " + i, null, "SKU-" + i, null, null)).toList());
        var payload = mapper.writeValueAsBytes(model);
        String edi = new String(writer.write(capability(BusinessDocumentType.ORDER_RESPONSE, EdiStandard.EDIFACT, "D96A", "ORDRSP"), payload), StandardCharsets.US_ASCII);
        assertThat(edi).contains("BGM+231+OR-42+29'", "LIN+8+5'", "CNT+2:8'");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.write(capability(BusinessDocumentType.ORDER_RESPONSE, EdiStandard.TRADACOMS, "9", "ORDRSP"), payload))
                .hasMessageContaining("partner contract");
        var hidden = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(payload);
        hidden.put("unrepresentedBusinessField", "populated");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.write(capability(BusinessDocumentType.ORDER_RESPONSE, EdiStandard.EDIFACT, "D96A", "ORDRSP"), mapper.writeValueAsBytes(hidden)))
                .hasMessageContaining("contract is invalid");
    }

    private byte[] json() {
        return ("{\"document\":{\"id\":\"PO-1001\",\"issueDate\":\"2026-11-01\",\"currency\":\"GBP\"},"
                + "\"parties\":{\"sender\":\"5012345678901\",\"receiver\":\"5098765432109\"},"
                + "\"lines\":[{\"id\":\"1\",\"quantity\":12,\"unitCode\":\"KGM\","
                + "\"gtin\":\"05012345000017\",\"buyerItemId\":\"BUY-1\",\"sellerItemId\":\"SELL-1\","
                + "\"name\":\"Coffee beans\",\"price\":4.50,\"lineExtension\":54.00}],"
                + "\"totals\":{\"lineExtension\":54.00,\"payable\":64.80}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] targetJson(BusinessDocumentType type) {
        try {
            var json = new ObjectMapper();
            var root = json.readTree(json());
            if (type != BusinessDocumentType.ORDER) ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("document")).remove("currency");
            if (type != BusinessDocumentType.INVOICE) ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("lines").path(0)).remove("lineExtension");
            return json.writeValueAsBytes(root);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    @Test void rejectsMappedValuesThatWouldPreviouslyHaveBeenSilentlyLost() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.write(capability(EdiStandard.TRADACOMS, "9", "ORDERS"), json()))
                .hasMessageContaining("cannot represent mapped field /lines/*/lineExtension");
        var unicode = new String(json(), StandardCharsets.UTF_8).replace("Coffee beans", "Coffee €");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.write(capability(EdiStandard.EDIFACT, "D96A", "ORDERS"), unicode.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("without data loss");
    }

    @Test void tradacomsMessageControlCountIncludesEveryLineAndEscapes() throws Exception {
        var json = new ObjectMapper(); var tree = json.readTree(targetJson(BusinessDocumentType.ORDER));
        var lines = (com.fasterxml.jackson.databind.node.ArrayNode) tree.path("lines");
        var template = lines.path(0).deepCopy();
        for (int index = 1; index < 12; index++) lines.add(template.deepCopy());
        String result = new String(writer.write(capability(EdiStandard.TRADACOMS, "9", "ORDERS"), json.writeValueAsBytes(tree)), StandardCharsets.US_ASCII);
        String body = result.substring(result.indexOf("MHD=2+"), result.indexOf("END=2'"));
        long count = body.chars().filter(value -> value == '\'').count();
        assertThat(body).endsWith("MTR=" + count + "'");
        assertThat(body.split("OLD=", -1)).hasSize(13);
    }

    private ExchangeCapability capability(EdiStandard standard, String release, String messageType) {
        return capability(BusinessDocumentType.ORDER, standard, release, messageType);
    }

    private ExchangeCapability capability(BusinessDocumentType type, EdiStandard standard, String release,
            String messageType) {
        return new ExchangeCapability(UUID.randomUUID(), ExchangeDirection.OUTBOUND, type,
                standard, release, messageType, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
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

    private String bgmCode(BusinessDocumentType type) {
        return switch (type) {
            case ORDER -> "220";
            case ORDER_RESPONSE -> "231";
            case DESPATCH_ADVICE -> "351";
            case INVOICE -> "380";
            case REMITTANCE_ADVICE -> "481";
            case RECEIPT_ADVICE -> "352";
        };
    }
}
