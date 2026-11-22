package com.edispark.mft.infrastructure.persistence;

import io.cottn.core.tenancy.TenantAccess;
import com.edispark.mft.infrastructure.MftCreationAdapter;

import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.MdnDelivery;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edispark.mft.domain.repository.MftRepository;

/** Explicit tenant primitives for trusted persistence and ingress adapters only. */
public interface MftPersistence extends MftRepository, MftDiscovery, MftCreationAdapter {
    @Override default boolean as2IdentityPairInUse(String localAs2Id, String remoteAs2Id, UUID except) {
        return findInboundPartner(localAs2Id, remoteAs2Id).filter(partner -> !partner.id().equals(except)).isPresent();
    }

    EvidencePage<Transfer> pageTransfers(String tenantId, UUID after, int limit);
    default EvidencePage<Transfer> pageTransfers(UUID after, int limit) { return pageTransfers(TenantAccess.currentId(), after, limit); }
    EvidencePage<PartnerProfile> pagePartners(String tenantId, UUID after, int limit);
    default EvidencePage<PartnerProfile> pagePartners(UUID after, int limit) { return pagePartners(TenantAccess.currentId(), after, limit); }
    EvidencePage<CertificateRecord> pageCertificates(String tenantId, UUID after, int limit);
    default EvidencePage<CertificateRecord> pageCertificates(UUID after, int limit) { return pageCertificates(TenantAccess.currentId(), after, limit); }
    EvidencePage<MdnDelivery> pageMdnDeliveries(String tenantId, UUID after, int limit);
    default EvidencePage<MdnDelivery> pageMdnDeliveries(UUID after, int limit) { return pageMdnDeliveries(TenantAccess.currentId(), after, limit); }

    PartnerProfile savePartner(PartnerProfile partner);
    PartnerProfile updatePartner(PartnerProfile partner);
    List<PartnerProfile> listPartners(String tenantId);
    Optional<PartnerProfile> findPartner(String tenantId, UUID partnerId);
    void deletePartner(String tenantId, UUID partnerId);
    Transfer saveOutboundIfAbsent(Transfer transfer);
    Transfer saveInboundIfAbsent(Transfer transfer);
    Transfer save(Transfer transfer);
    boolean saveIfStatus(Transfer transfer, TransferStatus expectedStatus);
    Optional<Transfer> findTransfer(String tenantId, TransferId id);
    Optional<Transfer> findByIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<Transfer> findOutboundByMessageId(String tenantId, String as2MessageId);
    Optional<Transfer> findInboundDuplicate(String tenantId, String as2MessageId, String sha256);
    Optional<Transfer> findInboundByMessageId(String tenantId, String as2MessageId);
    List<Transfer> listTransfers(String tenantId);
    CertificateRecord saveCertificate(CertificateRecord certificate);
    List<CertificateRecord> listCertificates(String tenantId);
    Optional<CertificateRecord> findCertificate(String tenantId, UUID certificateId);
    void deleteCertificate(String tenantId, UUID certificateId);
    boolean tryAcquireDeliveryLease(String tenantId, TransferId transferId, String owner, Instant now, Instant expiresAt);
    void releaseDeliveryLease(String tenantId, TransferId transferId, String owner);
    MdnDelivery saveMdnDelivery(MdnDelivery delivery);
    boolean saveMdnDeliveryIfAttempts(MdnDelivery delivery, int expectedAttempts);
    MdnDelivery saveMdnDeliveryIfAbsent(MdnDelivery delivery);
    List<MdnDelivery> listMdnDeliveries(String tenantId);
    boolean tryAcquireMdnLease(String tenantId, UUID deliveryId, String owner, Instant now, Instant expiresAt);
    void releaseMdnLease(String tenantId, UUID deliveryId, String owner);
    default List<PartnerProfile> listPartners() { return listPartners(TenantAccess.currentId()); }
    default Optional<PartnerProfile> findPartner(UUID partnerId) { return findPartner(TenantAccess.currentId(), partnerId); }
    default void deletePartner(UUID partnerId) { deletePartner(TenantAccess.currentId(), partnerId); }
    default Optional<Transfer> findTransfer(TransferId id) { return findTransfer(TenantAccess.currentId(), id); }
    default Optional<Transfer> findByIdempotencyKey(String idempotencyKey) { return findByIdempotencyKey(TenantAccess.currentId(), idempotencyKey); }
    default Optional<Transfer> findOutboundByMessageId(String as2MessageId) { return findOutboundByMessageId(TenantAccess.currentId(), as2MessageId); }
    default Optional<Transfer> findInboundDuplicate(String as2MessageId, String sha256) { return findInboundDuplicate(TenantAccess.currentId(), as2MessageId, sha256); }
    default Optional<Transfer> findInboundByMessageId(String as2MessageId) { return findInboundByMessageId(TenantAccess.currentId(), as2MessageId); }
    default List<Transfer> listTransfers() { return listTransfers(TenantAccess.currentId()); }
    default List<CertificateRecord> listCertificates() { return listCertificates(TenantAccess.currentId()); }
    default Optional<CertificateRecord> findCertificate(UUID certificateId) { return findCertificate(TenantAccess.currentId(), certificateId); }
    default void deleteCertificate(UUID certificateId) { deleteCertificate(TenantAccess.currentId(), certificateId); }
    default boolean tryAcquireDeliveryLease(TransferId transferId, String owner, Instant now, Instant expiresAt) { return tryAcquireDeliveryLease(TenantAccess.currentId(), transferId, owner, now, expiresAt); }
    default void releaseDeliveryLease(TransferId transferId, String owner) { releaseDeliveryLease(TenantAccess.currentId(), transferId, owner); }
    default List<MdnDelivery> listMdnDeliveries() { return listMdnDeliveries(TenantAccess.currentId()); }
    default boolean tryAcquireMdnLease(UUID deliveryId, String owner, Instant now, Instant expiresAt) { return tryAcquireMdnLease(TenantAccess.currentId(), deliveryId, owner, now, expiresAt); }
    default void releaseMdnLease(UUID deliveryId, String owner) { releaseMdnLease(TenantAccess.currentId(), deliveryId, owner); }
}
