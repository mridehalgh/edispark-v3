package com.edispark.architecture;

import com.edispark.architecture.fixtures.tenancy.application.BusinessFixtures;
import com.edispark.architecture.fixtures.tenancy.domain.repository.PortFixtures;
import com.edispark.architecture.fixtures.tenancy.infrastructure.TrustedIngress;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenancyGuardrailTest {
    @Test
    void businessCodeCannotSelectOrRebindContext() {
        assertThat(violations(Map.of(), BusinessFixtures.ReadsContext.class,
                BusinessFixtures.RebindsContext.class))
                .anyMatch(value -> value.startsWith("CONTEXT_ACCESS\t") && value.contains("tenant()"))
                .anyMatch(value -> value.startsWith("CONTEXT_ACCESS\t") && value.contains("process()"));
    }

    @Test
    void businessCodeCannotUseRawStorageOrConcreteInfrastructure() {
        assertThat(violations(Map.of(), BusinessFixtures.UsesRawStorage.class,
                BusinessFixtures.UsesConcretePersistence.class))
                .anyMatch(value -> value.startsWith("RAW_STORAGE\t"))
                .anyMatch(value -> value.startsWith("INFRASTRUCTURE_DEPENDENCY\t"));
    }

    @Test
    void selectorsAreDetectedWithoutDependingOnParameterNames() {
        String stringSelector = signature(PortFixtures.StringRepository.class, "find(java.lang.String, java.util.UUID)");
        assertThat(violations(Map.of(stringSelector, "tenant"), PortFixtures.TypedRepository.class,
                PortFixtures.StringRepository.class))
                .hasSize(2).allMatch(value -> value.startsWith("TENANT_SELECTOR\t"));
    }

    @Test
    void newStringPortInputsNeedExplicitClassification() {
        assertThat(violations(Map.of(), PortFixtures.UnclassifiedRepository.class))
                .singleElement().asString().startsWith("UNCLASSIFIED_STRING_PORT\t");
    }

    @Test
    void newServicesCannotReuseAnExistingUnscopedPort() {
        String selector = signature(PortFixtures.StringRepository.class, "find(java.lang.String, java.util.UUID)");
        assertThat(violations(Map.of(selector, "tenant"), BusinessFixtures.CallsStringSelector.class))
                .singleElement().asString().startsWith("TENANT_SELECTOR_CALL\t");
    }

    @Test
    void methodReferencesToTypedSelectorsCannotBypassCallSiteChecks() {
        assertThat(violations(Map.of(), BusinessFixtures.ReferencesTypedSelector.class))
                .singleElement().asString().startsWith("TENANT_SELECTOR_CALL\t");
    }

    @Test
    void privilegedPortDeclarationsAndBusinessDependenciesAreRejected() {
        String global = signature(PortFixtures.GlobalRepository.class,
                "findInboundPartner(java.lang.String, java.lang.String)");
        assertThat(violations(Map.of(global, "global"), PortFixtures.GlobalRepository.class,
                BusinessFixtures.CallsGlobalDiscovery.class))
                .anyMatch(value -> value.startsWith("PRIVILEGED_PORT\t"))
                .anyMatch(value -> value.startsWith("GLOBAL_DISCOVERY_DEPENDENCY\t"));
    }

    @Test
    void scopedServicesOwnershipEventsTrustedIngressAndBusinessKeysRemainLegal() {
        String businessKey = signature(PortFixtures.BusinessKeyRepository.class, "find(java.lang.String)");
        assertThat(violations(Map.of(businessKey, "business"), BusinessFixtures.ScopedBusinessService.class,
                PortFixtures.ScopedRepository.class, PortFixtures.OwnedEvent.class,
                PortFixtures.BusinessKeyRepository.class, TrustedIngress.class)).isEmpty();
    }

    @Test
    void baselineAllowsOnlyExactExistingViolationsAndReportsResolvedEntries() {
        var entry = new TenancyBaseline.Entry("CONTEXT_ACCESS\tExisting.method()", "MT 01", "Migrate scoped port");
        TenancyBaseline.verify(Set.of(entry.key()), List.of(entry));

        assertThatThrownBy(() -> TenancyBaseline.verify(Set.of(entry.key(), "CONTEXT_ACCESS\tNew.method()"), List.of(entry)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("New violations").hasMessageContaining("New.method()");
        assertThatThrownBy(() -> TenancyBaseline.verify(Set.of(), List.of(entry)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Resolved baseline entries");
    }

    @Test
    void baselineRejectsDuplicateEntriesAndMissingProvenance() {
        var entry = new TenancyBaseline.Entry("RULE\tMember", "MT 01", "Migrate scoped port");
        assertThatThrownBy(() -> TenancyBaseline.verify(Set.of(entry.key()), List.of(entry, entry)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> new TenancyBaseline.Entry("RULE\tMember", "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Set<String> violations(Map<String, String> classifications, Class<?>... types) {
        JavaClasses classes = new ClassFileImporter().importClasses(types);
        return TenancyRules.violations(classes, classifications);
    }

    private String signature(Class<?> owner, String method) {
        return owner.getName() + "." + method;
    }
}
