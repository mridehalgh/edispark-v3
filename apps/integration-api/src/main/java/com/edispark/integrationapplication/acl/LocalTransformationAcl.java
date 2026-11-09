package com.edispark.integrationapplication.acl;

import com.edispark.exchange.application.TransformationAcl;
import com.edispark.exchange.domain.ExchangeWarning;
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
    @Override public Result executePublished(ExchangeCapability capability, byte[] canonicalContent) {
        var mapping = mappings.get(capability.mappingRevisionId());
        if (mapping.status() != MappingStatus.PUBLISHED) {
            throw new IllegalArgumentException("Published mapping was not found");
        }
        if (!mapping.sourceSchemaId().equals(capability.sourceSchemaId()) || !mapping.targetSchemaId().equals(capability.targetSchemaId())) {
            throw new IllegalArgumentException("The pinned mapping does not match the route's source and target schemas.");
        }
        var result = runtime.executeDetailed(mapping, canonicalContent);
        return new Result(edi.write(capability, result.output()), result.report(), result.warnings().stream()
                .map(warning -> new ExchangeWarning(warning.code(), warning.sourcePath(), warning.disposition(),
                        warning.message()))
                .toList());
    }
}
