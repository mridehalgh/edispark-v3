package com.edispark.mft.api.dto;

import com.edispark.mft.domain.model.MdnMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;

public record CreatePartnerRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 128) String localAs2Id,
        @NotBlank @Size(max = 128) String remoteAs2Id,
        @NotNull URI endpoint,
        @NotNull UUID localCertificateId,
        @NotNull UUID partnerCertificateId,
        @NotNull MdnMode mdnMode,
        boolean signingRequired,
        boolean encryptionRequired,
        boolean compressionEnabled,
        boolean legacyCryptoEnabled) {
}
