package com.edispark.wireup.modules;

import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.PartnerStatus;
import com.edispark.partnerships.application.QualificationAcl;
import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.domain.MappingStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class InProcessQualificationAcl implements QualificationAcl {
    private final MappingService mappings;
    private final MftService mft;

    InProcessQualificationAcl(MappingService mappings, MftService mft) {
        this.mappings = mappings;
        this.mft = mft;
    }

    @Override
    public boolean publishedMappingMatches(UUID id, UUID sourceSchemaId, UUID targetSchemaId) {
        try {
            var mapping = mappings.get(id);
            return mapping.status() == MappingStatus.PUBLISHED && mapping.sourceSchemaId().equals(sourceSchemaId)
                    && mapping.targetSchemaId().equals(targetSchemaId);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean activeAs2ConnectionExists(UUID id) {
        return mft.listPartners().stream()
                .anyMatch(partner -> partner.id().equals(id) && partner.status() == PartnerStatus.ACTIVE);
    }

    @Override
    public QualificationAcl persistedView() {
        return new QualificationAcl() {
            @Override
            public boolean publishedMappingMatches(UUID id, UUID sourceSchemaId, UUID targetSchemaId) {
                try {
                    var mapping = mappings.getPersisted(id);
                    return mapping.status() == MappingStatus.PUBLISHED && mapping.sourceSchemaId().equals(sourceSchemaId)
                            && mapping.targetSchemaId().equals(targetSchemaId);
                } catch (IllegalArgumentException absent) {
                    return false;
                }
            }

            @Override
            public boolean activeAs2ConnectionExists(UUID id) {
                try {
                    return mft.persistedConnection(id).status() == PartnerStatus.ACTIVE;
                } catch (com.edispark.mft.application.MftNotFoundException absent) {
                    return false;
                }
            }

            @Override
            public RuleEvidence targetRuleEvidence(UUID id) {
                return InProcessQualificationAcl.this.targetRuleEvidence(id);
            }
        };
    }

    @Override public RuleEvidence targetRuleEvidence(UUID id) {
        var result = mappings.qualificationEvidence(id);
        return new RuleEvidence(result.valid(), result.identity(), result.failures());
    }
}
