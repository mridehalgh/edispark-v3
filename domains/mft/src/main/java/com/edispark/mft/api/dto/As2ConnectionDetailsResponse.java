package com.edispark.mft.api.dto;

import com.edispark.mft.application.ConnectionDetailsService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record As2ConnectionDetailsResponse(
        UUID partnerId,
        String partnerName,
        String as2Identifier,
        URI inboundUrl,
        URI asynchronousMdnUrl,
        List<String> supportedEncryptionAlgorithms,
        List<String> supportedSignatureAlgorithms,
        String keyEncryptionAlgorithm,
        UUID certificateId,
        String certificateName,
        String certificateFingerprintSha256,
        Instant certificateNotAfter,
        String publicCertificatePem) {
    public static As2ConnectionDetailsResponse from(ConnectionDetailsService.ConnectionDetails details) {
        return new As2ConnectionDetailsResponse(details.partnerId(), details.partnerName(), details.as2Identifier(),
                details.inboundUrl(), details.asynchronousMdnUrl(), details.supportedEncryptionAlgorithms(),
                details.supportedSignatureAlgorithms(), details.keyEncryptionAlgorithm(), details.certificateId(),
                details.certificateName(), details.certificateFingerprintSha256(), details.certificateNotAfter(),
                details.publicCertificatePem());
    }
}
