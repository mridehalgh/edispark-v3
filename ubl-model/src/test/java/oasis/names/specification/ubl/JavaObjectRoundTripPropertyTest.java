package oasis.names.specification.ubl;

import com.example.ubl.util.UblJsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.arbitraries.ListArbitrary;
import oasis.names.specification.ubl.schema.xsd.maindoc.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Java object round-trip consistency.
 *
 * <p><b>Property 2: Java Object Round-Trip Consistency</b>
 * <p>For any programmatically constructed UBL Java object with valid field values,
 * serializing to JSON and then deserializing back to a Java object SHALL produce
 * an equivalent object.
 *
 * <p><b>Validates: Requirements 5.2, 3.1, 4.1</b>
 *
 * <p>This property validates that:
 * <ul>
 *   <li>Objects can be constructed programmatically</li>
 *   <li>Serialization produces valid JSON</li>
 *   <li>Deserialization reconstructs equivalent objects</li>
 *   <li>All attributes (schemeID, currencyID, etc.) are preserved</li>
 * </ul>
 */
class JavaObjectRoundTripPropertyTest {

    private static final ObjectMapper MAPPER = UblJsonMapper.getInstance();
    private static final String[] CURRENCY_CODES = {"USD", "EUR", "GBP", "JPY", "CAD", "AUD"};
    private static final String[] UNIT_CODES = {"EA", "KG", "LTR", "MTR", "PCE", "SET"};
    private static final String[] SCHEME_IDS = {"GLN", "DUNS", "VAT", "IBAN"};

    /**
     * Property 2: Java Object Round-Trip Consistency for Invoice objects.
     *
     * <p><b>Validates: Requirements 5.2, 3.1, 4.1</b>
     *
     * <p>For any programmatically constructed Invoice object, serializing to JSON
     * and then deserializing back SHALL produce an equivalent object.
     */
    @Property(tries = 100)
    @Label("Feature: ubl-java-library, Property 2: Java object round-trip consistency")
    void invoiceObjectRoundTripPreservesData(@ForAll("validInvoices") Invoice original)
            throws JsonProcessingException {
        // Serialize to JSON
        String json = MAPPER.writeValueAsString(original);

        // Deserialize back to Java object
        Invoice deserialized = MAPPER.readValue(json, Invoice.class);

        // Verify equivalence using recursive comparison
        assertThat(deserialized)
                .as("Round-trip should preserve all Invoice data")
                .usingRecursiveComparison()
                .isEqualTo(original);
    }

    /**
     * Provides random valid Invoice objects for property testing.
     */
    @Provide
    Arbitrary<Invoice> validInvoices() {
        return Combinators.combine(
                identifiers(),
                dates(),
                supplierParties(),
                customerParties(),
                invoiceLines(),
                monetaryTotals()
        ).as(this::buildInvoice);
    }

    private Invoice buildInvoice(
            IdentifierType id,
            DateType issueDate,
            SupplierParty supplierParty,
            CustomerParty customerParty,
            List<InvoiceLine> lines,
            MonetaryTotal monetaryTotal) {
        return new Invoice()
                .withId(List.of(id))
                .withIssueDate(List.of(issueDate))
                .withAccountingSupplierParty(List.of(supplierParty))
                .withAccountingCustomerParty(List.of(customerParty))
                .withInvoiceLine(lines)
                .withLegalMonetaryTotal(List.of(monetaryTotal));
    }

    // ========== Basic Type Arbitraries ==========

    @Provide
    Arbitrary<IdentifierType> identifiers() {
        return Combinators.combine(
                invoiceIds(),
                Arbitraries.of(SCHEME_IDS).optional()
        ).as(this::buildIdentifier);
    }

    private IdentifierType buildIdentifier(String value, java.util.Optional<String> schemeId) {
        IdentifierType id = new IdentifierType().with__(value);
        schemeId.ifPresent(id::withSchemeID);
        return id;
    }

