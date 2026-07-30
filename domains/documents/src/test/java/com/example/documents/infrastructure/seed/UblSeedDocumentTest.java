package com.example.documents.infrastructure.seed;

import com.example.ubl.util.UblJsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import oasis.names.specification.ubl.schema.xsd.maindoc.CreditNote;
import oasis.names.specification.ubl.schema.xsd.maindoc.Invoice;
import oasis.names.specification.ubl.schema.xsd.maindoc.Order;
import oasis.names.specification.ubl.schema.xsd.maindoc.UBLCreditNote21;
import oasis.names.specification.ubl.schema.xsd.maindoc.UBLInvoice21;
import oasis.names.specification.ubl.schema.xsd.maindoc.UBLOrder21;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UBL seed documents")
class UblSeedDocumentTest {

    private static final String RESOURCE_ROOT = "/seed/documents/";
    private final ObjectMapper mapper = UblJsonMapper.getInstance();

    @Test
    @DisplayName("order is valid for the generated UBL 2.1 model and contains complete business detail")
    void parsesOrderWithUblModel() throws IOException {
        UBLOrder21 document = read("ubl-order-po-2026-0042.json", UBLOrder21.class);
        Order order = document.getOrder().getFirst();

        assertThat(document.getD()).isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:Order-2");
        assertThat(order.getId().getFirst().get__()).isEqualTo("PO-2026-0042");
        assertThat(order.getBuyerCustomerParty()).hasSize(1);
        assertThat(order.getSellerSupplierParty()).hasSize(1);
        assertThat(order.getOrderLine()).hasSize(2);
        assertThat(order.getAnticipatedMonetaryTotal().getFirst().getPayableAmount().getFirst().get__())
                .isEqualByComparingTo(new BigDecimal("558"));
    }

    @Test
    @DisplayName("invoice is valid for the generated UBL 2.1 model")
    void parsesInvoiceWithUblModel() throws IOException {
        UBLInvoice21 document = read("ubl-invoice-100045.json", UBLInvoice21.class);
        Invoice invoice = document.getInvoice().getFirst();

        assertThat(invoice.getId().getFirst().get__()).isEqualTo("INV-100045");
        assertThat(invoice.getAccountingSupplierParty()).hasSize(1);
        assertThat(invoice.getAccountingCustomerParty()).hasSize(1);
        assertThat(invoice.getInvoiceLine()).hasSize(2);
        assertThat(invoice.getLegalMonetaryTotal().getFirst().getPayableAmount().getFirst().get__())
                .isEqualByComparingTo(new BigDecimal("669.6"));
    }

    @Test
    @DisplayName("credit note is valid for the generated UBL 2.1 model")
    void parsesCreditNoteWithUblModel() throws IOException {
        UBLCreditNote21 document = read("ubl-credit-note-1007.json", UBLCreditNote21.class);
        CreditNote creditNote = document.getCreditNote().getFirst();

        assertThat(creditNote.getId().getFirst().get__()).isEqualTo("CN-1007");
        assertThat(creditNote.getAccountingSupplierParty()).hasSize(1);
        assertThat(creditNote.getAccountingCustomerParty()).hasSize(1);
        assertThat(creditNote.getCreditNoteLine()).hasSize(1);
        assertThat(creditNote.getLegalMonetaryTotal().getFirst().getPayableAmount().getFirst().get__())
                .isEqualByComparingTo(new BigDecimal("90"));
    }

    private <T> T read(String resourceName, Class<T> type) throws IOException {
        try (InputStream input = UblSeedDocumentTest.class.getResourceAsStream(RESOURCE_ROOT + resourceName)) {
            assertThat(input).as("seed resource %s", resourceName).isNotNull();
            return mapper.readValue(input, type);
        }
    }
}
