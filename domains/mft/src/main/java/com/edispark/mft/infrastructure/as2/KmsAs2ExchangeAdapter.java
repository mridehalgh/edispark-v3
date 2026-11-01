package com.edispark.mft.infrastructure.as2;

import com.edispark.mft.application.As2DeliveryResult;
import com.edispark.mft.application.As2ExchangePort;
import com.edispark.mft.application.As2TransportException;
import com.edispark.mft.application.CertificateService;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.domain.repository.MftRepository;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

public final class KmsAs2ExchangeAdapter implements As2ExchangePort {
    private final CertificateService certificates;
    private final KmsAs2Identity identity;
    private final KmsCmsCodec cms;
    private final As2EndpointPolicy endpoints;
    private final HttpClient http;
    private final URI asynchronousMdnUrl;
    private final Clock clock;
    private final MftRepository repository;

    public KmsAs2ExchangeAdapter(CertificateService certificates, KmsAs2Identity identity,
                                 KmsCmsCodec cms, As2EndpointPolicy endpoints,
                                 URI asynchronousMdnUrl, Clock clock, MftRepository repository) {
        this.certificates = certificates;
        this.identity = identity;
        this.cms = cms;
        this.endpoints = endpoints;
        this.asynchronousMdnUrl = asynchronousMdnUrl;
        this.clock = clock;
        this.repository = repository;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public As2DeliveryResult send(PartnerProfile partner, Transfer transfer, byte[] payload) {
        endpoints.validate(partner.endpoint());
        try {
            if (partner.compressionEnabled()) {
                throw new IllegalArgumentException("CMS compression is not supported by the KMS-native profile");
            }
            requireConfiguredIdentity(partner);
            X509Certificate partnerCertificate = partnerCertificate(partner);
            KmsCmsCodec.SignedContent signed = cms.signWithMic(
                    cms.payload(payload, transfer.contentType(), transfer.filename()));
            String contentMic = Base64.getEncoder().encodeToString(signed.mic()) + ", sha-256";
            if (partner.mdnMode() == MdnMode.ASYNCHRONOUS
                    && transfer.status() == TransferStatus.PROCESSING) {
                transfer.recordSentContentMic(contentMic, clock.instant());
                if (!repository.saveIfStatus(transfer, TransferStatus.PROCESSING)) {
                    throw new As2TransportException("Transfer completed while preparing AS2 delivery", null, false);
                }
            } else if (partner.mdnMode() == MdnMode.ASYNCHRONOUS) {
                requireMatchingMic(transfer.sentContentMic(), signed.mic());
            }
            var wirePart = partner.encryptionRequired()
                    ? cms.encrypt(signed.content(), partnerCertificate) : signed.content();
            KmsCmsCodec.EncodedBody body = cms.encode(wirePart);
            HttpRequest.Builder request = HttpRequest.newBuilder(partner.endpoint())
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", body.contentType())
                    .header("AS2-Version", "1.2")
                    .header("AS2-From", partner.localAs2Id())
                    .header("AS2-To", partner.remoteAs2Id())
                    .header("Message-ID", transfer.as2MessageId())
                    .header("Disposition-Notification-To", "as2@edispark.invalid")
                    .header("Disposition-Notification-Options",
                            "signed-receipt-protocol=required,pkcs7-signature; signed-receipt-micalg=required,sha-256");
            if (partner.mdnMode() == MdnMode.ASYNCHRONOUS) {
                request.header("Receipt-Delivery-Option", asynchronousMdnUrl.toString());
            }
            HttpResponse<byte[]> response = http.send(request.POST(
                    HttpRequest.BodyPublishers.ofByteArray(body.bytes())).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new As2TransportException("Partner returned HTTP " + response.statusCode(), null,
                        response.statusCode() >= 500 || response.statusCode() == 429);
            }
            if (partner.mdnMode() == MdnMode.ASYNCHRONOUS && response.body().length == 0) {
                return new As2DeliveryResult(true, null, null);
            }
            return verifyMdn(response, transfer, partnerCertificate, signed.mic());
        } catch (As2TransportException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new As2TransportException("AS2 delivery was interrupted", exception, true);
        } catch (java.io.IOException exception) {
            throw new As2TransportException("AS2 partner was unavailable", exception, true);
        } catch (Exception exception) {
            throw new As2TransportException("AS2 message preparation or MDN verification failed", exception, false);
        }
    }

    private As2DeliveryResult verifyMdn(HttpResponse<byte[]> response, Transfer transfer,
                                        X509Certificate partnerCertificate, byte[] expectedMic) {
        String contentType = response.headers().firstValue("Content-Type")
                .orElseThrow(() -> new IllegalArgumentException("Signed MDN has no Content-Type"));
        var verified = cms.verify(cms.parse(contentType, response.body()), partnerCertificate);
        var receipt = cms.readMdn(verified.content());
        if (!transfer.as2MessageId().equals(receipt.originalMessageId())) {
            throw new IllegalArgumentException("MDN Original-Message-ID does not match the transfer");
        }
        requireMatchingMic(receipt.receivedContentMic(), expectedMic);
        return new As2DeliveryResult(false, receipt.disposition(),
                response.headers().firstValue("Message-ID").orElse(null));
    }

    private void requireMatchingMic(String value, byte[] expected) {
        if (value == null) throw new IllegalArgumentException("MDN has no Received-content-MIC");
        String[] fields = value.split(",", 2);
        if (fields.length != 2 || !fields[1].trim().equalsIgnoreCase("sha-256")) {
            throw new IllegalArgumentException("MDN MIC must use SHA-256");
        }
        byte[] received = Base64.getDecoder().decode(fields[0].trim());
        if (!MessageDigest.isEqual(received, expected)) throw new IllegalArgumentException("MDN MIC does not match");
    }

    private void requireConfiguredIdentity(PartnerProfile partner) throws Exception {
        X509Certificate selected = certificate(certificates.load(
                partner.tenantId(), partner.localCertificateId()).bytes());
        if (!MessageDigest.isEqual(selected.getPublicKey().getEncoded(),
                identity.signingCertificate().getPublicKey().getEncoded())) {
            throw new IllegalArgumentException("Partner profile does not use the configured KMS signing certificate");
        }
    }

    private X509Certificate partnerCertificate(PartnerProfile partner) throws Exception {
        return certificate(certificates.load(partner.tenantId(), partner.partnerCertificateId()).bytes());
    }

    private X509Certificate certificate(byte[] bytes) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(bytes));
    }
}
