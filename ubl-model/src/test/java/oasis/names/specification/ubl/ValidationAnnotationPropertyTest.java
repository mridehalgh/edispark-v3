package oasis.names.specification.ubl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Property-based tests for validation annotations.
 * 
 * <p><b>Property 4: Required Field Validation Annotations</b>
 * <p>For any field marked as required in a UBL JSON schema, the corresponding
 * Java field SHALL have a {@code @NotNull} validation annotation.
 * 
 * <p><b>Validates: Requirements 2.7, 7.3</b>
 * 
 * <p>This property ensures that:
 * <ul>
 *   <li>Bean Validation can enforce required fields</li>
 *   <li>The mapping from schema {@code required} to Java annotations is correct</li>
 * </ul>
 */
class ValidationAnnotationPropertyTest {

    private static final String MAINDOC_PACKAGE = "oasis.names.specification.ubl.schema.xsd.maindoc";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    private static Map<String, SchemaInfo> schemaInfoMap;

    /**
     * Loads schema information for key document types before tests run.
     */
    @BeforeContainer
    static void loadSchemaInfo() throws IOException {
        schemaInfoMap = new HashMap<>();
        
        List<String> targetSchemas = List.of(
            "UBL-Invoice-2.1.json",
            "UBL-Order-2.1.json",
            "UBL-CreditNote-2.1.json"
        );
        
        Path maindocPath = findMaindocPath();
        
        for (String schemaFile : targetSchemas) {
            Path schemaPath = maindocPath.resolve(schemaFile);
            if (Files.exists(schemaPath)) {
                SchemaInfo info = parseSchemaInfo(schemaPath, schemaFile);
                schemaInfoMap.put(schemaFile, info);
            }
        }
        
        assertThat(schemaInfoMap)
            .as("Expected to load at least 3 schema files")
            .hasSizeGreaterThanOrEqualTo(3);
    }

    /**
     * Provides required field test cases for property testing.
     */
    @Provide
    Arbitrary<RequiredFieldTestCase> requiredFieldTestCases() {
        List<RequiredFieldTestCase> testCases = new ArrayList<>();
        
        for (SchemaInfo schemaInfo : schemaInfoMap.values()) {
            for (String requiredField : schemaInfo.requiredFields()) {
                testCases.add(new RequiredFieldTestCase(
                    schemaInfo.schemaFileName(),
                    schemaInfo.documentClassName(),
                    requiredField
                ));
            }
        }
        
        return Arbitraries.of(testCases);
    }

    /**
     * Property 4: Required Field Validation Annotations.
     * 
     * <p><b>Validates: Requirements 2.7, 7.3</b>
     * 
     * <p>For any field marked as required in a UBL JSON schema, the corresponding
     * Java field SHALL have a {@code @NotNull} validation annotation.
     */
    @Property(tries = 100)
    @Label("Feature: ubl-java-library, Property 4: Required fields have @NotNull annotation")
    void requiredFieldsHaveNotNullAnnotation(@ForAll("requiredFieldTestCases") RequiredFieldTestCase testCase) {
        Class<?> documentClass = loadDocumentClass(testCase.documentClassName());
        String javaFieldName = deriveJavaFieldName(testCase.requiredFieldName());
        
        Field field = findField(documentClass, javaFieldName, testCase.requiredFieldName());
        
        assertThat(field.isAnnotationPresent(NotNull.class))
            .as("Field '%s' (schema: '%s') in class %s should have @NotNull annotation",
                javaFieldName, testCase.requiredFieldName(), testCase.documentClassName())
            .isTrue();
    }

    /**
     * Parses schema information from a JSON schema file.
     */
    private static SchemaInfo parseSchemaInfo(Path schemaPath, String schemaFileName) throws IOException {
        String content = Files.readString(schemaPath);
        JsonNode root = JSON_MAPPER.readTree(content);
        
        String documentTypeName = extractDocumentTypeName(schemaFileName);
        String documentClassName = documentTypeName;
        
        List<String> requiredFields = extractRequiredFields(root, documentTypeName);
        
        return new SchemaInfo(schemaFileName, documentClassName, requiredFields);
    }

