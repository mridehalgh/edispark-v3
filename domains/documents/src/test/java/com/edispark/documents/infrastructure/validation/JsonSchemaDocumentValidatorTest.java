package com.edispark.documents.infrastructure.validation;
import com.edispark.documents.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaDocumentValidatorTest {
    private final JsonSchemaDocumentValidator validator = new JsonSchemaDocumentValidator();
    @ParameterizedTest
    @CsvSource({"Order,ubl-order-po-2026-0042.json", "Invoice,ubl-invoice-100045.json", "CreditNote,ubl-credit-note-1007.json",
            "OrderResponse,ubl-order-response-2026-0042.json", "DespatchAdvice,ubl-despatch-advice-2026-0042.json",
            "ReceiptAdvice,ubl-receipt-advice-2026-0042.json", "RemittanceAdvice,ubl-remittance-advice-2026-0042.json",
            "Order,bq-order-local-42.json", "OrderResponse,bq-ack-local-42.json", "OrderResponse,bq-ack-missing-vendor.json"})
    void validatesEverySupportedSeedAgainstTheCompleteSchemaGraph(String root, String resource) throws Exception {
        byte[] bytes = getClass().getResourceAsStream("/seed/documents/" + resource).readAllBytes();
        assertThat(validator.validateUbl(bytes, root).errors()).isEmpty();
    }
    @Test void validatesNestedOasisRulesAndRejectsThePreviouslyAcceptedResponseCode() throws Exception {
        byte[] bytes = getClass().getResourceAsStream("/seed/documents/ubl-order-response-2026-0042.json").readAllBytes();
        var valid = validator.validateUbl(bytes, "OrderResponse");
        assertThat(valid.errors()).isEmpty();
        var json = new ObjectMapper(); var tree = json.readTree(bytes);
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("OrderResponse").path(0)).putArray("ResponseCode").addObject().put("_", "Accepted");
        assertThat(validator.validateUbl(json.writeValueAsBytes(tree), "OrderResponse").valid()).isFalse();
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("OrderResponse").path(0)).remove("ID");
        assertThat(validator.validateUbl(json.writeValueAsBytes(tree), "OrderResponse").errors()).isNotEmpty();
    }
    @Test void neverFetchesRemoteReferencesOrApprovesUnsupportedFormats() {
        var data = Content.of("{}".getBytes(StandardCharsets.UTF_8), Format.JSON);
        var schema = Content.of("{\"$ref\":\"http://127.0.0.1/private\"}".getBytes(StandardCharsets.UTF_8), Format.JSON);
        assertThat(validator.validate(data, schema).valid()).isFalse();
        assertThat(new UnavailableDocumentValidator().validate(data, schema).valid()).isFalse();
    }

    @Test void catchesAcknowledgementWithAcceptedLinesDuringDocumentValidation() throws Exception {
        byte[] bytes = getClass().getResourceAsStream("/seed/documents/ubl-order-response-2026-0042.json").readAllBytes();
        var json = new ObjectMapper(); var tree = json.readTree(bytes);
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) tree.path("OrderResponse").path(0);
        response.putArray("OrderResponseCode").addObject().put("_", "AB");
        assertThat(validator.validateUbl(json.writeValueAsBytes(tree), "OrderResponse").errors())
                .anySatisfy(error -> {
                    assertThat(error.code()).isEqualTo("RESPONSE_STATUS_CONFLICT");
                    assertThat(error.path()).isEqualTo("/OrderResponse/0/OrderResponseCode/0/_");
                });
        response.putArray("OrderResponseCode").addObject().put("_", "AP");
        assertThat(validator.validateUbl(json.writeValueAsBytes(tree), "OrderResponse").valid()).isTrue();
    }
}
