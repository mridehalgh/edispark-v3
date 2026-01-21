package oasis.names.specification.ubl;

import com.example.ubl.util.UblJsonMapper;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import oasis.names.specification.ubl.schema.xsd.maindoc.UBLInvoice21;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Error handling tests for UBL JSON deserialization.
 * 
 * <p>These tests verify that the deserializer throws appropriate exceptions
 * with useful information when encountering malformed JSON, type mismatches,
 * or missing required fields.
 * 
 * <p>Validates: Requirements 4.4, 4.5
 */
@DisplayName("Error Handling Tests")
class ErrorHandlingTest {

    private static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        mapper = UblJsonMapper.getInstance();
    }

    @Nested
    @DisplayName("Malformed JSON Tests")
    class MalformedJsonTest {

        @Test
        @DisplayName("Missing closing brace throws JsonProcessingException")
        void missingClosingBrace_throwsJsonProcessingException() {
            // JSON with missing closing braces - truncated mid-document
            String malformedJson = """
                {
                    "_D": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
                    "Invoice": [{
                        "ID": [{"_": "INV-001"}]
                """;

            assertThatThrownBy(() -> mapper.readValue(malformedJson, UBLInvoice21.class))
                    .isInstanceOf(JsonProcessingException.class);
        }

        @Test
        @DisplayName("JsonProcessingException contains location information")
        void jsonProcessingException_containsLocationInfo() {
            String malformedJson = """
                {
                    "_D": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
                    "Invoice": [{
                        "ID": [{"_": "INV-001"}]
                """;

            assertThatThrownBy(() -> mapper.readValue(malformedJson, UBLInvoice21.class))
                    .isInstanceOf(JsonProcessingException.class)
                    .satisfies(ex -> {
                        JsonProcessingException jpe = (JsonProcessingException) ex;
                        assertThat(jpe.getLocation()).isNotNull();
                        assertThat(jpe.getLocation().getLineNr()).isGreaterThan(0);
                    });
        }

        @Test
        @DisplayName("Missing closing bracket throws JsonProcessingException")
        void missingClosingBracket_throwsJsonProcessingException() {
            String malformedJson = """
                {
                    "_D": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
                    "Invoice": [{
                        "ID": [{"_": "INV-001"}
                    }]
                }
                """;

            assertThatThrownBy(() -> mapper.readValue(malformedJson, UBLInvoice21.class))
                    .isInstanceOf(JsonProcessingException.class);
        }

        @Test
        @DisplayName("Invalid JSON syntax (missing comma) throws JsonParseException")
        void invalidJsonSyntax_throwsJsonParseException() {
            // Missing comma between properties - pure syntax error before mapping
            String malformedJson = """
                {
                    "_D": "value1"
                    "_A": "value2"
                }
                """;

            assertThatThrownBy(() -> mapper.readValue(malformedJson, UBLInvoice21.class))
                    .isInstanceOf(JsonParseException.class);
        }

        @Test
        @DisplayName("Trailing comma throws JsonParseException")
        void trailingComma_throwsJsonParseException() {
            String malformedJson = """
                {
                    "_D": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
                    "Invoice": [{}],
                }
                """;

            assertThatThrownBy(() -> mapper.readValue(malformedJson, UBLInvoice21.class))
                    .isInstanceOf(JsonParseException.class);
        }
    }

    @Nested
    @DisplayName("Type Mismatch Tests")
    class TypeMismatchTest {

        @Test
        @DisplayName("String where array expected throws JsonMappingException")
        void stringWhereArrayExpected_throwsJsonMappingException() {
            String invalidJson = """
                {
                    "_D": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
                    "Invoice": "not-an-array"
                }
                """;

            assertThatThrownBy(() -> mapper.readValue(invalidJson, UBLInvoice21.class))
                    .isInstanceOf(JsonMappingException.class);
        }

        @Test
        @DisplayName("JsonMappingException contains field path information")
        void jsonMappingException_containsFieldPath() {
            String invalidJson = """
                {
                    "_D": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
                    "Invoice": "not-an-array"
                }
                """;

            assertThatThrownBy(() -> mapper.readValue(invalidJson, UBLInvoice21.class))
                    .isInstanceOf(JsonMappingException.class)
                    .satisfies(ex -> {
                        JsonMappingException jme = (JsonMappingException) ex;
                        assertThat(jme.getPath()).isNotEmpty();
                    });
        }

        @Test
        @DisplayName("Object where string expected throws JsonMappingException")
        void objectWhereStringExpected_throwsJsonMappingException() {
            String invalidJson = """
                {
                    "_D": {"nested": "object"},
                    "Invoice": [{}]
                }
                """;

            assertThatThrownBy(() -> mapper.readValue(invalidJson, UBLInvoice21.class))
                    .isInstanceOf(JsonMappingException.class);
        }

        @Test
        @DisplayName("Number where object expected throws JsonMappingException")
        void numberWhereObjectExpected_throwsJsonMappingException() {
            String invalidJson = """
                {
                    "_D": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
                    "Invoice": [12345]
                }
                """;

            assertThatThrownBy(() -> mapper.readValue(invalidJson, UBLInvoice21.class))
                    .isInstanceOf(JsonMappingException.class);
        }
    }

    @Nested
    @DisplayName("Missing Required Fields Tests")
    class MissingRequiredFieldsTest {

        @Test
        @DisplayName("Empty Invoice object can be deserialized (validation happens separately)")
        void emptyInvoiceObject_canBeDeserialized() throws Exception {
            // Note: Jackson deserialization does not enforce @NotNull annotations.
            // Validation of required fields happens via Bean Validation (JSR-380)
            // at a separate validation step, not during deserialization.
            String json = """
                {
                    "_D": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
                    "Invoice": [{}]
                }
                """;

            UBLInvoice21 result = mapper.readValue(json, UBLInvoice21.class);
            
            assertThat(result).isNotNull();
            assertThat(result.getInvoice()).hasSize(1);
            // Generated classes initialize List fields to empty ArrayList
            // Required field validation would catch missing data at validation time
            assertThat(result.getInvoice().get(0).getId()).isEmpty();
        }

        @Test
        @DisplayName("Partial Invoice with some fields can be deserialized")
        void partialInvoice_canBeDeserialized() throws Exception {
            String json = """
                {
                    "_D": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
                    "Invoice": [{
                        "ID": [{"_": "INV-001"}],
                        "IssueDate": [{"_": "2024-01-15"}]
                    }]
                }
                """;

            UBLInvoice21 result = mapper.readValue(json, UBLInvoice21.class);
            
            assertThat(result).isNotNull();
            assertThat(result.getInvoice()).hasSize(1);
            assertThat(result.getInvoice().get(0).getId().get(0).get__()).isEqualTo("INV-001");
            assertThat(result.getInvoice().get(0).getIssueDate().get(0).get__()).isEqualTo("2024-01-15");
            // Other required fields are empty lists - validation would catch this
            assertThat(result.getInvoice().get(0).getAccountingSupplierParty()).isEmpty();
        }
    }
}
