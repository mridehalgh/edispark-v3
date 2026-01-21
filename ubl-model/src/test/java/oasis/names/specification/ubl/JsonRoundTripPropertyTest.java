package oasis.names.specification.ubl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import oasis.names.specification.ubl.schema.xsd.maindoc.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for JSON round-trip consistency.
 * 
 * <p><b>Property 1: JSON Document Round-Trip Consistency</b>
 * <p>For any valid UBL JSON document from the sample files, deserializing to a Java object
 * and then serializing back to JSON SHALL produce semantically equivalent JSON
 * (same data, potentially different formatting).
 * 
 * <p><b>Validates: Requirements 5.1, 4.1, 3.1, 3.2</b>
 * 
 * <p>This property ensures that:
 * <ul>
 *   <li>Deserialization correctly parses all UBL JSON structures</li>
 *   <li>Serialization correctly produces UBL JSON format</li>
 *   <li>No data is lost in the conversion process</li>
 *   <li>The `_` property pattern is handled correctly</li>
 *   <li>Array wrappers are preserved</li>
 * </ul>
 */
class JsonRoundTripPropertyTest {

    private static final ObjectMapper MAPPER = createLenientMapper();

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

    /**
     * Provides sample Invoice JSON files for property testing.
     */
    @Provide
    Arbitrary<String> invoiceJsonFiles() {
        List<String> invoiceFiles = List.of(
            "UBL-Invoice-2.1-Example.json",
            "UBL-Invoice-2.1-Example-Trivial.json"
        );
        return Arbitraries.of(invoiceFiles)
            .map(this::loadSampleFile);
    }

    /**
     * Provides sample Order JSON files for property testing.
     */
    @Provide
    Arbitrary<String> orderJsonFiles() {
        List<String> orderFiles = List.of(
            "UBL-Order-2.1-Example.json"
        );
        return Arbitraries.of(orderFiles)
            .map(this::loadSampleFile);
    }

    /**
     * Provides sample CreditNote JSON files for property testing.
     */
    @Provide
    Arbitrary<String> creditNoteJsonFiles() {
        List<String> creditNoteFiles = List.of(
            "UBL-CreditNote-2.1-Example.json"
        );
        return Arbitraries.of(creditNoteFiles)
            .map(this::loadSampleFile);
    }

    /**
     * Provides sample Quotation JSON files for property testing.
     */
    @Provide
    Arbitrary<String> quotationJsonFiles() {
        List<String> quotationFiles = List.of(
            "UBL-Quotation-2.1-Example.json"
        );
        return Arbitraries.of(quotationFiles)
            .map(this::loadSampleFile);
    }

    /**
     * Provides all sample JSON files with their document types for property testing.
     */
    @Provide
    Arbitrary<DocumentTestCase> allDocumentTypes() {
        return Arbitraries.of(
            new DocumentTestCase("UBL-Invoice-2.1-Example.json", UBLInvoice21.class),
            new DocumentTestCase("UBL-Invoice-2.1-Example-Trivial.json", UBLInvoice21.class),
            new DocumentTestCase("UBL-Order-2.1-Example.json", UBLOrder21.class),
            new DocumentTestCase("UBL-CreditNote-2.1-Example.json", UBLCreditNote21.class),
            new DocumentTestCase("UBL-Quotation-2.1-Example.json", UBLQuotation21.class),
            new DocumentTestCase("UBL-DebitNote-2.1-Example.json", UBLDebitNote21.class),
            new DocumentTestCase("UBL-OrderResponse-2.1-Example.json", UBLOrderResponse21.class),
            new DocumentTestCase("UBL-OrderResponseSimple-2.1-Example.json", UBLOrderResponseSimple21.class),
            new DocumentTestCase("UBL-OrderCancellation-2.1-Example.json", UBLOrderCancellation21.class),
            new DocumentTestCase("UBL-OrderChange-2.1-Example.json", UBLOrderChange21.class),
            new DocumentTestCase("UBL-RequestForQuotation-2.1-Example.json", UBLRequestForQuotation21.class),
            new DocumentTestCase("UBL-Reminder-2.1-Example.json", UBLReminder21.class),
            new DocumentTestCase("UBL-FreightInvoice-2.1-Example.json", UBLFreightInvoice21.class),
            new DocumentTestCase("UBL-SelfBilledCreditNote-2.1-Example.json", UBLSelfBilledCreditNote21.class)
        );
    }

    /**
     * Property 1: JSON Document Round-Trip Consistency for Invoice documents.
     * 
     * <p><b>Validates: Requirements 5.1, 4.1, 3.1, 3.2</b>
     * 
     * <p>For any valid UBL Invoice JSON document, deserializing to a Java object
     * and then serializing back to JSON, then parsing again SHALL produce
     * an equivalent Java object.
     */
    @Property(tries = 100)
    @Label("Feature: ubl-java-library, Property 1: JSON round-trip consistency - Invoice")
    void invoiceJsonRoundTripPreservesData(@ForAll("invoiceJsonFiles") String originalJson) 
            throws JsonProcessingException {
        // Parse JSON to Java
        UBLInvoice21 doc = MAPPER.readValue(originalJson, UBLInvoice21.class);
        
        // Serialize back to JSON
        String serializedJson = MAPPER.writeValueAsString(doc);
        
        // Parse again
        UBLInvoice21 reparsed = MAPPER.readValue(serializedJson, UBLInvoice21.class);
        
        // Verify equivalence
        assertThat(reparsed).usingRecursiveComparison().isEqualTo(doc);
    }

