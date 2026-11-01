package com.edispark.mft.api.dto;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record PartnerResponse(UUID id, String name, String localAs2Id, String remoteAs2Id, URI endpoint,
                              UUID localCertificateId, UUID partnerSigningCertificateId,
                              UUID partnerEncryptionCertificateId, UUID previousPartnerSigningCertificateId,
                              Instant signingCertificateGraceUntil, URI asynchronousMdnEndpoint,
                              MdnMode mdnMode, boolean signingRequired, boolean encryptionRequired,
                              boolean compressionEnabled, boolean legacyCryptoEnabled,
                              com.edispark.mft.domain.model.PartnerStatus status, Instant createdAt) {
    public static PartnerResponse from(PartnerProfile partner) {
        return new PartnerResponse(partner.id(), partner.name(), partner.localAs2Id(), partner.remoteAs2Id(),
                partner.endpoint(), partner.localCertificateId(), partner.partnerSigningCertificateId(),
                partner.partnerEncryptionCertificateId(), partner.previousPartnerSigningCertificateId(),
                partner.signingCertificateGraceUntil(), partner.asynchronousMdnEndpoint(), partner.mdnMode(),
                partner.signingRequired(), partner.encryptionRequired(),
                partner.compressionEnabled(), partner.legacyCryptoEnabled(), partner.status(), partner.createdAt());
    }
}
