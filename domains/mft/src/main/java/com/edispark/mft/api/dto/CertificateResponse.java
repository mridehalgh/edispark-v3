package com.edispark.mft.api.dto;

import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.domain.model.CertificateRecord;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record CertificateResponse(UUID id, String name, CertificateKind kind, String fingerprintSha256,
                                  String subject, String issuer, Instant notBefore, Instant notAfter,
                                  boolean hasPrivateKey, long daysUntilExpiry, String validity) {
    public static CertificateResponse from(CertificateRecord record) {
        Instant now = Instant.now();
        long days = ChronoUnit.DAYS.between(now, record.notAfter());
        String validity = now.isBefore(record.notBefore()) ? "NOT_YET_VALID"
                : now.isAfter(record.notAfter()) ? "EXPIRED" : days <= 30 ? "EXPIRING" : "VALID";
        return new CertificateResponse(record.id(), record.name(), record.kind(), record.fingerprintSha256(),
                record.subject(), record.issuer(), record.notBefore(), record.notAfter(), record.hasPrivateKey(), days, validity);
    }
}
