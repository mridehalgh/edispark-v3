package com.edispark.mft.infrastructure.persistence;

import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.repository.MftRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMftRepository implements MftRepository {
    private final ConcurrentHashMap<String, PartnerProfile> partners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Transfer> transfers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransferId> idempotency = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CertificateRecord> certificates = new ConcurrentHashMap<>();

    @Override
    public PartnerProfile savePartner(PartnerProfile partner) {
        partners.put(partner.tenantId() + ":" + partner.id(), partner);
        return partner;
    }

    @Override
    public List<PartnerProfile> listPartners(String tenantId) {
        return partners.values().stream().filter(item -> item.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(PartnerProfile::name)).toList();
    }

    @Override
    public Optional<PartnerProfile> findPartner(String tenantId, UUID partnerId) {
        return Optional.ofNullable(partners.get(tenantId + ":" + partnerId));
    }

    @Override
    public Optional<PartnerProfile> findInboundPartner(String localAs2Id, String remoteAs2Id) {
        return partners.values().stream().filter(item -> item.localAs2Id().equals(localAs2Id)
                && item.remoteAs2Id().equals(remoteAs2Id)).findFirst();
    }

    @Override
    public synchronized Transfer saveOutboundIfAbsent(Transfer transfer) {
        String key = transfer.tenantId() + ":" + transfer.idempotencyKey();
        TransferId existing = idempotency.putIfAbsent(key, transfer.id());
        if (existing != null) return transfers.get(transfer.tenantId() + ":" + existing);
        transfers.put(transfer.tenantId() + ":" + transfer.id(), transfer);
        return transfer;
    }

    @Override
    public Transfer save(Transfer transfer) {
        transfers.put(transfer.tenantId() + ":" + transfer.id(), transfer);
        return transfer;
    }

    @Override
    public Optional<Transfer> findTransfer(String tenantId, TransferId id) {
        return Optional.ofNullable(transfers.get(tenantId + ":" + id));
    }

    @Override
    public Optional<Transfer> findByIdempotencyKey(String tenantId, String key) {
        TransferId id = idempotency.get(tenantId + ":" + key);
        return id == null ? Optional.empty() : findTransfer(tenantId, id);
    }

    @Override
    public Optional<Transfer> findInboundDuplicate(String tenantId, String messageId, String sha256) {
        return transfers.values().stream().filter(item -> item.tenantId().equals(tenantId)
                && item.direction().name().equals("INBOUND") && item.as2MessageId().equals(messageId)
                && item.payload() != null && item.payload().sha256().equals(sha256)).findFirst();
    }

    @Override
    public List<Transfer> listTransfers(String tenantId) {
        return transfers.values().stream().filter(item -> item.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(Transfer::createdAt).reversed()).toList();
    }

    @Override
    public CertificateRecord saveCertificate(CertificateRecord certificate) {
        certificates.put(certificate.tenantId() + ":" + certificate.id(), certificate);
        return certificate;
    }

    @Override
    public List<CertificateRecord> listCertificates(String tenantId) {
        return certificates.values().stream().filter(item -> item.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(CertificateRecord::notAfter)).toList();
    }

    @Override
    public Optional<CertificateRecord> findCertificate(String tenantId, UUID certificateId) {
        return Optional.ofNullable(certificates.get(tenantId + ":" + certificateId));
    }

    @Override
    public List<Transfer> listPendingTransfers(Instant dueAt, int limit) {
        return transfers.values().stream()
                .filter(item -> (item.status().name().equals("QUEUED") || item.status().name().equals("RETRY_SCHEDULED"))
                        && !item.nextAttemptAt().isAfter(dueAt))
                .sorted(Comparator.comparing(Transfer::nextAttemptAt)).limit(limit).toList();
    }
}
