package com.example.documents.application.handler;

import com.example.documents.domain.model.Format;

/**
 * Exception thrown when a format transformation is not supported.
 */
public class UnsupportedFormatException extends RuntimeException {

    private final Format sourceFormat;
    private final Format targetFormat;

    public UnsupportedFormatException(Format sourceFormat, Format targetFormat) {
        super("Transformation from " + sourceFormat + " to " + targetFormat + " is not supported");
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
    }

    public Format sourceFormat() {
        return sourceFormat;
    }

    public Format targetFormat() {
        return targetFormat;
    }
}
