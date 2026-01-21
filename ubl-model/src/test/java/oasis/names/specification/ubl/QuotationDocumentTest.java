package oasis.names.specification.ubl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import oasis.names.specification.ubl.schema.xsd.maindoc.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for UBL Quotation document parsing.
 * 
 * <p>Validates: Requirements 4.1
 * 
 * <p>Note: The Quotation sample file uses ISO time format with timezone suffix (e.g., "11:32:26.0Z")
 * which requires a custom deserializer since the generated TimeType uses LocalTime.
 */
@DisplayName("Quotation Document Tests")
class QuotationDocumentTest {

    private static final String SAMPLE_QUOTATION_FILENAME = "UBL-Quotation-2.1-Example.json";
    
    private static ObjectMapper mapper;
    private static UBLQuotation21 quotationDocument;
    private static Quotation quotation;

    @BeforeAll
    static void setUp() throws IOException {
        mapper = createLenientMapper();
        
        Path sampleQuotationPath = findSampleFile(SAMPLE_QUOTATION_FILENAME);
        String quotationJson = Files.readString(sampleQuotationPath);
        quotationDocument = mapper.readValue(quotationJson, UBLQuotation21.class);
        quotation = quotationDocument.getQuotation().get(0);
    }
    
    /**
     * Creates an ObjectMapper that can handle ISO time with timezone suffix.
     * The UBL sample files use formats like "11:32:26.0Z" which LocalTime cannot parse directly.
     */
    private static ObjectMapper createLenientMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        
        SimpleModule lenientTimeModule = new SimpleModule();
        lenientTimeModule.addDeserializer(LocalTime.class, new LenientLocalTimeDeserializer());
        mapper.registerModule(lenientTimeModule);
        
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
    
    /**
     * Custom deserializer that handles ISO time with optional timezone suffix.
     */
    private static class LenientLocalTimeDeserializer extends StdDeserializer<LocalTime> {
        
        LenientLocalTimeDeserializer() {
            super(LocalTime.class);
        }
        
        @Override
        public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String timeStr = p.getText();
            if (timeStr == null || timeStr.isEmpty()) {
                return null;
            }
            
