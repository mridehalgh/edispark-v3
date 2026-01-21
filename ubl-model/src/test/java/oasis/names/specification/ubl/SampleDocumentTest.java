package oasis.names.specification.ubl;

import com.example.ubl.util.UblJsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import oasis.names.specification.ubl.schema.xsd.maindoc.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample document tests for UBL document parsing.
 * 
 * <p>These tests verify that the generated UBL classes can correctly parse
 * real UBL JSON sample documents and that key fields and nested structures
 * are correctly populated.
 * 
 * <p>Validates: Requirements 4.1, 4.2, 4.3
 */
@DisplayName("Sample Document Tests")
class SampleDocumentTest {

    private static final String SAMPLE_INVOICE_FILENAME = "UBL-Invoice-2.1-Example.json";
    private static final String SAMPLE_ORDER_FILENAME = "UBL-Order-2.1-Example.json";
    private static final String SAMPLE_CREDIT_NOTE_FILENAME = "UBL-CreditNote-2.1-Example.json";
    private static final String SAMPLE_QUOTATION_FILENAME = "UBL-Quotation-2.1-Example.json";
    
    private static ObjectMapper mapper;
    private static UBLInvoice21 invoiceDocument;
    private static Invoice invoice;
    private static UBLOrder21 orderDocument;
    private static Order order;
    private static UBLCreditNote21 creditNoteDocument;
    private static CreditNote creditNote;
    private static UBLQuotation21 quotationDocument;
    private static Quotation quotation;

    @BeforeAll
    static void setUp() throws IOException {
        mapper = UblJsonMapper.getInstance();
        
        // Load Invoice
        Path sampleInvoicePath = findSampleFile(SAMPLE_INVOICE_FILENAME);
        String invoiceJson = Files.readString(sampleInvoicePath);
        invoiceDocument = mapper.readValue(invoiceJson, UBLInvoice21.class);
        invoice = invoiceDocument.getInvoice().get(0);
        
        // Load Order
        Path sampleOrderPath = findSampleFile(SAMPLE_ORDER_FILENAME);
        String orderJson = Files.readString(sampleOrderPath);
        orderDocument = mapper.readValue(orderJson, UBLOrder21.class);
        order = orderDocument.getOrder().get(0);
        
        // Load CreditNote
        Path sampleCreditNotePath = findSampleFile(SAMPLE_CREDIT_NOTE_FILENAME);
        String creditNoteJson = Files.readString(sampleCreditNotePath);
        creditNoteDocument = mapper.readValue(creditNoteJson, UBLCreditNote21.class);
        creditNote = creditNoteDocument.getCreditNote().get(0);
        
        // Load Quotation
        Path sampleQuotationPath = findSampleFile(SAMPLE_QUOTATION_FILENAME);
        String quotationJson = Files.readString(sampleQuotationPath);
        quotationDocument = mapper.readValue(quotationJson, UBLQuotation21.class);
        quotation = quotationDocument.getQuotation().get(0);
    }

