package com.edispark.wireup.modules;

import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.integrationapplication.mapping.MappingUblValidator;
import com.edispark.integrationapplication.mapping.MappingValidationReport;
import com.edispark.integrationapplication.mapping.MappingValidationReport.Finding;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class InProcessMappingUblValidation implements MappingUblValidator {
    private final DocumentSetCommandHandler documents;
    private final ObjectReader json;

    InProcessMappingUblValidation(DocumentSetCommandHandler documents, ObjectMapper mapper) {
        this.documents = documents;
        this.json = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override
    public MappingValidationReport validate(BusinessDocumentType type, byte[] content) {
        String scope = "UBL 2.1 JSON schema";
        try {
            json.readTree(content);
        } catch (IOException invalid) {
            return MappingValidationReport.of(scope, List.of(Finding.error("INVALID_JSON", "/", "Supply one well-formed UBL JSON document.")));
        }
        var result = documents.validateCanonical(DocumentType.valueOf(type.name()), content);
        return MappingValidationReport.of(scope, result.errors().stream()
                .map(error -> Finding.error(error.code(), error.path(), error.message())).toList());
    }
}
