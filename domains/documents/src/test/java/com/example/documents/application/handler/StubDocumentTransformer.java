package com.example.documents.application.handler;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.service.DocumentTransformer;

/**
 * Stub implementation of DocumentTransformer for testing.
 */
class StubDocumentTransformer implements DocumentTransformer {

    private Content nextResult;
    private final Format supportedSourceFormat;
    private final Format supportedTargetFormat;

    StubDocumentTransformer(Format supportedSourceFormat, Format supportedTargetFormat) {
        this.supportedSourceFormat = supportedSourceFormat;
        this.supportedTargetFormat = supportedTargetFormat;
    }

    @Override
    public Content transform(Content source, Format targetFormat) {
        if (nextResult != null) {
            return nextResult;
        }
        // Default: return a simple transformed content
        return Content.of(("<transformed>" + new String(source.data()) + "</transformed>").getBytes(), targetFormat);
    }

    @Override
    public boolean supports(Format sourceFormat, Format targetFormat) {
        return sourceFormat == supportedSourceFormat && targetFormat == supportedTargetFormat;
    }

    public void setNextResult(Content result) {
        this.nextResult = result;
    }
}
