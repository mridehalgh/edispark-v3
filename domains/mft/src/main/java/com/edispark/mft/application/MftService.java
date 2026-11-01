package com.edispark.mft.application;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.PartnerStatus;
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
import java.security.interfaces.RSAPublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

public final class MftService {
    private final MftRepository repository;
    private final PayloadStore payloadStore;
    private final Clock clock;
    private final boolean legacyCryptoAllowed;
    private final boolean productionPolicy;
    private final long maximumPayloadBytes;
    private final java.time.Duration certificateRolloverGrace;
    private final InboundPayloadEventPublisher inboundEvents;

    public MftService(MftRepository repository, PayloadStore payloadStore, Clock clock) {
        this(repository, payloadStore, clock, false, false, 100L * 1024 * 1024,
                java.time.Duration.ofDays(7), InboundPayloadEventPublisher.noOp());
    }

    public MftService(MftRepository repository, PayloadStore payloadStore, Clock clock, boolean legacyCryptoAllowed) {
        this(repository, payloadStore, clock, legacyCryptoAllowed, false, 100L * 1024 * 1024,
                java.time.Duration.ofDays(7), InboundPayloadEventPublisher.noOp());
    }

    public MftService(MftRepository repository, PayloadStore payloadStore, Clock clock,
                      boolean legacyCryptoAllowed, boolean productionPolicy) {
        this(repository, payloadStore, clock, legacyCryptoAllowed, productionPolicy, 100L * 1024 * 1024,
                java.time.Duration.ofDays(7), InboundPayloadEventPublisher.noOp());
    }

    public MftService(MftRepository repository, PayloadStore payloadStore, Clock clock,
                      boolean legacyCryptoAllowed, boolean productionPolicy, long maximumPayloadBytes) {
        this(repository, payloadStore, clock, legacyCryptoAllowed, productionPolicy, maximumPayloadBytes,
                java.time.Duration.ofDays(7), InboundPayloadEventPublisher.noOp());
    }

    public MftService(MftRepository repository, PayloadStore payloadStore, Clock clock,
                      boolean legacyCryptoAllowed, boolean productionPolicy, long maximumPayloadBytes,
                      java.time.Duration certificateRolloverGrace) {
        this(repository, payloadStore, clock, legacyCryptoAllowed, productionPolicy, maximumPayloadBytes,
                certificateRolloverGrace, InboundPayloadEventPublisher.noOp());
    }

    public MftService(MftRepository repository, PayloadStore payloadStore, Clock clock,
                      boolean legacyCryptoAllowed, boolean productionPolicy, long maximumPayloadBytes,
                      java.time.Duration certificateRolloverGrace, InboundPayloadEventPublisher inboundEvents) {
        if (maximumPayloadBytes < 1) throw new IllegalArgumentException("maximumPayloadBytes must be positive");
        if (certificateRolloverGrace == null || certificateRolloverGrace.isNegative()
                || certificateRolloverGrace.isZero()) {
            throw new IllegalArgumentException("certificateRolloverGrace must be positive");
        }
        this.repository = repository;
        this.payloadStore = payloadStore;
        this.clock = clock;
        this.legacyCryptoAllowed = legacyCryptoAllowed;
        this.productionPolicy = productionPolicy;
        this.maximumPayloadBytes = maximumPayloadBytes;
        this.certificateRolloverGrace = certificateRolloverGrace;
        this.inboundEvents = java.util.Objects.requireNonNull(inboundEvents);
    }

    public PartnerProfile createPartner(CreatePartner command) {
        if (command.legacyCryptoEnabled() && !legacyCryptoAllowed) {
            throw new IllegalArgumentException("Legacy cryptography is disabled for this service");
        }
        String tenantId = tenantId();
        if (repository.findInboundPartner(command.localAs2Id(), command.remoteAs2Id()).isPresent()) {
            throw new IllegalArgumentException("The AS2 identity pair is already assigned to a workspace");
        }
        requireCertificate(tenantId, command.localCertificateId(), CertificateKind.LOCAL_IDENTITY,
                "Organisation AS2 certificate");
        requireCertificate(tenantId, command.partnerSigningCertificateId(), CertificateKind.PARTNER_PUBLIC,
                "Partner signing certificate");
        requireCertificate(tenantId, command.partnerEncryptionCertificateId(), CertificateKind.PARTNER_PUBLIC,
                "Partner encryption certificate");
        PartnerProfile partner = new PartnerProfile(UUID.randomUUID(), tenantId, command.name(),
                command.localAs2Id(), command.remoteAs2Id(), command.endpoint(), command.localCertificateId(),
                command.partnerSigningCertificateId(), command.partnerEncryptionCertificateId(),
                null, null, command.asynchronousMdnEndpoint(), command.mdnMode(),
                command.signingRequired(), command.encryptionRequired(), command.compressionEnabled(),
                command.legacyCryptoEnabled(), productionPolicy ? PartnerStatus.DRAFT : PartnerStatus.ACTIVE,
                clock.instant());
        if (productionPolicy) validateForActivation(partner);
        return repository.savePartner(partner);
    }

