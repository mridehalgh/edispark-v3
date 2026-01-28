package com.example.documents.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to enforce test quality standards.
 * 
 * <p>These rules ensure:
 * <ul>
 *   <li>No tests are disabled without proper justification</li>
 *   <li>Test classes follow naming conventions</li>
 *   <li>Tests are properly organised</li>
 * </ul>
 * 
 * <p>Note: Uses reflection-based scanning due to ArchUnit compatibility issues with Java 25.</p>
 */
@DisplayName("Test Quality Rules")
class TestQualityTest {

    private static final String BASE_PACKAGE = "com.example.documents";
    private static List<Class<?>> testClasses;

    @BeforeAll
    static void loadTestClasses() throws IOException, ClassNotFoundException {
        // Find the test-classes directory
        String classResourcePath = TestQualityTest.class.getName().replace('.', '/') + ".class";
        java.net.URL classUrl = TestQualityTest.class.getClassLoader().getResource(classResourcePath);
        assertThat(classUrl).as("Should find TestQualityTest.class on classpath").isNotNull();
        
        String urlPath = classUrl.getPath();
        String testClassesRoot = urlPath.substring(0, urlPath.indexOf(classResourcePath));
        Path rootPath = Paths.get(testClassesRoot);
        
        // Find all test class files
        testClasses = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(rootPath)) {
            List<Path> classFiles = walk
                    .filter(p -> p.toString().endsWith(".class"))
                    .filter(p -> !p.toString().contains("$")) // Skip inner classes for now
                    .toList();
            
            for (Path classFile : classFiles) {
                String relativePath = rootPath.relativize(classFile).toString();
                String className = relativePath
                        .replace(".class", "")
                        .replace('/', '.');
                
                if (className.startsWith(BASE_PACKAGE) && isTestClassName(className)) {
                    try {
                        Class<?> clazz = Class.forName(className);
                        testClasses.add(clazz);
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        // Skip classes that can't be loaded
                    }
                }
            }
        }
        
        assertThat(testClasses)
                .as("Should find test classes in package %s", BASE_PACKAGE)
                .isNotEmpty();
    }

    @Test
    @DisplayName("no test classes should be disabled with @Disabled")
    void noTestClassesShouldBeDisabled() {
        List<String> disabledClasses = testClasses.stream()
                .filter(c -> c.isAnnotationPresent(Disabled.class))
                .map(Class::getName)
                .toList();
        
        assertThat(disabledClasses)
                .as("Disabled tests indicate incomplete work or technical debt that should be addressed")
                .isEmpty();
    }

    @Test
    @DisplayName("no test methods should be disabled with @Disabled")
    void noTestMethodsShouldBeDisabled() {
        List<String> disabledMethods = new ArrayList<>();
        
        for (Class<?> testClass : testClasses) {
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Disabled.class)) {
                    disabledMethods.add(testClass.getName() + "#" + method.getName());
                }
            }
        }
        
        assertThat(disabledMethods)
                .as("Disabled tests indicate incomplete work or technical debt that should be addressed")
                .isEmpty();
    }

    @Test
    @DisplayName("no test classes should use JUnit 4 @Ignore annotation")
    void noTestClassesShouldUseJUnit4Ignore() {
        List<String> ignoredClasses = testClasses.stream()
                .filter(c -> c.isAnnotationPresent(org.junit.Ignore.class))
                .map(Class::getName)
                .toList();
        
        assertThat(ignoredClasses)
                .as("JUnit 4 @Ignore should not be used; migrate to JUnit 5 or remove the test")
                .isEmpty();
    }

    @Test
    @DisplayName("no test methods should use JUnit 4 @Ignore annotation")
    void noTestMethodsShouldUseJUnit4Ignore() {
        List<String> ignoredMethods = new ArrayList<>();
        
        for (Class<?> testClass : testClasses) {
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(org.junit.Ignore.class)) {
                    ignoredMethods.add(testClass.getName() + "#" + method.getName());
                }
            }
        }
        
        assertThat(ignoredMethods)
                .as("JUnit 4 @Ignore should not be used; migrate to JUnit 5 or remove the test")
                .isEmpty();
    }

    @Test
    @DisplayName("test classes should have Test suffix")
    void testClassesShouldHaveTestSuffix() {
        List<String> badlyNamedClasses = testClasses.stream()
                .filter(c -> hasTestMethods(c))
                .filter(c -> !isTestClassName(c.getSimpleName()))
                .map(Class::getName)
                .toList();
        
        assertThat(badlyNamedClasses)
                .as("Test classes should follow naming convention (ending with Test, Tests, or PropertyTest)")
                .isEmpty();
    }

    private static boolean isTestClassName(String name) {
        String simpleName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        return simpleName.endsWith("Test") 
                || simpleName.endsWith("Tests") 
                || simpleName.endsWith("PropertyTest");
    }

    private static boolean hasTestMethods(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class) 
                    || method.isAnnotationPresent(org.junit.Test.class)
                    || method.isAnnotationPresent(net.jqwik.api.Property.class)) {
                return true;
            }
        }
        return false;
    }
}
