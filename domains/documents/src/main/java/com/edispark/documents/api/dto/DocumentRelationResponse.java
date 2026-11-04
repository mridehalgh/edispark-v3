package com.edispark.documents.api.dto;

import com.edispark.documents.domain.model.DocumentRelationType;

import java.util.UUID;

public record DocumentRelationResponse(DocumentRelationType type, UUID targetDocumentId) {}
