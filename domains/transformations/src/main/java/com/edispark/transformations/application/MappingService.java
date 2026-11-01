package com.edispark.transformations.application;

import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingRepository;
import com.edispark.transformations.domain.MappingRevision;
import com.edispark.transformations.domain.MappingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cottn.core.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class MappingService {
    private final MappingRepository repository;
    private final MappingRuntime runtime;
    private final ObjectMapper mapper;
    private final Clock clock;

    public MappingService(MappingRepository repository, MappingRuntime runtime, ObjectMapper mapper, Clock clock) {
        this.repository = repository; this.runtime = runtime; this.mapper = mapper; this.clock = clock;
    }

    public MappingRevision create(String name, UUID sourceSchemaId, UUID targetSchemaId, List<MappingNode> nodes) {
        return repository.save(MappingRevision.draft(tenant(), name, sourceSchemaId, targetSchemaId, nodes, clock.instant()));
    }

    public MappingRevision update(UUID id, List<MappingNode> nodes) { return repository.save(require(id).revise(nodes)); }
    public List<MappingRevision> list() { return repository.list(tenant()); }
    public MappingRevision get(UUID id) { return require(id); }
    public MappingRevision createRevision(UUID id) { return repository.save(require(id).newDraft(clock.instant())); }

    public TestResult test(UUID id, byte[] input, byte[] expected) {
        byte[] output = runtime.execute(require(id), input);
        boolean matches = expected == null || java.util.Arrays.equals(normalize(expected), normalize(output));
        return new TestResult(matches, output, matches ? List.of() : List.of("Output did not match the expected fixture"));
    }

    public MappingRevision publish(UUID id, List<Fixture> fixtures) {
        MappingRevision mapping = require(id);
        if (mapping.status() != MappingStatus.DRAFT) throw new IllegalStateException("Only drafts can be published");
        if (fixtures == null || fixtures.isEmpty()) throw new IllegalArgumentException("At least one passing fixture is required");
        for (Fixture fixture : fixtures) {
            TestResult result = test(id, fixture.input(), fixture.expected());
            if (!result.passed()) throw new IllegalArgumentException("Mapping fixture failed: " + fixture.name());
        }
        return repository.save(mapping.publish(hash(mapping), clock.instant()));
    }

    private MappingRevision require(UUID id) { return repository.find(tenant(), id).orElseThrow(() -> new IllegalArgumentException("Mapping was not found")); }
    private String tenant() { return TenantContext.getCurrentTenant().asString(); }
    private byte[] normalize(byte[] json) {
        try { return mapper.writeValueAsBytes(mapper.readTree(json)); }
        catch (Exception exception) { throw new IllegalArgumentException("Fixture is not valid JSON", exception); }
    }
    private String hash(MappingRevision mapping) {
        try {
            byte[] bytes = mapper.writeValueAsString(mapping.nodes()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) { throw new IllegalStateException("Could not hash mapping", exception); }
    }

    public record Fixture(String name, byte[] input, byte[] expected) { }
    public record TestResult(boolean passed, byte[] output, List<String> failures) { }
}
