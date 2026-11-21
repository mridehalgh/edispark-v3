package com.edispark.documents.application.reconciliation;

import static com.edispark.documents.application.reconciliation.UblReconciliation.Comparison.DESPATCH_RECEIPT;
import static com.edispark.documents.application.reconciliation.UblReconciliation.Comparison.INVOICE_REMITTANCE;
import static com.edispark.documents.application.reconciliation.UblReconciliation.Comparison.ORDER_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class UblReconciliationTest {
    @Test
    void comparesResponseOnlyWhenItsLineExplicitlyReferencesTheOrderLine() {
        var order = document("Order", "PO-1", """
                "OrderLine":[{"LineItem":[{"ID":[{"_":"10"}],"Quantity":[{"_":4,"unitCode":"EA"}]}]}]
                """);
        var response = document("OrderResponse", "ACK-1", """
                "OrderReference":[{"ID":[{"_":"PO-1"}]}],
                "OrderLine":[{"LineItem":[{"ID":[{"_":"10"}],"Quantity":[{"_":4,"unitCode":"EA"}]}]}]
                """);

        var report = UblReconciliation.compare(ORDER_RESPONSE, order, List.of(response));

        assertThat(report.findings()).extracting(UblReconciliation.Finding::code)
                .containsExactly("LINE_REFERENCE_UNMATCHED", "NO_MATCHING_LINE");
        assertThat(report.values()).singleElement().satisfies(value -> assertThat(value.observed()).isNull());
    }

    @Test
    void totalsSplitReceiptsWithExactReferencesAndMatchingUnits() {
        var despatch = document("DespatchAdvice", "ASN-1", """
                "DespatchLine":[{"ID":[{"_":"10"}],"DeliveredQuantity":[{"_":8,"unitCode":"EA"}]}]
                """);
        var receivedPartOne = receipt("REC-1", 3, "EA");
        var receivedPartTwo = receipt("REC-2", 5, "EA");
        var unrelatedUnit = receipt("REC-3", 8, "KGM");

        var report = UblReconciliation.compare(DESPATCH_RECEIPT, despatch,
                List.of(receivedPartOne, receivedPartTwo, unrelatedUnit));

        assertThat(report.findings()).extracting(UblReconciliation.Finding::code)
                .containsExactly("UNIT_UNAVAILABLE_OR_INCOMPATIBLE");
        assertThat(report.values()).singleElement().satisfies(value -> {
            assertThat(value.observed()).isEqualByComparingTo("8");
            assertThat(value.unit()).isEqualTo("EA");
        });
    }

    @Test
    void preservesExactPartialAllocationsAndFlagsCurrencyConflicts() {
        var invoice = document("Invoice", "INV-1", """
                "LegalMonetaryTotal":[{"PayableAmount":[{"_":100.10,"currencyID":"GBP"}]}]
                """);
        var remittance = remittance("REM-1", "20.02", "GBP");
        var foreign = remittance("REM-2", "80.08", "USD");

        var report = UblReconciliation.compare(INVOICE_REMITTANCE, invoice, List.of(remittance, foreign));

        assertThat(report.findings()).extracting(UblReconciliation.Finding::code)
                .containsExactly("CURRENCY_UNAVAILABLE_OR_INCOMPATIBLE", "ALLOCATION_DIFFERENCE");
        assertThat(report.values()).singleElement().satisfies(value -> {
            assertThat(value.expected()).isEqualByComparingTo(new BigDecimal("100.10"));
            assertThat(value.observed()).isEqualByComparingTo(new BigDecimal("20.02"));
        });
    }

    private static byte[] receipt(String id, int quantity, String unit) {
        return document("ReceiptAdvice", id, """
                "DespatchDocumentReference":[{"ID":[{"_":"ASN-1"}]}],
                "ReceiptLine":[{"DespatchLineReference":[{"LineID":[{"_":"10"}]}],
                "ReceivedQuantity":[{"_":%s,"unitCode":"%s"}]}]
                """.formatted(quantity, unit));
    }

    private static byte[] remittance(String id, String amount, String currency) {
        return document("RemittanceAdvice", id, """
                "RemittanceAdviceLine":[{"BillingReference":[{"InvoiceDocumentReference":[{"ID":[{"_":"INV-1"}]}]}],
                "CreditLineAmount":[{"_":%s,"currencyID":"%s"}]}]
                """.formatted(amount, currency));
    }

    private static byte[] document(String root, String id, String fields) {
        return """
                {"_D":"urn:oasis:names:specification:ubl:schema:xsd:%1$s-2","%1$s":[{"ID":[{"_":"%2$s"}],%3$s}]}
                """.formatted(root, id, fields).getBytes(StandardCharsets.UTF_8);
    }
}
