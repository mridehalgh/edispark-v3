package com.edispark.integrationapplication.acl;

import com.edispark.partnerships.application.QualificationAcl;
import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.domain.MappingStatus;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class IntegrationQualificationAcl implements QualificationAcl {
    private final MappingService mappings;
    private final RestClient mft;

    public IntegrationQualificationAcl(MappingService mappings, @Qualifier("mftRestClient") RestClient mft) {
        this.mappings = mappings; this.mft = mft;
    }
    @Override public boolean publishedMappingMatches(UUID id, UUID sourceSchemaId, UUID targetSchemaId) {
        try {
            var item = mappings.get(id);
            return item.status() == MappingStatus.PUBLISHED && item.sourceSchemaId().equals(sourceSchemaId)
                    && item.targetSchemaId().equals(targetSchemaId);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
    @Override public boolean activeAs2ConnectionExists(UUID id) {
        MftPartner[] partners = mft.get().uri("/api/mft/partners").retrieve().body(MftPartner[].class);
        return partners != null && Arrays.stream(partners).anyMatch(item -> id.equals(item.id()) && "ACTIVE".equals(item.status()));
    }
    private record MftPartner(UUID id, String status) { }
}
