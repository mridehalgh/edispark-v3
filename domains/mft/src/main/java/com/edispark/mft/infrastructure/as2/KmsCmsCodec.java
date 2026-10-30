package com.edispark.mft.infrastructure.as2;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.activation.DataHandler;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAESOAEPparams;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.SignerInfoGeneratorBuilder;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMEEnvelopedParser;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.mail.smime.SMIMESignedParser;
import org.bouncycastle.mail.smime.SMIMEUtil;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.operator.jcajce.JceAsymmetricKeyWrapper;
import software.amazon.awssdk.services.kms.KmsClient;

public final class KmsCmsCodec {
    private final KmsClient kms;
    private final String signingKeyId;
    private final String decryptionKeyId;
    private final X509Certificate signingCertificate;

    public KmsCmsCodec(KmsClient kms, String signingKeyId, String decryptionKeyId,
                       X509Certificate signingCertificate) {
        this.kms = kms;
        this.signingKeyId = signingKeyId;
        this.decryptionKeyId = decryptionKeyId;
        this.signingCertificate = signingCertificate;
    }

    public MimeBodyPart sign(MimeBodyPart content) {
        try {
            SMIMESignedGenerator generator = new SMIMESignedGenerator();
            generator.addSignerInfoGenerator(new SignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                    .build(new KmsContentSigner(kms, signingKeyId),
                            new JcaX509CertificateHolder(signingCertificate)));
            generator.addCertificates(new JcaCertStore(List.of(signingCertificate)));
            MimeMultipart signed = generator.generate(content);
            MimeBodyPart result = new MimeBodyPart();
            result.setContent(signed);
            result.setHeader("Content-Type", signed.getContentType());
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create KMS-signed CMS content", exception);
        }
    }

    public SignedContent signWithMic(MimeBodyPart content) {
        MimeBodyPart signed = sign(content);
        return new SignedContent(signed, verify(signed, signingCertificate).mic());
    }

    public MimeBodyPart payload(byte[] content, String contentType, String filename) {
        try {
            MimeBodyPart part = new MimeBodyPart();
            part.setDataHandler(new DataHandler(new ByteArrayDataSource(content, contentType)));
            part.setFileName(filename);
            part.setHeader("Content-Transfer-Encoding", "binary");
            return part;
        } catch (MessagingException exception) {
            throw new IllegalArgumentException("Unable to create AS2 payload", exception);
        }
    }

    public byte[] content(MimeBodyPart part) {
        try {
            return part.getInputStream().readAllBytes();
        } catch (MessagingException | IOException exception) {
            throw new IllegalArgumentException("Unable to read AS2 content", exception);
        }
    }

    public MimeBodyPart signedMdn(String originalMessageId, String disposition, byte[] mic) {
        try {
            MimeBodyPart explanation = new MimeBodyPart();
            explanation.setText("The AS2 message was received and processed by EdiSpark.", StandardCharsets.UTF_8.name());
            String notification = "Original-Message-ID: " + originalMessageId + "\r\n"
                    + "Disposition: automatic-action/MDN-sent-automatically; " + disposition + "\r\n"
                    + "Received-content-MIC: " + Base64.getEncoder().encodeToString(mic) + ", sha-256\r\n";
            MimeBodyPart receipt = new MimeBodyPart();
            receipt.setDataHandler(new DataHandler(new ByteArrayDataSource(
                    notification.getBytes(StandardCharsets.US_ASCII), "message/disposition-notification")));
            MimeMultipart report = new MimeMultipart("report; report-type=disposition-notification");
            report.addBodyPart(explanation);
            report.addBodyPart(receipt);
            MimeBodyPart body = new MimeBodyPart();
            body.setContent(report);
            body.setHeader("Content-Type", report.getContentType());
            return sign(body);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Unable to create signed AS2 MDN", exception);
        }
    }

    public MdnReceipt readMdn(MimeBodyPart content) {
        try {
            if (!(content.getContent() instanceof MimeMultipart report)) {
                throw new IllegalArgumentException("AS2 MDN must be a multipart report");
            }
            for (int index = 0; index < report.getCount(); index++) {
                var part = report.getBodyPart(index);
                if (part.isMimeType("message/disposition-notification")) {
                    return parseMdnFields(new String(part.getInputStream().readAllBytes(), StandardCharsets.US_ASCII));
                }
            }
            throw new IllegalArgumentException("AS2 MDN does not contain a disposition notification");
        } catch (MessagingException | IOException exception) {
            throw new IllegalArgumentException("Unable to read AS2 MDN", exception);
        }
    }

