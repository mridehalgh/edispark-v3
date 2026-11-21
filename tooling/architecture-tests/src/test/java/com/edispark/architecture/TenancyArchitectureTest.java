package com.edispark.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenancyArchitectureTest {
    private static final Map<String, String> DOMAINS = Map.of(
            "documents", "com.edispark.documents",
            "mft", "com.edispark.mft",
            "partnerships", "com.edispark.partnerships",
            "transformations", "com.edispark.transformations",
            "exchange", "com.edispark.exchange");

    @Test
    void businessCodeMustNotIntroduceNewTenancyResponsibilities() throws IOException {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(DOMAINS.values().toArray(String[]::new));
        assertEveryDomainIsPresent(classes);
        var classifications = TenancyBaseline.classifications();
        Set<String> methods = classes.stream().flatMap(type -> type.getMethods().stream())
                .map(method -> method.getFullName()).collect(Collectors.toSet());
        assertThat(methods).as("classified port signatures must still exist").containsAll(classifications.keySet());
        TenancyBaseline.verify(TenancyRules.violations(classes, classifications), TenancyBaseline.load());
    }

    private void assertEveryDomainIsPresent(JavaClasses classes) throws IOException {
        Path directory = Path.of(System.getProperty("user.dir"), "../../domains").normalize();
        try (var children = Files.list(directory)) {
            var modules = children.filter(path -> Files.isRegularFile(path.resolve("pom.xml")))
                    .map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertThat(DOMAINS.keySet()).as("all domain modules must participate").containsExactlyInAnyOrderElementsOf(modules);
        }
        DOMAINS.values().forEach(prefix -> {
            assertThat(classes.stream().filter(type -> type.getPackageName().startsWith(prefix + ".domain")).count())
                    .as("domain classes imported for %s", prefix).isPositive();
            assertThat(classes.stream().filter(type -> type.getPackageName().startsWith(prefix + ".application")).count())
                    .as("application classes imported for %s", prefix).isPositive();
        });
    }
}
