package com.edispark.mft.infrastructure.as2;

import java.util.Objects;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAESOAEPparams;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.KeyTransRecipient;
import org.bouncycastle.cms.RecipientOperator;
import org.bouncycastle.operator.InputDecryptor;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JceInputDecryptorProviderBuilder;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptionAlgorithmSpec;

public final class KmsOaepKeyTransRecipient implements KeyTransRecipient {
    private final KmsClient kms;
    private final String keyId;

    public KmsOaepKeyTransRecipient(KmsClient kms, String keyId) {
        this.kms = Objects.requireNonNull(kms);
        this.keyId = Objects.requireNonNull(keyId);
    }

    @Override
    public RecipientOperator getRecipientOperator(AlgorithmIdentifier keyEncryptionAlgorithm,
                                                   AlgorithmIdentifier contentEncryptionAlgorithm,
                                                   byte[] encryptedContentKey) throws CMSException {
        requireOaepSha256(keyEncryptionAlgorithm);
        byte[] plaintextKey = kms.decrypt(DecryptRequest.builder()
                .keyId(keyId)
                .ciphertextBlob(SdkBytes.fromByteArray(encryptedContentKey))
                .encryptionAlgorithm(EncryptionAlgorithmSpec.RSAES_OAEP_SHA_256)
                .build()).plaintext().asByteArray();
        try {
            InputDecryptor decryptor = new JceInputDecryptorProviderBuilder().setProvider("BC")
                    .build(plaintextKey).get(contentEncryptionAlgorithm);
            return new RecipientOperator(decryptor);
        } catch (OperatorCreationException exception) {
            throw new CMSException("Unsupported AS2 content-encryption algorithm", exception);
        } finally {
            java.util.Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    private void requireOaepSha256(AlgorithmIdentifier algorithm) throws CMSException {
        if (!PKCSObjectIdentifiers.id_RSAES_OAEP.equals(algorithm.getAlgorithm())) {
            throw new CMSException("RSA PKCS#1 v1.5 key transport is not supported; RSA-OAEP-SHA256 is required");
        }
        RSAESOAEPparams parameters = RSAESOAEPparams.getInstance(algorithm.getParameters());
        if (!NISTObjectIdentifiers.id_sha256.equals(parameters.getHashAlgorithm().getAlgorithm())) {
            throw new CMSException("RSA-OAEP must use SHA-256");
        }
        AlgorithmIdentifier maskHash = AlgorithmIdentifier.getInstance(
                parameters.getMaskGenAlgorithm().getParameters());
        if (!PKCSObjectIdentifiers.id_mgf1.equals(parameters.getMaskGenAlgorithm().getAlgorithm())
                || !NISTObjectIdentifiers.id_sha256.equals(maskHash.getAlgorithm())) {
            throw new CMSException("RSA-OAEP MGF1 must use SHA-256");
        }
    }
}
