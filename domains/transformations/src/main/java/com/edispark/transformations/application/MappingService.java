package com.edispark.transformations.application;

import com.edispark.transformations.domain.GlobalMappingCatalog;
import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingRepository;
import com.edispark.transformations.domain.MappingRevision;
import com.edispark.transformations.domain.MappingStatus;
import com.edispark.transformations.domain.TargetValidationEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cottn.core.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class MappingService {
    private final MappingRepository repository;
    private final MappingRuntime runtime;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final GlobalMappingCatalog globalCatalog;
    private final TargetContractValidator targetValidator;

    public MappingService(MappingRepository repository, MappingRuntime runtime, ObjectMapper mapper, Clock clock,
            GlobalMappingCatalog globalCatalog) {
        this(repository, runtime, mapper, clock, globalCatalog, TargetContractValidator.unavailable());
    }

    @Autowired
    public MappingService(MappingRepository repository, MappingRuntime runtime, ObjectMapper mapper, Clock clock,
            GlobalMappingCatalog globalCatalog, TargetContractValidator targetValidator) {
        this.repository = repository; this.runtime = runtime; this.mapper = mapper; this.clock = clock;
        this.globalCatalog = globalCatalog;
        this.targetValidator = targetValidator;
    }

    public MappingRevision create(String name, UUID sourceSchemaId, UUID targetSchemaId, List<MappingNode> nodes) {
        return repository.save(MappingRevision.draft(tenant(), name, sourceSchemaId, targetSchemaId, nodes, clock.instant()));
    }

    public MappingRevision update(UUID id, List<MappingNode> nodes) { return repository.save(requireTenant(id).revise(nodes)); }
    public List<MappingRevision> list() {
        promoteDue();
        var combined = new LinkedHashMap<UUID, MappingRevision>();
        globalCatalog.list().forEach(mapping -> combined.put(mapping.id(), mapping));
        repository.list(tenant()).forEach(mapping -> combined.putIfAbsent(mapping.id(), mapping));
        return List.copyOf(combined.values());
    }
    public MappingRevision get(UUID id) { return promote(require(id)); }
    public MappingRevision createRevision(UUID id) {
        return repository.save(require(id).forkForTenant(tenant(), clock.instant()));
    }

    public TestResult test(UUID id, byte[] input, byte[] expected) {
        var mapping = require(id);
        var execution = runtime.executeDetailed(mapping, input);
        byte[] output = execution.output();
        boolean matches = expected == null || sameJson(expected, output);
        var failures = new ArrayList<String>();
        if (!matches) failures.add("Output did not match the expected fixture");
        execution.warnings().forEach(warning -> failures.add(warning.sourcePath() + ": " + warning.message()));
        if (requiresTargetEvidence(mapping)) {
            validateTarget(mapping, input, output).findings().forEach(finding ->
                    failures.add(finding.code() + " " + finding.path() + ": " + finding.message()));
        }
        return new TestResult(failures.isEmpty(), output, List.copyOf(failures));
    }

    public byte[] executePublished(UUID id, byte[] input) {
        MappingRevision mapping = require(id);
        if (mapping.status() != MappingStatus.PUBLISHED) {
            throw new IllegalArgumentException("Published mapping was not found");
        }
        return runtime.execute(mapping, input);
    }

    public MappingRevision publish(UUID id, List<Fixture> fixtures) {
        return publish(id, fixtures, null);
    }

    public MappingRevision publish(UUID id, List<Fixture> fixtures, Instant effectiveAt) {
        MappingRevision mapping = requireTenant(id);
        if (mapping.status() != MappingStatus.DRAFT) throw new IllegalStateException("Only drafts can be published");
        if (fixtures == null || fixtures.isEmpty()) throw new IllegalArgumentException("At least one passing fixture is required");
        for (Fixture fixture : fixtures) {
            TestResult result = test(id, fixture.input(), fixture.expected());
            if (!result.passed()) throw new IllegalArgumentException("Mapping fixture failed: " + fixture.name());
        }
        if (requiresTargetEvidence(mapping)) {
            var fixture = fixtures.getFirst();
            var target = validateTarget(mapping, fixture.input(), runtime.execute(mapping, fixture.input()));
            mapping = mapping.withTargetEvidence(new TargetValidationEvidence(target.ruleSet(), target.fingerprint(),
                    hash(mapping), fixtures.stream().map(item -> digest(item.input())).toList()));
        }
        Instant now = clock.instant();
        return repository.save(effectiveAt != null && effectiveAt.isAfter(now)
                ? mapping.schedule(hash(mapping), effectiveAt) : mapping.publish(hash(mapping), now));
    }

    public MappingRevision archive(UUID id) { return repository.save(requireTenant(id).archive(clock.instant())); }

    public TargetContractValidator.Result validateTarget(MappingRevision mapping, byte[] source, byte[] mapped) {
        return targetValidator.validate(mapping.sourceSchemaId(), mapping.targetSchemaId(), source, mapped);
    }

    public static boolean requiresTargetEvidence(MappingRevision mapping) {
        var compatibility = mapping.compatibility();
        return compatibility != null && compatibility.direction().equals("OUTBOUND")
                && compatibility.documentType().equals("ORDER_RESPONSE");
    }

    /** Qualification may use a curated public fixture or retained hash-only tenant evidence. */
    public QualificationEvidence qualificationEvidence(UUID id) {
        var mapping = require(id);
        if (!requiresTargetEvidence(mapping)) return new QualificationEvidence(true, "", List.of());
        if (mapping.sample() != null && mapping.scope() == com.edispark.transformations.domain.MappingScope.GLOBAL) {
            byte[] input = mapping.sample().sourceDocument().getBytes(StandardCharsets.UTF_8);
            var result = test(id, input, mapping.sample().targetDocument().getBytes(StandardCharsets.UTF_8));
            var rules = validateTarget(mapping, input, result.output());
            return new QualificationEvidence(result.passed(), identity(mapping, rules), result.failures());
        }
        var retained = mapping.targetValidationEvidence();
        if (retained == null) return new QualificationEvidence(false, "", List.of("Publish a new Order Response mapping with passing source and target fixtures."));
        var current = validateTarget(mapping, new byte[]{'{', '}'}, new byte[]{'{', '}'});
        boolean fresh = retained.mappingHash().equals(hash(mapping)) && retained.ruleFingerprint().equals(current.fingerprint())
                && retained.ruleSet().equals(current.ruleSet());
        return new QualificationEvidence(fresh, identity(mapping, current), fresh ? List.of()
                : List.of("Target validation rules changed. Test and publish a new mapping revision, then requalify the route."));
    }

    private String identity(MappingRevision mapping, TargetContractValidator.Result rules) {
        return "Target rules " + mapping.id() + ":" + hash(mapping) + ":" + rules.ruleSet() + ":" + rules.fingerprint();
    }

    public void deleteDraft(UUID id) {
        MappingRevision mapping = requireTenant(id);
        if (mapping.status() != MappingStatus.DRAFT) {
            throw new IllegalStateException("Only an unused draft mapping can be permanently deleted");
        }
        repository.delete(tenant(), id);
    }

    public Usage usage(UUID id) {
        MappingRevision mapping = require(id);
        if (mapping.scope() == com.edispark.transformations.domain.MappingScope.GLOBAL) {
            return new Usage(false, List.of(new Dependency("global-catalogue", mapping.id(),
                    "EdiSpark-vended mappings are immutable and retained for every tenant")));
        }
        boolean deletable = mapping.status() == MappingStatus.DRAFT;
        return new Usage(deletable, deletable ? List.of() : List.of(
                new Dependency("mapping-revision", mapping.id(), "Revision has entered its retained lifecycle")));
    }

    private void promoteDue() { repository.list(tenant()).forEach(this::promote); }
    private MappingRevision promote(MappingRevision mapping) {
        MappingRevision promoted = mapping.publishDue(clock.instant());
        return promoted == mapping ? mapping : repository.save(promoted);
    }

    private MappingRevision require(UUID id) {
        return globalCatalog.find(id).or(() -> repository.find(tenant(), id))
                .orElseThrow(() -> new IllegalArgumentException("Mapping was not found"));
    }
    private MappingRevision requireTenant(UUID id) {
        MappingRevision mapping = require(id);
        if (mapping.scope() == com.edispark.transformations.domain.MappingScope.GLOBAL) {
            throw new IllegalStateException("EdiSpark-vended global mappings are immutable; create a tenant draft first");
        }
        return mapping;
    }
    private String tenant() { return TenantContext.getCurrentTenant().asString(); }
    private byte[] normalize(byte[] json) {
        try { return mapper.writeValueAsBytes(mapper.readTree(json)); }
        catch (Exception exception) { throw new IllegalArgumentException("Fixture is not valid JSON", exception); }
    }
    private boolean sameJson(byte[] expected, byte[] actual) {
        try { return mapper.readTree(expected).equals(mapper.readTree(actual)); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("Fixture is not valid JSON", exception); }
    }
    private String hash(MappingRevision mapping) {
        try {
            byte[] bytes = mapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(mapping.nodes());
            return digest(bytes);
        } catch (Exception exception) { throw new IllegalStateException("Could not hash mapping", exception); }
    }

    private String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record Fixture(String name, byte[] input, byte[] expected) { }
    public record TestResult(boolean passed, byte[] output, List<String> failures) { }
    public record QualificationEvidence(boolean valid, String identity, List<String> failures) { }
    public record Usage(boolean deletable, List<Dependency> dependencies) { }
    public record Dependency(String type, UUID id, String label) { }
}
