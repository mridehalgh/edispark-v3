package com.edispark.integrationapplication.acl;

import com.edispark.exchange.application.TransformationAcl;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.transformations.application.MappingRuntime;
import com.edispark.transformations.domain.MappingRepository;
import com.edispark.transformations.domain.MappingStatus;
import io.cottn.core.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class LocalTransformationAcl implements TransformationAcl {
    private final MappingRepository repository;
    private final MappingRuntime runtime;
    private final EdiPayloadWriter edi;
    public LocalTransformationAcl(MappingRepository repository, MappingRuntime runtime, EdiPayloadWriter edi) {
        this.repository = repository; this.runtime = runtime; this.edi = edi;
    }
    @Override public byte[] executePublished(ExchangeCapability capability, byte[] canonicalContent) {
        var mapping = repository.find(TenantContext.getCurrentTenant().asString(), capability.mappingRevisionId())
                .filter(item -> item.status() == MappingStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalArgumentException("Published mapping was not found"));
        return edi.write(capability, runtime.execute(mapping, canonicalContent));
    }
}
