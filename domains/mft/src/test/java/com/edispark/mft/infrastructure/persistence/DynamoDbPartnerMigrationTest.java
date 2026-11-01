package com.edispark.mft.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.PartnerStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DynamoDbPartnerMigrationTest {

    @Test
    void legacySingleCertificatePartnerIsUpgradedWithoutChangingItsIdentity() throws Exception {
        UUID certificateId = UUID.randomUUID();
        String legacyJson = """
                {"id":"%s","tenantId":"tenant-a","name":"Retailer","localAs2Id":"EDISPARK-A",
                "remoteAs2Id":"RETAILER-A","endpoint":"https://retailer.example/as2",
                "localCertificateId":"%s","partnerCertificateId":"%s","mdnMode":"SYNCHRONOUS",
                "signingRequired":true,"encryptionRequired":true,"compressionEnabled":false,
                "legacyCryptoEnabled":false,"createdAt":"2026-11-01T12:00:00Z"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), certificateId);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ObjectNode stored = (ObjectNode) mapper.readTree(legacyJson);

        DynamoDbMftRepository.migrateLegacyPartner(stored);
        PartnerProfile restored = mapper.treeToValue(stored, PartnerProfile.class);

        assertThat(restored.partnerSigningCertificateId()).isEqualTo(certificateId);
        assertThat(restored.partnerEncryptionCertificateId()).isEqualTo(certificateId);
        assertThat(restored.status()).isEqualTo(PartnerStatus.ACTIVE);
    }
}
