package com.edispark.crypto;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Disposable local KMS behavioral double. It is never used as deployment key management. */
public final class LocalTenantCipher implements TenantCipher {
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] root;
    private final String service;

    public LocalTenantCipher(Path rootFile, String service) {
        this.service = java.util.Objects.requireNonNull(service);
        this.root = rootKey(rootFile.toAbsolutePath().normalize());
    }

    @Override public byte[] encrypt(String tenantId, byte[] plaintext, Map<String, String> context) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, tenantId, nonce, plaintext, context);
        return ByteBuffer.allocate(1 + NONCE_BYTES + encrypted.length).put((byte) 1).put(nonce).put(encrypted).array();
    }

    @Override public byte[] decrypt(String tenantId, byte[] ciphertext, Map<String, String> context) {
        if (ciphertext.length < 1 + NONCE_BYTES + 16 || ciphertext[0] != 1) {
            throw new SecurityException("Invalid encrypted content envelope");
        }
        var buffer = ByteBuffer.wrap(ciphertext);
        buffer.get();
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        return crypt(Cipher.DECRYPT_MODE, tenantId, nonce, encrypted, context);
    }

    private byte[] crypt(int mode, String tenant, byte[] nonce, byte[] input, Map<String, String> context) {
        byte[] aad = authenticatedData(tenant, context);
        try {
            Mac derivation = Mac.getInstance("HmacSHA256");
            derivation.init(new SecretKeySpec(root, "HmacSHA256"));
            byte[] branch = derivation.doFinal(("tenant/" + tenant).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            derivation.init(new SecretKeySpec(branch, "HmacSHA256"));
            byte[] resource = derivation.doFinal(aad);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(resource, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw new SecurityException("Tenant content could not be authenticated", failure);
        }
    }

    private byte[] authenticatedData(String tenant, Map<String, String> context) {
        if (tenant == null || tenant.isBlank()) throw new IllegalArgumentException("Tenant is required");
        var values = new TreeMap<>(context);
        bind(values, "tenant_id", tenant);
        bind(values, "service", service);
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            for (var entry : values.entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeUTF(entry.getValue());
            }
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void bind(Map<String, String> values, String name, String expected) {
        String previous = values.put(name, expected);
        if (previous != null && !previous.equals(expected)) throw new SecurityException("Encryption context mismatch");
    }

    private byte[] rootKey(Path file) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.isSymbolicLink(file)) throw new SecurityException("Local root key cannot be a symbolic link");
            if (!Files.exists(file)) createRoot(file);
            byte[] key = Files.readAllBytes(file);
            if (key.length != 32) throw new SecurityException("Invalid local root key");
            return key;
        } catch (IOException failure) {
            throw new UncheckedIOException("Local encryption root is unavailable", failure);
        }
    }

    private void createRoot(Path file) throws IOException {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        try (var channel = Files.newByteChannel(file, java.util.Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            var buffer = ByteBuffer.wrap(key);
            while (buffer.hasRemaining()) channel.write(buffer);
        } catch (FileAlreadyExistsException concurrentCreator) {
            // Another local startup owns the same root. Read it rather than replacing it.
        }
    }
}