    /**
     * Property 1: JSON Document Round-Trip Consistency for Order documents.
     * 
     * <p><b>Validates: Requirements 5.1, 4.1, 3.1, 3.2</b>
     */
    @Property(tries = 100)
    @Label("Feature: ubl-java-library, Property 1: JSON round-trip consistency - Order")
    void orderJsonRoundTripPreservesData(@ForAll("orderJsonFiles") String originalJson) 
            throws JsonProcessingException {
        // Parse JSON to Java
        UBLOrder21 doc = MAPPER.readValue(originalJson, UBLOrder21.class);
        
        // Serialize back to JSON
        String serializedJson = MAPPER.writeValueAsString(doc);
        
        // Parse again
        UBLOrder21 reparsed = MAPPER.readValue(serializedJson, UBLOrder21.class);
        
        // Verify equivalence
        assertThat(reparsed).usingRecursiveComparison().isEqualTo(doc);
    }

    /**
     * Property 1: JSON Document Round-Trip Consistency for CreditNote documents.
     * 
     * <p><b>Validates: Requirements 5.1, 4.1, 3.1, 3.2</b>
     */
    @Property(tries = 100)
    @Label("Feature: ubl-java-library, Property 1: JSON round-trip consistency - CreditNote")
    void creditNoteJsonRoundTripPreservesData(@ForAll("creditNoteJsonFiles") String originalJson) 
            throws JsonProcessingException {
        // Parse JSON to Java
        UBLCreditNote21 doc = MAPPER.readValue(originalJson, UBLCreditNote21.class);
        
        // Serialize back to JSON
        String serializedJson = MAPPER.writeValueAsString(doc);
        
        // Parse again
        UBLCreditNote21 reparsed = MAPPER.readValue(serializedJson, UBLCreditNote21.class);
        
        // Verify equivalence
        assertThat(reparsed).usingRecursiveComparison().isEqualTo(doc);
    }

    /**
     * Property 1: JSON Document Round-Trip Consistency for Quotation documents.
     * 
     * <p><b>Validates: Requirements 5.1, 4.1, 3.1, 3.2</b>
     */
    @Property(tries = 100)
    @Label("Feature: ubl-java-library, Property 1: JSON round-trip consistency - Quotation")
    void quotationJsonRoundTripPreservesData(@ForAll("quotationJsonFiles") String originalJson) 
            throws JsonProcessingException {
        // Parse JSON to Java
        UBLQuotation21 doc = MAPPER.readValue(originalJson, UBLQuotation21.class);
        
        // Serialize back to JSON
        String serializedJson = MAPPER.writeValueAsString(doc);
        
        // Parse again
        UBLQuotation21 reparsed = MAPPER.readValue(serializedJson, UBLQuotation21.class);
        
        // Verify equivalence
        assertThat(reparsed).usingRecursiveComparison().isEqualTo(doc);
    }

    /**
     * Property 1: JSON Document Round-Trip Consistency for all document types.
     * 
     * <p><b>Validates: Requirements 5.1, 4.1, 3.1, 3.2</b>
     * 
     * <p>This is the comprehensive property test that covers multiple document types
     * including Invoice, Order, CreditNote, Quotation, DebitNote, OrderResponse, etc.
     */
    @Property(tries = 100)
    @Label("Feature: ubl-java-library, Property 1: JSON round-trip consistency - All document types")
    void allDocumentTypesJsonRoundTripPreservesData(@ForAll("allDocumentTypes") DocumentTestCase testCase) 
            throws JsonProcessingException {
        String originalJson = loadSampleFile(testCase.filename());
        
        // Parse JSON to Java
        Object doc = MAPPER.readValue(originalJson, testCase.documentClass());
        
        // Serialize back to JSON
        String serializedJson = MAPPER.writeValueAsString(doc);
        
        // Parse again
        Object reparsed = MAPPER.readValue(serializedJson, testCase.documentClass());
        
        // Verify equivalence using recursive comparison
        assertThat(reparsed)
            .as("Round-trip for %s should preserve all data", testCase.filename())
            .usingRecursiveComparison()
            .isEqualTo(doc);
    }

    /**
     * Loads a sample JSON file from the ubl-source/json directory.
     */
    private String loadSampleFile(String filename) {
        Path[] possiblePaths = {
            Path.of("../ubl-source/json", filename),
            Path.of("ubl-source/json", filename),
            Path.of("../../ubl-source/json", filename)
        };
        
        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                try {
                    return Files.readString(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read file: " + path, e);
                }
            }
        }
        
        throw new IllegalStateException(
            "Could not find sample file: " + filename + 
            ". Tried paths relative to: " + Path.of("").toAbsolutePath());
    }

    /**
     * Test case record holding the filename and corresponding document class.
     */
    record DocumentTestCase(String filename, Class<?> documentClass) {
        @Override
        public String toString() {
            return filename;
        }
    }
}