    public PartnerProfile activatePartner(UUID partnerId) {
        PartnerProfile partner = requirePartner(partnerId);
        validateForActivation(partner);
        Instant now = clock.instant();
        repository.listPartners(tenantId()).stream()
                .filter(item -> item.familyId().equals(partner.familyId()) && !item.id().equals(partner.id()))
                .map(item -> item.supersede(now)).filter(item -> item.status() == PartnerStatus.SUPERSEDED)
                .forEach(repository::updatePartner);
        return repository.updatePartner(partner.activate(now));
    }

    public PartnerProfile activatePartner(UUID partnerId, Instant effectiveAt) {
        PartnerProfile partner = requirePartner(partnerId);
        validateForActivation(partner);
        Instant now = clock.instant();
        return effectiveAt != null && effectiveAt.isAfter(now)
                ? repository.updatePartner(partner.schedule(effectiveAt, now)) : activatePartner(partnerId);
    }

    public PartnerProfile suspendPartner(UUID partnerId) {
        return repository.updatePartner(requirePartner(partnerId).suspend());
    }

    public PartnerProfile rotatePartnerCertificates(UUID partnerId, UUID signingCertificateId,
                                                     UUID encryptionCertificateId) {
        PartnerProfile candidate = requirePartner(partnerId)
                .withPartnerCertificates(signingCertificateId, encryptionCertificateId,
                        clock.instant().plus(certificateRolloverGrace));
        validateForActivation(candidate);
        return repository.updatePartner(candidate);
    }

    public List<PartnerProfile> listPartners() {
        String tenant = tenantId();
        Instant now = clock.instant();
        repository.listPartners(tenant).stream().map(item -> item.activateDue(now))
                .filter(item -> item.status() == PartnerStatus.ACTIVE)
                .forEach(item -> {
                    PartnerProfile current = repository.findPartner(tenant, item.id()).orElseThrow();
                    if (current.status() != item.status()) activatePartner(item.id());
                });
        return repository.listPartners(tenant);
    }

    public PartnerProfile createPartnerRevision(UUID partnerId) {
        return repository.savePartner(requirePartner(partnerId).newDraft(clock.instant()));
    }

    public PartnerProfile updatePartnerDraft(UUID partnerId, CreatePartner command) {
        PartnerProfile partner = requirePartner(partnerId);
        if (!partner.localAs2Id().equals(command.localAs2Id()) || !partner.remoteAs2Id().equals(command.remoteAs2Id())
                || !partner.localCertificateId().equals(command.localCertificateId())) {
            throw new IllegalArgumentException("AS2 identities and the local identity certificate are fixed for a revision family");
        }
        PartnerProfile revised = partner.revise(command.name(), command.endpoint(), command.asynchronousMdnEndpoint(),
                command.mdnMode(), command.signingRequired(), command.encryptionRequired(), command.compressionEnabled(),
                command.legacyCryptoEnabled(), clock.instant())
                .withPartnerCertificates(command.partnerSigningCertificateId(), command.partnerEncryptionCertificateId());
        return repository.updatePartner(revised);
    }

    public PartnerProfile archivePartner(UUID partnerId) {
        return repository.updatePartner(requirePartner(partnerId).archive(clock.instant()));
    }

    public void deletePartnerDraft(UUID partnerId) {
        PartnerProfile partner = requirePartner(partnerId);
        if (partner.status() != PartnerStatus.DRAFT) {
            throw new IllegalStateException("Only an unused draft AS2 revision can be permanently deleted");
        }
        boolean used = repository.listTransfers(tenantId()).stream().anyMatch(item -> item.partnerId().equals(partnerId));
        if (used) throw new IllegalStateException("AS2 profile revision is referenced by transfer evidence");
        repository.deletePartner(tenantId(), partnerId);
    }

    public Usage partnerUsage(UUID partnerId) {
        requirePartner(partnerId);
        List<TransferId> transfers = repository.listTransfers(tenantId()).stream()
                .filter(item -> item.partnerId().equals(partnerId)).map(Transfer::id).toList();
        return new Usage(transfers.isEmpty() && requirePartner(partnerId).status() == PartnerStatus.DRAFT, transfers);
    }

