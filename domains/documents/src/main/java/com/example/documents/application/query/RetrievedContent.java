package com.example.documents.application.query;

import java.util.Objects;

import com.example.documents.domain.model.Format;

public record RetrievedContent(
        byte[] bytes,
        Format format,
        String contentHash,
        String contentType,
        String fileName) {

    public RetrievedContent {
        Objects.requireNonNull(bytes, "Bytes cannot be null");
        Objects.requireNonNull(format, "Format cannot be null");
        Objects.requireNonNull(contentHash, "Content hash cannot be null");
        Objects.requireNonNull(contentType, "Content type cannot be null");
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
