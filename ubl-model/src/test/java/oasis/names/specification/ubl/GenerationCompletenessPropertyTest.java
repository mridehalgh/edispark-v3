package oasis.names.specification.ubl;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Property-based tests for generation completeness.
 * 
 * <p><b>Property 3: Generation Completeness</b>
 * <p>For any JSON schema file in the {@code ubl-source/json-schema/maindoc/} directory,
 * the build process SHALL generate a corresponding Java class that can be instantiated.
 * 
 * <p><b>Validates: Requirements 2.1, 1.1, 6.1</b>
 * 
 * <p>This property ensures that:
 * <ul>
 *   <li>All 65+ UBL document types have generated classes</li>
 *   <li>The generation process doesn't silently skip schemas</li>
 *   <li>Generated classes are valid Java (compile successfully)</li>
 * </ul>
 */
class GenerationCompletenessPropertyTest {

    private static final String MAINDOC_PACKAGE = "oasis.names.specification.ubl.schema.xsd.maindoc";
    private static List<String> schemaFiles;

    /**
     * Loads all schema file names from the maindoc directory before tests run.
     */
    @BeforeContainer
    static void loadSchemaFiles() throws IOException {
        Path maindocPath = findMaindocPath();
        try (Stream<Path> files = Files.list(maindocPath)) {
            schemaFiles = files
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();
        }
        
        // Sanity check - we expect at least 65 document types
        assertThat(schemaFiles)
            .as("Expected at least 65 UBL document type schemas")
            .hasSizeGreaterThanOrEqualTo(65);
    }

    /**
     * Provides all schema file names for property testing.
     */
    @Provide
    Arbitrary<String> schemaFileNames() {
        return Arbitraries.of(schemaFiles);
    }

    /**
     * Property 3: Generation Completeness - Corresponding Java class exists.
     * 
     * <p><b>Validates: Requirements 2.1, 1.1, 6.1</b>
     * 
     * <p>For any JSON schema file in maindoc/, a corresponding Java wrapper class
     * SHALL exist in the generated sources.
     */
    @Property(tries = 100)
    @Label("Feature: ubl-java-library, Property 3: Generation Completeness - Class exists")
    void schemaFileHasCorrespondingJavaClass(@ForAll("schemaFileNames") String schemaFileName) {
        String expectedClassName = deriveClassName(schemaFileName);
        String fullyQualifiedName = MAINDOC_PACKAGE + "." + expectedClassName;
        
        try {
            Class<?> clazz = Class.forName(fullyQualifiedName);
            assertThat(clazz)
                .as("Class %s should exist for schema %s", expectedClassName, schemaFileName)
                .isNotNull();
        } catch (ClassNotFoundException e) {
            fail("Expected class %s not found for schema file %s. " +
                 "The code generation may have skipped this schema.", 
                 fullyQualifiedName, schemaFileName);
        }
    }

    /**
     * Property 3: Generation Completeness - Class can be instantiated.
     * 
     * <p><b>Validates: Requirements 2.1, 1.1, 6.1</b>
     * 
     * <p>For any JSON schema file in maindoc/, the corresponding Java wrapper class
     * SHALL be instantiable via its no-arg constructor.
     */
    @Property(tries = 100)
    @Label("Feature: ubl-java-library, Property 3: Generation Completeness - Class instantiable")
    void schemaClassCanBeInstantiated(@ForAll("schemaFileNames") String schemaFileName) {
        String expectedClassName = deriveClassName(schemaFileName);
        String fullyQualifiedName = MAINDOC_PACKAGE + "." + expectedClassName;
        
        try {
            Class<?> clazz = Class.forName(fullyQualifiedName);
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            
            assertThat(instance)
                .as("Instance of %s should be created successfully", expectedClassName)
                .isNotNull();
        } catch (ClassNotFoundException e) {
            fail("Class %s not found for schema %s", fullyQualifiedName, schemaFileName);
        } catch (NoSuchMethodException e) {
            fail("No-arg constructor not found for class %s (schema: %s)", 
                 fullyQualifiedName, schemaFileName);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            fail("Failed to instantiate class %s (schema: %s): %s", 
                 fullyQualifiedName, schemaFileName, e.getMessage());
        }
    }

    /**
     * Derives the expected Java class name from a schema file name.
     * 
     * <p>Transformation rules:
     * <ul>
     *   <li>Remove "UBL-" prefix</li>
     *   <li>Remove "-2.1.json" suffix</li>
     *   <li>Prepend "UBL" and append "21"</li>
     *   <li>Remove hyphens (already PascalCase in schema names)</li>
     * </ul>
     * 
     * <p>Examples:
     * <ul>
     *   <li>{@code UBL-Invoice-2.1.json} → {@code UBLInvoice21}</li>
     *   <li>{@code UBL-CreditNote-2.1.json} → {@code UBLCreditNote21}</li>
     *   <li>{@code UBL-OrderResponse-2.1.json} → {@code UBLOrderResponse21}</li>
     * </ul>
     * 
     * @param schemaFileName the JSON schema file name
     * @return the expected Java class name
     */
    private String deriveClassName(String schemaFileName) {
        // UBL-Invoice-2.1.json -> Invoice
        String baseName = schemaFileName
            .replace("UBL-", "")
            .replace("-2.1.json", "");
        
        // Invoice -> UBLInvoice21
        return "UBL" + baseName + "21";
    }

    /**
     * Finds the maindoc directory path, trying multiple relative paths.
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
}
