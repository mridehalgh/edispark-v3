package com.edispark.integrationapplication.mapping;

import com.edispark.partnerships.domain.model.BusinessDocumentType;

public interface MappingUblValidator {
    MappingValidationReport validate(BusinessDocumentType type, byte[] content);
}
