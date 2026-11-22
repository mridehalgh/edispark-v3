package com.edispark.wireup.modules;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.infrastructure.validation.JsonSchemaDocumentValidator;
import com.edispark.integrationapplication.acl.*;
import com.edispark.exchange.application.*;
import com.edispark.partnerships.domain.model.*;
import com.edispark.transformations.application.*;
import com.edispark.transformations.infrastructure.*;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BqAcknowledgementReadinessTest {
    @Test void validatesSourceAndWireBeforeAllocatingAnyControlsAndPointsMissingFactsToTheEditor() throws Exception {
        var json = new ObjectMapper(); var runtime = new MappingRuntime(json);
        var documents = mock(DocumentSetCommandHandler.class);
        when(documents.validateCanonical(any(), any())).thenAnswer(call ->
                new JsonSchemaDocumentValidator().validateUbl(call.getArgument(1), "OrderResponse"));
        var validation = new InProcessMappingTargetValidation(documents, new OrderResponseTargetContract(json), new BqAcknowledgementTargetContract(json));
        var mappings = new MappingService(new InMemoryMappingRepository(), runtime, json, Clock.systemUTC(), new ClasspathGlobalMappingCatalog(), validation);
        var controls = mock(OutboundControlAllocator.class);
        var writer = new EdiPayloadWriter(json, Clock.fixed(java.time.Instant.parse("2026-11-09T00:00:00Z"), java.time.ZoneOffset.UTC));
        var transformations = new LocalTransformationAcl(mappings, writer, controls);
        var capability = new ExchangeCapability(UUID.randomUUID(), ExchangeDirection.OUTBOUND, BusinessDocumentType.ORDER_RESPONSE,
                EdiStandard.TRADACOMS, "9", "ORDRSP", UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac16"),
                UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac34"), LocalBqAcknowledgementFixture.MAPPING,
                UUID.randomUUID(), AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString("2OnHVzeAJKwm0ThFLcy1HfLHGPm")), () -> {
            var mapping = mappings.get(capability.mappingRevisionId());
            var proof = mappings.qualificationEvidence(mapping.id());
            assertThat(proof.failures()).isEmpty();
            assertThat(proof.valid()).isTrue();
            byte[] source = mapping.sample().sourceDocument().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var checked = transformations.executePublished(capability, source);
            assertThat(checked.warnings()).isEmpty();
            assertThat(new String(checked.payload(), java.nio.charset.StandardCharsets.US_ASCII))
                    .isEqualTo(mapping.sample().targetRaw().replace("\n", ""));
            verifyNoInteractions(controls);
            var exchange = UUID.randomUUID();
            when(controls.reserve(capability.as2ConnectionId(), exchange)).thenReturn(new OutboundControlAllocator.Control(7, 9));
            var sent = transformations.prepareForDispatch(capability, source, exchange, checked);
            String wire = new String(sent.payload(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(wire).contains("+7++ACKHDR'", "FIL=9+1+261109'", "KTR=8'", "MHD=3+ACKTLR:4'");
            assertThat(new com.edispark.tradacoms.ack.BqAcknowledgementV14().validateBytes(wire).valid()).isTrue();
            clearInvocations(controls);
            try {
                var missing = json.readTree(source);
                ((com.fasterxml.jackson.databind.node.ObjectNode) missing.at("/OrderResponse/0/SellerSupplierParty/0")).remove("CustomerAssignedAccountID");
                assertThatThrownBy(() -> transformations.executePublished(capability, json.writeValueAsBytes(missing)))
                        .isInstanceOfSatisfying(ReadinessFailure.class, failure -> assertThat(failure.issues()).anySatisfy(issue -> {
                            assertThat(issue.code()).isEqualTo("BQ_VENDOR_NUMBER_REQUIRED");
                            assertThat(issue.path()).isEqualTo("/OrderResponse/0/SellerSupplierParty/0/CustomerAssignedAccountID/0/_");
                            assertThat(issue.owner()).isEqualTo("DOCUMENT");
                        }));
                verifyNoInteractions(controls);
            } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        });
    }
}
