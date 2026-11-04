package com.edispark.documents.api.dto;

import com.edispark.documents.domain.model.DocumentRelationType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentRelationResponse(DocumentRelationType type, UUID targetDocumentId, UUID targetDocumentSetId) {}
