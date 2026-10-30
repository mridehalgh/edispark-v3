package com.edispark.mft.infrastructure.as2;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

public final class KmsContentSigner implements ContentSigner {
    private static final AlgorithmIdentifier ALGORITHM =
            new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WITHRSA");
    private final KmsClient kms;
    private final String keyId;
    private final ByteArrayOutputStream content = new ByteArrayOutputStream();

    public KmsContentSigner(KmsClient kms, String keyId) {
        this.kms = kms;
        this.keyId = keyId;
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return ALGORITHM;
    }

    @Override
    public OutputStream getOutputStream() {
        content.reset();
        return content;
    }

    @Override
    public byte[] getSignature() {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        return kms.sign(SignRequest.builder()
                .keyId(keyId)
                .message(SdkBytes.fromByteArray(digest))
                .messageType(MessageType.DIGEST)
                .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256)
                .build()).signature().asByteArray();
    }
}
