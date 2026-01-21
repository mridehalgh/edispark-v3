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
 * Tests for UBL CreditNote document parsing.
 * 
 * <p>Validates: Requirements 4.1
 */
@DisplayName("CreditNote Document Tests")
class CreditNoteDocumentTest {

    private static final String SAMPLE_CREDIT_NOTE_FILENAME = "UBL-CreditNote-2.1-Example.json";
    
    private static ObjectMapper mapper;
    private static UBLCreditNote21 creditNoteDocument;
    private static CreditNote creditNote;

    @BeforeAll
    static void setUp() throws IOException {
        mapper = UblJsonMapper.getInstance();
        
        Path sampleCreditNotePath = findSampleFile(SAMPLE_CREDIT_NOTE_FILENAME);
        String creditNoteJson = Files.readString(sampleCreditNotePath);
        creditNoteDocument = mapper.readValue(creditNoteJson, UBLCreditNote21.class);
        creditNote = creditNoteDocument.getCreditNote().get(0);
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
        @DisplayName("CreditNote ID is correctly parsed")
        void creditNoteId_isParsedCorrectly() {
            assertThat(creditNote.getId()).hasSize(1);
            assertThat(creditNote.getId().get(0).get__()).isEqualTo("TOSL108");
        }

        @Test
        @DisplayName("Issue Date is correctly parsed")
        void issueDate_isParsedCorrectly() {
            assertThat(creditNote.getIssueDate()).hasSize(1);
            assertThat(creditNote.getIssueDate().get(0).get__()).isEqualTo("2009-12-15");
        }

        @Test
        @DisplayName("UBL Version ID is correctly parsed")
        void ublVersionId_isParsedCorrectly() {
            assertThat(creditNote.getUBLVersionID()).hasSize(1);
            assertThat(creditNote.getUBLVersionID().get(0).get__()).isEqualTo("2.1");
        }

        @Test
        @DisplayName("Document Currency Code is correctly parsed with attributes")
        void documentCurrencyCode_isParsedWithAttributes() {
            assertThat(creditNote.getDocumentCurrencyCode()).hasSize(1);
            CodeType currencyCode = creditNote.getDocumentCurrencyCode().get(0);
            
            assertThat(currencyCode.get__()).isEqualTo("EUR");
            assertThat(currencyCode.getListID()).isEqualTo("ISO 4217 Alpha");
            assertThat(currencyCode.getListAgencyID()).isEqualTo("6");
        }
    }

    @Nested
    @DisplayName("Party Verification")
    class PartyTest {

        @Test
        @DisplayName("AccountingSupplierParty is present")
        void accountingSupplierParty_isPresent() {
            assertThat(creditNote.getAccountingSupplierParty()).hasSize(1);
        }

        @Test
        @DisplayName("Supplier Party name is correctly parsed")
        void supplierPartyName_isParsedCorrectly() {
            SupplierParty supplierParty = creditNote.getAccountingSupplierParty().get(0);
            Party party = supplierParty.getParty().get(0);
            
            assertThat(party.getPartyName()).hasSize(1);
            assertThat(party.getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Salescompany ltd.");
        }

        @Test
        @DisplayName("AccountingCustomerParty is present")
        void accountingCustomerParty_isPresent() {
            assertThat(creditNote.getAccountingCustomerParty()).hasSize(1);
        }

        @Test
        @DisplayName("Customer Party name is correctly parsed")
        void customerPartyName_isParsedCorrectly() {
            CustomerParty customerParty = creditNote.getAccountingCustomerParty().get(0);
            Party party = customerParty.getParty().get(0);
            
            assertThat(party.getPartyName()).hasSize(1);
            assertThat(party.getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Buyercompany ltd");
        }
    }

    @Nested
    @DisplayName("CreditNoteLine Verification")
    class CreditNoteLineTest {

        @Test
        @DisplayName("CreditNote has correct number of lines")
        void creditNoteLines_hasCorrectCount() {
            assertThat(creditNote.getCreditNoteLine()).hasSize(5);
        }

        @Test
        @DisplayName("First credit note line ID is correctly parsed")
        void firstCreditNoteLine_idIsParsedCorrectly() {
            CreditNoteLine firstLine = creditNote.getCreditNoteLine().get(0);
            
            assertThat(firstLine.getId().get(0).get__()).isEqualTo("1");
        }

        @Test
        @DisplayName("First credit note line extension amount is correctly parsed")
        void firstCreditNoteLine_lineExtensionAmountIsParsedCorrectly() {
            CreditNoteLine firstLine = creditNote.getCreditNoteLine().get(0);
            AmountType amount = firstLine.getLineExtensionAmount().get(0);
            
            assertThat(amount.get__()).isEqualByComparingTo(new BigDecimal("1273"));
            assertThat(amount.getCurrencyID()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Credit note line item name is correctly parsed")
        void creditNoteLine_itemNameIsParsedCorrectly() {
            CreditNoteLine firstLine = creditNote.getCreditNoteLine().get(0);
            Item item = firstLine.getItem().get(0);
            
            assertThat(item.getName().get(0).get__()).isEqualTo("Labtop computer");
        }

        @Test
        @DisplayName("Credit note line with negative quantity is correctly parsed")
        void creditNoteLine_negativeQuantityIsParsedCorrectly() {
            CreditNoteLine secondLine = creditNote.getCreditNoteLine().get(1);
            
            assertThat(secondLine.getCreditedQuantity().get(0).get__())
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
            assertThat(creditNote.getTaxTotal()).hasSize(1);
        }

        @Test
        @DisplayName("Total tax amount is correctly parsed")
        void taxTotal_amountIsParsedCorrectly() {
            TaxTotal taxTotal = creditNote.getTaxTotal().get(0);
            AmountType taxAmount = taxTotal.getTaxAmount().get(0);
            
            assertThat(taxAmount.get__()).isEqualByComparingTo(new BigDecimal("292.20"));
            assertThat(taxAmount.getCurrencyID()).isEqualTo("EUR");
        }
    }

    @Nested
    @DisplayName("LegalMonetaryTotal Verification")
    class LegalMonetaryTotalTest {

        @Test
        @DisplayName("LegalMonetaryTotal is present")
        void legalMonetaryTotal_isPresent() {
            assertThat(creditNote.getLegalMonetaryTotal()).hasSize(1);
        }

        @Test
        @DisplayName("Payable amount is correctly parsed")
        void payableAmount_isParsedCorrectly() {
            MonetaryTotal monetaryTotal = creditNote.getLegalMonetaryTotal().get(0);
            AmountType payableAmount = monetaryTotal.getPayableAmount().get(0);
            
            assertThat(payableAmount.get__()).isEqualByComparingTo(new BigDecimal("729"));
            assertThat(payableAmount.getCurrencyID()).isEqualTo("EUR");
        }
    }

    @Nested
    @DisplayName("Document Wrapper Verification")
    class DocumentWrapperTest {

        @Test
        @DisplayName("Document namespace URIs are correctly parsed")
        void documentNamespaces_areParsedCorrectly() {
            assertThat(creditNoteDocument.getD())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2");
            assertThat(creditNoteDocument.getA())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2");
            assertThat(creditNoteDocument.getB())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2");
        }

        @Test
        @DisplayName("CreditNote array contains exactly one credit note")
        void creditNoteArray_containsOneCreditNote() {
            assertThat(creditNoteDocument.getCreditNote()).hasSize(1);
        }
    }
}