    private static Path findSampleFile(String filename) {
        // Try multiple possible locations for the sample file
        Path[] possiblePaths = {
            Path.of("../ubl-source/json", filename),           // Running from ubl-model
            Path.of("ubl-source/json", filename),              // Running from project root
            Path.of("../../ubl-source/json", filename)         // Running from deeper directory
        };
        
        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                return path;
            }
        }
        
        throw new IllegalStateException(
            "Could not find sample file: " + filename + 
            ". Tried paths relative to: " + Path.of("").toAbsolutePath());
    }

    @Nested
    @DisplayName("Key Fields Verification")
    class KeyFieldsTest {

        @Test
        @DisplayName("Invoice ID is correctly parsed")
        void invoiceId_isParsedCorrectly() {
            assertThat(invoice.getId()).hasSize(1);
            assertThat(invoice.getId().get(0).get__()).isEqualTo("TOSL108");
        }

        @Test
        @DisplayName("Issue Date is correctly parsed")
        void issueDate_isParsedCorrectly() {
            assertThat(invoice.getIssueDate()).hasSize(1);
            assertThat(invoice.getIssueDate().get(0).get__()).isEqualTo("2009-12-15");
        }

        @Test
        @DisplayName("UBL Version ID is correctly parsed")
        void ublVersionId_isParsedCorrectly() {
            assertThat(invoice.getUBLVersionID()).hasSize(1);
            assertThat(invoice.getUBLVersionID().get(0).get__()).isEqualTo("2.1");
        }

        @Test
        @DisplayName("Document Currency Code is correctly parsed with attributes")
        void documentCurrencyCode_isParsedWithAttributes() {
            assertThat(invoice.getDocumentCurrencyCode()).hasSize(1);
            CodeType currencyCode = invoice.getDocumentCurrencyCode().get(0);
            
            assertThat(currencyCode.get__()).isEqualTo("EUR");
            assertThat(currencyCode.getListID()).isEqualTo("ISO 4217 Alpha");
            assertThat(currencyCode.getListAgencyID()).isEqualTo("6");
        }

        @Test
        @DisplayName("Invoice Type Code is correctly parsed with attributes")
        void invoiceTypeCode_isParsedWithAttributes() {
            assertThat(invoice.getInvoiceTypeCode()).hasSize(1);
            CodeType typeCode = invoice.getInvoiceTypeCode().get(0);
            
            assertThat(typeCode.get__()).isEqualTo("380");
            assertThat(typeCode.getListID()).isEqualTo("UN/ECE 1001 Subset");
            assertThat(typeCode.getListAgencyID()).isEqualTo("6");
        }
    }

    @Nested
    @DisplayName("AccountingSupplierParty Verification")
    class AccountingSupplierPartyTest {

        @Test
        @DisplayName("AccountingSupplierParty is present")
        void accountingSupplierParty_isPresent() {
            assertThat(invoice.getAccountingSupplierParty()).hasSize(1);
        }

        @Test
        @DisplayName("Supplier Party name is correctly parsed")
        void supplierPartyName_isParsedCorrectly() {
            SupplierParty supplierParty = invoice.getAccountingSupplierParty().get(0);
            Party party = supplierParty.getParty().get(0);
            
            assertThat(party.getPartyName()).hasSize(1);
            assertThat(party.getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Salescompany ltd.");
        }

        @Test
        @DisplayName("Supplier Party endpoint ID is correctly parsed with scheme attributes")
        void supplierEndpointId_isParsedWithSchemeAttributes() {
            SupplierParty supplierParty = invoice.getAccountingSupplierParty().get(0);
            Party party = supplierParty.getParty().get(0);
            IdentifierType endpointId = party.getEndpointID().get(0);
            
            assertThat(endpointId.get__()).isEqualTo("1234567890123");
            assertThat(endpointId.getSchemeID()).isEqualTo("GLN");
            assertThat(endpointId.getSchemeAgencyID()).isEqualTo("9");
        }

        @Test
        @DisplayName("Supplier postal address is correctly parsed")
        void supplierPostalAddress_isParsedCorrectly() {
            SupplierParty supplierParty = invoice.getAccountingSupplierParty().get(0);
            Party party = supplierParty.getParty().get(0);
            Address address = party.getPostalAddress().get(0);
            
            assertThat(address.getStreetName().get(0).get__()).isEqualTo("Main street");
            assertThat(address.getCityName().get(0).get__()).isEqualTo("Big city");
            assertThat(address.getPostalZone().get(0).get__()).isEqualTo("54321");
            assertThat(address.getCountry().get(0).getIdentificationCode().get(0).get__())
                    .isEqualTo("DK");
        }
    }

    @Nested
    @DisplayName("InvoiceLine Verification")
    class InvoiceLineTest {

        @Test
        @DisplayName("Invoice has correct number of lines")
        void invoiceLines_hasCorrectCount() {
            assertThat(invoice.getInvoiceLine()).hasSize(5);
        }

        @Test
        @DisplayName("First invoice line ID is correctly parsed")
        void firstInvoiceLine_idIsParsedCorrectly() {
            InvoiceLine firstLine = invoice.getInvoiceLine().get(0);
            
            assertThat(firstLine.getId().get(0).get__()).isEqualTo("1");
        }

        @Test
        @DisplayName("First invoice line extension amount is correctly parsed")
        void firstInvoiceLine_lineExtensionAmountIsParsedCorrectly() {
            InvoiceLine firstLine = invoice.getInvoiceLine().get(0);
            AmountType amount = firstLine.getLineExtensionAmount().get(0);
            
            assertThat(amount.get__()).isEqualByComparingTo(new BigDecimal("1273"));
            assertThat(amount.getCurrencyID()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Invoice line item name is correctly parsed")
        void invoiceLine_itemNameIsParsedCorrectly() {
            InvoiceLine firstLine = invoice.getInvoiceLine().get(0);
            Item item = firstLine.getItem().get(0);
            
            assertThat(item.getName().get(0).get__()).isEqualTo("Labtop computer");
        }

        @Test
        @DisplayName("Invoice line with negative quantity is correctly parsed")
        void invoiceLine_negativeQuantityIsParsedCorrectly() {
            InvoiceLine secondLine = invoice.getInvoiceLine().get(1);
            
            assertThat(secondLine.getInvoicedQuantity().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("-1"));
            assertThat(secondLine.getLineExtensionAmount().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("-3.96"));
        }
    }

    @Nested
    @DisplayName("TaxTotal Verification")
    class TaxTotalTest {

        @Test
        @DisplayName("TaxTotal is present")
        void taxTotal_isPresent() {
            assertThat(invoice.getTaxTotal()).hasSize(1);
        }

        @Test
        @DisplayName("Total tax amount is correctly parsed")
        void taxTotal_amountIsParsedCorrectly() {
            TaxTotal taxTotal = invoice.getTaxTotal().get(0);
            AmountType taxAmount = taxTotal.getTaxAmount().get(0);
            
            assertThat(taxAmount.get__()).isEqualByComparingTo(new BigDecimal("292.20"));
            assertThat(taxAmount.getCurrencyID()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("TaxSubtotals are correctly parsed")
        void taxSubtotals_areParsedCorrectly() {
            TaxTotal taxTotal = invoice.getTaxTotal().get(0);
            
            assertThat(taxTotal.getTaxSubtotal()).hasSize(3);
        }

        @Test
        @DisplayName("First TaxSubtotal details are correctly parsed")
        void firstTaxSubtotal_detailsAreParsedCorrectly() {
            TaxTotal taxTotal = invoice.getTaxTotal().get(0);
            TaxSubtotal firstSubtotal = taxTotal.getTaxSubtotal().get(0);
            
            assertThat(firstSubtotal.getTaxableAmount().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("1460.5"));
            assertThat(firstSubtotal.getTaxAmount().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("292.1"));
        }

        @Test
        @DisplayName("TaxCategory with percent is correctly parsed")
        void taxCategory_percentIsParsedCorrectly() {
            TaxTotal taxTotal = invoice.getTaxTotal().get(0);
            TaxSubtotal firstSubtotal = taxTotal.getTaxSubtotal().get(0);
            TaxCategory taxCategory = firstSubtotal.getTaxCategory().get(0);
            
            assertThat(taxCategory.getId().get(0).get__()).isEqualTo("S");
            assertThat(taxCategory.getPercent().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("20"));
        }
    }

    @Nested
    @DisplayName("LegalMonetaryTotal Verification")
    class LegalMonetaryTotalTest {

        @Test
        @DisplayName("LegalMonetaryTotal is present")
        void legalMonetaryTotal_isPresent() {
            assertThat(invoice.getLegalMonetaryTotal()).hasSize(1);
        }

        @Test
        @DisplayName("Payable amount is correctly parsed")
        void payableAmount_isParsedCorrectly() {
            MonetaryTotal monetaryTotal = invoice.getLegalMonetaryTotal().get(0);
            AmountType payableAmount = monetaryTotal.getPayableAmount().get(0);
            
            assertThat(payableAmount.get__()).isEqualByComparingTo(new BigDecimal("729"));
            assertThat(payableAmount.getCurrencyID()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Tax inclusive amount is correctly parsed")
        void taxInclusiveAmount_isParsedCorrectly() {
            MonetaryTotal monetaryTotal = invoice.getLegalMonetaryTotal().get(0);
            AmountType taxInclusiveAmount = monetaryTotal.getTaxInclusiveAmount().get(0);
            
            assertThat(taxInclusiveAmount.get__()).isEqualByComparingTo(new BigDecimal("1729"));
            assertThat(taxInclusiveAmount.getCurrencyID()).isEqualTo("EUR");
        }
    }

    @Nested
    @DisplayName("Document Wrapper Verification")
    class DocumentWrapperTest {

        @Test
        @DisplayName("Document namespace URIs are correctly parsed")
        void documentNamespaces_areParsedCorrectly() {
            assertThat(invoiceDocument.getD())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2");
            assertThat(invoiceDocument.getA())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2");
            assertThat(invoiceDocument.getB())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2");
        }

        @Test
        @DisplayName("Invoice array contains exactly one invoice")
        void invoiceArray_containsOneInvoice() {
            assertThat(invoiceDocument.getInvoice()).hasSize(1);
        }
    }
}
