package com.edispark.mft.infrastructure.storage;

import com.edispark.mft.domain.repository.SecretStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.Cipher;

public final class LocalEncryptedSecretStore implements SecretStore {
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path root;
    private final LocalKms kms;

    public LocalEncryptedSecretStore(Path root, Path masterKeyPath) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.kms = new LocalKms(masterKeyPath);
    }

    @Override
    public String put(String tenantId, String name, byte[] plaintext) throws IOException {
        String reference = tenantId + "/" + UUID.randomUUID() + "-" + safeName(name) + ".enc";
        Path target = root.resolve(reference).normalize();
        if (!target.startsWith(root)) throw new SecurityException("invalid secret path");
        Files.createDirectories(target.getParent());
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, nonce, plaintext, context(tenantId, reference));
        byte[] stored = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, stored, 0, nonce.length);
        System.arraycopy(ciphertext, 0, stored, nonce.length, ciphertext.length);
        Files.write(target, stored);
        LocalKms.restrict(target);
        return reference;
    }

    @Override
    public byte[] get(String tenantId, String reference) throws IOException {
        Path target = root.resolve(reference).normalize();
        if (!target.startsWith(root.resolve(tenantId).normalize())) throw new SecurityException("secret is outside the tenant boundary");
        byte[] stored = Files.readAllBytes(target);
        if (stored.length <= NONCE_BYTES) throw new IOException("encrypted secret is invalid");
        byte[] nonce = java.util.Arrays.copyOfRange(stored, 0, NONCE_BYTES);
        byte[] ciphertext = java.util.Arrays.copyOfRange(stored, NONCE_BYTES, stored.length);
        return crypt(Cipher.DECRYPT_MODE, nonce, ciphertext, context(tenantId, reference));
    }

    private byte[] crypt(int mode, byte[] nonce, byte[] input, LocalKms.Context context) throws IOException {
        try {
            return kms.cipher(mode, nonce, context).doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to process encrypted secret", exception);
        }
    }

    private LocalKms.Context context(String tenantId, String reference) {
        return new LocalKms.Context(tenantId, "certificate-material", reference);
    }

    private static String safeName(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "certificate" : safe;
    }
}
