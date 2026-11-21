package com.edispark.documents.infrastructure.storage;

import com.edispark.documents.domain.model.*;
import com.edispark.documents.domain.repository.ContentStore;
import io.cottn.core.tenancy.TenantAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;

/** Explicit administrative/local upgrade tool. Never used as a runtime plaintext fallback. */
public record LegacyContentMigration(Path legacyDirectory, ContentStore destination) {
    public enum Status { WOULD_COPY, COPIED, VERIFIED, MISSING, CORRUPT, UNREFERENCED }
    public record Receipt(String tenantId, String contentHash, Status status) { }

    public List<Receipt> reconcile(Collection<ContentHash> ownedHashes, boolean apply) {
        String owner = TenantAccess.currentId();
        return ownedHashes.stream().distinct().map(hash -> reconcile(owner, hash, apply)).toList();
    }

    private Receipt reconcile(String owner, ContentHash hash, boolean apply) {
        try {
            if (destination.retrieve(hash).isPresent()) return receipt(owner, hash, Status.VERIFIED);
            Path legacy = legacyDirectory.resolve(ContentAddress.filename(hash));
            if (Files.isSymbolicLink(legacy)) return receipt(owner, hash, Status.CORRUPT);
            if (!Files.exists(legacy)) return receipt(owner, hash, Status.MISSING);
            byte[] plaintext = ContentAddress.verify(hash, Files.readAllBytes(legacy));
            if (!apply) return receipt(owner, hash, Status.WOULD_COPY);
            destination.store(Content.of(plaintext, Format.JSON));
            ContentAddress.verify(hash, destination.retrieve(hash).orElseThrow());
            return receipt(owner, hash, Status.COPIED);
        } catch (SecurityException invalid) {
            return receipt(owner, hash, Status.CORRUPT);
        } catch (IOException failure) {
            throw new UncheckedIOException("Legacy content migration could not read a referenced blob", failure);
        }
    }

    public List<Receipt> unreferenced(Collection<ContentHash> allTenantReferences) {
        Set<String> referenced = new HashSet<>();
        allTenantReferences.forEach(hash -> referenced.add(ContentAddress.filename(hash)));
        if (!Files.isDirectory(legacyDirectory)) return List.of();
        try (var files = Files.list(legacyDirectory)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("sha-256_[a-f0-9]{64}"))
                    .filter(name -> !referenced.contains(name))
                    .map(name -> new Receipt("", "sha-256:" + name.substring(8), Status.UNREFERENCED)).toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private Receipt receipt(String owner, ContentHash hash, Status status) {
        return new Receipt(owner, hash.toFullString(), status);
    }
}
