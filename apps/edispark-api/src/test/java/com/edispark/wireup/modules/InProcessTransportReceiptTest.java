package com.edispark.wireup.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.edispark.exchange.application.TransportAcl;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferProvenance;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InProcessTransportReceiptTest {
    @ParameterizedTest
    @CsvSource({"automatic-action/MDN-sent-automatically; processed,true", "automatic-action/MDN-sent-automatically; processed/error,false", "automatic-action/MDN-sent-automatically; failed,false"})
    void requiresAPositiveRetainedMdnAndMatchingExchangeProvenance(String disposition, boolean accepted) {
        UUID exchangeId = UUID.randomUUID(); var now = Instant.now();
        var provenance = new TransferProvenance(exchangeId, UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var transfer = Transfer.outbound("test-tenant", UUID.randomUUID(), "test.edi", "application/edifact", "message", "once", null, provenance, now);
        transfer.startAttempt(now); transfer.deliver(disposition, now.plusSeconds(1));
        var mft = mock(MftService.class); when(mft.getTransfer(transfer.id())).thenReturn(transfer);
        var acl = new InProcessTransportAcl(mft);
        assertThat(acl.receipt(transfer.id().value(), exchangeId).outcome())
                .isEqualTo(accepted ? TransportAcl.Outcome.DELIVERED : TransportAcl.Outcome.NEEDS_REVIEW);
        assertThat(acl.receipt(transfer.id().value(), UUID.randomUUID()).outcome()).isEqualTo(TransportAcl.Outcome.NEEDS_REVIEW);
    }
}
