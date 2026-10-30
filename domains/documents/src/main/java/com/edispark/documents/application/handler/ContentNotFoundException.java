package com.edispark.documents.application.handler;

import com.edispark.documents.domain.model.ContentHash;

public class ContentNotFoundException extends RuntimeException {

    private final ContentHash contentHash;

    public ContentNotFoundException(ContentHash contentHash) {
        super("Content not found for hash: " + contentHash.toFullString());
        this.contentHash = contentHash;
    }

    public ContentHash contentHash() {
        return contentHash;
    }
}
