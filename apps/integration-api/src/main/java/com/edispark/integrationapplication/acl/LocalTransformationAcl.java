package com.edispark.integrationapplication.acl;

import com.edispark.exchange.application.TransformationAcl;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.transformations.application.MappingRuntime;
import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.domain.MappingStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class LocalTransformationAcl implements TransformationAcl {
    private final MappingService mappings;
    private final MappingRuntime runtime;
    private final EdiPayloadWriter edi;
    public LocalTransformationAcl(MappingService mappings, MappingRuntime runtime, EdiPayloadWriter edi) {
        this.mappings = mappings; this.runtime = runtime; this.edi = edi;
    }
    @Override public byte[] executePublished(ExchangeCapability capability, byte[] canonicalContent) {
        var mapping = mappings.get(capability.mappingRevisionId());
        if (mapping.status() != MappingStatus.PUBLISHED) {
            throw new IllegalArgumentException("Published mapping was not found");
        }
        return edi.write(capability, runtime.execute(mapping, canonicalContent));
    }
}
