package com.example.documents.application.handler;

import com.example.documents.domain.model.ContentHash;

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