    public Transfer submitOutbound(SubmitOutbound command) throws IOException {
        String tenantId = tenantId();
        validateFilename(command.filename());
        validateContentType(command.contentType());
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 200) {
            throw new IllegalArgumentException("Idempotency key must contain 1-200 characters");
        }
        Transfer existing = repository.findByIdempotencyKey(tenantId, command.idempotencyKey()).orElse(null);
        if (existing != null) return existing;
        PartnerProfile partner = repository.findPartner(tenantId, command.partnerId())
                .orElseThrow(() -> new MftNotFoundException("Partner was not found"));
        requireActive(partner);
        Instant now = clock.instant();
        Transfer transfer = Transfer.outbound(tenantId, command.partnerId(), command.filename(),
                command.contentType(), messageId(), command.idempotencyKey(), command.resendOf(), command.provenance(), now);
        transfer.attachPayload(payloadStore.put(tenantId, transfer.id(), command.filename(),
                new MaximumSizeInputStream(command.content(), maximumPayloadBytes)), now);
        return repository.saveOutboundIfAbsent(transfer);
    }

    public Transfer resend(TransferId originalId) throws IOException {
        String tenantId = tenantId();
        Transfer original = getTransfer(originalId);
        InputStream content = payloadStore.get(tenantId, original.payload());
        String key = "resend:" + original.id() + ":" + UUID.randomUUID();
        return submitOutbound(new SubmitOutbound(original.partnerId(), key, original.filename(),
                original.contentType(), content, original.id(), original.provenance()));
    }

    public List<Transfer> listTransfers() {
        return repository.listTransfers(tenantId());
    }

    public Transfer receiveInbound(String localAs2Id, String remoteAs2Id, String messageId,
                                   String filename, String contentType, byte[] content) throws IOException {
        PartnerProfile partner = repository.findInboundPartner(localAs2Id, remoteAs2Id)
                .orElseThrow(() -> new MftNotFoundException("Inbound AS2 partnership was not found"));
        requireActive(partner);
        String contentSha256 = sha256(content);
        Transfer prior = repository.findInboundByMessageId(partner.tenantId(), messageId).orElse(null);
        if (prior != null) {
            if (prior.payload() != null && prior.payload().sha256().equals(contentSha256)) {
                publishInbound(prior);
                return prior;
            }
            throw new As2ReplayConflictException();
        }
        Instant now = clock.instant();
        Transfer transfer = Transfer.inbound(partner.tenantId(), partner.id(), filename, contentType, messageId, now);
        transfer.attachPayload(payloadStore.put(partner.tenantId(), transfer.id(), filename,
                new ByteArrayInputStream(content)), now);
        transfer.deliver("automatic-action/MDN-sent-automatically; processed", now);
        Transfer saved = repository.saveInboundIfAbsent(transfer);
        publishInbound(saved);
        return saved;
    }

    private void publishInbound(Transfer transfer) {
        UUID eventId = UUID.nameUUIDFromBytes((transfer.tenantId() + ":inbound:" + transfer.id())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        inboundEvents.publish(new InboundPayloadReceived(eventId, transfer.tenantId(), transfer.id().value(),
                transfer.partnerId(), transfer.as2MessageId(), transfer.filename(), transfer.contentType(),
                transfer.payload().sha256(), transfer.payload().size(), transfer.createdAt()));
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

    private CertificateRecord requireCertificate(String tenantId, UUID certificateId, CertificateKind kind,
                                                  String label) {
        CertificateRecord certificate = repository.findCertificate(tenantId, certificateId)
                .orElseThrow(() -> new MftNotFoundException(label + " was not found"));
        if (certificate.kind() != kind) throw new IllegalArgumentException(label + " has the wrong purpose");
        return certificate;
    }

    private PartnerProfile requirePartner(UUID partnerId) {
        return repository.findPartner(tenantId(), partnerId)
                .orElseThrow(() -> new MftNotFoundException("Partner was not found"));
    }

    private void requireActive(PartnerProfile partner) {
        if (partner.status() != PartnerStatus.ACTIVE) {
            throw new IllegalStateException("Partner is not active");
        }
    }

    private void validateForActivation(PartnerProfile partner) {
        CertificateRecord local = requireCertificate(partner.tenantId(), partner.localCertificateId(),
                CertificateKind.LOCAL_IDENTITY, "Organisation AS2 certificate");
        CertificateRecord signing = requireCertificate(partner.tenantId(), partner.partnerSigningCertificateId(),
                CertificateKind.PARTNER_PUBLIC, "Partner signing certificate");
        CertificateRecord encryption = requireCertificate(partner.tenantId(), partner.partnerEncryptionCertificateId(),
                CertificateKind.PARTNER_PUBLIC, "Partner encryption certificate");
        validateCertificate(local, CertificatePurpose.SIGNING);
        validateCertificate(signing, CertificatePurpose.SIGNING);
        validateCertificate(encryption, CertificatePurpose.ENCRYPTION);
        if (!productionPolicy) return;
        if (!"https".equalsIgnoreCase(partner.endpoint().getScheme())) {
            throw new IllegalArgumentException("Production partner endpoint must use HTTPS");
        }
        if (!partner.signingRequired() || !partner.encryptionRequired()) {
            throw new IllegalArgumentException("Production AS2 requires signing and encryption");
        }
        if (partner.compressionEnabled()) {
            throw new IllegalArgumentException("CMS compression is not supported by the production profile");
        }
        if (partner.legacyCryptoEnabled()) {
            throw new IllegalArgumentException("Legacy cryptography cannot be activated in production");
        }
        if (partner.mdnMode() == MdnMode.ASYNCHRONOUS) {
            if (partner.asynchronousMdnEndpoint() == null
                    || !"https".equalsIgnoreCase(partner.asynchronousMdnEndpoint().getScheme())) {
                throw new IllegalArgumentException("Asynchronous MDN delivery requires an explicit HTTPS endpoint");
            }
        }
    }

    private void validateCertificate(CertificateRecord record, CertificatePurpose purpose) {
        Instant now = clock.instant();
        if (now.isBefore(record.notBefore()) || !now.isBefore(record.notAfter())) {
            throw new IllegalArgumentException(record.name() + " is not currently valid");
        }
        try {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(
                            record.publicCertificatePem().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            if (!(certificate.getPublicKey() instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException(record.name() + " must use an RSA key of at least 2048 bits");
            }
            boolean[] usage = certificate.getKeyUsage();
            int requiredBit = purpose == CertificatePurpose.SIGNING ? 0 : 2;
            if (usage != null && (usage.length <= requiredBit || !usage[requiredBit])) {
                throw new IllegalArgumentException(record.name() + " does not permit " + purpose.label);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(record.name() + " is not a valid X.509 certificate", exception);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.contains("/") || filename.contains("\\")
                || filename.chars().anyMatch(value -> value < 32 || value == 127)) {
            throw new IllegalArgumentException("Filename must be a plain 1-255 character name");
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank() || contentType.length() > 200
                || contentType.indexOf('\r') >= 0 || contentType.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Content type is invalid");
        }
    }

    public record CreatePartner(String name, String localAs2Id, String remoteAs2Id, URI endpoint,
                                UUID localCertificateId, UUID partnerSigningCertificateId,
                                UUID partnerEncryptionCertificateId, URI asynchronousMdnEndpoint,
                                MdnMode mdnMode, boolean signingRequired, boolean encryptionRequired,
                                boolean compressionEnabled, boolean legacyCryptoEnabled) {
        public CreatePartner(String name, String localAs2Id, String remoteAs2Id, URI endpoint,
                             UUID localCertificateId, UUID partnerCertificateId, MdnMode mdnMode,
                             boolean signingRequired, boolean encryptionRequired,
                             boolean compressionEnabled, boolean legacyCryptoEnabled) {
            this(name, localAs2Id, remoteAs2Id, endpoint, localCertificateId, partnerCertificateId,
                    partnerCertificateId, null, mdnMode, signingRequired, encryptionRequired,
                    compressionEnabled, legacyCryptoEnabled);
        }
    }

    public record SubmitOutbound(UUID partnerId, String idempotencyKey, String filename,
                                 String contentType, InputStream content, TransferId resendOf,
                                 com.edispark.mft.domain.model.TransferProvenance provenance) {
        public SubmitOutbound(UUID partnerId, String idempotencyKey, String filename, String contentType,
                              InputStream content, TransferId resendOf) {
            this(partnerId, idempotencyKey, filename, contentType, content, resendOf, null);
        }
    }

    public record Usage(boolean deletable, List<TransferId> transferIds) { }

    private enum CertificatePurpose {
        SIGNING("digital signatures"), ENCRYPTION("key encipherment");

        private final String label;

        CertificatePurpose(String label) {
            this.label = label;
        }
    }

    private static final class MaximumSizeInputStream extends java.io.FilterInputStream {
        private final long maximumBytes;
        private long bytesRead;

        private MaximumSizeInputStream(InputStream input, long maximumBytes) {
            super(input);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) account(1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) account(read);
            return read;
        }

        private void account(int count) throws IOException {
            bytesRead += count;
            if (bytesRead > maximumBytes) throw new IOException("MFT payload exceeds the configured size limit");
        }
    }
}
