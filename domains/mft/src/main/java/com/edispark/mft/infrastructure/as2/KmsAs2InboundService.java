package com.edispark.mft.infrastructure.as2;

import com.edispark.mft.application.As2TransportException;
import com.edispark.mft.application.AsyncMdnOutboxService;
import com.edispark.mft.application.CertificateService;
import com.edispark.mft.application.MdnService;
import com.edispark.mft.application.MftNotFoundException;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.PartnerStatus;
import com.edispark.mft.domain.repository.MftRepository;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeBodyPart;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.UUID;

public final class KmsAs2InboundService {
    private final MftRepository repository;
    private final CertificateService certificates;
    private final MftService transfers;
    private final MdnService mdns;
    private final KmsCmsCodec cms;
    private final As2EndpointPolicy endpoints;
    private final As2AdmissionController admission;
    private final AsyncMdnOutboxService mdnOutbox;
    private final Clock clock;

    public KmsAs2InboundService(MftRepository repository, CertificateService certificates,
                                MftService transfers, MdnService mdns, KmsCmsCodec cms,
                                As2EndpointPolicy endpoints, As2AdmissionController admission,
                                AsyncMdnOutboxService mdnOutbox, Clock clock) {
        this.repository = repository;
        this.certificates = certificates;
        this.transfers = transfers;
        this.mdns = mdns;
        this.cms = cms;
        this.endpoints = endpoints;
        this.admission = admission;
        this.mdnOutbox = mdnOutbox;
        this.clock = clock;
    }

    public InboundResponse receive(String as2From, String as2To, String messageId, String contentType,
                                   String receiptDeliveryOption, byte[] body) {
        validateMessageId(messageId);
        PartnerProfile partner = partner(as2To, as2From);
        boolean authenticated = false;
        byte[] authenticatedMic = sha256(body);
        try (As2AdmissionController.Permit ignored = admission.acquirePartner(partner)) {
            MimeBodyPart current = cms.parse(contentType, body);
            boolean encrypted = isEncrypted(contentType);
            if (partner.encryptionRequired() && !encrypted) {
                throw new IllegalArgumentException("This AS2 partnership requires encryption");
            }
            if (encrypted) current = cms.decrypt(current);
            boolean signed = current.isMimeType("multipart/signed");
            if (partner.signingRequired() && !signed) {
                throw new IllegalArgumentException("This AS2 partnership requires a CMS signature");
            }
            byte[] mic = authenticatedMic;
            if (signed) {
                KmsCmsCodec.VerifiedContent verified = verifyPartnerSignature(current, partner);
                current = verified.content();
                mic = verified.mic();
                authenticated = true;
                authenticatedMic = mic;
            }
            if (isCompressed(current.getContentType())) {
                throw new IllegalArgumentException("CMS compression is not supported by the KMS-native profile");
            }
            String filename = safeFilename(current.getFileName());
            String payloadType = current.getContentType();
            transfers.receiveInbound(partner.localAs2Id(), partner.remoteAs2Id(), messageId,
                    filename, payloadType, cms.content(current));
            MimeBodyPart mdn = cms.signedMdn(messageId, "processed", mic);
            if (receiptDeliveryOption != null && !receiptDeliveryOption.isBlank()) {
                if (partner.mdnMode() != MdnMode.ASYNCHRONOUS) {
                    throw new IllegalArgumentException("Partner profile does not allow asynchronous MDNs");
                }
                queueAsyncMdn(partner, URI.create(receiptDeliveryOption), mdn, messageId);
                return InboundResponse.empty();
            }
            if (partner.mdnMode() == MdnMode.ASYNCHRONOUS) {
                throw new IllegalArgumentException("Asynchronous MDN URL is required for this partnership");
            }
            KmsCmsCodec.EncodedBody encoded = cms.encode(mdn);
            return new InboundResponse(encoded.contentType(), encoded.bytes(), messageId());
        } catch (As2TransportException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new As2TransportException("AS2 storage or MDN delivery was unavailable", exception, true);
        } catch (IllegalArgumentException exception) {
            if (authenticated) {
                return negativeMdn(partner, messageId, receiptDeliveryOption, authenticatedMic);
            }
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("AS2 message could not be processed", exception);
        }
    }

    private InboundResponse negativeMdn(PartnerProfile partner, String originalMessageId,
                                        String receiptDeliveryOption, byte[] mic) {
        MimeBodyPart mdn = cms.signedMdn(originalMessageId,
                "failed/error: unexpected-processing-error", mic);
        try {
            if (receiptDeliveryOption != null && !receiptDeliveryOption.isBlank()) {
                queueAsyncMdn(partner, URI.create(receiptDeliveryOption), mdn, originalMessageId);
                return InboundResponse.empty();
            }
            KmsCmsCodec.EncodedBody encoded = cms.encode(mdn);
            return new InboundResponse(encoded.contentType(), encoded.bytes(), messageId());
        } catch (IOException exception) {
            throw new As2TransportException("Negative AS2 MDN could not be queued", exception, true);
        }
    }

