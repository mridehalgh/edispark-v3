package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.documents.infrastructure.validation.JsonSchemaDocumentValidator;
import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import com.edispark.integrationapplication.mapping.*;
import com.edispark.transformations.application.*;
import com.edispark.transformations.infrastructure.ClasspathGlobalMappingCatalog;
import com.edispark.transformations.infrastructure.TescoUkMappings;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cottn.core.tenancy.TenantExecution;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;

class MigPreviewTest {
    @Test
    void validatesInboundTescoOrderAgainstIndependentHeldOutUblAndRepeats() {
        var result = preview(new MigPreview.Fixture(bytes(inbound()), bytes(expected()), false, false));

        assertThat(result.findings()).isEmpty();
        assertThat(result.expectedMatch()).isTrue();
        assertThat(result.generatedChecks()).isNotEmpty().allMatch(GeneratedCase::passed);
    }

    @Test
    void checksAnUnpairedExpectedUblAgainstTheSchemaEvenWhenItsValuesDifferFromTheCatalogueInput() {
        var reference = expected().replace("PO-SYNTH-1", "DIFFERENT-ORDER");
        var result = preview(new MigPreview.Fixture(bytes(inbound()), bytes(reference), true, true));

        assertThat(result.check()).isEqualTo("CATALOGUE_STRUCTURE_WITH_REFERENCE");
        assertThat(result.findings()).isEmpty();
        assertThat(result.expectedMatch()).isNull();
        assertThat(result.generatedChecks()).isNotEmpty().allMatch(GeneratedCase::passed);
    }

    @Test
    void reportsSchemaErrorsInAStandaloneReferenceEvenWithoutACompatibleCatalogueInput() {
        var result = preview(new MigPreview.Fixture(null, bytes("{}"), true, true));

        assertThat(result.expectedMatch()).isNull();
        assertThat(result.referenceFindings()).anyMatch(finding -> finding.code().startsWith("REFERENCE_"));
        assertThat(result.findings()).extracting(MigFinding::code).containsExactly("PREVIEW_UNAVAILABLE");
    }

    @Test
    void keepsPartialReferenceFindingsSeparateFromAValidPreviewWithAnUploadedInput() {
        var partial = bytes("{\"Order\":[{\"ID\":[{\"_\":\"REFERENCE-ONLY\"}]}]}");
        var result = preview(new MigPreview.Fixture(bytes(inbound()), partial, false, true));

        assertThat(result.check()).isEqualTo("UPLOADED_STRUCTURE_WITH_REFERENCE");
        assertThat(result.expectedMatch()).isNull();
        assertThat(result.findings()).isEmpty();
        assertThat(result.referenceFindings()).isNotEmpty();
    }

    @Test
    void validatesTheMappedOutputWithoutAnExpectedFile() {
        var result = preview(new MigPreview.Fixture(bytes(inbound()), null, true, false));

        assertThat(result.check()).isEqualTo("CATALOGUE_STRUCTURE");
        assertThat(result.findings()).isEmpty();
        assertThat(result.expectedMatch()).isNull();
    }

    private FixtureResult preview(MigPreview.Fixture fixture) {
        var format = new Coordinates("INBOUND", "ORDER", "EDIFACT", "D96A", "ORDERS");
        var catalog = new ClasspathGlobalMappingCatalog();
        var mapping = catalog.find(TescoUkMappings.ORDER).orElseThrow();
        var profile = new MigEvidence.Profile(mapping.sourceSchemaId(), mapping.targetSchemaId(), "schema",
                MigEvidence.runtimeFingerprint(), List.of(), Set.of(), Set.of(), null, List.of(), List.of(), Map.of(), null);
        var pack = new MigEvidence.Pack(1, format, profile, List.of(), List.of(), List.of(), List.of(), "digest");
        var job = new MigJob(UUID.randomUUID(), State.REVIEW, 0L, Long.MAX_VALUE, pack, List.of(), 0,
                Map.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null, ModelMode.LOCAL_FIXTURE, null, null, null);
        var rules = mapping.nodes().stream().map(node -> new Rule("Synthetic benchmark rule", List.of("fixture"),
                node, null, null, null, List.of(), true)).toList();

        var json = new ObjectMapper();
        var ubl = new JsonSchemaDocumentValidator();
        MappingUblValidator validate = (type, content) -> MappingValidationReport.of("UBL Order 2.1",
                ubl.validateUbl(content, "Order").errors().stream()
                        .map(error -> MappingValidationReport.Finding.error(error.code(), error.path(), error.message())).toList());
        var service = new MappingService(null, new MappingRuntime(json), json, Clock.systemUTC(), catalog);
        var preview = new MigPreview(new InboundEdiTranslator(json, 10), null, validate, new MappingEdiValidator(), service, json);

        return TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> preview.run(job, rules, fixture, Map.of()));
    }

    private String inbound() {
        return """
                UNB+UNOC:3+DEMO-BUYER+DEMO-SUPPLIER+261122:1200+SYNTH1'
                UNH+1+ORDERS:D:96A:UN:EAN008'
                BGM+220+PO-SYNTH-1+9'
                DTM+137:20261122:102'
                DTM+64:202611231130:203'
                CUX+2:GBP:9'
                NAD+BY+DEMO-BUYER::9'
                NAD+SE+DEMO-SUPPLIER::9'
                LOC+7+DEMO-DEPOT::92'
                LIN+1++05010000000015:EN'
                QTY+21:4:EA'
                LIN+2++05010000000022:EN'
                QTY+21:9:EA'
                UNT+13+1'
                UNZ+1+SYNTH1'
                """;
    }

    private String expected() {
        return """
                {
                  "_D":"urn:oasis:names:specification:ubl:schema:xsd:Order-2",
                  "_A":"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
                  "_B":"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
                  "Order":[{
                    "ID":[{"_":"PO-SYNTH-1"}],
                    "UBLVersionID":[{"_":"2.1"}],
                    "IssueDate":[{"_":"2026-11-22"}],
                    "DocumentCurrencyCode":[{"_":"GBP"}],
                    "BuyerCustomerParty":[{"Party":[{"EndpointID":[{"_":"DEMO-BUYER"}]}]}],
                    "SellerSupplierParty":[{"Party":[{"EndpointID":[{"_":"DEMO-SUPPLIER"}]}]}],
                    "Delivery":[{"DeliveryLocation":[{"ID":[{"_":"DEMO-DEPOT"}]}],
                      "RequestedDeliveryPeriod":[{"EndDate":[{"_":"2026-11-23"}],"EndTime":[{"_":"11:30:00Z"}]}]}],
                    "OrderLine":[
                      {"LineItem":[{"ID":[{"_":"1"}],"Quantity":[{"_":4.0,"unitCode":"EA"}],
                        "Item":[{"StandardItemIdentification":[{"ID":[{"_":"05010000000015"}]}]}]}]},
                      {"LineItem":[{"ID":[{"_":"2"}],"Quantity":[{"_":9.0,"unitCode":"EA"}],
                        "Item":[{"StandardItemIdentification":[{"ID":[{"_":"05010000000022"}]}]}]}]}
                    ]
                  }]
                }
                """;
    }

    private byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
