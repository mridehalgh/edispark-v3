package com.edispark.architecture;

import com.edispark.pagination.NewestFirstPagination;
import com.edispark.pagination.PaginatedResult;
import com.edispark.pagination.api.PaginatedResponse;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cross-domain pagination architecture")
class PaginationArchitectureTest {

    private static final Map<String, String> DOMAIN_MODULES = Map.of(
            "documents", "com.edispark.documents",
            "mft", "com.edispark.mft",
            "partnerships", "com.edispark.partnerships",
            "transformations", "com.edispark.transformations",
            "exchange", "com.edispark.exchange");

    private static JavaClasses domainClasses;

    @BeforeAll
    static void importEveryDomain() {
        domainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(DOMAIN_MODULES.values().toArray(String[]::new));
    }

    @Test
    @DisplayName("paginated domain methods must declare newest-first ordering")
    void paginatedDomainMethodsMustDeclareNewestFirstOrdering() {
        ArchRule newestFirstPagination = methods()
                .that(returnSharedPaginationType())
                .should().beAnnotatedWith(NewestFirstPagination.class)
                .because("all user-facing pagination must be deterministic and ordered from most recent to oldest");

        newestFirstPagination.check(domainClasses);
    }

    @Test
    @DisplayName("every domain module must participate in cross-domain architecture checks")
    void everyDomainModuleMustParticipateInArchitectureChecks() throws IOException {
        Path domainsDirectory = Path.of(System.getProperty("user.dir"), "../../domains").normalize();
        Set<String> domainDirectories;
        try (var directories = Files.list(domainsDirectory)) {
            domainDirectories = directories
                    .filter(path -> Files.isRegularFile(path.resolve("pom.xml")))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toUnmodifiableSet());
        }

        assertThat(DOMAIN_MODULES.keySet())
                .as("domain modules included in the cross-domain architecture suite")
                .containsExactlyInAnyOrderElementsOf(domainDirectories);
    }

    private static DescribedPredicate<JavaMethod> returnSharedPaginationType() {
        return DescribedPredicate.describe(
                "return the shared paginated result or response type",
                method -> method.getReturnType().getAllInvolvedRawTypes().stream()
                        .map(type -> type.getName())
                        .anyMatch(type -> type.equals(PaginatedResult.class.getName())
                                || type.equals(PaginatedResponse.class.getName())));
    }
}
