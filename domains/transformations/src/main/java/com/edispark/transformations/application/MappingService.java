package com.edispark.transformations.application;

import com.edispark.transformations.domain.GlobalMappingCatalog;
import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.ReferenceTableReader;
import com.edispark.transformations.domain.ReferenceTableRevision;
import com.edispark.transformations.domain.MappingRepository;
import com.edispark.transformations.domain.MappingRevision;
import com.edispark.transformations.domain.MappingStatus;
import com.edispark.transformations.domain.TargetValidationEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final Optional<ReferenceTableReader> referenceTables;

    public MappingService(MappingRepository repository, MappingRuntime runtime, ObjectMapper mapper, Clock clock,
            GlobalMappingCatalog globalCatalog) {
        this(repository, runtime, mapper, clock, globalCatalog, TargetContractValidator.unavailable());
    }

    public MappingService(MappingRepository repository, MappingRuntime runtime, ObjectMapper mapper, Clock clock,
            GlobalMappingCatalog globalCatalog, TargetContractValidator targetValidator) {
        this(repository, runtime, mapper, clock, globalCatalog, targetValidator, Optional.empty());
    }

    @Autowired
    public MappingService(MappingRepository repository, MappingRuntime runtime, ObjectMapper mapper, Clock clock,
            GlobalMappingCatalog globalCatalog, TargetContractValidator targetValidator,
            Optional<ReferenceTableReader> referenceTables) {
        this.repository = repository; this.runtime = runtime; this.mapper = mapper; this.clock = clock;
        this.globalCatalog = globalCatalog;
        this.targetValidator = targetValidator;
        this.referenceTables = referenceTables;
    }

    public MappingRevision create(String name, UUID sourceSchemaId, UUID targetSchemaId, List<MappingNode> nodes) {
        return repository.save(repository.draft( name, sourceSchemaId, targetSchemaId, nodes, clock.instant()));
    }

    public MappingRevision update(UUID id, List<MappingNode> nodes) { return repository.save(requireTenant(id).revise(nodes)); }
    public List<MappingRevision> list() {
        promoteDue();
        var combined = new LinkedHashMap<UUID, MappingRevision>();
        globalCatalog.list().forEach(mapping -> combined.put(mapping.id(), mapping));
        repository.list().forEach(mapping -> combined.putIfAbsent(mapping.id(), mapping));
        return List.copyOf(combined.values());
    }
    public MappingRevision get(UUID id) { return promote(require(id)); }
    public MappingRevision getPersisted(UUID id) { return require(id); }
    public com.edispark.transformations.domain.MappingRevisionComparison comparePersisted(UUID baselineId, UUID candidateId) {
        return com.edispark.transformations.domain.MappingRevisionComparison.between(getPersisted(baselineId), getPersisted(candidateId));
    }

    public MappingRevision createRevision(UUID id) {
        return repository.save(repository.fork(require(id), clock.instant()));
    }

    public TestResult test(UUID id, byte[] input, byte[] expected) {
        return test(id, input, expected, Map.of());
    }

    public TestResult test(UUID id, byte[] input, byte[] expected, Map<String, UUID> bindings) {
        var preview = preview(require(id), input, expected, bindings);
        var failures = new ArrayList<String>();
        if (Boolean.FALSE.equals(preview.expectedMatch())) failures.add("Output did not match the expected fixture");
        preview.warnings().forEach(warning -> failures.add(warning.sourcePath() + ": " + warning.message()));
        preview.findings().forEach(finding -> failures.add(finding.code() + " " + finding.path() + ": " + finding.message()));
        return new TestResult(preview.passed(), preview.output(), List.copyOf(failures));
    }

    public PreviewResult preview(UUID id, byte[] input, byte[] expected) {
        return preview(require(id), input, expected, Map.of());
    }

    public PreviewResult previewRetained(UUID id, RetainedMappingSource.Reference reference,
                                         RetainedMappingSource sources) {
        var mapping = require(id);
        var compatibility = mapping.compatibility();
        if (compatibility == null || !"OUTBOUND".equals(compatibility.direction())) {
            throw new IllegalArgumentException("Retained document testing requires a compatible outbound mapping.");
        }
        return preview(mapping, sources.read(reference, compatibility.documentType()), null, Map.of());
    }

    private PreviewResult preview(MappingRevision mapping, byte[] input, byte[] expected, Map<String, UUID> bindings) {
        var execution = executeWithReferenceTables(mapping, input, bindings);
        byte[] output = execution.output();
        Boolean matches = expected == null ? null : sameJson(expected, output);
        var findings = requiresTargetEvidence(mapping) ? validateTarget(mapping, input, output).findings()
                : List.<TargetContractValidator.Finding>of();
        return new PreviewResult(!Boolean.FALSE.equals(matches) && execution.warnings().isEmpty() && findings.isEmpty(),
                matches, output, execution.warnings(), findings);
    }

    public record PreviewResult(boolean passed, Boolean expectedMatch, byte[] output,
                                List<com.edispark.transformations.domain.MappingWarning> warnings,
                                List<TargetContractValidator.Finding> findings) {}

    public byte[] executePublished(UUID id, byte[] input) {
        return executePublished(id, input, Map.of());
    }

    public byte[] executePublished(UUID id, byte[] input, Map<String, UUID> bindings) {
        MappingRevision mapping = require(id);
        if (mapping.status() != MappingStatus.PUBLISHED) {
            throw new IllegalArgumentException("Published mapping was not found");
        }
        return executeWithReferenceTables(mapping, input, bindings).output();
    }

    public MappingRuntime.ExecutionResult executeWithReferenceTables(MappingRevision mapping, byte[] input,
            Map<String, UUID> bindings) {
        return runtime.executeDetailed(mapping, input, resolveTables(mapping, bindings));
    }

    public void validateReferenceTables(UUID mappingId, Map<String, UUID> bindings) {
        resolveTables(require(mappingId), bindings);
    }

    private Map<String, ReferenceTableRevision> resolveTables(MappingRevision mapping, Map<String, UUID> bindings) {
        var resolved = new LinkedHashMap<String, ReferenceTableRevision>();
        var configured = bindings == null ? Map.<String, UUID>of() : bindings;
        for (var node : mapping.nodes()) {
            var lookup = node.tableLookup();
            if (lookup == null) continue;
            var id = configured.get(lookup.alias());
            if (id == null) {
                throw new IllegalArgumentException("Bind a published reference table to " + lookup.alias() + ".");
            }
            var table = resolved.computeIfAbsent(lookup.alias(), alias -> referenceTables
                    .orElseThrow(() -> new IllegalArgumentException("Reference table access is unavailable.")).require(id));
            if (!table.rows().getFirst().containsKey(lookup.resultColumn())) {
                throw new IllegalArgumentException("Reference table " + lookup.alias()
                        + " has no column " + lookup.resultColumn() + ".");
            }
        }
        if (!resolved.keySet().containsAll(configured.keySet())) {
            throw new IllegalArgumentException("Bind only aliases declared by this mapping.");
        }
        return resolved;
    }

    public MappingRevision publish(UUID id, List<Fixture> fixtures) {
        return publish(id, fixtures, null);
    }

    public MappingRevision publish(UUID id, List<Fixture> fixtures, Instant effectiveAt) {
        MappingRevision mapping = requireTenant(id);
        if (mapping.status() != MappingStatus.DRAFT) throw new IllegalStateException("Only drafts can be published");
        if (fixtures == null || fixtures.isEmpty()) throw new IllegalArgumentException("At least one passing fixture is required");
        for (Fixture fixture : fixtures) {
            TestResult result = test(id, fixture.input(), fixture.expected(), fixture.referenceTableRevisions());
            if (!result.passed()) throw new IllegalArgumentException("Mapping fixture failed: " + fixture.name());
        }
        if (requiresTargetEvidence(mapping)) {
            var fixture = fixtures.getFirst();
            var target = validateTarget(mapping, fixture.input(), executeWithReferenceTables(mapping, fixture.input(), fixture.referenceTableRevisions()).output());
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
        repository.delete(id);
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

    private void promoteDue() { repository.list().forEach(this::promote); }
    private MappingRevision promote(MappingRevision mapping) {
        MappingRevision promoted = mapping.publishDue(clock.instant());
        return promoted == mapping ? mapping : repository.save(promoted);
    }

    private MappingRevision require(UUID id) {
        return globalCatalog.find(id).or(() -> repository.find(id))
                .orElseThrow(() -> new IllegalArgumentException("Mapping was not found"));
    }
    private MappingRevision requireTenant(UUID id) {
        MappingRevision mapping = require(id);
        if (mapping.scope() == com.edispark.transformations.domain.MappingScope.GLOBAL) {
            throw new IllegalStateException("EdiSpark-vended global mappings are immutable; create a tenant draft first");
        }
        return mapping;
    }
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

    public record Fixture(String name, byte[] input, byte[] expected, Map<String, UUID> referenceTableRevisions) {
        public Fixture(String name, byte[] input, byte[] expected) {
            this(name, input, expected, Map.of());
        }
    }
    public record TestResult(boolean passed, byte[] output, List<String> failures) { }
    public record QualificationEvidence(boolean valid, String identity, List<String> failures) { }
    public record Usage(boolean deletable, List<Dependency> dependencies) { }
    public record Dependency(String type, UUID id, String label) { }
}
