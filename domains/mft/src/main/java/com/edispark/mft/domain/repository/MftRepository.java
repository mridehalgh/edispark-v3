package com.edispark.mft.domain.repository;

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

public interface MftRepository extends MftCreation {
    EvidencePage<Transfer> pageTransfers(UUID after, int limit);
    EvidencePage<PartnerProfile> pagePartners(UUID after, int limit);
    EvidencePage<CertificateRecord> pageCertificates(UUID after, int limit);
    EvidencePage<MdnDelivery> pageMdnDeliveries(UUID after, int limit);

    record EvidencePage<T>(List<T> items, UUID next) { }

    PartnerProfile savePartner(PartnerProfile partner);
    PartnerProfile updatePartner(PartnerProfile partner);
    List<PartnerProfile> listPartners();
    Optional<PartnerProfile> findPartner(UUID partnerId);
    void deletePartner(UUID partnerId);
    Transfer saveOutboundIfAbsent(Transfer transfer);
    Transfer saveInboundIfAbsent(Transfer transfer);
    Transfer save(Transfer transfer);
    boolean saveIfStatus(Transfer transfer, TransferStatus expectedStatus);
    Optional<Transfer> findTransfer(TransferId id);
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
    Optional<Transfer> findOutboundByMessageId(String as2MessageId);
    Optional<Transfer> findInboundDuplicate(String as2MessageId, String sha256);
    Optional<Transfer> findInboundByMessageId(String as2MessageId);
    List<Transfer> listTransfers();
    CertificateRecord saveCertificate(CertificateRecord certificate);
    List<CertificateRecord> listCertificates();
    Optional<CertificateRecord> findCertificate(UUID certificateId);
    void deleteCertificate(UUID certificateId);
    boolean tryAcquireDeliveryLease(TransferId transferId, String owner, Instant now, Instant expiresAt);
    void releaseDeliveryLease(TransferId transferId, String owner);
    MdnDelivery saveMdnDelivery(MdnDelivery delivery);
    boolean saveMdnDeliveryIfAttempts(MdnDelivery delivery, int expectedAttempts);
    MdnDelivery saveMdnDeliveryIfAbsent(MdnDelivery delivery);
    List<MdnDelivery> listMdnDeliveries();
    boolean tryAcquireMdnLease(UUID deliveryId, String owner, Instant now, Instant expiresAt);
    void releaseMdnLease(UUID deliveryId, String owner);
}
