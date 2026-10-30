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
        if (!transfer.partnerId().equals(partner.id())) {
            throw new IllegalArgumentException("The MDN was signed by the wrong trading partner");
        }
        requireMatchingMic(transfer.sentContentMic(), receivedContentMic);
        if (transfer.status() == TransferStatus.DELIVERED && disposition.equals(transfer.mdnDisposition())) {
            return transfer;
        }
        if (transfer.status() != TransferStatus.AWAITING_MDN) {
            throw new IllegalStateException("The transfer is not awaiting an asynchronous MDN");
        }
        As2DeliveryResult result = new As2DeliveryResult(false, disposition, null);
        if (result.processed()) transfer.deliver(disposition, clock.instant());
        else transfer.requireReview(disposition, clock.instant());
        return repository.save(transfer);
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
