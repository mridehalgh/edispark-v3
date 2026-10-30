package com.edispark.mft.infrastructure.as2;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Arrays;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.EncryptionAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

public record KmsAs2Identity(String signingKeyId, String decryptionKeyId,
                             X509Certificate signingCertificate,
                             X509Certificate encryptionCertificate) {
    public static KmsAs2Identity load(KmsClient kms, String signingKeyId, String decryptionKeyId,
                                      byte[] signingCertificate, byte[] encryptionCertificate, Clock clock) {
        X509Certificate signing = parse(signingCertificate, "signing");
        X509Certificate encryption = parse(encryptionCertificate, "encryption");
        checkValidity(signing, clock, "signing");
        checkValidity(encryption, clock, "encryption");
        requireSigningKey(kms, signingKeyId, signing);
        requireDecryptionKey(kms, decryptionKeyId, encryption);
        return new KmsAs2Identity(signingKeyId, decryptionKeyId, signing, encryption);
    }

    private static void requireSigningKey(KmsClient kms, String keyId, X509Certificate certificate) {
        GetPublicKeyResponse key = publicKey(kms, keyId);
        if (!key.signingAlgorithms().contains(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256)) {
            throw new IllegalStateException("AS2 signing KMS key must support RSASSA_PKCS1_V1_5_SHA_256");
        }
        requireMatchingPublicKey(key, certificate, "signing");
    }

    private static void requireDecryptionKey(KmsClient kms, String keyId, X509Certificate certificate) {
        GetPublicKeyResponse key = publicKey(kms, keyId);
        if (!key.encryptionAlgorithms().contains(EncryptionAlgorithmSpec.RSAES_OAEP_SHA_256)) {
            throw new IllegalStateException("AS2 decryption KMS key must support RSAES_OAEP_SHA_256");
        }
        requireMatchingPublicKey(key, certificate, "encryption");
    }

    private static GetPublicKeyResponse publicKey(KmsClient kms, String keyId) {
        if (keyId == null || keyId.isBlank()) throw new IllegalStateException("AS2 KMS key ID is required");
        return kms.getPublicKey(GetPublicKeyRequest.builder().keyId(keyId).build());
    }

    private static void requireMatchingPublicKey(GetPublicKeyResponse key, X509Certificate certificate, String purpose) {
        if (key.publicKey() == null || !Arrays.equals(key.publicKey().asByteArray(),
                certificate.getPublicKey().getEncoded())) {
            throw new IllegalStateException("AS2 " + purpose + " certificate does not match its KMS key");
        }
    }

    private static X509Certificate parse(byte[] bytes, String purpose) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("AS2 " + purpose + " certificate is invalid", exception);
        }
    }

    private static void checkValidity(X509Certificate certificate, Clock clock, String purpose) {
        try {
            certificate.checkValidity(java.util.Date.from(clock.instant()));
        } catch (Exception exception) {
            throw new IllegalStateException("AS2 " + purpose + " certificate is not currently valid", exception);
        }
    }
}
