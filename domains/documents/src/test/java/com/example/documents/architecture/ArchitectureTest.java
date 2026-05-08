package com.example.documents.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Architecture tests enforcing hexagonal/DDD design patterns.
 * 
 * <p>These tests ensure the codebase follows the established architectural rules:
 * <ul>
 *   <li>Domain layer has no dependencies on outer layers</li>
 *   <li>Application layer depends only on domain</li>
 *   <li>Infrastructure implements domain ports</li>
 *   <li>API layer handles HTTP concerns only</li>
 * </ul>
 */
@DisplayName("Architecture Rules")
class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.example.documents";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Nested
    @DisplayName("Layered Architecture")
    class LayeredArchitectureTests {

        @Test
        @DisplayName("should follow hexagonal architecture layers")
        void shouldFollowHexagonalArchitecture() {
            ArchRule rule = layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain").definedBy(BASE_PACKAGE + ".domain..")
                    .layer("Application").definedBy(BASE_PACKAGE + ".application..")
                    .layer("Infrastructure").definedBy(BASE_PACKAGE + ".infrastructure..")
                    .layer("API").definedBy(BASE_PACKAGE + ".api..")
                    .whereLayer("Domain").mayNotAccessAnyLayer()
                    .whereLayer("Application").mayOnlyAccessLayers("Domain")
                    .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Application")
                    .whereLayer("API").mayOnlyAccessLayers("Domain", "Application")
                    .allowEmptyShould(true);

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Domain Layer Rules")
    class DomainLayerTests {

        @Test
        @DisplayName("domain model should not depend on Spring")
        void domainModelShouldNotDependOnSpring() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE_PACKAGE + ".domain.model..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("domain model should not depend on infrastructure")
        void domainModelShouldNotDependOnInfrastructure() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(BASE_PACKAGE + ".infrastructure..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("domain model should not depend on API layer")
        void domainModelShouldNotDependOnApi() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(BASE_PACKAGE + ".api..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("domain model should not depend on AWS SDK")
        void domainModelShouldNotDependOnAwsSdk() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE_PACKAGE + ".domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("software.amazon.awssdk..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("domain events should reside in domain.event package")
        void domainEventsShouldResideInEventPackage() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("Event")
                    .or().implement("com.example.documents.domain.event.DomainEvent")
                    .should().resideInAPackage(BASE_PACKAGE + ".domain.event..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("repository interfaces should reside in domain.repository package")
        void repositoryInterfacesShouldResideInRepositoryPackage() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("Repository")
                    .and().areInterfaces()
                    .should().resideInAPackage(BASE_PACKAGE + ".domain.repository..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Application Layer Rules")
    class ApplicationLayerTests {

        @Test
        @DisplayName("application layer should not depend on infrastructure")
        void applicationShouldNotDependOnInfrastructure() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE_PACKAGE + ".application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(BASE_PACKAGE + ".infrastructure..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("application layer should not depend on API layer")
        void applicationShouldNotDependOnApi() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE_PACKAGE + ".application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(BASE_PACKAGE + ".api..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("command handlers should reside in application.handler package")
        void commandHandlersShouldResideInHandlerPackage() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("CommandHandler")
                    .should().resideInAPackage(BASE_PACKAGE + ".application.handler..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("commands should reside in application.command package")
        void commandsShouldResideInCommandPackage() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("Command")
                    .should().resideInAPackage(BASE_PACKAGE + ".application.command..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Infrastructure Layer Rules")
    class InfrastructureLayerTests {

        @Test
        @DisplayName("DynamoDB repositories should reside in infrastructure.persistence")
        void dynamoDbRepositoriesShouldResideInPersistence() {
            ArchRule rule = classes()
                    .that().haveSimpleNameStartingWith("DynamoDb")
                    .and().haveSimpleNameEndingWith("Repository")
                    .should().resideInAPackage(BASE_PACKAGE + ".infrastructure.persistence..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("infrastructure config should reside in infrastructure.config")
        void configShouldResideInConfigPackage() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("Config")
                    .and().areAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                    .should().resideInAPackage(BASE_PACKAGE + ".infrastructure.config..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("API Layer Rules")
    class ApiLayerTests {

        @Test
        @DisplayName("controllers should reside in api.rest package")
        void controllersShouldResideInRestPackage() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("Controller")
                    .should().resideInAPackage(BASE_PACKAGE + ".api.rest..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("DTOs should reside in api.dto package")
        void dtosShouldResideInDtoPackage() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("Request")
                    .or().haveSimpleNameEndingWith("Response")
                    .and().resideInAPackage(BASE_PACKAGE + ".api..")
                    .should().resideInAPackage(BASE_PACKAGE + ".api.dto..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("exception handlers should reside in api.rest package")
        void exceptionHandlersShouldResideInRestPackage() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("ExceptionHandler")
                    .and().resideInAPackage(BASE_PACKAGE + ".api..")
                    .should().resideInAPackage(BASE_PACKAGE + ".api.rest..")
                    .allowEmptyShould(true);

            rule.check(classes);
        }
    }

    @Nested
    @DisplayName("Naming Conventions")
    class NamingConventionTests {

        @Test
        @DisplayName("exceptions should have Exception suffix")
        void exceptionsShouldHaveExceptionSuffix() {
            ArchRule rule = classes()
                    .that().areAssignableTo(Exception.class)
                    .should().haveSimpleNameEndingWith("Exception")
                    .allowEmptyShould(true);

            rule.check(classes);
        }

        @Test
        @DisplayName("interfaces should not have I prefix")
        void interfacesShouldNotHaveIPrefix() {
            ArchRule rule = noClasses()
                    .that().areInterfaces()
                    .should().haveSimpleNameStartingWith("I")
                    .allowEmptyShould(true);

            rule.check(classes);
        }
    }
}
