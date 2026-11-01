package com.edispark.mft.infrastructure.as2;

import com.edispark.mft.application.As2TransportException;
import com.edispark.mft.application.CertificateService;
import com.edispark.mft.application.MdnService;
import com.edispark.mft.application.MftNotFoundException;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.repository.MftRepository;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeBodyPart;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.UUID;

public final class KmsAs2InboundService {
    private final MftRepository repository;
    private final CertificateService certificates;
    private final MftService transfers;
    private final MdnService mdns;
    private final KmsCmsCodec cms;
    private final As2EndpointPolicy endpoints;
    private final HttpClient http;

    public KmsAs2InboundService(MftRepository repository, CertificateService certificates,
                                MftService transfers, MdnService mdns, KmsCmsCodec cms,
                                As2EndpointPolicy endpoints) {
        this.repository = repository;
        this.certificates = certificates;
        this.transfers = transfers;
        this.mdns = mdns;
        this.cms = cms;
        this.endpoints = endpoints;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public InboundResponse receive(String as2From, String as2To, String messageId, String contentType,
                                   String receiptDeliveryOption, byte[] body) {
        PartnerProfile partner = partner(as2To, as2From);
        try {
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
            byte[] mic = sha256(body);
            if (signed) {
                KmsCmsCodec.VerifiedContent verified = cms.verify(current, partnerCertificate(partner));
                current = verified.content();
                mic = verified.mic();
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
                sendAsyncMdn(partner, URI.create(receiptDeliveryOption), mdn);
                return InboundResponse.empty();
            }
            if (partner.mdnMode() == MdnMode.ASYNCHRONOUS) {
                throw new IllegalArgumentException("Asynchronous MDN URL is required for this partnership");
            }
            KmsCmsCodec.EncodedBody encoded = cms.encode(mdn);
            return new InboundResponse(encoded.contentType(), encoded.bytes(), messageId());
        } catch (As2TransportException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new As2TransportException("AS2 MDN delivery was interrupted", exception, true);
        } catch (IOException exception) {
            throw new As2TransportException("AS2 storage or MDN delivery was unavailable", exception, true);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("AS2 message could not be processed", exception);
        }
    }

    public void receiveMdn(String as2From, String as2To, String contentType, byte[] body) {
        PartnerProfile partner = partner(as2To, as2From);
        KmsCmsCodec.VerifiedContent verified = cms.verify(cms.parse(contentType, body), partnerCertificate(partner));
        KmsCmsCodec.MdnReceipt receipt = cms.readMdn(verified.content());
        mdns.record(partner, receipt.originalMessageId(), receipt.disposition(), receipt.receivedContentMic());
    }

    private void sendAsyncMdn(PartnerProfile partner, URI callback, MimeBodyPart mdn)
            throws IOException, InterruptedException {
        endpoints.requireSameOrigin(partner.endpoint(), callback);
        KmsCmsCodec.EncodedBody encoded = cms.encode(mdn);
        HttpRequest request = HttpRequest.newBuilder(callback).timeout(Duration.ofSeconds(30))
                .header("Content-Type", encoded.contentType())
                .header("AS2-Version", "1.2")
                .header("AS2-From", partner.localAs2Id())
                .header("AS2-To", partner.remoteAs2Id())
                .header("Message-ID", messageId())
                .POST(HttpRequest.BodyPublishers.ofByteArray(encoded.bytes())).build();
        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new As2TransportException("Partner rejected asynchronous MDN with HTTP "
                    + response.statusCode(), null, response.statusCode() >= 500 || response.statusCode() == 429);
        }
    }

    private PartnerProfile partner(String localAs2Id, String remoteAs2Id) {
        if (localAs2Id == null || remoteAs2Id == null) {
            throw new IllegalArgumentException("AS2-From and AS2-To are required");
        }
        return repository.findInboundPartner(unquote(localAs2Id), unquote(remoteAs2Id))
                .orElseThrow(() -> new MftNotFoundException("Inbound AS2 partnership was not found"));
    }

    private X509Certificate partnerCertificate(PartnerProfile partner) {
        try {
            byte[] bytes = certificates.load(partner.tenantId(), partner.partnerCertificateId()).bytes();
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
