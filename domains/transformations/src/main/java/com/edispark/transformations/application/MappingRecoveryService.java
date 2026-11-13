package com.edispark.transformations.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public final class MappingRecoveryService {
    private final MappingService mappings;
    private final RetainedMappingSource sources;

    public Result test(UUID mappingId, RetainedMappingSource.Reference reference) {
        var mapping = mappings.get(mappingId);
        var compatibility = mapping.compatibility();
        if (compatibility == null || !compatibility.direction().equals("OUTBOUND")) {
            throw new IllegalArgumentException("Retained document testing requires a compatible outbound mapping.");
        }
        byte[] source = sources.read(reference, compatibility.documentType());
        return new Result(reference, new String(source, StandardCharsets.UTF_8), mappings.test(mappingId, source, null));
    }

    public record Result(RetainedMappingSource.Reference source, String input, MappingService.TestResult test) { }
}
