package com.edispark.mft.application;

import com.edispark.mft.domain.model.MdnDelivery;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.PayloadStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

public final class AsyncMdnOutboxService {
    private final MftRepository repository;
    private final PayloadStore payloads;
    private final Clock clock;

    public AsyncMdnOutboxService(MftRepository repository, PayloadStore payloads, Clock clock) {
        this.repository = repository;
        this.payloads = payloads;
        this.clock = clock;
    }

    public MdnDelivery enqueue(PartnerProfile partner, URI callback, String contentType, byte[] body,
                               String messageId, String originalMessageId) throws IOException {
        UUID id = UUID.nameUUIDFromBytes((partner.tenantId() + "\u0000" + originalMessageId)
                .getBytes(StandardCharsets.UTF_8));
        TransferId payloadId = new TransferId(id);
        var payload = payloads.put(partner.tenantId(), payloadId, "signed-mdn.mime",
                new ByteArrayInputStream(body));
        MdnDelivery delivery = MdnDelivery.pending(id, partner.tenantId(), partner.id(), callback, contentType,
                partner.localAs2Id(), partner.remoteAs2Id(), messageId, originalMessageId, payload, clock.instant());
        return repository.saveMdnDeliveryIfAbsent(delivery);
    }
}
