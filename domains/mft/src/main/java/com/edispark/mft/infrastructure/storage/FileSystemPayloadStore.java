package com.edispark.mft.infrastructure.storage;

import com.edispark.mft.domain.model.PayloadRef;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.repository.PayloadStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

public final class FileSystemPayloadStore implements PayloadStore {
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path root;
    private final LocalKms kms;

    public FileSystemPayloadStore(Path root) throws IOException {
        this(root, root.resolve(".payload-master.key"));
    }

    public FileSystemPayloadStore(Path root, Path masterKeyPath) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.kms = new LocalKms(masterKeyPath);
    }

    @Override
    public PayloadRef put(String tenantId, TransferId transferId, String filename, InputStream content) throws IOException {
        String safeName = Path.of(filename).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        Path directory = resolveSafe(tenantId, transferId.toString());
        Files.createDirectories(directory);
        Path target = directory.resolve(safeName).normalize();
        String reference = root.relativize(target).toString();
        Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
        MessageDigest digest = sha256();
        long size = 0;
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try (OutputStream raw = Files.newOutputStream(temporary)) {
            raw.write(nonce);
            try (OutputStream output = new CipherOutputStream(raw,
                    cipher(Cipher.ENCRYPT_MODE, nonce, tenantId, reference))) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = content.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                size += read;
            }
            }
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return new PayloadRef(reference, HexFormat.of().formatHex(digest.digest()), size);
    }

    @Override
    public InputStream get(String tenantId, PayloadRef payload) throws IOException {
        Path resolved = root.resolve(payload.key()).normalize();
        if (!resolved.startsWith(root.resolve(tenantId).normalize())) throw new SecurityException("payload is outside the tenant boundary");
        InputStream input = Files.newInputStream(resolved);
        byte[] nonce = input.readNBytes(NONCE_BYTES);
        if (nonce.length != NONCE_BYTES) {
            input.close();
            throw new IOException("encrypted payload is invalid");
        }
        return new CipherInputStream(input, cipher(Cipher.DECRYPT_MODE, nonce, tenantId, payload.key()));
    }

    private Path resolveSafe(String tenantId, String transferId) {
        Path resolved = root.resolve(tenantId).resolve(transferId).normalize();
        if (!resolved.startsWith(root)) throw new SecurityException("invalid payload path");
        return resolved;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String tenantId, String resourceId) throws IOException {
        return kms.cipher(mode, nonce, new LocalKms.Context(tenantId, "payload", resourceId));
    }
}
