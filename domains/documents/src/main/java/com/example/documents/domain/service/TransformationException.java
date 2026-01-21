package com.example.documents.domain.service;

import com.example.documents.domain.model.Format;

/**
 * Exception thrown when document transformation fails.
 * 
 * <p>This exception indicates that the transformation from one format to another
 * could not be completed, either due to unsupported format combinations or
 * errors during the transformation process.</p>
 */
public class TransformationException extends RuntimeException {

    private final Format sourceFormat;
    private final Format targetFormat;

    public TransformationException(Format sourceFormat, Format targetFormat, String message) {
        super(message);
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
    }

    public TransformationException(Format sourceFormat, Format targetFormat, String message, Throwable cause) {
        super(message, cause);
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
