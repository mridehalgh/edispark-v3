package com.edispark.mft.application;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.PayloadStore;
import io.cottn.core.tenancy.TenantContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class MftService {
    private final MftRepository repository;
    private final PayloadStore payloadStore;
    private final Clock clock;
    private final boolean legacyCryptoAllowed;

    public MftService(MftRepository repository, PayloadStore payloadStore, Clock clock) {
        this(repository, payloadStore, clock, false);
    }

    public MftService(MftRepository repository, PayloadStore payloadStore, Clock clock, boolean legacyCryptoAllowed) {
        this.repository = repository;
        this.payloadStore = payloadStore;
        this.clock = clock;
        this.legacyCryptoAllowed = legacyCryptoAllowed;
    }

    public PartnerProfile createPartner(CreatePartner command) {
        if (command.legacyCryptoEnabled() && !legacyCryptoAllowed) {
            throw new IllegalArgumentException("Legacy cryptography is disabled for this service");
        }
        if (repository.findInboundPartner(command.localAs2Id(), command.remoteAs2Id()).isPresent()) {
            throw new IllegalArgumentException("The AS2 identity pair is already assigned to a workspace");
        }
        String tenantId = tenantId();
        PartnerProfile partner = new PartnerProfile(UUID.randomUUID(), tenantId, command.name(),
                command.localAs2Id(), command.remoteAs2Id(), command.endpoint(), command.localCertificateId(),
                command.partnerCertificateId(), command.mdnMode(),
                command.signingRequired(), command.encryptionRequired(), command.compressionEnabled(),
                command.legacyCryptoEnabled(), clock.instant());
        return repository.savePartner(partner);
    }

    public List<PartnerProfile> listPartners() {
        return repository.listPartners(tenantId());
    }

    public Transfer submitOutbound(SubmitOutbound command) throws IOException {
        String tenantId = tenantId();
        Transfer existing = repository.findByIdempotencyKey(tenantId, command.idempotencyKey()).orElse(null);
        if (existing != null) return existing;
        repository.findPartner(tenantId, command.partnerId())
                .orElseThrow(() -> new MftNotFoundException("Partner was not found"));
        Instant now = clock.instant();
        Transfer transfer = Transfer.outbound(tenantId, command.partnerId(), command.filename(),
                command.contentType(), messageId(), command.idempotencyKey(), command.resendOf(), now);
        transfer.attachPayload(payloadStore.put(tenantId, transfer.id(), command.filename(), command.content()), now);
        return repository.saveOutboundIfAbsent(transfer);
    }

    public Transfer resend(TransferId originalId) throws IOException {
        String tenantId = tenantId();
        Transfer original = getTransfer(originalId);
        InputStream content = payloadStore.get(tenantId, original.payload());
        String key = "resend:" + original.id() + ":" + UUID.randomUUID();
        return submitOutbound(new SubmitOutbound(original.partnerId(), key, original.filename(),
                original.contentType(), content, original.id()));
    }

    public List<Transfer> listTransfers() {
        return repository.listTransfers(tenantId());
    }

    public Transfer receiveInbound(String localAs2Id, String remoteAs2Id, String messageId,
                                   String filename, String contentType, byte[] content) throws IOException {
        PartnerProfile partner = repository.findInboundPartner(localAs2Id, remoteAs2Id)
                .orElseThrow(() -> new MftNotFoundException("Inbound AS2 partnership was not found"));
        Transfer duplicate = repository.findInboundDuplicate(partner.tenantId(), messageId, sha256(content)).orElse(null);
        if (duplicate != null) return duplicate;
        Instant now = clock.instant();
        Transfer transfer = Transfer.inbound(partner.tenantId(), partner.id(), filename, contentType, messageId, now);
        transfer.attachPayload(payloadStore.put(partner.tenantId(), transfer.id(), filename,
                new ByteArrayInputStream(content)), now);
        transfer.deliver("automatic-action/MDN-sent-automatically; processed", now);
        return repository.save(transfer);
    }

    public Transfer getTransfer(TransferId id) {
        return repository.findTransfer(tenantId(), id)
                .orElseThrow(() -> new MftNotFoundException("Transfer was not found"));
    }

    public InputStream getPayload(TransferId id) throws IOException {
        Transfer transfer = getTransfer(id);
        return payloadStore.get(tenantId(), transfer.payload());
    }

    private String tenantId() {
        return TenantContext.getCurrentTenant().asString();
    }

    private String messageId() {
        return "<" + UUID.randomUUID() + "@as2.edispark.local>";
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CreatePartner(String name, String localAs2Id, String remoteAs2Id, URI endpoint,
                                UUID localCertificateId, UUID partnerCertificateId,
                                MdnMode mdnMode, boolean signingRequired, boolean encryptionRequired,
                                boolean compressionEnabled, boolean legacyCryptoEnabled) {
    }

    public record SubmitOutbound(UUID partnerId, String idempotencyKey, String filename,
                                 String contentType, InputStream content, TransferId resendOf) {
    }
}
