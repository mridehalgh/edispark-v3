package com.edispark.integrationapplication.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.transformations.application.MappingRuntime;
import com.edispark.transformations.infrastructure.ClasspathGlobalMappingCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

final class InboundEdiTranslatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final InboundEdiTranslator translator = new InboundEdiTranslator(mapper);

    @Test
    void parsesCompleteEdifactOrderAndExecutesTheSelectedUblMapping() throws Exception {
        byte[] payload = new ClassPathResource("global-mappings/edifact-orders-d96a.edi")
                .getContentAsByteArray();

        var result = translator.translate(payload);

        assertThat(result.standard()).isEqualTo(EdiStandard.EDIFACT);
        assertThat(result.release()).isEqualTo("D96A");
        assertThat(result.documentType()).isEqualTo(BusinessDocumentType.ORDER);
        var mappingInput = mapper.readTree(result.mappingInputJson());
        assertThat(mappingInput.at("/document/id").asText()).isEqualTo("PO-2026-0042");
        assertThat(mappingInput.at("/lines")).hasSize(8);
        assertThat(mappingInput.at("/totals/payable").decimalValue()).isEqualByComparingTo("2575.58");

        var mapping = new ClasspathGlobalMappingCatalog().find(
                UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac24")).orElseThrow();
        var ubl = mapper.readTree(new MappingRuntime(mapper).execute(mapping, result.mappingInputJson()));
        assertThat(ubl.at("/Order/0/ID/0/_").asText()).isEqualTo("PO-2026-0042");
        assertThat(ubl.at("/Order/0/OrderLine")).hasSize(8);
        assertThat(ubl.at("/Order/0/OrderLine/7/LineItem/0/Item/0/Name/0/_").asText())
                .isEqualTo("Herbal Tea 80");
    }

    @Test
    void treatsAnOmittedEdifactAllowanceAsZero() throws Exception {
        String payload = new ClassPathResource("global-mappings/edifact-orders-d96a.edi")
                .getContentAsString(StandardCharsets.US_ASCII)
                .replace("MOA+8:5.58'", "");

        var result = translator.translate(payload.getBytes(StandardCharsets.US_ASCII));

        assertThat(mapper.readTree(result.mappingInputJson()).at("/totals/allowance").decimalValue())
                .isEqualByComparingTo("0");
    }

    @Test
    void parsesTradacomsOrderReleaseNineToMappingInput() throws Exception {
        byte[] payload = ("STX=ANAA:1+BUYER:[name]+SUPPLIER:[name]+261101:120000+1'"
                + "MHD=1+ORDERS:9'CLO=STORE-1:[name]'"
                + "ORD=PO-2002+261101+GBP'DIN=261108++DAP'"
                + "OLD=1+05012345000017+BUY-1:SELL-1+Product one+2:EA+10.00'"
                + "OTR=1+20.00+0.00+24.00'MTR=10'END=1'").getBytes(StandardCharsets.US_ASCII);

        var result = translator.translate(payload);

        assertThat(result.standard()).isEqualTo(EdiStandard.TRADACOMS);
        assertThat(result.release()).isEqualTo("9");
        var mappingInput = mapper.readTree(result.mappingInputJson());
        assertThat(mappingInput.at("/document/id").asText()).isEqualTo("PO-2002");
        assertThat(mappingInput.at("/parties/buyerName").asText()).isEqualTo("[name]");
        assertThat(mappingInput.at("/parties/supplierName").asText()).isEqualTo("[name]");
        assertThat(mappingInput.at("/lines/0/sellerItemId").asText())
                .isEqualTo("SELL-1");
    }

    @Test
    void parsesEdifactInvoiceAndExecutesTheSelectedUblMapping() throws Exception {
        byte[] payload = new ClassPathResource("global-mappings/edifact-invoic-d96a.edi")
                .getContentAsByteArray();

        var result = translator.translate(payload);

        assertThat(result.documentType()).isEqualTo(BusinessDocumentType.INVOICE);
        var mappingInput = mapper.readTree(result.mappingInputJson());
        assertThat(mappingInput.at("/document/id").asText()).isEqualTo("INV-100045");
        assertThat(mappingInput.at("/document/orderReference").asText()).isEqualTo("PO-2026-0042");
        assertThat(mappingInput.at("/lines")).hasSize(8);

        var mapping = new ClasspathGlobalMappingCatalog().find(
                UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac25")).orElseThrow();
        var ubl = mapper.readTree(new MappingRuntime(mapper).execute(mapping, result.mappingInputJson()));
        assertThat(ubl.at("/Invoice/0/ID/0/_").asText()).isEqualTo("INV-100045");
        assertThat(ubl.at("/Invoice/0/InvoiceLine")).hasSize(8);
    }

    @Test
    void parsesTradacomsInvoiceReleaseNineToMappingInput() throws Exception {
        byte[] payload = new ClassPathResource("global-mappings/tradacoms-invoic-9.edi")
                .getContentAsByteArray();

        var result = translator.translate(payload);

        assertThat(result.documentType()).isEqualTo(BusinessDocumentType.INVOICE);
        var mappingInput = mapper.readTree(result.mappingInputJson());
        assertThat(mappingInput.at("/document/id").asText()).isEqualTo("INV-100045");
        assertThat(mappingInput.at("/lines/0/orderLineId").asText()).isEqualTo("1");
        assertThat(mappingInput.at("/totals/payable").decimalValue()).isEqualByComparingTo("2575.58");
    }

    @Test
    void splitsEveryEdifactBusinessMessageWithStableOrdinals() {
        byte[] payload = "UNH+1+ORDERS:D:96A:UN'BGM+220+PO-1+9'UNT+2+1'"
                .concat("UNH+2+ORDERS:D:96A:UN'BGM+220+PO-2+9'UNT+2+2'")
                .getBytes(StandardCharsets.US_ASCII);

        var messages = translator.split(payload);

        assertThat(messages).extracting(InboundEdiBatchSplitter.InboundMessage::ordinal)
                .containsExactly(1, 2);
        assertThat(messages).extracting(InboundEdiBatchSplitter.InboundMessage::sourceReference)
                .containsExactly("1", "2");
        assertThat(messages).allSatisfy(message -> assertThat(message.messageCount()).isEqualTo(2));
    }

    @Test
    void usesTradacomsFileHeadersAsContextWithoutCreatingDocumentsForThem() {
        byte[] payload = ("STX=ANAA:1+BUYER+SUPPLIER+261101:120000+1'"
                + "MHD=1+ORDHDR:9'CDT=BUYER:[name]'SDT=SUPPLIER:[name]'MTR=4'"
                + "MHD=2+ORDERS:9'ORD=PO-1+261101+GBP'MTR=3'"
                + "MHD=3+ORDERS:9'ORD=PO-2+261101+GBP'MTR=3'END=3'")
                .getBytes(StandardCharsets.US_ASCII);

        var messages = translator.split(payload);

        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().mappingSegments()).contains("MHD=1+ORDHDR:9", "CDT=BUYER:[name]");
        assertThat(new String(messages.getFirst().rawMessage(), StandardCharsets.US_ASCII))
                .startsWith("MHD=2+ORDERS:9'").doesNotContain("ORDHDR");
    }

    @Test
    void rejectsMismatchedEdifactMessageReferences() {
        byte[] payload = "UNH+1+ORDERS:D:96A:UN'BGM+220+PO-1+9'UNT+2+2'"
                .getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> translator.split(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Inbound EDI message references do not match");
    }
}
