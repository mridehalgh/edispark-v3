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
    private final ConcurrentHashMap<String, String> inboundOwners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Transfer> transfers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransferId> idempotency = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransferId> outboundMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransferId> inboundDeduplication = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CertificateRecord> certificates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeliveryLease> deliveryLeases = new ConcurrentHashMap<>();

    @Override
    public synchronized PartnerProfile savePartner(PartnerProfile partner) {
        String pair = partner.localAs2Id() + "\u0000" + partner.remoteAs2Id();
        String owner = partner.tenantId() + ":" + partner.id();
        String existing = inboundOwners.putIfAbsent(pair, owner);
        if (existing != null && !existing.equals(owner)) {
            throw new IllegalArgumentException("The AS2 identity pair is already assigned to a workspace");
        }
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
        outboundMessages.put(transfer.tenantId() + ":" + transfer.as2MessageId(), transfer.id());
        return transfer;
    }

    @Override
    public Transfer save(Transfer transfer) {
        transfers.put(transfer.tenantId() + ":" + transfer.id(), transfer);
        return transfer;
    }

    @Override
    public synchronized Transfer saveInboundIfAbsent(Transfer transfer) {
        String key = inboundKey(transfer.tenantId(), transfer.as2MessageId(), transfer.payload().sha256());
        TransferId existing = inboundDeduplication.putIfAbsent(key, transfer.id());
        if (existing != null) return transfers.get(transfer.tenantId() + ":" + existing);
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
    public Optional<Transfer> findOutboundByMessageId(String tenantId, String as2MessageId) {
        TransferId id = outboundMessages.get(tenantId + ":" + as2MessageId);
        return id == null ? Optional.empty() : findTransfer(tenantId, id);
    }

    @Override
    public Optional<Transfer> findInboundDuplicate(String tenantId, String messageId, String sha256) {
        TransferId id = inboundDeduplication.get(inboundKey(tenantId, messageId, sha256));
        if (id != null) return findTransfer(tenantId, id);
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

    @Override
    public synchronized boolean tryAcquireDeliveryLease(String tenantId, TransferId transferId, String owner,
                                                         Instant now, Instant expiresAt) {
        String key = tenantId + ":" + transferId;
        DeliveryLease existing = deliveryLeases.get(key);
        if (existing != null && !existing.expiresAt().isBefore(now)) return false;
        deliveryLeases.put(key, new DeliveryLease(owner, expiresAt));
        return true;
    }

    @Override
    public synchronized void releaseDeliveryLease(String tenantId, TransferId transferId, String owner) {
        deliveryLeases.computeIfPresent(tenantId + ":" + transferId,
                (key, lease) -> lease.owner().equals(owner) ? null : lease);
    }

    private String inboundKey(String tenantId, String messageId, String sha256) {
        return tenantId + ":" + messageId + ":" + sha256;
    }

    private record DeliveryLease(String owner, Instant expiresAt) {}
}
