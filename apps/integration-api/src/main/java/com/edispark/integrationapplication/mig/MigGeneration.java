package com.edispark.integrationapplication.mig;

import com.edispark.transformations.domain.MappingCompatibility;
import com.edispark.partnerships.domain.model.DocumentFieldRule;
import java.util.Map;
import com.edispark.transformations.domain.MappingNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MigGeneration {
    ModelMode modelMode();
    Map<Candidate, String> modelIds();
    default UUID demoJobId() { return null; }
    Upload upload(UploadRequest request);
    Job estimate(EstimateRequest request);
    Job start(UUID jobId);
    Job status(UUID jobId);
    Job rerun(UUID jobId);
    Map<String, List<String>> findFields(UUID jobId, String query);
    FixtureResult checkCandidate(UUID jobId, Candidate candidate);
    Job retry(UUID jobId);
    Job cancel(UUID jobId);
    Job review(UUID jobId, ReviewRequest request);
    Job createDraft(UUID jobId, DraftRequest request);

    enum FileRole { PRIMARY, SUPPLEMENT, INPUT, EXPECTED, REFERENCE }
    enum State { ESTIMATED, QUEUED, RUNNING, CANCEL_REQUESTED, INCOMPLETE, RECONCILIATION, REVIEW, READY, IMPORTING, IMPORTED, CANCELLED }
    enum Candidate { SONNET, NOVA, JEV }

    enum ModelMode { LOCAL_FIXTURE, LONDON_BEDROCK, BEDROCK_AND_JEV, UNRECORDED }

    record Availability(boolean enabled, UUID demoJobId, ModelMode modelMode, Map<Candidate, String> modelIds) { }
    record UploadRequest(@NotNull FileRole role, @NotBlank @Size(max = 160) String name,
            @Size(max = 100) String edition, @NotBlank @Size(max = 4_194_304) String base64) { }
    record Upload(UUID id, String name, FileRole role, String edition, String digest) { }
    record EstimateRequest(@NotNull UUID primary, @Size(max = 3) List<@NotNull UUID> supplements,
            UUID input, UUID expected, UUID partnerId, UUID catalogueRevisionId, @NotNull @Valid Coordinates target, UUID reference) { }
    record Coordinates(@NotBlank @Pattern(regexp = "INBOUND|OUTBOUND") String direction,
            @NotBlank @Pattern(regexp = "[A-Z_]{1,32}") String documentType,
            @NotBlank @Pattern(regexp = "EDIFACT|TRADACOMS") String standard,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{1,12}") String release,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{1,12}") String messageType) {
        public MappingCompatibility compatibility() {
            return new MappingCompatibility(direction, documentType, standard, release, messageType);
        }
    }
    record Section(String anchor, String excerpt) { }
    enum StructureState { COMPARED, DIRECTORY_UNAVAILABLE, GUIDE_RELEASE_MISMATCH }
    enum StructureResult { MATCH, PARTNER_RESTRICTION, CONFLICT, NEEDS_REVIEW, ENVELOPE }
    record GuideStructure(String area, String position, String tag, String usage, int maxRepeats,
            String usageNote, String anchor) { }
    record StandardStructure(String position, String area, String groupPath, String tag, String usage, int maxRepeats) { }
    record StructureComparison(GuideStructure guide, StandardStructure standard, StructureResult result, String detail) { }
    record StandardsReview(StructureState state, String source, String digest, int omittedRows,
            List<StructureComparison> rows) { }
    record MigFinding(String code, String detail, List<String> citations) { }
    record Rule(String requirement, List<String> citations, MappingNode operation, String gap, RuleFacts facts,
            DocumentFieldRule profileRule, List<Map<String, String>> suggestedLookupRows, Boolean correctionAllowed) { }
    record RuleFacts(String presence, String segmentOrGroup, String qualifier, String cardinality, String valueType,
            String length, List<String> codes, String condition) { }
    record FixtureResult(String check, Boolean expectedMatch, List<MigFinding> findings, List<GeneratedCase> generatedChecks, List<MigFinding> referenceFindings) {
        public FixtureResult(String check, Boolean expectedMatch, List<MigFinding> findings) {
            this(check, expectedMatch, findings, List.of());
        }

        public FixtureResult(String check, Boolean expectedMatch, List<MigFinding> findings, List<GeneratedCase> generatedChecks) {
            this(check, expectedMatch, findings, generatedChecks, List.of());
        }
    }
    record GeneratedCase(String name, boolean passed, String detail) { }
    record Proposal(Candidate candidate, List<Rule> rules, List<MigFinding> findings, FixtureResult fixture) { }
    record Difference(String id, Rule sonnet, Rule nova, boolean preselected, Rule jev) {
        public Difference(String id, Rule sonnet, Rule nova, boolean preselected) {
            this(id, sonnet, nova, preselected, null);
        }
    }
    record Selection(@NotBlank String id, @NotNull Candidate candidate, @Size(max = 280) String reason, @Valid MappingNode override) { }
    record ReviewRequest(@NotNull @Size(max = 600) List<@NotNull @Valid Selection> selections,
            @Size(max = 600) List<@NotNull @Valid Supersession> supersessions,
            @Size(max = 20) Map<@Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{0,63}") String, @NotNull UUID> referenceTableRevisions) { }
    record Supersession(@NotBlank String differenceId, @NotBlank String prevailingAnchor, @NotBlank @Size(max = 280) String reason) { }
    record DraftRequest(@NotBlank @Size(max = 160) String name, Boolean includePartnerProfile) { }
    record Job(UUID id, State state, Coordinates target, List<Upload> uploads, String evidenceDigest,
            BigDecimal estimatedCredits, BigDecimal reservedCredits, BigDecimal settledCredits, String expiresAt,
            List<Section> sections, List<MigFinding> findings, List<Proposal> proposals, List<Difference> differences,
            List<Selection> selections, List<Supersession> supersessions, FixtureResult reviewedFixture, UUID draftId, UUID partnerId, UUID profileDraftId,
            Map<String, UUID> referenceTableRevisions, EvidenceManifest manifest, List<Invocation> invocations, StandardsReview standardsReview, ModelMode modelMode, UUID rerunOf, String uploadsExpireAt, Map<Candidate, String> modelIds) { }
    record Invocation(UUID id, Candidate candidate, String state, BigDecimal reservedCredits, BigDecimal settledCredits) { }
    record EvidenceManifest(int version, String ublSchemaDigest, String runtimeDigest, List<EvidenceRevision> revisions) { }
    record EvidenceRevision(UUID id, int revision, String digest, String category) { }
}
