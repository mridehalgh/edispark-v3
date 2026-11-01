package com.edispark.mft.infrastructure.persistence;

import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.MdnDelivery;
import com.edispark.mft.domain.model.MdnDeliveryStatus;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.domain.repository.MftRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMftRepository implements MftRepository {
    private final ConcurrentHashMap<String, PartnerProfile> partners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> inboundOwners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Transfer> transfers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransferStatus> persistedStatuses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransferId> idempotency = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransferId> outboundMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TransferId> inboundMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CertificateRecord> certificates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeliveryLease> deliveryLeases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MdnDelivery> mdnDeliveries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeliveryLease> mdnLeases = new ConcurrentHashMap<>();

    @Override
    public synchronized PartnerProfile savePartner(PartnerProfile partner) {
        if (partner.revision() > 1) {
            partners.put(partner.tenantId() + ":" + partner.id(), partner);
            return partner;
        }
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
    public synchronized PartnerProfile updatePartner(PartnerProfile partner) {
        String key = partner.tenantId() + ":" + partner.id();
        if (!partners.containsKey(key)) throw new IllegalArgumentException("Partner does not exist");
        partners.put(key, partner);
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
                && item.remoteAs2Id().equals(remoteAs2Id))
                .sorted(Comparator.comparing(item -> item.status() == com.edispark.mft.domain.model.PartnerStatus.ACTIVE ? 0 : 1))
                .findFirst();
    }

    @Override public void deletePartner(String tenantId, UUID partnerId) { partners.remove(tenantId + ":" + partnerId); }

    @Override
    public synchronized Transfer saveOutboundIfAbsent(Transfer transfer) {
        String key = transfer.tenantId() + ":" + transfer.idempotencyKey();
        TransferId existing = idempotency.putIfAbsent(key, transfer.id());
        if (existing != null) return transfers.get(transfer.tenantId() + ":" + existing);
        transfers.put(transfer.tenantId() + ":" + transfer.id(), transfer);
        persistedStatuses.put(transfer.tenantId() + ":" + transfer.id(), transfer.status());
        outboundMessages.put(transfer.tenantId() + ":" + transfer.as2MessageId(), transfer.id());
        return transfer;
    }

    @Override
    public synchronized Transfer save(Transfer transfer) {
        String key = transfer.tenantId() + ":" + transfer.id();
        transfers.put(key, transfer);
        persistedStatuses.put(key, transfer.status());
        return transfer;
    }

    @Override
    public synchronized boolean saveIfStatus(Transfer transfer, TransferStatus expectedStatus) {
        String key = transfer.tenantId() + ":" + transfer.id();
        if (persistedStatuses.get(key) != expectedStatus) return false;
        transfers.put(key, transfer);
        persistedStatuses.put(key, transfer.status());
        return true;
    }

    @Override
    public synchronized Transfer saveInboundIfAbsent(Transfer transfer) {
        String key = inboundKey(transfer.tenantId(), transfer.as2MessageId());
        TransferId existing = inboundMessages.putIfAbsent(key, transfer.id());
        if (existing != null) {
            Transfer original = transfers.get(transfer.tenantId() + ":" + existing);
            if (original == null || original.payload() == null
                    || !original.payload().sha256().equals(transfer.payload().sha256())) {
                throw new com.edispark.mft.application.As2ReplayConflictException();
            }
            return original;
        }
        transfers.put(transfer.tenantId() + ":" + transfer.id(), transfer);
        persistedStatuses.put(transfer.tenantId() + ":" + transfer.id(), transfer.status());
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
        return findInboundByMessageId(tenantId, messageId)
                .filter(item -> item.payload() != null && item.payload().sha256().equals(sha256));
    }

    @Override
    public Optional<Transfer> findInboundByMessageId(String tenantId, String messageId) {
        TransferId id = inboundMessages.get(inboundKey(tenantId, messageId));
        if (id != null) return findTransfer(tenantId, id);
        return transfers.values().stream().filter(item -> item.tenantId().equals(tenantId)
                && item.direction().name().equals("INBOUND") && item.as2MessageId().equals(messageId)).findFirst();
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

    @Override public void deleteCertificate(String tenantId, UUID certificateId) {
        certificates.remove(tenantId + ":" + certificateId);
    }

    @Override
    public List<Transfer> listPendingTransfers(Instant dueAt, int limit) {
        return transfers.values().stream()
                .filter(item -> (item.status().name().equals("QUEUED") || item.status().name().equals("RETRY_SCHEDULED")
                        || item.status().name().equals("PROCESSING"))
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

    @Override
    public MdnDelivery saveMdnDelivery(MdnDelivery delivery) {
        mdnDeliveries.put(delivery.tenantId() + ":" + delivery.id(), delivery);
        return delivery;
    }

    @Override
    public synchronized boolean saveMdnDeliveryIfAttempts(MdnDelivery delivery, int expectedAttempts) {
        String key = delivery.tenantId() + ":" + delivery.id();
        MdnDelivery current = mdnDeliveries.get(key);
        if (current == null || current.status() != MdnDeliveryStatus.PENDING
                || current.attempts() != expectedAttempts) return false;
        mdnDeliveries.put(key, delivery);
        return true;
    }

    @Override
    public MdnDelivery saveMdnDeliveryIfAbsent(MdnDelivery delivery) {
        return mdnDeliveries.computeIfAbsent(delivery.tenantId() + ":" + delivery.id(), ignored -> delivery);
    }

    @Override
    public List<MdnDelivery> listPendingMdnDeliveries(Instant dueAt, int limit) {
        return mdnDeliveries.values().stream()
                .filter(item -> item.status() == MdnDeliveryStatus.PENDING && !item.nextAttemptAt().isAfter(dueAt))
                .sorted(Comparator.comparing(MdnDelivery::nextAttemptAt)).limit(limit).toList();
    }

    @Override
    public List<MdnDelivery> listMdnDeliveries(String tenantId) {
        return mdnDeliveries.values().stream().filter(item -> item.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(MdnDelivery::createdAt).reversed()).toList();
    }

    @Override
    public synchronized boolean tryAcquireMdnLease(String tenantId, UUID deliveryId, String owner,
                                                    Instant now, Instant expiresAt) {
        String key = tenantId + ":" + deliveryId;
        DeliveryLease existing = mdnLeases.get(key);
        if (existing != null && !existing.expiresAt().isBefore(now)) return false;
        mdnLeases.put(key, new DeliveryLease(owner, expiresAt));
        return true;
    }

    @Override
    public synchronized void releaseMdnLease(String tenantId, UUID deliveryId, String owner) {
        mdnLeases.computeIfPresent(tenantId + ":" + deliveryId,
                (key, lease) -> lease.owner().equals(owner) ? null : lease);
    }

    private String inboundKey(String tenantId, String messageId) {
        return tenantId + ":" + messageId;
    }

    private record DeliveryLease(String owner, Instant expiresAt) {}
}
