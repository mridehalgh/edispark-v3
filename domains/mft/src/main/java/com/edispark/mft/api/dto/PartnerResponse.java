package com.edispark.mft.api.dto;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record PartnerResponse(UUID id, String name, String localAs2Id, String remoteAs2Id, URI endpoint,
                              UUID localCertificateId, UUID partnerCertificateId,
                              MdnMode mdnMode, boolean signingRequired, boolean encryptionRequired,
                              boolean compressionEnabled, boolean legacyCryptoEnabled, Instant createdAt) {
    public static PartnerResponse from(PartnerProfile partner) {
        return new PartnerResponse(partner.id(), partner.name(), partner.localAs2Id(), partner.remoteAs2Id(),
                partner.endpoint(), partner.localCertificateId(), partner.partnerCertificateId(), partner.mdnMode(),
                partner.signingRequired(), partner.encryptionRequired(),
                partner.compressionEnabled(), partner.legacyCryptoEnabled(), partner.createdAt());
    }
}
