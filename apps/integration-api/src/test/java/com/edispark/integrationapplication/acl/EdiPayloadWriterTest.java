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
    }

    @Test
    void createsATradacomsOrdersEnvelope() {
        String result = new String(writer.write(capability(EdiStandard.TRADACOMS, "9", "ORDERS"),
                json()), StandardCharsets.US_ASCII);

        assertThat(result).startsWith("STX=ANA:1+5012345678901+5098765432109+261101:123400+");
        assertThat(result).contains("MHD=1+ORDHDR:9'", "FIL=PO-1001+1'", "ORD=PO-1001++GBP'",
                "OLD=1+05012345000017+BUY-1:SELL-1+Coffee beans+12:KGM+4.5'").endsWith("END=2'");
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

    private ExchangeCapability capability(EdiStandard standard, String release, String messageType) {
        return new ExchangeCapability(UUID.randomUUID(), ExchangeDirection.OUTBOUND, BusinessDocumentType.ORDER,
                standard, release, messageType, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
    }
}
