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
 * Tests for UBL Order document parsing.
 * 
 * <p>Validates: Requirements 4.1
 */
@DisplayName("Order Document Tests")
class OrderDocumentTest {

    private static final String SAMPLE_ORDER_FILENAME = "UBL-Order-2.1-Example.json";
    
    private static ObjectMapper mapper;
    private static UBLOrder21 orderDocument;
    private static Order order;

    @BeforeAll
    static void setUp() throws IOException {
        mapper = UblJsonMapper.getInstance();
        
        Path sampleOrderPath = findSampleFile(SAMPLE_ORDER_FILENAME);
        String orderJson = Files.readString(sampleOrderPath);
        orderDocument = mapper.readValue(orderJson, UBLOrder21.class);
        order = orderDocument.getOrder().get(0);
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
        @DisplayName("Order ID is correctly parsed")
        void orderId_isParsedCorrectly() {
            assertThat(order.getId()).hasSize(1);
            assertThat(order.getId().get(0).get__()).isEqualTo("34");
        }

        @Test
        @DisplayName("Issue Date is correctly parsed")
        void issueDate_isParsedCorrectly() {
            assertThat(order.getIssueDate()).hasSize(1);
            assertThat(order.getIssueDate().get(0).get__()).isEqualTo("2010-01-20");
        }

        @Test
        @DisplayName("UBL Version ID is correctly parsed")
        void ublVersionId_isParsedCorrectly() {
            assertThat(order.getUBLVersionID()).hasSize(1);
            assertThat(order.getUBLVersionID().get(0).get__()).isEqualTo("2.1");
        }

        @Test
        @DisplayName("Document Currency Code is correctly parsed")
        void documentCurrencyCode_isParsedCorrectly() {
            assertThat(order.getDocumentCurrencyCode()).hasSize(1);
            assertThat(order.getDocumentCurrencyCode().get(0).get__()).isEqualTo("SEK");
        }
    }

    @Nested
    @DisplayName("Party Verification")
    class PartyTest {

        @Test
        @DisplayName("BuyerCustomerParty is present")
        void buyerCustomerParty_isPresent() {
            assertThat(order.getBuyerCustomerParty()).hasSize(1);
        }

        @Test
        @DisplayName("Buyer Party name is correctly parsed")
        void buyerPartyName_isParsedCorrectly() {
            CustomerParty buyerParty = order.getBuyerCustomerParty().get(0);
            Party party = buyerParty.getParty().get(0);
            
            assertThat(party.getPartyName()).hasSize(1);
            assertThat(party.getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Johnssons byggvaror");
        }

        @Test
        @DisplayName("SellerSupplierParty is present")
        void sellerSupplierParty_isPresent() {
            assertThat(order.getSellerSupplierParty()).hasSize(1);
        }

        @Test
        @DisplayName("Seller Party name is correctly parsed")
        void sellerPartyName_isParsedCorrectly() {
            SupplierParty sellerParty = order.getSellerSupplierParty().get(0);
            Party party = sellerParty.getParty().get(0);
            
            assertThat(party.getPartyName()).hasSize(1);
            assertThat(party.getPartyName().get(0).getName().get(0).get__())
                    .isEqualTo("Moderna Produkter AB");
        }
    }

    @Nested
    @DisplayName("OrderLine Verification")
    class OrderLineTest {

        @Test
        @DisplayName("Order has correct number of lines")
        void orderLines_hasCorrectCount() {
            assertThat(order.getOrderLine()).hasSize(2);
        }

        @Test
        @DisplayName("First order line item name is correctly parsed")
        void firstOrderLine_itemNameIsParsedCorrectly() {
            OrderLine firstLine = order.getOrderLine().get(0);
            LineItem lineItem = firstLine.getLineItem().get(0);
            Item item = lineItem.getItem().get(0);
            
            assertThat(item.getName().get(0).get__()).isEqualTo("Falu Rödfärg");
        }

        @Test
        @DisplayName("First order line quantity is correctly parsed")
        void firstOrderLine_quantityIsParsedCorrectly() {
            OrderLine firstLine = order.getOrderLine().get(0);
            LineItem lineItem = firstLine.getLineItem().get(0);
            QuantityType quantity = lineItem.getQuantity().get(0);
            
            assertThat(quantity.get__()).isEqualByComparingTo(new BigDecimal("120"));
            assertThat(quantity.getUnitCode()).isEqualTo("LTR");
        }

        @Test
        @DisplayName("First order line extension amount is correctly parsed")
        void firstOrderLine_lineExtensionAmountIsParsedCorrectly() {
            OrderLine firstLine = order.getOrderLine().get(0);
            LineItem lineItem = firstLine.getLineItem().get(0);
            AmountType amount = lineItem.getLineExtensionAmount().get(0);
            
            assertThat(amount.get__()).isEqualByComparingTo(new BigDecimal("6000"));
            assertThat(amount.getCurrencyID()).isEqualTo("SEK");
        }
    }

    @Nested
    @DisplayName("AnticipatedMonetaryTotal Verification")
    class AnticipatedMonetaryTotalTest {

        @Test
        @DisplayName("AnticipatedMonetaryTotal is present")
        void anticipatedMonetaryTotal_isPresent() {
            assertThat(order.getAnticipatedMonetaryTotal()).hasSize(1);
        }

        @Test
        @DisplayName("Payable amount is correctly parsed")
        void payableAmount_isParsedCorrectly() {
            MonetaryTotal monetaryTotal = order.getAnticipatedMonetaryTotal().get(0);
            AmountType payableAmount = monetaryTotal.getPayableAmount().get(0);
            
            assertThat(payableAmount.get__()).isEqualByComparingTo(new BigDecimal("6225"));
            assertThat(payableAmount.getCurrencyID()).isEqualTo("SEK");
        }
    }

    @Nested
    @DisplayName("Document Wrapper Verification")
    class DocumentWrapperTest {

        @Test
        @DisplayName("Document namespace URIs are correctly parsed")
        void documentNamespaces_areParsedCorrectly() {
            assertThat(orderDocument.getD())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:Order-2");
            assertThat(orderDocument.getA())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2");
            assertThat(orderDocument.getB())
                    .isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2");
        }

        @Test
        @DisplayName("Order array contains exactly one order")
        void orderArray_containsOneOrder() {
            assertThat(orderDocument.getOrder()).hasSize(1);
        }
    }
}