    public void receiveMdn(String as2From, String as2To, String contentType, byte[] body) {
        PartnerProfile partner = partner(as2To, as2From);
        try (As2AdmissionController.Permit ignored = admission.acquirePartner(partner)) {
            KmsCmsCodec.VerifiedContent verified = verifyPartnerSignature(cms.parse(contentType, body), partner);
            KmsCmsCodec.MdnReceipt receipt = cms.readMdn(verified.content());
            mdns.record(partner, receipt.originalMessageId(), receipt.disposition(), receipt.receivedContentMic());
        }
    }

    private void queueAsyncMdn(PartnerProfile partner, URI callback, MimeBodyPart mdn,
                               String originalMessageId) throws IOException {
        endpoints.validate(callback);
        if (partner.asynchronousMdnEndpoint() == null || !partner.asynchronousMdnEndpoint().equals(callback)) {
            throw new As2TransportException("Asynchronous MDN URL does not match the activated partner profile",
                    null, false);
        }
        KmsCmsCodec.EncodedBody encoded = cms.encode(mdn);
        mdnOutbox.enqueue(partner, callback, encoded.contentType(), encoded.bytes(), messageId(), originalMessageId);
    }

    private PartnerProfile partner(String localAs2Id, String remoteAs2Id) {
        validateAs2Id(localAs2Id);
        validateAs2Id(remoteAs2Id);
        PartnerProfile partner = repository.findInboundPartner(unquote(localAs2Id), unquote(remoteAs2Id))
                .orElseThrow(() -> new MftNotFoundException("Inbound AS2 partnership was not found"));
        if (partner.status() != PartnerStatus.ACTIVE) {
            throw new MftNotFoundException("Inbound AS2 partnership was not found");
        }
        return partner;
    }

    private void validateAs2Id(String value) {
        if (value == null || value.isBlank() || value.length() > 130
                || value.chars().anyMatch(character -> character < 32 || character > 126)) {
            throw new IllegalArgumentException("AS2-From and AS2-To must be printable AS2 identifiers");
        }
        String unquoted = unquote(value);
        if (unquoted.isBlank() || unquoted.length() > 128) {
            throw new IllegalArgumentException("AS2-From and AS2-To must be printable AS2 identifiers");
        }
    }

    private void validateMessageId(String value) {
        if (value == null || value.length() < 3 || value.length() > 998
                || !value.startsWith("<") || !value.endsWith(">")
                || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException("Message-ID must be a bracketed printable identifier");
        }
    }

    private X509Certificate partnerCertificate(PartnerProfile partner) {
        return partnerCertificate(partner, partner.partnerSigningCertificateId());
    }

    private KmsCmsCodec.VerifiedContent verifyPartnerSignature(MimeBodyPart signed, PartnerProfile partner) {
        try {
            return cms.verify(signed, partnerCertificate(partner));
        } catch (IllegalArgumentException currentFailure) {
            if (partner.previousPartnerSigningCertificateId() == null
                    || partner.signingCertificateGraceUntil() == null
                    || !clock.instant().isBefore(partner.signingCertificateGraceUntil())) {
                throw currentFailure;
            }
            return cms.verify(signed, partnerCertificate(partner, partner.previousPartnerSigningCertificateId()));
        }
    }

    private X509Certificate partnerCertificate(PartnerProfile partner, UUID certificateId) {
        try {
            byte[] bytes = certificates.load(partner.tenantId(), certificateId).bytes();
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Partner certificate could not be loaded", exception);
        }
    }

    private boolean isEncrypted(String contentType) {
        try {
            ContentType parsed = new ContentType(contentType);
            String base = parsed.getBaseType();
            return (base.equalsIgnoreCase("application/pkcs7-mime")
                    || base.equalsIgnoreCase("application/x-pkcs7-mime"))
                    && "enveloped-data".equalsIgnoreCase(parsed.getParameter("smime-type"));
        } catch (Exception exception) {
            throw new IllegalArgumentException("AS2 Content-Type is invalid", exception);
        }
    }

    private boolean isCompressed(String contentType) {
        try {
            ContentType parsed = new ContentType(contentType);
            String base = parsed.getBaseType();
            return (base.equalsIgnoreCase("application/pkcs7-mime")
                    || base.equalsIgnoreCase("application/x-pkcs7-mime"))
                    && "compressed-data".equalsIgnoreCase(parsed.getParameter("smime-type"));
        } catch (Exception exception) {
            throw new IllegalArgumentException("AS2 Content-Type is invalid", exception);
        }
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) return "as2-message.edi";
        if (value.length() > 255 || value.contains("/") || value.contains("\\") || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("AS2 filename is invalid");
        }
        return value;
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String messageId() {
        return "<" + UUID.randomUUID() + "@as2.edispark.invalid>";
    }

    private byte[] sha256(byte[] value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record InboundResponse(String contentType, byte[] body, String messageId) {
        public InboundResponse {
            body = body.clone();
        }

        public static InboundResponse empty() {
            return new InboundResponse(null, new byte[0], null);
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