    private MdnReceipt parseMdnFields(String body) {
        String original = null;
        String disposition = null;
        String mic = null;
        for (String line : body.replace("\r\n", "\n").split("\n")) {
            int separator = line.indexOf(':');
            if (separator < 1) continue;
            String name = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (name.equalsIgnoreCase("Original-Message-ID")) original = value;
            if (name.equalsIgnoreCase("Disposition")) disposition = value;
            if (name.equalsIgnoreCase("Received-content-MIC")) mic = value;
        }
        if (original == null || disposition == null) {
            throw new IllegalArgumentException("AS2 MDN is missing required fields");
        }
        return new MdnReceipt(original, disposition, mic);
    }

    public MimeBodyPart encrypt(MimeBodyPart content, X509Certificate recipientCertificate) {
        try {
            recipientCertificate.checkValidity();
            AlgorithmIdentifier sha256 = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE);
            AlgorithmIdentifier mgf1 = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, sha256);
            AlgorithmIdentifier pSource = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_pSpecified,
                    new DEROctetString(new byte[0]));
            AlgorithmIdentifier oaep = new AlgorithmIdentifier(PKCSObjectIdentifiers.id_RSAES_OAEP,
                    new RSAESOAEPparams(sha256, mgf1, pSource));
            JceAsymmetricKeyWrapper wrapper = new JceAsymmetricKeyWrapper(oaep,
                    recipientCertificate.getPublicKey()).setProvider("BC");
            SMIMEEnvelopedGenerator generator = new SMIMEEnvelopedGenerator();
            generator.setContentTransferEncoding("binary");
            generator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(recipientCertificate, wrapper));
            return generator.generate(content, new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC)
                    .setProvider("BC").build());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create RSA-OAEP AS2 envelope", exception);
        }
    }

    public MimeBodyPart decrypt(MimeBodyPart envelope) {
        try {
            SMIMEEnvelopedParser parser = new SMIMEEnvelopedParser(envelope);
            Collection<RecipientInformation> recipients = parser.getRecipientInfos().getRecipients();
            if (recipients.size() != 1) throw new CMSException("AS2 envelope must contain exactly one recipient");
            RecipientInformation recipient = recipients.iterator().next();
            return SMIMEUtil.toMimeBodyPart(recipient.getContentStream(
                    new KmsOaepKeyTransRecipient(kms, decryptionKeyId)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to decrypt AS2 envelope", exception);
        }
    }

    public VerifiedContent verify(MimeBodyPart signedPart, X509Certificate expectedSigner) {
        try {
            if (!(signedPart.getContent() instanceof MimeMultipart multipart)) {
                throw new CMSException("Signed AS2 content must be multipart/signed");
            }
            SMIMESignedParser parser = new SMIMESignedParser(
                    new JcaDigestCalculatorProviderBuilder().setProvider("BC").build(), multipart);
            Collection<SignerInformation> signers = parser.getSignerInfos().getSigners();
            if (signers.size() != 1) throw new CMSException("AS2 content must contain exactly one signer");
            SignerInformation signer = signers.iterator().next();
            JcaX509CertificateHolder expectedCertificate = new JcaX509CertificateHolder(expectedSigner);
            expectedSigner.checkValidity();
            if (!signer.getSID().match(expectedCertificate)) {
                throw new CMSException("AS2 signer identifier does not match the configured partner certificate");
            }
            if (!signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(expectedSigner))) {
                throw new CMSException("AS2 CMS signature is invalid");
            }
            MimeBodyPart content = parser.getContent();
            return new VerifiedContent(content, signer.getContentDigest());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to verify AS2 signature", exception);
        }
    }

    public MimeBodyPart parse(String contentType, byte[] body) {
        try {
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            message.write(("Content-Type: " + contentType + "\r\nMIME-Version: 1.0\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            message.write(body);
            return new MimeBodyPart(new ByteArrayInputStream(message.toByteArray()));
        } catch (MessagingException | IOException exception) {
            throw new IllegalArgumentException("AS2 MIME body is invalid", exception);
        }
    }

    public EncodedBody encode(MimeBodyPart part) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Object content = part.getContent();
            if (content instanceof MimeMultipart multipart) multipart.writeTo(output);
            else part.getDataHandler().writeTo(output);
            return new EncodedBody(part.getContentType(), output.toByteArray());
        } catch (MessagingException | IOException exception) {
            throw new IllegalStateException("Unable to encode AS2 MIME body", exception);
        }
    }

    public record VerifiedContent(MimeBodyPart content, byte[] mic) {}
    public record SignedContent(MimeBodyPart content, byte[] mic) {}
    public record EncodedBody(String contentType, byte[] bytes) {}
    public record MdnReceipt(String originalMessageId, String disposition, String receivedContentMic) {}
}
