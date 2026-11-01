package com.edispark.mft.domain.repository;

import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MftRepository {
    PartnerProfile savePartner(PartnerProfile partner);
    List<PartnerProfile> listPartners(String tenantId);
    Optional<PartnerProfile> findPartner(String tenantId, UUID partnerId);
    Optional<PartnerProfile> findInboundPartner(String localAs2Id, String remoteAs2Id);
    Transfer saveOutboundIfAbsent(Transfer transfer);
    Transfer saveInboundIfAbsent(Transfer transfer);
    Transfer save(Transfer transfer);
    boolean saveIfStatus(Transfer transfer, TransferStatus expectedStatus);
    Optional<Transfer> findTransfer(String tenantId, TransferId id);
    Optional<Transfer> findByIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<Transfer> findOutboundByMessageId(String tenantId, String as2MessageId);
    Optional<Transfer> findInboundDuplicate(String tenantId, String as2MessageId, String sha256);
    List<Transfer> listTransfers(String tenantId);
    CertificateRecord saveCertificate(CertificateRecord certificate);
    List<CertificateRecord> listCertificates(String tenantId);
    Optional<CertificateRecord> findCertificate(String tenantId, UUID certificateId);
    List<Transfer> listPendingTransfers(Instant dueAt, int limit);
    boolean tryAcquireDeliveryLease(String tenantId, TransferId transferId, String owner,
                                    Instant now, Instant expiresAt);
    void releaseDeliveryLease(String tenantId, TransferId transferId, String owner);
}
