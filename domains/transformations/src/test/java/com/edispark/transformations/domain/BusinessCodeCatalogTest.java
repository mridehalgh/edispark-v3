package com.edispark.transformations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BusinessCodeCatalogTest {
    private final BusinessCodeCatalog catalog = new BusinessCodeCatalog();

    @Test
    void providesStandardsBackedCodesNeededByBusinessDocumentsAndEdiMappings() {
        assertThat(catalog.get(BusinessCodeList.UNIT_OF_MEASURE).values())
                .extracting(BusinessCode::code)
                .contains("EA", "C62", "KGM", "GRM", "TNE", "LTR", "MTR", "MTQ");
        assertThat(catalog.get(BusinessCodeList.CURRENCY).values())
                .extracting(BusinessCode::code)
                .contains("GBP", "EUR", "USD");
        assertThat(catalog.get(BusinessCodeList.PAYMENT_MEANS).values())
                .extracting(BusinessCode::code)
                .contains("30", "31", "42", "48", "49", "58", "59");
        assertThat(catalog.get(BusinessCodeList.TAX_CATEGORY).values())
                .extracting(BusinessCode::code)
                .contains("S", "Z", "E", "AE", "G", "O", "K");
    }

    @Test
    void everyCodeListHasUniqueCodesAndDocumentsItsAuthority() {
        assertThat(catalog.list()).allSatisfy(definition -> {
            assertThat(definition.authority()).isNotBlank();
            assertThat(definition.values()).isNotEmpty();
            assertThat(definition.values()).extracting(BusinessCode::code).doesNotHaveDuplicates();
        });
    }

    @Test
    void exposesExplicitUblEdifactAndTradacomsRepresentations() {
        BusinessCode kilograms = catalog.get(BusinessCodeList.UNIT_OF_MEASURE).values().stream()
                .filter(value -> value.code().equals("KGM"))
                .findFirst().orElseThrow();

        assertThat(kilograms.representations())
                .containsEntry(BusinessCodeSyntax.UBL_2_1, "KGM")
                .containsEntry(BusinessCodeSyntax.EDIFACT_D96A, "KGM")
                .containsEntry(BusinessCodeSyntax.TRADACOMS_9, "KGM");
        assertThat(catalog.translationMap(BusinessCodeList.UNIT_OF_MEASURE,
                BusinessCodeSyntax.UBL_2_1, BusinessCodeSyntax.EDIFACT_D96A))
                .containsEntry("EA", "EA")
                .containsEntry("KGM", "KGM");
    }

    @Test
    void omitsModernCodesThatAreNotDefinedByEdifactD96a() {
        BusinessCode sepaCreditTransfer = catalog.get(BusinessCodeList.PAYMENT_MEANS).values().stream()
                .filter(value -> value.code().equals("58"))
                .findFirst().orElseThrow();

        assertThat(sepaCreditTransfer.representations())
                .containsEntry(BusinessCodeSyntax.UBL_2_1, "58")
                .doesNotContainKey(BusinessCodeSyntax.EDIFACT_D96A);
        assertThat(catalog.translationMap(BusinessCodeList.PAYMENT_MEANS,
                BusinessCodeSyntax.UBL_2_1, BusinessCodeSyntax.EDIFACT_D96A))
                .doesNotContainKey("58");
    }

    @Test
    void rejectsAmbiguousRepresentationsWithinOneSyntax() {
        assertThatThrownBy(() -> new BusinessCodeCatalog(java.util.List.of(
                new BusinessCodeListDefinition(BusinessCodeList.CURRENCY, "test", "test", true,
                        java.util.List.of(
                                new BusinessCode("GBP", "Pound", java.util.Map.of(
                                        BusinessCodeSyntax.UBL_2_1, "GBP",
                                        BusinessCodeSyntax.EDIFACT_D96A, "GBP")),
                                new BusinessCode("GBX", "Pence", java.util.Map.of(
                                        BusinessCodeSyntax.UBL_2_1, "GBX",
                                        BusinessCodeSyntax.EDIFACT_D96A, "GBP")))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EDIFACT_D96A representations must be unique");
    }
}
