package com.edispark.mft.domain.repository;
import com.edispark.mft.domain.model.*;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
public interface MftCreation {
    Transfer outbound(UUID partnerId, String filename, String contentType, String as2MessageId, String idempotencyKey, TransferId resendOf, Instant now);
    Transfer outbound(UUID partnerId, String filename, String contentType, String as2MessageId, String idempotencyKey, TransferId resendOf, TransferProvenance provenance, Instant now);
    Transfer inbound(UUID partnerId, String filename, String contentType, String as2MessageId, Instant now);
    PartnerProfile partner(String name, String localAs2Id, String remoteAs2Id, URI endpoint, UUID localCertificateId, UUID partnerSigningCertificateId, UUID partnerEncryptionCertificateId, UUID previousPartnerSigningCertificateId, Instant signingCertificateGraceUntil, URI asynchronousMdnEndpoint, MdnMode mdnMode, boolean signingRequired, boolean encryptionRequired, boolean compressionEnabled, boolean legacyCryptoEnabled, PartnerStatus status, Instant createdAt);
    CertificateRecord certificate(UUID id, String name, CertificateKind kind, String fingerprintSha256, String subject, String issuer, Instant notBefore, Instant notAfter, boolean hasPrivateKey, String publicCertificatePem, String secretRef, Instant createdAt);
    MdnDelivery pendingMdn(UUID id, UUID partnerId, URI callback, String contentType, String as2From, String as2To, String messageId, String originalMessageId, PayloadRef payload, Instant now);
    UUID mdnId(String originalMessageId);
}