    /**
     * Extracts the document type name from the schema file name.
     */
    private static String extractDocumentTypeName(String schemaFileName) {
        // UBL-Invoice-2.1.json -> Invoice
        return schemaFileName
            .replace("UBL-", "")
            .replace("-2.1.json", "");
    }

    /**
     * Extracts required fields from the schema definitions.
     */
    private static List<String> extractRequiredFields(JsonNode root, String documentTypeName) {
        List<String> requiredFields = new ArrayList<>();
        
        JsonNode definitions = root.path("definitions");
        JsonNode documentDef = definitions.path(documentTypeName);
        
        if (documentDef.isMissingNode()) {
            return requiredFields;
        }
        
        JsonNode requiredArray = documentDef.path("required");
        if (requiredArray.isArray()) {
            for (JsonNode fieldNode : requiredArray) {
                requiredFields.add(fieldNode.asText());
            }
        }
        
        return requiredFields;
    }

    /**
     * Derives the Java field name from the JSON schema property name.
     */
    private String deriveJavaFieldName(String schemaFieldName) {
        if (schemaFieldName.isEmpty()) {
            return schemaFieldName;
        }
        // JSON property names like "ID" become "id", "IssueDate" becomes "issueDate"
        // Handle special cases like "ID" which should become "id"
        if (schemaFieldName.equals("ID")) {
            return "id";
        }
        if (schemaFieldName.equals("UUID")) {
            return "uuid";
        }
        // Standard camelCase conversion: first letter lowercase
        return Character.toLowerCase(schemaFieldName.charAt(0)) + schemaFieldName.substring(1);
    }

    /**
     * Loads the document class by name.
     */
    private Class<?> loadDocumentClass(String className) {
        String fullyQualifiedName = MAINDOC_PACKAGE + "." + className;
        try {
            return Class.forName(fullyQualifiedName);
        } catch (ClassNotFoundException e) {
            fail("Document class not found: %s", fullyQualifiedName);
            return null; // Never reached
        }
    }

    /**
     * Finds a field in the class, checking both derived name and original name.
     */
    private Field findField(Class<?> clazz, String javaFieldName, String schemaFieldName) {
        // Try the derived Java field name first
        try {
            return clazz.getDeclaredField(javaFieldName);
        } catch (NoSuchFieldException e) {
            // Try the original schema field name
            try {
                return clazz.getDeclaredField(schemaFieldName);
            } catch (NoSuchFieldException e2) {
                // Try lowercase version
                try {
                    return clazz.getDeclaredField(schemaFieldName.toLowerCase());
                } catch (NoSuchFieldException e3) {
                    fail("Field not found in class %s. Tried: '%s', '%s', '%s'",
                        clazz.getSimpleName(), javaFieldName, schemaFieldName, 
                        schemaFieldName.toLowerCase());
                    return null; // Never reached
                }
            }
        }
    }

    /**
     * Finds the maindoc directory path.
     */
    private static Path findMaindocPath() {
        Path[] possiblePaths = {
            Path.of("../ubl-source/json-schema/maindoc"),
            Path.of("ubl-source/json-schema/maindoc"),
            Path.of("../../ubl-source/json-schema/maindoc")
        };
        
        for (Path path : possiblePaths) {
            if (Files.exists(path) && Files.isDirectory(path)) {
                return path;
            }
        }
        
        throw new IllegalStateException(
            "Could not find maindoc directory. Tried paths relative to: " + 
            Path.of("").toAbsolutePath());
    }

    /**
     * Record holding schema information.
     */
    record SchemaInfo(
        String schemaFileName,
        String documentClassName,
        List<String> requiredFields
    ) {}

    /**
     * Test case record for required field validation.
     */
    record RequiredFieldTestCase(
        String schemaFileName,
        String documentClassName,
        String requiredFieldName
    ) {
        @Override
        public String toString() {
            return String.format("%s.%s (from %s)", 
                documentClassName, requiredFieldName, schemaFileName);
        }
    }
}
