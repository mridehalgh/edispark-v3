package com.example.documents.domain.model;

import java.util.Objects;

/**
 * A cryptographic hash of document content used for integrity verification and duplicate detection.
 *
 * @param algorithm the hash algorithm used (e.g., "SHA-256")
 * @param hash the hexadecimal representation of the hash value
 */
public record ContentHash(String algorithm, String hash) {

    public static final String DEFAULT_ALGORITHM = "SHA-256";

    public ContentHash {
        Objects.requireNonNull(algorithm, "Algorithm cannot be null");
        Objects.requireNonNull(hash, "Hash cannot be null");
        if (algorithm.isBlank()) {
            throw new IllegalArgumentException("Algorithm cannot be blank");
        }
        if (hash.isBlank()) {
            throw new IllegalArgumentException("Hash cannot be blank");
        }
    }

    /**
     * Creates a ContentHash with the default SHA-256 algorithm.
     */
    public static ContentHash sha256(String hash) {
        return new ContentHash(DEFAULT_ALGORITHM, hash);
    }

    /**
     * Returns the full hash identifier in the format "algorithm:hash".
     */
    public String toFullString() {
        return algorithm.toLowerCase() + ":" + hash;
    }

    @Override
    public String toString() {
        return toFullString();
    }
}
