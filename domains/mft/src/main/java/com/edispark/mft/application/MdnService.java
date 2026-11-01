package com.edispark.mft.application;

import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.domain.repository.MftRepository;
import java.time.Clock;

public final class MdnService {
    private final MftRepository repository;
    private final Clock clock;

    public MdnService(MftRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Transfer record(PartnerProfile partner, String originalMessageId, String disposition,
                           String receivedContentMic) {
        Transfer transfer = repository.findOutboundByMessageId(partner.tenantId(), originalMessageId)
                .orElseThrow(() -> new MftNotFoundException("The MDN does not match an outbound transfer"));
        for (int attempt = 0; attempt < 3; attempt++) {
            Transfer completed = complete(partner, transfer, disposition, receivedContentMic);
            if (completed != null) return completed;
            transfer = repository.findTransfer(partner.tenantId(), transfer.id())
                    .orElseThrow(() -> new MftNotFoundException("The MDN transfer no longer exists"));
        }
        throw new IllegalStateException("The MDN transfer changed repeatedly while it was being completed");
    }

    private Transfer complete(PartnerProfile partner, Transfer transfer, String disposition,
                              String receivedContentMic) {
        if (!transfer.partnerId().equals(partner.id())) {
            throw new IllegalArgumentException("The MDN was signed by the wrong trading partner");
        }
        requireMatchingMic(transfer.sentContentMic(), receivedContentMic);
        if (transfer.status() == TransferStatus.DELIVERED && disposition.equals(transfer.mdnDisposition())) {
            return transfer;
        }
        if (transfer.status() == TransferStatus.NEEDS_REVIEW && disposition.equals(transfer.failureReason())) {
            return transfer;
        }
        TransferStatus expectedStatus = transfer.status();
        if (expectedStatus != TransferStatus.PROCESSING
                && expectedStatus != TransferStatus.AWAITING_MDN
                && expectedStatus != TransferStatus.RETRY_SCHEDULED) {
            throw new IllegalStateException("The transfer is not awaiting an asynchronous MDN");
        }
        As2DeliveryResult result = new As2DeliveryResult(false, disposition, null);
        if (result.processed()) transfer.deliver(disposition, clock.instant());
        else transfer.requireReview(disposition, clock.instant());
        if (repository.saveIfStatus(transfer, expectedStatus)) return transfer;
        return null;
    }

    private void requireMatchingMic(String expected, String received) {
        if (expected == null || received == null) throw new IllegalArgumentException("The MDN MIC is missing");
        String[] expectedFields = expected.split(",", 2);
        String[] receivedFields = received.split(",", 2);
        if (expectedFields.length != 2 || receivedFields.length != 2
                || !expectedFields[1].trim().equalsIgnoreCase("sha-256")
                || !receivedFields[1].trim().equalsIgnoreCase("sha-256")) {
            throw new IllegalArgumentException("The MDN MIC must use SHA-256");
        }
        byte[] expectedBytes;
        byte[] receivedBytes;
        try {
            expectedBytes = java.util.Base64.getDecoder().decode(expectedFields[0].trim());
            receivedBytes = java.util.Base64.getDecoder().decode(receivedFields[0].trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The MDN MIC is invalid", exception);
        }
        if (!java.security.MessageDigest.isEqual(expectedBytes, receivedBytes)) {
            throw new IllegalArgumentException("The MDN MIC does not match the outbound message");
        }
    }
}
