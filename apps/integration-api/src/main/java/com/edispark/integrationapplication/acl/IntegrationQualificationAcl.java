package com.edispark.integrationapplication.acl;

import com.edispark.partnerships.application.QualificationAcl;
import com.edispark.transformations.domain.MappingRepository;
import com.edispark.transformations.domain.MappingStatus;
import io.cottn.core.tenancy.TenantContext;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class IntegrationQualificationAcl implements QualificationAcl {
    private final MappingRepository mappings;
    private final RestClient mft;

    public IntegrationQualificationAcl(MappingRepository mappings, @Qualifier("mftRestClient") RestClient mft) {
        this.mappings = mappings; this.mft = mft;
    }
    @Override public boolean publishedMappingMatches(UUID id, UUID sourceSchemaId, UUID targetSchemaId) {
        return mappings.find(TenantContext.getCurrentTenant().asString(), id)
                .filter(item -> item.status() == MappingStatus.PUBLISHED)
                .filter(item -> item.sourceSchemaId().equals(sourceSchemaId))
                .filter(item -> item.targetSchemaId().equals(targetSchemaId)).isPresent();
    }
    @Override public boolean activeAs2ConnectionExists(UUID id) {
        MftPartner[] partners = mft.get().uri("/api/mft/partners").retrieve().body(MftPartner[].class);
        return partners != null && Arrays.stream(partners).anyMatch(item -> id.equals(item.id()) && "ACTIVE".equals(item.status()));
    }
    private record MftPartner(UUID id, String status) { }
}
