package com.edispark.integrationapplication.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class InboundEdiTranslatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final InboundEdiTranslator translator = new InboundEdiTranslator(mapper);

    @Test
    void translatesEdifactOrderD96aToCanonicalJson() throws Exception {
        byte[] payload = "UNB+UNOC:3+SUPPLIER+BUYER+261101:1200+1'UNH+1+ORDERS:D:96A:UN'"
                .concat("BGM+220+PO-1001+9'DTM+137:20261101:102'NAD+BY+BUYER'NAD+SU+SUPPLIER'UNT+6+1'UNZ+1+1'")
                .getBytes(StandardCharsets.US_ASCII);

        var result = translator.translate(payload);

        assertThat(result.standard()).isEqualTo(EdiStandard.EDIFACT);
        assertThat(result.release()).isEqualTo("D96A");
        assertThat(result.documentType()).isEqualTo(BusinessDocumentType.ORDER);
        assertThat(mapper.readTree(result.canonicalJson()).at("/Order/0/ID/0/_").asText()).isEqualTo("PO-1001");
        assertThat(mapper.readTree(result.canonicalJson())
                .at("/Order/0/BuyerCustomerParty/0/Party/0/EndpointID/0/_").asText()).isEqualTo("BUYER");
    }

    @Test
    void translatesTradacomsOrderReleaseNineToCanonicalJson() throws Exception {
        byte[] payload = "STX=ANAA:1+SUPPLIER+BUYER+261101:120000+1'MHD=1+ORDERS:9'"
                .concat("ORD=PO-2002+261101+261108'MTR=3'END=1'").getBytes(StandardCharsets.US_ASCII);

        var result = translator.translate(payload);

        assertThat(result.standard()).isEqualTo(EdiStandard.TRADACOMS);
        assertThat(result.release()).isEqualTo("9");
        assertThat(mapper.readTree(result.canonicalJson()).at("/Order/0/ID/0/_").asText()).isEqualTo("PO-2002");
    }

    @Test
    void rejectsBatchedPayloadUntilMessageSplittingHasOccurred() {
        byte[] payload = "UNH+1+ORDERS:D:96A:UN'BGM+220+PO-1+9'UNT+2+1'"
                .concat("UNH+2+ORDERS:D:96A:UN'BGM+220+PO-2+9'UNT+2+2'")
                .getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> translator.translate(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inbound EDI payload must contain exactly one message");
    }
}
