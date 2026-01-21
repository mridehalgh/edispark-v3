package com.example.documents.domain.service;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.Format;

/**
 * Domain service interface for transforming documents between formats.
 * 
 * <p>Implementations transform document content from one format to another
 * while preserving semantic content (e.g., XML to JSON, JSON to XML).
 * 
 * <p>This is a port in the hexagonal architecture - implementations are
 * provided in the infrastructure layer.
 */
public interface DocumentTransformer {

    /**
     * Transforms document content to the specified target format.
     *
     * @param source the source document content to transform
     * @param targetFormat the desired output format
     * @return the transformed content in the target format
     * @throws TransformationException if the transformation fails
     */
    Content transform(Content source, Format targetFormat);

    /**
     * Checks whether this transformer supports the given format combination.
     *
     * @param sourceFormat the format of the source document
     * @param targetFormat the desired output format
     * @return true if this transformer can handle the format combination
     */
    boolean supports(Format sourceFormat, Format targetFormat);
}
