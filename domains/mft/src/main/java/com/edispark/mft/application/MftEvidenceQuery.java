package com.edispark.mft.application;

import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.MdnDelivery;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.PartnerStatus;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferDirection;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.MftRepository.EvidencePage;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public final class MftEvidenceQuery {
    private static final int PAGE_SIZE = 32;
    private final MftRepository records;

    public MftEvidenceQuery(MftRepository records) {
        this.records = records;
    }

    public Transfer transfer(TransferId id) {
        return records.findTransfer(id).orElseThrow(() -> new MftNotFoundException("Transfer was not found"));
    }

    public Page<Transfer> findTransfers(TransferSearch search, UUID after) {
        return filter(records.pageTransfers(after, PAGE_SIZE), search::includes);
    }

    public Page<MdnDelivery> receiptDeliveries(TransferId id, UUID after) {
        var transfer = transfer(id);
        if (transfer.direction() != TransferDirection.INBOUND) {
            throw new IllegalArgumentException("Receipt deliveries require an inbound transfer");
        }
        return filter(records.pageMdnDeliveries(after, PAGE_SIZE), delivery ->
                delivery.partnerId().equals(transfer.partnerId()) && delivery.originalMessageId().equals(transfer.as2MessageId()));
    }

    public PartnerProfile connection(UUID id) {
        return records.findPartner(id).orElseThrow(() -> new MftNotFoundException("Connection was not found"));
    }

    public Page<PartnerProfile> findConnections(PartnerStatus status, UUID after) {
        return filter(records.pagePartners(after, PAGE_SIZE), profile -> status == null || status == profile.status());
    }

    public CertificateRecord certificate(UUID id) {
        return records.findCertificate(id).orElseThrow(() -> new MftNotFoundException("Certificate was not found"));
    }

    public Page<CertificateRecord> findCertificates(CertificateKind kind, DateWindow expiry, UUID after) {
        return filter(records.pageCertificates(after, PAGE_SIZE), certificate ->
                (kind == null || kind == certificate.kind()) && (expiry == null || expiry.includes(certificate.notAfter())));
    }

    public Page<CertificateUsage> certificateUsage(UUID id, UUID after) {
        var certificate = certificate(id);
        var page = records.pagePartners(after, PAGE_SIZE);
        var matches = page.items().stream().map(profile -> CertificateUsage.of(certificate.id(), profile))
                .filter(CertificateUsage::inUse).toList();
        return new Page<>(matches, page.next());
    }

    public List<ConnectionField> compareConnections(UUID baselineId, UUID candidateId) {
        var baseline = connection(baselineId);
        var candidate = connection(candidateId);
        if (!baseline.familyId().equals(candidate.familyId())) {
            throw new IllegalArgumentException("Compare revisions of the same AS2 connection");
        }
        return Arrays.stream(ConnectionField.values()).filter(field -> field.changed(baseline, candidate)).toList();
    }

    public ReceiptDisposition receiptDisposition(Transfer transfer) {
        if (transfer.mdnDisposition() == null) return ReceiptDisposition.NOT_RECORDED;
        var parts = transfer.mdnDisposition().split(";", 2);
        if (parts.length < 2) return ReceiptDisposition.UNCLASSIFIED;
        return switch (parts[1].trim().split("[:/]", 2)[0]) {
            case "processed" -> ReceiptDisposition.PROCESSED;
            case "failed" -> ReceiptDisposition.FAILED;
            default -> ReceiptDisposition.UNCLASSIFIED;
        };
    }

    private static <T> Page<T> filter(EvidencePage<T> page, Predicate<T> predicate) {
        return new Page<>(page.items().stream().filter(predicate).toList(), page.next());
    }

    public record Page<T>(List<T> items, UUID next) { }

    public enum ReceiptDisposition { NOT_RECORDED, PROCESSED, FAILED, UNCLASSIFIED }

    public record TransferSearch(DateWindow createdAt, UUID connectionId, TransferDirection direction, TransferStatus status) {
        public TransferSearch { Objects.requireNonNull(createdAt, "A transfer search requires a date window"); }

        boolean includes(Transfer transfer) {
            return createdAt.includes(transfer.createdAt())
                    && (connectionId == null || connectionId.equals(transfer.partnerId()))
                    && (direction == null || direction == transfer.direction())
                    && (status == null || status == transfer.status());
        }
    }

    public record DateWindow(Instant from, Instant to) {
        public DateWindow {
            if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
                throw new IllegalArgumentException("Search requires a date window of up to 31 days");
            }
        }

        boolean includes(Instant instant) {
            return !instant.isBefore(from) && instant.isBefore(to);
        }
    }

    public record CertificateUsage(UUID connectionId, int revision, PartnerStatus status, boolean localIdentity,
                                   boolean partnerSigning, boolean partnerEncryption, boolean previousPartnerSigning) {
        static CertificateUsage of(UUID certificateId, PartnerProfile profile) {
            return new CertificateUsage(profile.id(), profile.revision(), profile.status(),
                    certificateId.equals(profile.localCertificateId()), certificateId.equals(profile.partnerSigningCertificateId()),
                    certificateId.equals(profile.partnerEncryptionCertificateId()), certificateId.equals(profile.previousPartnerSigningCertificateId()));
        }

        boolean inUse() {
            return localIdentity || partnerSigning || partnerEncryption || previousPartnerSigning;
        }
    }

    public enum ConnectionField {
        RECEIPT_MODE(PartnerProfile::mdnMode), SIGNING_POLICY(PartnerProfile::signingRequired),
        ENCRYPTION_POLICY(PartnerProfile::encryptionRequired), COMPRESSION(PartnerProfile::compressionEnabled),
        LEGACY_CRYPTOGRAPHY(PartnerProfile::legacyCryptoEnabled), LOCAL_CERTIFICATE(PartnerProfile::localCertificateId),
        SIGNING_CERTIFICATE(PartnerProfile::partnerSigningCertificateId),
        ENCRYPTION_CERTIFICATE(PartnerProfile::partnerEncryptionCertificateId),
        SIGNING_GRACE_PERIOD(PartnerProfile::signingCertificateGraceUntil),
        DELIVERY_ENDPOINT(PartnerProfile::endpoint), RECEIPT_ENDPOINT(PartnerProfile::asynchronousMdnEndpoint);

        private final Function<PartnerProfile, Object> value;

        ConnectionField(Function<PartnerProfile, Object> value) {
            this.value = value;
        }

        boolean changed(PartnerProfile baseline, PartnerProfile candidate) {
            return !Objects.equals(value.apply(baseline), value.apply(candidate));
        }
    }
}
