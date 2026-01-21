package oasis.names.specification.ubl;

import com.example.ubl.util.UblJsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import oasis.names.specification.ubl.schema.xsd.maindoc.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for programmatic construction of UBL Invoice objects using builder-style methods.
 * 
 * <p>These tests verify that Invoice objects can be constructed programmatically using
 * the generated with* builder methods, and that all fields are set correctly.
 * 
 * <p>Validates: Requirements 2.1, 3.1
 */
@DisplayName("Invoice Builder Tests")
class InvoiceBuilderTest {

    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        mapper = UblJsonMapper.getInstance();
    }

    @Nested
    @DisplayName("Basic Field Construction")
    class BasicFieldConstructionTest {

        @Test
        @DisplayName("Invoice ID can be set using builder")
        void invoiceId_canBeSetUsingBuilder() {
            Invoice invoice = new Invoice()
                    .withId(List.of(new IdentifierType().with__("INV-001")));

            assertThat(invoice.getId()).hasSize(1);
            assertThat(invoice.getId().get(0).get__()).isEqualTo("INV-001");
        }

        @Test
        @DisplayName("Issue Date can be set using builder")
        void issueDate_canBeSetUsingBuilder() {
            LocalDate date = LocalDate.of(2024, 1, 15);
            Invoice invoice = new Invoice()
                    .withIssueDate(List.of(new DateType().with__(date)));

            assertThat(invoice.getIssueDate()).hasSize(1);
            assertThat(invoice.getIssueDate().get(0).get__()).isEqualTo(date);
        }

        @Test
        @DisplayName("Document Currency Code can be set with attributes")
        void documentCurrencyCode_canBeSetWithAttributes() {
            Invoice invoice = new Invoice()
                    .withDocumentCurrencyCode(List.of(
                            new CodeType()
                                    .with__("USD")
                                    .withListID("ISO 4217 Alpha")
                                    .withListAgencyID("6")));

            assertThat(invoice.getDocumentCurrencyCode()).hasSize(1);
            CodeType currencyCode = invoice.getDocumentCurrencyCode().get(0);
            assertThat(currencyCode.get__()).isEqualTo("USD");
            assertThat(currencyCode.getListID()).isEqualTo("ISO 4217 Alpha");
            assertThat(currencyCode.getListAgencyID()).isEqualTo("6");
        }

        @Test
        @DisplayName("Identifier with scheme attributes can be constructed")
        void identifier_withSchemeAttributes_canBeConstructed() {
            IdentifierType identifier = new IdentifierType()
                    .with__("1234567890123")
                    .withSchemeID("GLN")
                    .withSchemeAgencyID("9");

            assertThat(identifier.get__()).isEqualTo("1234567890123");
            assertThat(identifier.getSchemeID()).isEqualTo("GLN");
            assertThat(identifier.getSchemeAgencyID()).isEqualTo("9");
        }
    }

    @Nested
    @DisplayName("Nested Object Construction")
    class NestedObjectConstructionTest {

        @Test
        @DisplayName("AccountingSupplierParty with Party details can be constructed")
        void accountingSupplierParty_withPartyDetails_canBeConstructed() {
            Party supplierParty = new Party()
                    .withPartyName(List.of(
                            new PartyName().withName(List.of(
                                    new TextType().with__("Acme Corporation")))))
                    .withEndpointID(List.of(
                            new IdentifierType()
                                    .with__("1234567890123")
                                    .withSchemeID("GLN")
                                    .withSchemeAgencyID("9")));

            SupplierParty accountingSupplierParty = new SupplierParty()
                    .withParty(List.of(supplierParty));

            Invoice invoice = new Invoice()
                    .withAccountingSupplierParty(List.of(accountingSupplierParty));

            assertThat(invoice.getAccountingSupplierParty()).hasSize(1);
            Party party = invoice.getAccountingSupplierParty().get(0).getParty().get(0);
            assertThat(party.getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Acme Corporation");
            assertThat(party.getEndpointID().get(0).get__()).isEqualTo("1234567890123");
        }

        @Test
        @DisplayName("AccountingCustomerParty with Party details can be constructed")
        void accountingCustomerParty_withPartyDetails_canBeConstructed() {
            Party customerParty = new Party()
                    .withPartyName(List.of(
                            new PartyName().withName(List.of(
                                    new TextType().with__("Customer Inc.")))));

            CustomerParty accountingCustomerParty = new CustomerParty()
                    .withParty(List.of(customerParty));

            Invoice invoice = new Invoice()
                    .withAccountingCustomerParty(List.of(accountingCustomerParty));

            assertThat(invoice.getAccountingCustomerParty()).hasSize(1);
            Party party = invoice.getAccountingCustomerParty().get(0).getParty().get(0);
            assertThat(party.getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Customer Inc.");
        }

        @Test
        @DisplayName("Party with postal address can be constructed")
        void party_withPostalAddress_canBeConstructed() {
            Address address = new Address()
                    .withStreetName(List.of(new TextType().with__("123 Main Street")))
                    .withCityName(List.of(new TextType().with__("New York")))
                    .withPostalZone(List.of(new TextType().with__("10001")))
                    .withCountry(List.of(
                            new Country().withIdentificationCode(List.of(
                                    new CodeType().with__("US")))));

            Party party = new Party()
                    .withPostalAddress(List.of(address));

            assertThat(party.getPostalAddress()).hasSize(1);
            Address postalAddress = party.getPostalAddress().get(0);
            assertThat(postalAddress.getStreetName().get(0).get__()).isEqualTo("123 Main Street");
            assertThat(postalAddress.getCityName().get(0).get__()).isEqualTo("New York");
            assertThat(postalAddress.getPostalZone().get(0).get__()).isEqualTo("10001");
            assertThat(postalAddress.getCountry().get(0).getIdentificationCode().get(0).get__())
                    .isEqualTo("US");
        }
    }

    @Nested
    @DisplayName("Invoice Line Construction")
    class InvoiceLineConstructionTest {

        @Test
        @DisplayName("InvoiceLine with Item and amounts can be constructed")
        void invoiceLine_withItemAndAmounts_canBeConstructed() {
            Item item = new Item()
                    .withName(List.of(new TextType().with__("Widget")))
                    .withDescription(List.of(new TextType().with__("A high-quality widget")));

            InvoiceLine invoiceLine = new InvoiceLine()
                    .withId(List.of(new IdentifierType().with__("1")))
                    .withInvoicedQuantity(List.of(
                            new QuantityType()
                                    .with__(new BigDecimal("10"))
                                    .withUnitCode("EA")))
                    .withLineExtensionAmount(List.of(
                            new AmountType()
                                    .with__(new BigDecimal("100.00"))
                                    .withCurrencyID("USD")))
                    .withItem(List.of(item));

            assertThat(invoiceLine.getId().get(0).get__()).isEqualTo("1");
            assertThat(invoiceLine.getInvoicedQuantity().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("10"));
            assertThat(invoiceLine.getInvoicedQuantity().get(0).getUnitCode()).isEqualTo("EA");
            assertThat(invoiceLine.getLineExtensionAmount().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(invoiceLine.getLineExtensionAmount().get(0).getCurrencyID()).isEqualTo("USD");
            assertThat(invoiceLine.getItem().get(0).getName().get(0).get__()).isEqualTo("Widget");
        }

        @Test
        @DisplayName("Multiple invoice lines can be added")
        void multipleInvoiceLines_canBeAdded() {
            InvoiceLine line1 = createInvoiceLine("1", "Product A", new BigDecimal("50.00"));
            InvoiceLine line2 = createInvoiceLine("2", "Product B", new BigDecimal("75.00"));
            InvoiceLine line3 = createInvoiceLine("3", "Product C", new BigDecimal("25.00"));

            Invoice invoice = new Invoice()
                    .withInvoiceLine(List.of(line1, line2, line3));

            assertThat(invoice.getInvoiceLine()).hasSize(3);
            assertThat(invoice.getInvoiceLine().get(0).getId().get(0).get__()).isEqualTo("1");
            assertThat(invoice.getInvoiceLine().get(1).getId().get(0).get__()).isEqualTo("2");
            assertThat(invoice.getInvoiceLine().get(2).getId().get(0).get__()).isEqualTo("3");
        }

        private InvoiceLine createInvoiceLine(String id, String itemName, BigDecimal amount) {
            return new InvoiceLine()
                    .withId(List.of(new IdentifierType().with__(id)))
                    .withLineExtensionAmount(List.of(
                            new AmountType().with__(amount).withCurrencyID("USD")))
                    .withItem(List.of(new Item().withName(List.of(
                            new TextType().with__(itemName)))));
        }
    }

    @Nested
    @DisplayName("LegalMonetaryTotal Construction")
    class LegalMonetaryTotalConstructionTest {

        @Test
        @DisplayName("LegalMonetaryTotal with all amounts can be constructed")
        void legalMonetaryTotal_withAllAmounts_canBeConstructed() {
            MonetaryTotal monetaryTotal = new MonetaryTotal()
                    .withLineExtensionAmount(List.of(
                            new AmountType().with__(new BigDecimal("1000.00")).withCurrencyID("USD")))
                    .withTaxExclusiveAmount(List.of(
                            new AmountType().with__(new BigDecimal("1000.00")).withCurrencyID("USD")))
                    .withTaxInclusiveAmount(List.of(
                            new AmountType().with__(new BigDecimal("1100.00")).withCurrencyID("USD")))
                    .withPayableAmount(List.of(
                            new AmountType().with__(new BigDecimal("1100.00")).withCurrencyID("USD")));

            Invoice invoice = new Invoice()
                    .withLegalMonetaryTotal(List.of(monetaryTotal));

            assertThat(invoice.getLegalMonetaryTotal()).hasSize(1);
            MonetaryTotal total = invoice.getLegalMonetaryTotal().get(0);
            assertThat(total.getLineExtensionAmount().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(total.getTaxInclusiveAmount().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("1100.00"));
            assertThat(total.getPayableAmount().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("1100.00"));
        }
    }

    @Nested
    @DisplayName("Complete Invoice Construction")
    class CompleteInvoiceConstructionTest {

        @Test
        @DisplayName("Complete Invoice with all required fields can be constructed")
        void completeInvoice_withAllRequiredFields_canBeConstructed() {
            Invoice invoice = createCompleteInvoice();

            // Verify ID
            assertThat(invoice.getId().get(0).get__()).isEqualTo("INV-2024-001");

            // Verify Issue Date
            assertThat(invoice.getIssueDate().get(0).get__()).isEqualTo(LocalDate.of(2024, 1, 15));

            // Verify Supplier Party
            assertThat(invoice.getAccountingSupplierParty().get(0).getParty().get(0)
                    .getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Supplier Company Ltd.");

            // Verify Customer Party
            assertThat(invoice.getAccountingCustomerParty().get(0).getParty().get(0)
                    .getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Customer Corporation");

            // Verify Invoice Lines
            assertThat(invoice.getInvoiceLine()).hasSize(2);

            // Verify Legal Monetary Total
            assertThat(invoice.getLegalMonetaryTotal().get(0).getPayableAmount().get(0).get__())
                    .isEqualByComparingTo(new BigDecimal("1210.00"));
        }

        @Test
        @DisplayName("Complete Invoice can be serialized to JSON")
        void completeInvoice_canBeSerializedToJson() throws JsonProcessingException {
            Invoice invoice = createCompleteInvoice();

            String json = mapper.writeValueAsString(invoice);

            assertThat(json).isNotEmpty();
            assertThat(json).contains("\"ID\"");
            assertThat(json).contains("\"INV-2024-001\"");
            assertThat(json).contains("\"IssueDate\"");
            assertThat(json).contains("\"AccountingSupplierParty\"");
            assertThat(json).contains("\"AccountingCustomerParty\"");
            assertThat(json).contains("\"InvoiceLine\"");
            assertThat(json).contains("\"LegalMonetaryTotal\"");
        }

        @Test
        @DisplayName("Serialized Invoice can be deserialized back")
        void serializedInvoice_canBeDeserializedBack() throws JsonProcessingException {
            Invoice original = createCompleteInvoice();

            String json = mapper.writeValueAsString(original);
            Invoice deserialized = mapper.readValue(json, Invoice.class);

            assertThat(deserialized.getId().get(0).get__())
                    .isEqualTo(original.getId().get(0).get__());
            assertThat(deserialized.getIssueDate().get(0).get__())
                    .isEqualTo(original.getIssueDate().get(0).get__());
            assertThat(deserialized.getAccountingSupplierParty().get(0).getParty().get(0)
                    .getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo(original.getAccountingSupplierParty().get(0).getParty().get(0)
                            .getPartyName().get(0).getName().get(0).get__());
            assertThat(deserialized.getInvoiceLine()).hasSize(original.getInvoiceLine().size());
            assertThat(deserialized.getLegalMonetaryTotal().get(0).getPayableAmount().get(0).get__())
                    .isEqualByComparingTo(original.getLegalMonetaryTotal().get(0)
                            .getPayableAmount().get(0).get__());
        }

        private Invoice createCompleteInvoice() {
            // Create Supplier Party
            Party supplierParty = new Party()
                    .withPartyName(List.of(new PartyName().withName(List.of(
                            new TextType().with__("Supplier Company Ltd.")))))
                    .withPostalAddress(List.of(new Address()
                            .withStreetName(List.of(new TextType().with__("100 Supplier Street")))
                            .withCityName(List.of(new TextType().with__("Supplier City")))
                            .withPostalZone(List.of(new TextType().with__("12345")))
                            .withCountry(List.of(new Country()
                                    .withIdentificationCode(List.of(new CodeType().with__("US")))))));

            SupplierParty accountingSupplierParty = new SupplierParty()
                    .withParty(List.of(supplierParty));

            // Create Customer Party
            Party customerParty = new Party()
                    .withPartyName(List.of(new PartyName().withName(List.of(
                            new TextType().with__("Customer Corporation")))))
                    .withPostalAddress(List.of(new Address()
                            .withStreetName(List.of(new TextType().with__("200 Customer Avenue")))
                            .withCityName(List.of(new TextType().with__("Customer Town")))
                            .withPostalZone(List.of(new TextType().with__("67890")))
                            .withCountry(List.of(new Country()
                                    .withIdentificationCode(List.of(new CodeType().with__("US")))))));

            CustomerParty accountingCustomerParty = new CustomerParty()
                    .withParty(List.of(customerParty));

            // Create Invoice Lines
            InvoiceLine line1 = new InvoiceLine()
                    .withId(List.of(new IdentifierType().with__("1")))
                    .withInvoicedQuantity(List.of(new QuantityType()
                            .with__(new BigDecimal("5"))
                            .withUnitCode("EA")))
                    .withLineExtensionAmount(List.of(new AmountType()
                            .with__(new BigDecimal("500.00"))
                            .withCurrencyID("USD")))
                    .withItem(List.of(new Item()
                            .withName(List.of(new TextType().with__("Premium Widget")))
                            .withDescription(List.of(new TextType()
                                    .with__("High-quality premium widget")))));

            InvoiceLine line2 = new InvoiceLine()
                    .withId(List.of(new IdentifierType().with__("2")))
                    .withInvoicedQuantity(List.of(new QuantityType()
                            .with__(new BigDecimal("10"))
                            .withUnitCode("EA")))
                    .withLineExtensionAmount(List.of(new AmountType()
                            .with__(new BigDecimal("600.00"))
                            .withCurrencyID("USD")))
                    .withItem(List.of(new Item()
                            .withName(List.of(new TextType().with__("Standard Gadget")))
                            .withDescription(List.of(new TextType()
                                    .with__("Standard quality gadget")))));

            // Create Legal Monetary Total
            MonetaryTotal legalMonetaryTotal = new MonetaryTotal()
                    .withLineExtensionAmount(List.of(new AmountType()
                            .with__(new BigDecimal("1100.00"))
                            .withCurrencyID("USD")))
                    .withTaxExclusiveAmount(List.of(new AmountType()
                            .with__(new BigDecimal("1100.00"))
                            .withCurrencyID("USD")))
                    .withTaxInclusiveAmount(List.of(new AmountType()
                            .with__(new BigDecimal("1210.00"))
                            .withCurrencyID("USD")))
                    .withPayableAmount(List.of(new AmountType()
                            .with__(new BigDecimal("1210.00"))
                            .withCurrencyID("USD")));

            // Build complete Invoice
            return new Invoice()
                    .withId(List.of(new IdentifierType().with__("INV-2024-001")))
                    .withIssueDate(List.of(new DateType().with__(LocalDate.of(2024, 1, 15))))
                    .withDocumentCurrencyCode(List.of(new CodeType()
                            .with__("USD")
                            .withListID("ISO 4217 Alpha")))
                    .withAccountingSupplierParty(List.of(accountingSupplierParty))
                    .withAccountingCustomerParty(List.of(accountingCustomerParty))
                    .withInvoiceLine(List.of(line1, line2))
                    .withLegalMonetaryTotal(List.of(legalMonetaryTotal));
        }
    }
}
