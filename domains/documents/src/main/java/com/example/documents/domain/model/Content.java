package com.example.documents.domain.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Represents document or schema content with its format and computed hash.
 * The hash is computed automatically if not provided.
 */
public record Content(byte[] data, Format format, ContentHash hash) {

    public Content {
        Objects.requireNonNull(data, "Data cannot be null");
        Objects.requireNonNull(format, "Format cannot be null");
        // Defensive copy of the byte array
        data = data.clone();
        // Compute hash if not provided
        if (hash == null) {
            hash = computeHash(data);
        }
    }

    /**
     * Creates Content with automatic hash computation.
     */
    public static Content of(byte[] data, Format format) {
        return new Content(data, format, null);
    }

    /**
     * Computes the SHA-256 hash of the given data.
     */
    public static ContentHash computeHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            String hexHash = HexFormat.of().formatHex(hashBytes);
            return ContentHash.sha256(hexHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Returns a defensive copy of the data.
     */
    @Override
    public byte[] data() {
        return data.clone();
    }

    /**
     * Returns the size of the content in bytes.
     */
    public int size() {
        return data.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content content = (Content) o;
        return Arrays.equals(data, content.data) 
            && format == content.format 
            && Objects.equals(hash, content.hash);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(format, hash);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return "Content[format=" + format + ", size=" + data.length + ", hash=" + hash + "]";
    }
}
