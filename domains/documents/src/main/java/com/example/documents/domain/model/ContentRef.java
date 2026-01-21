package com.example.documents.domain.model;

import java.util.Objects;

/**
 * A reference to content stored in the content store, identified by its hash.
 */
public record ContentRef(ContentHash hash) {

    public ContentRef {
        Objects.requireNonNull(hash, "Content hash cannot be null");
    }

    /**
     * Creates a ContentRef from a ContentHash.
     */
    public static ContentRef of(ContentHash hash) {
        return new ContentRef(hash);
    }

    @Override
    public String toString() {
        return "ContentRef[" + hash + "]";
    }
}
