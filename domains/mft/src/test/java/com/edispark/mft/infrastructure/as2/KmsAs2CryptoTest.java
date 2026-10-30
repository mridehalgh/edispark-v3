package com.edispark.mft.infrastructure.as2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.mft.testutil.AwsClientFake;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.spec.MGF1ParameterSpec;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.EncryptionAlgorithmSpec;

class KmsAs2CryptoTest {
    private static final byte[] SHA256_DIGEST_INFO_PREFIX = java.util.HexFormat.of()
            .parseHex("3031300d060960864801650304020105000420");

    @Test
    void contentSignerHashesLocallyAndSendsOnlyDigestToKms() throws Exception {
        AtomicReference<SignRequest> captured = new AtomicReference<>();
        byte[] expectedSignature = {4, 5, 6};
        KmsClient kms = AwsClientFake.create(KmsClient.class, (proxy, method, arguments) -> {
            captured.set((SignRequest) arguments[0]);
            return SignResponse.builder().signature(SdkBytes.fromByteArray(expectedSignature)).build();
        });
        KmsContentSigner signer = new KmsContentSigner(kms, "signing-key");
        byte[] content = "signed CMS attributes".getBytes(StandardCharsets.UTF_8);
        signer.getOutputStream().write(content);

        assertThat(signer.getSignature()).isEqualTo(expectedSignature);
        assertThat(captured.get().keyId()).isEqualTo("signing-key");
        assertThat(captured.get().messageType()).isEqualTo(MessageType.DIGEST);
        assertThat(captured.get().message().asByteArray())
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(content));
    }

    @Test
    void decryptionRejectsLegacyPkcs1BeforeCallingKms() {
        AtomicInteger calls = new AtomicInteger();
        KmsClient kms = AwsClientFake.create(KmsClient.class, (proxy, method, arguments) -> {
            calls.incrementAndGet();
            throw new AssertionError("KMS must not be called");
        });
        KmsOaepKeyTransRecipient recipient = new KmsOaepKeyTransRecipient(kms, "decrypt-key");

        assertThatThrownBy(() -> recipient.getRecipientOperator(
                new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption),
                new AlgorithmIdentifier(org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_aes128_CBC),
                new byte[]{1}))
                .isInstanceOf(CMSException.class)
                .hasMessageContaining("PKCS#1 v1.5");
        assertThat(calls).hasValue(0);
    }

    @Test
    void kmsCmsRoundTripSignsEncryptsDecryptsAndVerifiesWithoutPrivateKeyExport() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPair signing = keyPair();
        KeyPair decryption = keyPair();
        X509Certificate signingCertificate = certificate(signing, "CN=AS2 Signing");
        X509Certificate decryptionCertificate = certificate(decryption, "CN=AS2 Encryption");
        KmsClient kms = kms(signing.getPrivate(), decryption.getPrivate());
        KmsCmsCodec codec = new KmsCmsCodec(kms, "signing", "decryption", signingCertificate);
        byte[] edi = "UNH+1+ORDERS:D:96A:UN'".getBytes(StandardCharsets.US_ASCII);

        KmsCmsCodec.SignedContent signed = codec.signWithMic(
                codec.payload(edi, "application/edifact", "order.edi"));
        var encoded = codec.encode(codec.encrypt(signed.content(), decryptionCertificate));
        var decrypted = codec.decrypt(codec.parse(encoded.contentType(), encoded.bytes()));
        var originalSigned = codec.encode(signed.content());
        var decryptedSigned = codec.encode(decrypted);
        assertThat(decryptedSigned.bytes()).as("encrypted transport must preserve signed MIME bytes")
                .isEqualTo(originalSigned.bytes());
        var verified = codec.verify(decrypted, signingCertificate);

        assertThat(codec.content(verified.content())).isEqualTo(edi);
        assertThat(verified.mic()).isEqualTo(signed.mic());
        assertThat(verified.content().getFileName()).isEqualTo("order.edi");
    }

    @Test
    void signedMdnCanBeVerifiedAndRead() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPair signing = keyPair();
        KeyPair decryption = keyPair();
        X509Certificate signingCertificate = certificate(signing, "CN=AS2 Signing");
        KmsCmsCodec codec = new KmsCmsCodec(kms(signing.getPrivate(), decryption.getPrivate()),
                "signing", "decryption", signingCertificate);
        byte[] mic = MessageDigest.getInstance("SHA-256").digest("edi".getBytes(StandardCharsets.US_ASCII));

        var encoded = codec.encode(codec.signedMdn("<message@example.invalid>", "processed", mic));
        var verified = codec.verify(codec.parse(encoded.contentType(), encoded.bytes()), signingCertificate);
        var receipt = codec.readMdn(verified.content());

        assertThat(receipt.originalMessageId()).isEqualTo("<message@example.invalid>");
        assertThat(receipt.disposition()).endsWith("; processed");
        assertThat(receipt.receivedContentMic()).startsWith(java.util.Base64.getEncoder().encodeToString(mic));
    }

    @Test
    void startupRejectsCertificateThatDoesNotMatchItsKmsKey() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPair signing = keyPair();
        KeyPair encryption = keyPair();
        X509Certificate wrongSigningCertificate = certificate(keyPair(), "CN=Wrong Signing");
        X509Certificate encryptionCertificate = certificate(encryption, "CN=Encryption");
        KmsClient kms = AwsClientFake.create(KmsClient.class, (proxy, method, arguments) -> {
            var request = (software.amazon.awssdk.services.kms.model.GetPublicKeyRequest) arguments[0];
            boolean signingKey = request.keyId().equals("signing");
            return GetPublicKeyResponse.builder()
                    .publicKey(SdkBytes.fromByteArray((signingKey ? signing : encryption).getPublic().getEncoded()))
                    .signingAlgorithms(signingKey ? java.util.List.of(
                            SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256) : java.util.List.of())
                    .encryptionAlgorithms(signingKey ? java.util.List.of() : java.util.List.of(
                            EncryptionAlgorithmSpec.RSAES_OAEP_SHA_256))
                    .build();
        });

        assertThatThrownBy(() -> KmsAs2Identity.load(kms, "signing", "encryption",
                wrongSigningCertificate.getEncoded(), encryptionCertificate.getEncoded(), java.time.Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match its KMS key");
    }

    private KmsClient kms(PrivateKey signing, PrivateKey decryption) {
        return AwsClientFake.create(KmsClient.class, (proxy, method, arguments) -> {
            if (method.getName().equals("sign")) {
                SignRequest request = (SignRequest) arguments[0];
                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, signing);
                byte[] digestInfo = new byte[SHA256_DIGEST_INFO_PREFIX.length + request.message().asByteArray().length];
                System.arraycopy(SHA256_DIGEST_INFO_PREFIX, 0, digestInfo, 0, SHA256_DIGEST_INFO_PREFIX.length);
                System.arraycopy(request.message().asByteArray(), 0, digestInfo, SHA256_DIGEST_INFO_PREFIX.length,
                        request.message().asByteArray().length);
                return SignResponse.builder().signature(SdkBytes.fromByteArray(cipher.doFinal(digestInfo))).build();
            }
            if (method.getName().equals("decrypt")) {
                DecryptRequest request = (DecryptRequest) arguments[0];
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
                cipher.init(Cipher.DECRYPT_MODE, decryption, new OAEPParameterSpec("SHA-256", "MGF1",
                        MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
                return DecryptResponse.builder().plaintext(SdkBytes.fromByteArray(
                        cipher.doFinal(request.ciphertextBlob().asByteArray()))).build();
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private X509Certificate certificate(KeyPair pair, String subject) throws Exception {
        Instant now = Instant.now();
        X500Name name = new X500Name(subject);
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(3600)), name, pair.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
    }
}
