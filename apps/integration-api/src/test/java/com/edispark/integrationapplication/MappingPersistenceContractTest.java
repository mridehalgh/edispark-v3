package com.edispark.integrationapplication;

import static org.assertj.core.api.Assertions.assertThat;
import com.edispark.transformations.domain.MappingRevision;
import com.edispark.transformations.domain.TargetValidationEvidence;
import com.edispark.transformations.infrastructure.ClasspathGlobalMappingCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MappingPersistenceContractTest {
    @Test void restoresPublishedEvidenceSamplesAndCompatibilityAfterRestart() throws Exception {
        var mapping = new ClasspathGlobalMappingCatalog().find(UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac60")).orElseThrow()
                .forkForTenant("test-tenant", Instant.now())
                .withTargetEvidence(new TargetValidationEvidence("rules", "fingerprint", "mapping-hash", List.of("fixture-hash")))
                .publish("mapping-hash", Instant.now());
        var json = new ObjectMapper().registerModule(new JavaTimeModule());
        var restored = json.readValue(json.writeValueAsBytes(mapping), MappingRevision.class);
        assertThat(restored).isEqualTo(mapping);
        assertThat(restored.compatibility().documentType()).isEqualTo("ORDER_RESPONSE");
    }
}
