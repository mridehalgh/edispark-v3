package com.edispark.mft.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsyncMdnIsolationTest {
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final String MIC = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}) + ", sha-256";

    @Test
    void asynchronousMdnCanOnlyCompleteTheMatchingPartnerTransferInItsTenant() {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        PartnerProfile owner = partner("tenant-a");
        PartnerProfile other = partner("tenant-a");
        Transfer transfer = awaiting(owner, "<same-message@example.invalid>");
        repository.saveOutboundIfAbsent(transfer);
        MdnService service = new MdnService(repository, Clock.fixed(NOW.plusSeconds(5), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.record(other, transfer.as2MessageId(),
                "automatic-action/MDN-sent-automatically; processed", MIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrong trading partner");
        assertThat(repository.findTransfer("tenant-a", transfer.id()).orElseThrow().status())
                .isEqualTo(TransferStatus.AWAITING_MDN);

        Transfer completed = service.record(owner, transfer.as2MessageId(),
                "automatic-action/MDN-sent-automatically; processed", MIC);
        assertThat(completed.status()).isEqualTo(TransferStatus.DELIVERED);
        assertThat(service.record(owner, transfer.as2MessageId(), completed.mdnDisposition(), MIC))
                .isSameAs(completed);
    }

    @Test
    void asynchronousMdnRejectsAValidSignatureWithTheWrongMic() {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        PartnerProfile owner = partner("tenant-a");
        Transfer transfer = awaiting(owner, "<message@example.invalid>");
        repository.saveOutboundIfAbsent(transfer);
        MdnService service = new MdnService(repository, Clock.fixed(NOW.plusSeconds(5), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.record(owner, transfer.as2MessageId(),
                "automatic-action/MDN-sent-automatically; processed",
                Base64.getEncoder().encodeToString(new byte[]{9}) + ", sha-256"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
        assertThat(transfer.status()).isEqualTo(TransferStatus.AWAITING_MDN);
    }

    private Transfer awaiting(PartnerProfile partner, String messageId) {
        Transfer transfer = Transfer.outbound(partner.tenantId(), partner.id(), "order.edi",
                "application/edifact", messageId, UUID.randomUUID().toString(), null, NOW);
        transfer.startAttempt(NOW.plusSeconds(1));
        transfer.recordSentContentMic(MIC, NOW.plusSeconds(2));
        transfer.awaitMdn(NOW.plusSeconds(3));
        return transfer;
    }

    private PartnerProfile partner(String tenantId) {
        return new PartnerProfile(UUID.randomUUID(), tenantId, "Retailer", "EDISPARK-" + UUID.randomUUID(),
                "RETAILER-" + UUID.randomUUID(), URI.create("https://retailer.example/as2"),
                UUID.randomUUID(), UUID.randomUUID(), MdnMode.ASYNCHRONOUS,
                true, true, false, false, NOW);
    }
}
