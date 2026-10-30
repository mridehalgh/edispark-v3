package com.edispark.mft.infrastructure.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Local test double for tenant-scoped AWS KMS envelope encryption. */
public final class LocalKms {
    private static final String SERVICE = "mft";
    private final SecretKey rootKey;

    public LocalKms(Path rootKeyPath) throws IOException {
        rootKey = loadOrCreateKey(rootKeyPath.toAbsolutePath().normalize());
    }

    public String tenantKeyAlias(String tenantId) {
        requireText(tenantId, "tenantId");
        return "alias/edispark/tenant/" + tenantId + "/data";
    }

    public Cipher cipher(int mode, byte[] nonce, Context context) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, tenantKey(context.tenantId()), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.authenticatedData());
            return cipher;
        } catch (GeneralSecurityException exception) {
            throw new IOException("Local KMS operation failed", exception);
        }
    }

    private SecretKey tenantKey(String tenantId) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(rootKey.getEncoded(), "HmacSHA256"));
        return new SecretKeySpec(hmac.doFinal(tenantKeyAlias(tenantId).getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private static SecretKey loadOrCreateKey(Path path) throws IOException {
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
            throw new IOException("Unable to create the local KMS root key", exception);
        }
    }

    static void restrict(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException ignored) {
            // POSIX permissions are unavailable on Windows.
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    public record Context(String tenantId, String resourceType, String resourceId) {
        public Context {
            requireText(tenantId, "tenantId");
            requireText(resourceType, "resourceType");
            requireText(resourceId, "resourceId");
        }

        byte[] authenticatedData() {
            return ("tenant_id=" + tenantId + "\nservice=" + SERVICE + "\nresource_type=" + resourceType
                    + "\nresource_id=" + resourceId).getBytes(StandardCharsets.UTF_8);
        }
    }
}
