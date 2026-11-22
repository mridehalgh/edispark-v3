package com.edispark.wireup.managed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.documents.infrastructure.validation.JsonSchemaDocumentValidator;
import com.edispark.integrationapplication.acl.EdiPayloadWriter;
import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.transformations.application.MappingRuntime;
import com.edispark.transformations.infrastructure.ClasspathGlobalMappingCatalog;
import com.edispark.transformations.infrastructure.TescoUkMappings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TescoManagedWorkflowTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ClasspathGlobalMappingCatalog catalogue = new ClasspathGlobalMappingCatalog();
    private final MappingRuntime runtime = new MappingRuntime(json);
    private final EdiPayloadWriter writer = new EdiPayloadWriter(json, Clock.systemUTC());

    @Test void inboundOrderPreservesBothLinesTheDepotAndTheContractReference() throws Exception {
        var edi = """
                UNB+UNOA:3+0501000000015:14+0501000000022:14+261122:0930+DEMO42++ORDERS'
                UNH+1+ORDERS:D:96A:UN:EAN008'BGM+220+LM00000000042+9'DTM+137:20261122:102'
                DTM+64:202611230900:203'RFF+CT:DEMO42'NAD+BY+0501000000015::9'
                NAD+DP+0501000000039::9'LOC+7+0501000000039::9'NAD+SE+0501000000022::9'CUX+2:GBP:4'
                LIN+1++5010000000015:EN'PIA+1+DEMO-1:IN'QTY+21:3'LIN+2++5010000000022:EN'PIA+1+DEMO-2:IN'QTY+21:4'
                UNS+S'CNT+2:2'UNT+19+1'UNZ+1+DEMO42'
                """;
        var translated = new InboundEdiTranslator(json).translate(edi.getBytes(StandardCharsets.US_ASCII));
        assertThat(translated.association()).isEqualTo("EAN008");
        var result = runtime.executeDetailed(catalogue.find(TescoUkMappings.ORDER).orElseThrow(), translated.mappingInputJson());
        assertThat(result.warnings()).isEmpty();
        var order = json.readTree(result.output()).at("/Order/0");
        assertThat(order.at("/ID/0/_").asText()).isEqualTo("LM00000000042");
        assertThat(order.at("/CustomerReference/0/_").asText()).isEqualTo("DEMO42");
        assertThat(order.at("/Delivery/0/DeliveryLocation/0/ID/0/_").asText()).isEqualTo("0501000000039");
        assertThat(order.at("/OrderLine")).hasSize(2);
        assertThat(order.at("/OrderLine/1/LineItem/0/Quantity/0/_").asInt()).isEqualTo(4);
        assertThat(new JsonSchemaDocumentValidator().validateUbl(result.output(), "Order").errors()).isEmpty();
    }

    @Test void invoiceReconcilesMixedVatAndRejectsAnIncorrectPayableAmount() throws Exception {
        var id = TescoUkMappings.INVOICE;
        var model = mapped(id, "Invoice");
        var capability = capability(id, BusinessDocumentType.INVOICE, "INVOIC");
        String payload = new String(writer.write(capability, model), StandardCharsets.US_ASCII);
        assertThat(payload).contains("INVOIC:D:96A:UN:EAN008", "CNT+2:2", "MOA+124:12", "MOA+124:0", "RFF+ON:LM00000000042");
        var incorrect = json.readTree(model);
        ((com.fasterxml.jackson.databind.node.ObjectNode) incorrect.path("totals")).put("payable", 113);
        assertThatThrownBy(() -> writer.write(capability, json.writeValueAsBytes(incorrect))).hasMessageContaining("payable");
    }

    @Test void depotAsnPreservesBothPalletsAndRejectsIncompleteCaseAllocation() throws Exception {
        var id = TescoUkMappings.DESPATCH;
        var model = mapped(id, "DespatchAdvice");
        var capability = capability(id, BusinessDocumentType.DESPATCH_ADVICE, "DESADV");
        String payload = new String(writer.write(capability, model), StandardCharsets.US_ASCII);
        assertThat(payload).contains("DESADV:D:96A:UN:DESAD3", "RFF+PK:LM00000000042", "PAC+2",
                "GIN+BJ+000000000000000017", "GIN+BJ+000000000000000024", "CNT+2:3");
        var incorrect = json.readTree(model);
        ((com.fasterxml.jackson.databind.node.ObjectNode) incorrect.at("/pallets/1/allocations/0/DeliveredQuantity/0")).put("_", 1);
        assertThatThrownBy(() -> writer.write(capability, json.writeValueAsBytes(incorrect))).hasMessageContaining("allocations");
    }

    private byte[] mapped(UUID id, String root) throws Exception {
        byte[] sample;
        try (var input = getClass().getResourceAsStream("/managed-demo/" + root + ".json")) {
            sample = input.readAllBytes();
        }
        assertThat(new JsonSchemaDocumentValidator().validateUbl(sample, root).errors()).isEmpty();
        var mapped = runtime.executeDetailed(catalogue.find(id).orElseThrow(), sample);
        assertThat(mapped.warnings()).isEmpty();
        return mapped.output();
    }

    private com.edispark.partnerships.domain.model.ExchangeCapability capability(UUID id, BusinessDocumentType document, String message) {
        var mapping = catalogue.find(id).orElseThrow();
        return new ManagedTemplate.Capability(ExchangeDirection.OUTBOUND, document, EdiStandard.EDIFACT, "D96A", message,
                mapping.sourceSchemaId(), mapping.targetSchemaId(), id).bind(UUID.randomUUID(), UUID.randomUUID());
    }
}
