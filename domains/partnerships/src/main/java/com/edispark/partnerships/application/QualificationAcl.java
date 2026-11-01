package com.edispark.partnerships.application;

import java.util.UUID;

public interface QualificationAcl {
    boolean publishedMappingMatches(UUID mappingRevisionId, UUID sourceSchemaId, UUID targetSchemaId);
    boolean activeAs2ConnectionExists(UUID connectionId);
}
