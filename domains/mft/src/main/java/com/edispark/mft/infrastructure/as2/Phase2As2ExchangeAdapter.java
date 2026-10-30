package com.edispark.mft.infrastructure.as2;

import com.edispark.mft.application.As2DeliveryResult;
import com.edispark.mft.application.As2ExchangePort;
import com.edispark.mft.application.As2TransportException;
import com.edispark.mft.application.CertificateMaterial;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.helger.phase2.client.AS2Client;
import com.helger.phase2.client.AS2ClientRequest;
import com.helger.phase2.client.AS2ClientResponse;
import com.helger.phase2.client.AS2ClientSettings;
import com.helger.phase2.crypto.ECompressionType;
import com.helger.phase2.crypto.ECryptoAlgorithmCrypt;
import com.helger.phase2.crypto.ECryptoAlgorithmSign;
import com.helger.phase2.disposition.DispositionOptions;
import com.helger.security.keystore.EKeyStoreType;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.apache.hc.core5.util.Timeout;

public final class Phase2As2ExchangeAdapter implements As2ExchangePort {
    private final boolean allowPrivateNetworks;
    private final String asyncMdnUrl;

    public Phase2As2ExchangeAdapter(boolean allowPrivateNetworks, String asyncMdnUrl) {
        this.allowPrivateNetworks = allowPrivateNetworks;
        this.asyncMdnUrl = asyncMdnUrl;
    }

    @Override
    public As2DeliveryResult send(PartnerProfile partner, Transfer transfer, byte[] payload,
                                  CertificateMaterial localIdentity, CertificateMaterial partnerCertificate) {
        validateEndpoint(partner.endpoint());
        try {
            X509Certificate receiverCertificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(partnerCertificate.bytes()));
            AS2ClientSettings settings = new AS2ClientSettings();
            settings.setKeyStore(EKeyStoreType.PKCS12, localIdentity.bytes(), localIdentity.password());
            settings.setSenderData(partner.localAs2Id(), "as2@edispark.local", localIdentity.alias());
            settings.setReceiverData(partner.remoteAs2Id(), partner.remoteAs2Id(), partner.endpoint().toString());
            settings.setReceiverCertificate(receiverCertificate);
            settings.setPartnershipName(partner.localAs2Id() + "_" + partner.remoteAs2Id());
            settings.setMessageIDFormat(transfer.as2MessageId());
            settings.setRetryCount(0);
            settings.setConnectTimeout(Timeout.ofSeconds(10));
            settings.setResponseTimeout(Timeout.ofSeconds(30));
            ECryptoAlgorithmSign signature = partner.legacyCryptoEnabled()
                    ? ECryptoAlgorithmSign.DIGEST_SHA_1 : ECryptoAlgorithmSign.DIGEST_SHA_256;
            ECryptoAlgorithmCrypt encryption = partner.legacyCryptoEnabled()
                    ? ECryptoAlgorithmCrypt.CRYPT_3DES : ECryptoAlgorithmCrypt.CRYPT_AES128_GCM;
            settings.setMDNOptions(new DispositionOptions()
                    .setMICAlg(signature)
                    .setMICAlgImportance(DispositionOptions.IMPORTANCE_REQUIRED)
                    .setProtocol(DispositionOptions.PROTOCOL_PKCS7_SIGNATURE)
                    .setProtocolImportance(DispositionOptions.IMPORTANCE_REQUIRED));
            settings.setEncryptAndSign(
                    partner.encryptionRequired() ? encryption : null,
                    partner.signingRequired() ? signature : null);
            if (partner.compressionEnabled()) {
                settings.setCompress(ECompressionType.ZLIB, AS2ClientSettings.DEFAULT_COMPRESS_BEFORE_SIGNING);
            }
            if (partner.mdnMode() == MdnMode.ASYNCHRONOUS) settings.setAsyncMDNUrl(asyncMdnUrl);

            AS2ClientRequest request = new AS2ClientRequest("EdiSpark transfer " + transfer.id());
            request.setData(payload).setFilename(transfer.filename()).setContentType(transfer.contentType());
            AS2ClientResponse response = new AS2Client().sendSynchronous(settings, request);
            if (response.hasException()) {
                throw new As2TransportException("AS2 request failed", response.getException(), true);
            }
            if (partner.mdnMode() == MdnMode.ASYNCHRONOUS && !response.hasMDN()) {
                return new As2DeliveryResult(true, null, null);
            }
            return new As2DeliveryResult(false, response.getMDNDisposition(), response.getMDNMessageID());
        } catch (As2TransportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new As2TransportException("AS2 message preparation failed", exception, false);
        }
    }

    private void validateEndpoint(URI endpoint) {
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new As2TransportException("AS2 endpoint must use HTTP or HTTPS", null, false);
        }
        if (endpoint.getUserInfo() != null || endpoint.getHost() == null) {
            throw new As2TransportException("AS2 endpoint is invalid", null, false);
        }
        if (allowPrivateNetworks) return;
        try {
            for (InetAddress address : InetAddress.getAllByName(endpoint.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new As2TransportException("AS2 endpoint resolves to a restricted network", null, false);
                }
            }
        } catch (As2TransportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new As2TransportException("AS2 endpoint could not be resolved", exception, true);
        }
    }
}