            try {
                return LocalTime.parse(timeStr);
            } catch (DateTimeParseException e) {
                // Try parsing as OffsetTime and extract LocalTime
                try {
                    return OffsetTime.parse(timeStr).toLocalTime();
                } catch (DateTimeParseException e2) {
                    throw new IOException("Cannot parse time: " + timeStr, e2);
                }
            }
        }
    }

    private static Path findSampleFile(String filename) {
        Path[] possiblePaths = {
            Path.of("../ubl-source/json", filename),
            Path.of("ubl-source/json", filename),
            Path.of("../../ubl-source/json", filename)
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
        @DisplayName("Quotation ID is correctly parsed")
        void quotationId_isParsedCorrectly() {
            assertThat(quotation.getId()).hasSize(1);
            assertThat(quotation.getId().get(0).get__()).isEqualTo("QIY7655");
        }

        @Test
        @DisplayName("Issue Date is correctly parsed")
        void issueDate_isParsedCorrectly() {
            assertThat(quotation.getIssueDate()).hasSize(1);
            assertThat(quotation.getIssueDate().get(0).get__()).isEqualTo("2008-05-01");
        }

        @Test
        @DisplayName("UBL Version ID is correctly parsed")
        void ublVersionId_isParsedCorrectly() {
            assertThat(quotation.getUBLVersionID()).hasSize(1);
            assertThat(quotation.getUBLVersionID().get(0).get__()).isEqualTo("2.0");
        }

        @Test
        @DisplayName("UUID is correctly parsed")
        void uuid_isParsedCorrectly() {
            assertThat(quotation.getUuid()).hasSize(1);
            assertThat(quotation.getUuid().get(0).get__())
                    .isEqualTo("4D07786B-DA6D-439F-82D1-6FFFC7F4E3B1");
        }
    }

    @Nested
    @DisplayName("Party Verification")
    class PartyTest {

        @Test
        @DisplayName("SellerSupplierParty is present")
        void sellerSupplierParty_isPresent() {
            assertThat(quotation.getSellerSupplierParty()).hasSize(1);
        }

        @Test
        @DisplayName("Seller Party name is correctly parsed")
        void sellerPartyName_isParsedCorrectly() {
            SupplierParty sellerParty = quotation.getSellerSupplierParty().get(0);
            Party party = sellerParty.getParty().get(0);
            
            assertThat(party.getPartyName()).hasSize(1);
            assertThat(party.getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Delcomputer A/S");
        }

        @Test
        @DisplayName("OriginatorCustomerParty is present")
        void originatorCustomerParty_isPresent() {
            assertThat(quotation.getOriginatorCustomerParty()).hasSize(1);
        }

        @Test
        @DisplayName("Originator Party name is correctly parsed")
        void originatorPartyName_isParsedCorrectly() {
            CustomerParty originatorParty = quotation.getOriginatorCustomerParty().get(0);
            Party party = originatorParty.getParty().get(0);
            
            assertThat(party.getPartyName()).hasSize(1);
            assertThat(party.getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Gentofte Kommune");
        }
    }

    @Nested
    @DisplayName("QuotationLine Verification")
    class QuotationLineTest {

        @Test
        @DisplayName("Quotation has correct number of lines")
        void quotationLines_hasCorrectCount() {
            assertThat(quotation.getQuotationLine()).hasSize(4);
        }

        @Test
        @DisplayName("First quotation line ID is correctly parsed")
        void firstQuotationLine_idIsParsedCorrectly() {
            QuotationLine firstLine = quotation.getQuotationLine().get(0);
            
            assertThat(firstLine.getId().get(0).get__()).isEqualTo("1");
        }

        @Test
        @DisplayName("First quotation line item name is correctly parsed")
        void firstQuotationLine_itemNameIsParsedCorrectly() {
            QuotationLine firstLine = quotation.getQuotationLine().get(0);
            LineItem lineItem = firstLine.getLineItem().get(0);
            Item item = lineItem.getItem().get(0);
            
            assertThat(item.getName().get(0).get__()).isEqualTo("Dell PrecisionTM  T3400");
        }

        @Test
        @DisplayName("First quotation line extension amount is correctly parsed")
        void firstQuotationLine_lineExtensionAmountIsParsedCorrectly() {
            QuotationLine firstLine = quotation.getQuotationLine().get(0);
            LineItem lineItem = firstLine.getLineItem().get(0);
            AmountType amount = lineItem.getLineExtensionAmount().get(0);
            
            assertThat(amount.get__()).isEqualByComparingTo(new BigDecimal("150500.00"));
            assertThat(amount.getCurrencyID()).isEqualTo("DKK");
        }

        @Test
        @DisplayName("First quotation line quantity is correctly parsed")
        void firstQuotationLine_quantityIsParsedCorrectly() {
            QuotationLine firstLine = quotation.getQuotationLine().get(0);
            LineItem lineItem = firstLine.getLineItem().get(0);
            QuantityType quantity = lineItem.getQuantity().get(0);
            
            assertThat(quantity.get__()).isEqualByComparingTo(new BigDecimal("35"));
            assertThat(quantity.getUnitCode()).isEqualTo("EA");
        }
    }

    @Nested
    @DisplayName("QuotedMonetaryTotal Verification")
    class QuotedMonetaryTotalTest {

        @Test
        @DisplayName("QuotedMonetaryTotal is present")
        void quotedMonetaryTotal_isPresent() {
            assertThat(quotation.getQuotedMonetaryTotal()).hasSize(1);
        }

        @Test
        @DisplayName("Payable amount is correctly parsed")
        void payableAmount_isParsedCorrectly() {
            MonetaryTotal monetaryTotal = quotation.getQuotedMonetaryTotal().get(0);
            AmountType payableAmount = monetaryTotal.getPayableAmount().get(0);
            
            assertThat(payableAmount.get__()).isEqualByComparingTo(new BigDecimal("247187.50"));
            assertThat(payableAmount.getCurrencyID()).isEqualTo("DKK");
        }

        @Test
        @DisplayName("Line extension amount is correctly parsed")
        void lineExtensionAmount_isParsedCorrectly() {
            MonetaryTotal monetaryTotal = quotation.getQuotedMonetaryTotal().get(0);
            AmountType lineExtensionAmount = monetaryTotal.getLineExtensionAmount().get(0);
            
            assertThat(lineExtensionAmount.get__()).isEqualByComparingTo(new BigDecimal("197750.00"));
            assertThat(lineExtensionAmount.getCurrencyID()).isEqualTo("DKK");
        }
    }

    @Nested
    @DisplayName("Document Wrapper Verification")
    class DocumentWrapperTest {

        @Test
        @DisplayName("Document namespace URIs are correctly parsed")
        void documentNamespaces_areParsedCorrectly() {
            assertThat(quotationDocument.getD())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:Quotation-2");
            assertThat(quotationDocument.getA())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2");
            assertThat(quotationDocument.getB())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2");
        }

        @Test
        @DisplayName("Quotation array contains exactly one quotation")
        void quotationArray_containsOneQuotation() {
            assertThat(quotationDocument.getQuotation()).hasSize(1);
        }
    }
}
