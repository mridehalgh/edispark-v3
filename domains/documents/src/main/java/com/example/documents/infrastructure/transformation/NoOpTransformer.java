package com.example.documents.infrastructure.transformation;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.service.DocumentTransformer;

import java.util.logging.Logger;

/**
 * A stub transformer that returns input content unchanged (identity transformation).
 * 
 * <p>This is a placeholder implementation for initial development.
 * It logs a warning on each transformation call to indicate that actual
 * transformation is not being performed.
 * 
 * <p>Replace with format-specific transformers (XmlToJsonTransformer, JsonToXmlTransformer)
 * for production use.
 */
public class NoOpTransformer implements DocumentTransformer {

    private static final Logger LOGGER = Logger.getLogger(NoOpTransformer.class.getName());

    @Override
    public Content transform(Content source, Format targetFormat) {
        LOGGER.warning("Transformation is stubbed - returning input unchanged. " +
                "Source format: " + source.format() + ", Target format: " + targetFormat);
        
        // Return the source content unchanged (identity transformation)
        // In a real implementation, this would convert the content to the target format
        return Content.of(source.data(), targetFormat);
    }

    @Override
    public boolean supports(Format sourceFormat, Format targetFormat) {
        // Stub supports all format combinations
        return true;
    }
}
