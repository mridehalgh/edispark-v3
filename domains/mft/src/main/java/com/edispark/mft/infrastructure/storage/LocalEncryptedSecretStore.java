package com.edispark.mft.infrastructure.storage;

import com.edispark.mft.domain.repository.SecretStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class LocalEncryptedSecretStore implements SecretStore {
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path root;
    private final SecretKey key;

    public LocalEncryptedSecretStore(Path root, Path masterKeyPath) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.key = loadOrCreateKey(masterKeyPath.toAbsolutePath().normalize());
    }

    @Override
    public String put(String tenantId, String name, byte[] plaintext) throws IOException {
        String reference = tenantId + "/" + UUID.randomUUID() + "-" + safeName(name) + ".enc";
        Path target = root.resolve(reference).normalize();
        if (!target.startsWith(root)) throw new SecurityException("invalid secret path");
        Files.createDirectories(target.getParent());
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, nonce, plaintext);
        byte[] stored = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, stored, 0, nonce.length);
        System.arraycopy(ciphertext, 0, stored, nonce.length, ciphertext.length);
        Files.write(target, stored);
        restrict(target);
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
        return crypt(Cipher.DECRYPT_MODE, nonce, ciphertext);
    }

    private byte[] crypt(int mode, byte[] nonce, byte[] input) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to process encrypted secret", exception);
        }
    }

    static SecretKey loadOrCreateKey(Path path) throws IOException {
        if (Files.exists(path)) {
            return new SecretKeySpec(Base64.getDecoder().decode(Files.readString(path).trim()), "AES");
        }
        try {
            Files.createDirectories(path.getParent());
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            SecretKey generated = generator.generateKey();
            Files.writeString(path, Base64.getEncoder().encodeToString(generated.getEncoded()));
            restrict(path);
            return generated;
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to create the local master key", exception);
        }
    }

    private static void restrict(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException ignored) {
            // POSIX permissions are unavailable on Windows.
        }
    }

    private static String safeName(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "certificate" : safe;
    }
}
