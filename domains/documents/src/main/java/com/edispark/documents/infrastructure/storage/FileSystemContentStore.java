package com.edispark.documents.infrastructure.storage;

import com.edispark.crypto.LocalTenantCipher;
import com.edispark.crypto.TenantCipher;
import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.ContentHash;
import com.edispark.documents.domain.repository.ContentStore;
import io.cottn.core.tenancy.TenantAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

/** Tenant-scoped encrypted local storage. Legacy hash-only files require explicit migration. */
public final class FileSystemContentStore implements ContentStore {
    private final Path root;
    private final TenantCipher cipher;

    public FileSystemContentStore(Path directory) {
        this(directory, new LocalTenantCipher(directory.resolve(".tenant-root.key"), "edispark-documents"));
    }

    public FileSystemContentStore(Path directory, TenantCipher cipher) {
        this.root = directory.toAbsolutePath().normalize();
        this.cipher = java.util.Objects.requireNonNull(cipher);
    }

    @Override public void store(Content content) {
        String key = ContentAddress.currentKey(content.hash());
        byte[] bytes = ContentAddress.verify(content.hash(), content.data());
        Path target = path(key);
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
            byte[] encrypted = cipher.encrypt(TenantAccess.currentId(), bytes, ContentAddress.context(key));
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".content-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                Files.write(temporary, encrypted);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Content could not be stored", failure);
        }
    }

    @Override public Optional<byte[]> retrieve(ContentHash hash) {
        String key = ContentAddress.currentKey(hash);
        try {
            byte[] encrypted = Files.readAllBytes(path(key));
            byte[] plaintext = cipher.decrypt(TenantAccess.currentId(), encrypted, ContentAddress.context(key));
            return Optional.of(ContentAddress.verify(hash, plaintext));
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException failure) {
            throw new UncheckedIOException("Content could not be read", failure);
        }
    }

    @Override public boolean exists(ContentHash hash) {
        return Files.isRegularFile(path(ContentAddress.currentKey(hash)), LinkOption.NOFOLLOW_LINKS);
    }

    @Override public void delete(ContentHash hash) {
        try {
            Files.deleteIfExists(path(ContentAddress.currentKey(hash)));
        } catch (IOException failure) {
            throw new UncheckedIOException("Content could not be deleted", failure);
        }
    }

    private Path path(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw new SecurityException("Content path is outside its namespace");
        for (Path current = resolved; current != null && current.startsWith(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new SecurityException("Content paths cannot contain symbolic links");
        }
        return resolved;
    }
}