    @Provide
    Arbitrary<String> invoiceIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "INV-" + s);
    }

    @Provide
    Arbitrary<DateType> dates() {
        return Arbitraries.integers().between(2020, 2025)
                .flatMap(year -> Arbitraries.integers().between(1, 12)
                        .flatMap(month -> Arbitraries.integers().between(1, 28)
                                .map(day -> new DateType()
                                        .with__(LocalDate.of(year, month, day)))));
    }

    @Provide
    Arbitrary<TextType> textTypes() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50)
                .map(s -> new TextType().with__(s));
    }

    @Provide
    Arbitrary<AmountType> amounts() {
        return Combinators.combine(
                Arbitraries.bigDecimals()
                        .between(BigDecimal.ONE, new BigDecimal("999999.99"))
                        .ofScale(2),
                Arbitraries.of(CURRENCY_CODES)
        ).as((value, currency) -> new AmountType()
                .with__(value)
                .withCurrencyID(currency));
    }

    @Provide
    Arbitrary<QuantityType> quantities() {
        return Combinators.combine(
                Arbitraries.bigDecimals()
                        .between(BigDecimal.ONE, new BigDecimal("1000"))
                        .ofScale(2),
                Arbitraries.of(UNIT_CODES)
        ).as((value, unitCode) -> new QuantityType()
                .with__(value)
                .withUnitCode(unitCode));
    }

    // ========== Party Arbitraries ==========

    @Provide
    Arbitrary<SupplierParty> supplierParties() {
        return parties().map(party -> new SupplierParty().withParty(List.of(party)));
    }

    @Provide
    Arbitrary<CustomerParty> customerParties() {
        return parties().map(party -> new CustomerParty().withParty(List.of(party)));
    }

    @Provide
    Arbitrary<Party> parties() {
        return partyNames().map(name -> new Party().withPartyName(List.of(name)));
    }

    @Provide
    Arbitrary<PartyName> partyNames() {
        return companyNames().map(name ->
                new PartyName().withName(List.of(new TextType().with__(name))));
    }

    @Provide
    Arbitrary<String> companyNames() {
        return Arbitraries.of(
                "Acme Corporation",
                "Global Industries Ltd",
                "Tech Solutions Inc",
                "Premier Services Co",
                "Quality Products LLC"
        );
    }

    // ========== Invoice Line Arbitraries ==========

    @Provide
    ListArbitrary<InvoiceLine> invoiceLines() {
        return invoiceLine().list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<InvoiceLine> invoiceLine() {
        return Combinators.combine(
                lineIds(),
                amounts(),
                items(),
                quantities().optional()
        ).as(this::buildInvoiceLine);
    }

    private InvoiceLine buildInvoiceLine(
            IdentifierType id,
            AmountType amount,
            Item item,
            java.util.Optional<QuantityType> quantity) {
        InvoiceLine line = new InvoiceLine()
                .withId(List.of(id))
                .withLineExtensionAmount(List.of(amount))
                .withItem(List.of(item));
        quantity.ifPresent(q -> line.withInvoicedQuantity(List.of(q)));
        return line;
    }

    @Provide
    Arbitrary<IdentifierType> lineIds() {
        return Arbitraries.integers()
                .between(1, 100)
                .map(i -> new IdentifierType().with__(String.valueOf(i)));
    }

    @Provide
    Arbitrary<Item> items() {
        return itemNames().map(name ->
                new Item().withName(List.of(new TextType().with__(name))));
    }

    @Provide
    Arbitrary<String> itemNames() {
        return Arbitraries.of(
                "Widget",
                "Gadget",
                "Component",
                "Service",
                "Subscription",
                "License",
                "Hardware",
                "Software"
        );
    }

    // ========== Monetary Total Arbitraries ==========

    @Provide
    Arbitrary<MonetaryTotal> monetaryTotals() {
        return amounts().map(amount -> new MonetaryTotal()
                .withLineExtensionAmount(List.of(amount))
                .withTaxExclusiveAmount(List.of(amount))
                .withTaxInclusiveAmount(List.of(amount))
                .withPayableAmount(List.of(amount)));
    }
}
