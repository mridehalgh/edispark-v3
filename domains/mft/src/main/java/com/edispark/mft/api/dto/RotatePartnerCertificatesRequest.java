package com.edispark.mft.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RotatePartnerCertificatesRequest(
        @NotNull UUID partnerSigningCertificateId,
        @NotNull UUID partnerEncryptionCertificateId) {
}
