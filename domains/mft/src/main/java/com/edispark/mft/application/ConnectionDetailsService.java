package com.edispark.mft.application;

import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.repository.MftRepository;
import io.cottn.core.tenancy.TenantContext;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ConnectionDetailsService {
    private final MftRepository repository;
    private final URI inboundUrl;
    private final URI asynchronousMdnUrl;
    private final List<String> encryptionAlgorithms;
    private final List<String> signatureAlgorithms;
    private final String keyEncryptionAlgorithm;

    public ConnectionDetailsService(MftRepository repository, URI inboundUrl, URI asynchronousMdnUrl,
                                    List<String> encryptionAlgorithms, List<String> signatureAlgorithms,
                                    String keyEncryptionAlgorithm) {
        this.repository = repository;
        this.inboundUrl = inboundUrl;
        this.asynchronousMdnUrl = asynchronousMdnUrl;
        this.encryptionAlgorithms = List.copyOf(encryptionAlgorithms);
        this.signatureAlgorithms = List.copyOf(signatureAlgorithms);
        this.keyEncryptionAlgorithm = keyEncryptionAlgorithm;
    }

    public List<ConnectionDetails> list() {
        String tenantId = TenantContext.getCurrentTenant().asString();
        return repository.listPartners(tenantId).stream()
                .map(partner -> connectionDetails(tenantId, partner))
                .toList();
    }

    private ConnectionDetails connectionDetails(String tenantId, PartnerProfile partner) {
        CertificateRecord certificate = repository.findCertificate(tenantId, partner.localCertificateId())
                .orElseThrow(() -> new MftNotFoundException("Organisation AS2 certificate was not found"));
        return new ConnectionDetails(partner.id(), partner.name(), partner.localAs2Id(), inboundUrl,
                asynchronousMdnUrl, encryptionAlgorithms, signatureAlgorithms, keyEncryptionAlgorithm,
                certificate.id(), certificate.name(), certificate.fingerprintSha256(), certificate.notAfter(),
                certificate.publicCertificatePem());
    }

    public record ConnectionDetails(
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
    }
}
