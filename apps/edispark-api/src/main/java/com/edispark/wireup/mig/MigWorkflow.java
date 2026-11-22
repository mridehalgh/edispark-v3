package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;
import static com.edispark.wireup.mig.MigJob.Attempt.Phase.*;

import com.edispark.integrationapplication.mig.MigGeneration;
import com.edispark.integrationapplication.acl.EdiPayloadWriter;
import com.edispark.partnerships.domain.model.*;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import com.edispark.transformations.domain.*;
import io.cottn.core.tenancy.TenantAccess;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

@RequiredArgsConstructor
final class MigWorkflow implements MigGeneration {
    private static final UUID DEMO_JOB = UUID.fromString("f98a4ce5-242c-4b58-aea6-b190c0928511");
    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_FIXTURE_BYTES = 1024 * 1024;
    private static final long LEASE_SECONDS = 240;
    private static final long RETENTION_SECONDS = 30 * 86_400;
    private static final String PRODUCTION_RECONCILIATION = "An invocation has no confirmed usage receipt. Reconciliation is required before it can be retried.";

    private final MigStore store;
    private final MigExtractor extractor;
    private final MigExtractor.Ocr ocr;
    private final MigEvidence evidence;
    private final MigReview review;
    private final MigPreview preview;
    private final MigCandidateModel models;
    private final MappingRepository mappings;
    private final PartnershipRepository partnerships;
    private final MeterRegistry meters;
    private final Clock clock;
    private final String demoTenant;

    @Override
    public Upload upload(UploadRequest request) {
        if ((request.role() == FileRole.PRIMARY || request.role() == FileRole.SUPPLEMENT)
                && (request.edition() == null || request.edition().isBlank())) {
            throw new IllegalArgumentException("Label each guide with its edition or revision date.");
        }
        String name = request.name().strip();
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Provide a file name without directory components.");
        }
        byte[] raw;
        try { raw = Base64.getDecoder().decode(request.base64()); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("The upload must be valid base64."); }
        if (raw.length == 0 || raw.length > MigExtractor.MAX_BYTES) throw new IllegalArgumentException("Guide files must be between 1 byte and 3 MiB.");
        var upload = new Upload(UUID.randomUUID(), name, request.role(), request.edition(), MigEvidence.digest(raw));
        if (request.role() == FileRole.PRIMARY || request.role() == FileRole.SUPPLEMENT) extractor.extract(upload, raw, null, new int[1]);
        else validateFixture(raw);
        store.upload(upload, raw, now() + RETENTION_SECONDS);
        return upload;
    }

    private void validateFixture(byte[] raw) {
        if (raw.length > MAX_FIXTURE_BYTES) throw new IllegalArgumentException("Fixture files must be 1 MiB or smaller.");
        try { MigExtractor.text(raw); }
        catch (IOException invalid) { throw new IllegalArgumentException("Fixture files must be UTF-8 text."); }
    }

    @Override public ModelMode modelMode() { return models.mode(); }

    @Override public UUID demoJobId() {
        if (!TenantAccess.currentId().equals(demoTenant)) return null;
        try { store.load(DEMO_JOB); return DEMO_JOB; }
        catch (org.springframework.web.server.ResponseStatusException missing) { return null; }
    }

    void seedDemo() throws IOException {
        if (!TenantAccess.currentId().equals(demoTenant)) return;
        var assets = List.of(demoAsset("order-d96a.txt", FileRole.PRIMARY),
                demoAsset("order.json", FileRole.INPUT), demoAsset("order.edi", FileRole.EXPECTED));
        if (store.demoMatches(DEMO_JOB, assets.stream().map(asset -> MigEvidence.digest(asset.bytes())).toList(), modelMode())) return;
        var ids = assets.stream().map(this::seedFile).toList();
        var target = new Coordinates("OUTBOUND", "ORDER", "EDIFACT", "D96A", "ORDERS");
        var job = estimate(new EstimateRequest(ids.get(0), List.of(), ids.get(1), ids.get(2), null, null, target, null), DEMO_JOB, null);
        store.replaceDemo(job);
    }

    private record DemoAsset(String name, FileRole role, byte[] bytes) { }

    private DemoAsset demoAsset(String name, FileRole role) throws IOException {
        try (var stream = new org.springframework.core.io.ClassPathResource("mig-demo/" + name).getInputStream()) {
            return new DemoAsset(name, role, stream.readAllBytes());
        }
    }

    private UUID seedFile(DemoAsset asset) {
        return upload(new UploadRequest(asset.role(), asset.name(), "1.0", Base64.getEncoder().encodeToString(asset.bytes()))).id();
    }

    @Override public Job estimate(EstimateRequest request) {
        return createEstimate(request, null);
    }

    @Override public Job rerun(UUID jobId) {
        var original = store.load(jobId).job();
        require(original.expiry() - now() > LEASE_SECONDS, "The original uploads expire soon or have expired. Upload fresh copies to rerun.");
        var primary = uploadId(original, FileRole.PRIMARY);
        require(primary != null, "No retained primary guide is available for this job.");
        var supplements = original.uploads().stream().filter(upload -> upload.role() == FileRole.SUPPLEMENT).map(Upload::id).toList();
        var request = new EstimateRequest(primary, supplements, uploadId(original, FileRole.INPUT),
                uploadId(original, FileRole.EXPECTED), original.partnerId(), catalogueSelection(original),
                original.pack().coordinates(), uploadId(original, FileRole.REFERENCE));
        return createEstimate(request, original.id());
    }

    private static UUID uploadId(MigJob job, FileRole role) {
        return job.uploads().stream().filter(upload -> upload.role() == role).map(Upload::id).findFirst().orElse(null);
    }

    private static UUID catalogueSelection(MigJob job) {
        if (job.catalogueRevisionId() != null) return job.catalogueRevisionId();
        var profile = job.pack().profile();
        if (profile == null) return null;
        if (profile.encoderRevision() != null) return profile.encoderRevision();
        // Legacy jobs retain the compatible revisions in the order used to select the published baseline.
        return profile.revisions().stream().map(MigEvidence.Reference::id)
                .filter(id -> !EdiPayloadWriter.usesCatalogueEncoder(id)).findFirst().orElse(null);
    }

    private Job createEstimate(EstimateRequest request, UUID rerunOf) {
        var job = estimate(request, UUID.randomUUID(), rerunOf);
        store.create(job);
        return job.view(evidence);
    }

    private MigJob estimate(EstimateRequest request, UUID id, UUID rerunOf) {
        BusinessDocumentType.valueOf(request.target().documentType());
        var guides = new ArrayList<MigStore.File>();
        guides.add(file(request.primary(), FileRole.PRIMARY));
        var supplementIds = request.supplements() == null ? List.<UUID>of() : request.supplements();
        if (new HashSet<>(supplementIds).size() != supplementIds.size()) throw new IllegalArgumentException("Select each supplement only once.");
        supplementIds.forEach(supplementId -> guides.add(file(supplementId, FileRole.SUPPLEMENT)));
        var files = new ArrayList<>(guides);
        if (request.input() != null) files.add(file(request.input(), FileRole.INPUT));
        if (request.expected() != null && request.reference() != null) {
            throw new IllegalArgumentException("Choose either an exact expected output or a reference output.");
        }
        if (request.expected() != null) files.add(file(request.expected(), FileRole.EXPECTED));
        if (request.reference() != null) files.add(file(request.reference(), FileRole.REFERENCE));
        var pack = extract(request.target(), evidence.profile(request.target(), request.partnerId(), request.catalogueRevisionId()), guides, false);
        var rates = Map.of(Candidate.SONNET, models.rate(Candidate.SONNET), Candidate.NOVA, models.rate(Candidate.NOVA));
        long maximum = Arrays.stream(Candidate.values()).mapToLong(candidate -> maximum(rates.get(candidate))).sum();
        long expiry = files.stream().mapToLong(MigStore.File::expiresAt).min().orElseThrow();
        return new MigJob(id, State.ESTIMATED, now(), expiry, pack, files.stream().map(MigStore.File::upload).toList(),
                maximum, rates, List.of(), List.of(), List.of(), null, null, null, null, request.partnerId(), null, modelMode(),
                request.catalogueRevisionId(), rerunOf);
    }

    private MigEvidence.Pack extract(Coordinates target, MigEvidence.Profile profile, List<MigStore.File> files, boolean recover) {
        var budget = new int[]{recover ? 6 : 0};
        var extracts = files.stream().map(file -> extractor.extract(file.upload(), store.raw(file), recover ? ocr : null, budget)).toList();
        return evidence.pack(target, profile, files.stream().map(MigStore.File::upload).toList(), extracts);
    }

    @Override public Job status(UUID jobId) { return store.load(jobId).job().view(evidence); }
    @Override public Map<String, List<String>> findFields(UUID jobId, String query) {
        var job = store.load(jobId).job();
        return new MigCandidateTools(job, review, preview).fields(query);
    }

    @Override public FixtureResult checkCandidate(UUID jobId, Candidate candidate) {
        var job = store.load(jobId).job();
        var proposed = job.proposals().stream().filter(item -> item.candidate() == candidate).findFirst().orElseThrow(MigStore::missing);
        var relevant = new HashSet<String>();
        MigReview.nodes(proposed.rules()).stream().map(MappingNode::tableLookup).filter(Objects::nonNull)
                .forEach(lookup -> relevant.add(lookup.alias()));
        var bindings = new HashMap<String, UUID>();
        if (job.review() != null && job.review().referenceTableRevisions() != null) {
            job.review().referenceTableRevisions().forEach((alias, id) -> { if (relevant.contains(alias)) bindings.put(alias, id); });
        }
        return preview.run(job, proposed.rules(), preview.fixture(job, store), bindings);
    }

    @Override public Job start(UUID jobId) { return queue(store.load(jobId), State.ESTIMATED); }
    @Override public Job retry(UUID jobId) { return queue(store.load(jobId), State.INCOMPLETE); }

    private Job queue(MigStore.Checkpoint checkpoint, State required) {
        var job = checkpoint.job();
        if (job.state() == State.QUEUED || job.state() == State.RUNNING) return job.view(evidence);
        require(job.state() == required, "This job is unavailable for dispatch in its current state.");
        require(job.recordedMode() == modelMode(), "The model runner changed. Prepare a new job to get a matching credit estimate.");
        require(job.attempts().size() < MAX_ATTEMPTS, "The candidate retry limit was reached.");
        require(job.expiry() - now() > LEASE_SECONDS, "The upload window is too close to expiry. Upload a fresh copy.");
        String month = YearMonth.from(clock.instant().atZone(ZoneOffset.UTC)).toString();
        var fresh = Arrays.stream(Candidate.values()).filter(candidate -> job.proposals().stream().noneMatch(p -> p.candidate() == candidate))
                .map(candidate -> new MigJob.Attempt(UUID.randomUUID(), candidate, maximum(job.rates().get(candidate)),
                        null, month, HELD, null, null)).toList();
        long ceiling = fresh.stream().mapToLong(MigJob.Attempt::maximum).sum();
        var attempts = Stream.concat(job.attempts().stream(), fresh.stream()).toList();
        var next = job.move(State.QUEUED, attempts, job.proposals(), job.differences());
        var writes = new ArrayList<>(store.reserve(month, ceiling));
        writes.add(store.enqueue(next));
        fresh.forEach(attempt -> writes.add(store.ledger(job.id(), attempt.ledger(), job.expiry())));
        store.save(checkpoint, next, 0, writes);
        record(required == State.ESTIMATED ? "started" : "retried");
        return next.view(evidence);
    }

    @Override
    public Job cancel(UUID jobId) {
        var checkpoint = store.load(jobId);
        var job = checkpoint.job();
        require(job.state() != State.IMPORTED && job.state() != State.IMPORTING, "A draft is already being created or has been imported.");
        if (job.state() == State.CANCELLED || job.state() == State.CANCEL_REQUESTED) return job.view(evidence);
        if (job.state() == State.RUNNING && checkpoint.leaseUntil() > now()) {
            var next = job.move(State.CANCEL_REQUESTED, job.attempts(), job.proposals(), job.differences());
            store.save(checkpoint, next, checkpoint.leaseUntil(), List.of());
            return next.view(evidence);
        }
        return finish(checkpoint, true).view(evidence);
    }

    void process(MigStore.Work message) {
        var checkpoint = store.loadWorker(message.id());
        var job = checkpoint.job();
        if (!job.pack().digest().equals(message.digest()) || checkpoint.leaseUntil() > now()) return;
        if (job.state() == State.RUNNING || job.state() == State.CANCEL_REQUESTED) {
            recover(checkpoint);
            checkpoint = store.loadWorker(job.id());
        }
        if (checkpoint.job().state() == State.CANCEL_REQUESTED || job.expiry() <= now()) {
            finish(checkpoint, true);
            return;
        }
        if (checkpoint.job().state() != State.QUEUED && checkpoint.job().state() != State.RUNNING) return;
        if (job.recordedMode() != modelMode()) {
            finish(checkpoint, false);
            return;
        }
        var running = checkpoint.job().move(State.RUNNING, checkpoint.job().attempts(), job.proposals(), List.of());
        store.save(checkpoint, running, now() + LEASE_SECONDS, List.of());
        recoverEvidence(running);
        dispatch(running.id());
    }

    private void recoverEvidence(MigJob job) {
        if (!job.proposals().isEmpty() || job.attempts().stream().anyMatch(a -> a.phase() != HELD)
                || job.pack().findings().stream().noneMatch(finding -> finding.code().equals("UNREADABLE_GUIDE_SECTION"))) return;
        var guides = job.pack().guides().stream().map(guide -> store.file(guide.id())).toList();
        var pack = extract(job.pack().coordinates(), job.pack().profile(), guides, true);
        if (pack.digest().equals(job.pack().digest())) return;
        var checkpoint = store.loadWorker(job.id());
        store.save(checkpoint, checkpoint.job().replacePack(pack), checkpoint.leaseUntil(),
                List.of(store.replaceQueueDigest(job.id(), pack.digest())));
    }

    private void dispatch(UUID id) {
        for (var candidate : Candidate.values()) {
            var checkpoint = store.loadWorker(id);
            if (checkpoint.job().state() == State.CANCEL_REQUESTED) break;
            var attempt = checkpoint.job().attempts().stream()
                    .filter(a -> a.candidate() == candidate && a.phase() == HELD).findFirst().orElse(null);
            if (attempt != null) invoke(checkpoint, attempt);
        }
        var checkpoint = store.loadWorker(id);
        finish(checkpoint, checkpoint.job().state() == State.CANCEL_REQUESTED);
    }

    private void invoke(MigStore.Checkpoint checkpoint, MigJob.Attempt held) {
        var job = checkpoint.job();
        var attempt = held.phase(DISPATCHED, null, null);
        saveAttempt(checkpoint, attempt, null, false);
        var response = models.propose(attempt.candidate(), attempt.id(), job.pack(), evidence.forModel(job.pack(), job.partnerId() != null),
                job.rates().get(attempt.candidate()), new MigCandidateTools(job, review, preview),
                (turn, phase, actual, receipt) -> store.invocationTurn(job.id(), attempt.id(), turn, phase, actual, receipt, job.expiry()));
        var current = store.loadWorker(job.id());
        if (current.job().attempts().stream().noneMatch(a -> a.id().equals(attempt.id()) && a.phase() == DISPATCHED)) return;
        var received = attempt.receipt(response.receipt());
        if (response.actualMicros() == null || response.actualMicros() > attempt.maximum()) {
            saveAttempt(current, received.phase(AMBIGUOUS, null, PRODUCTION_RECONCILIATION), null, false);
            return;
        }
        Proposal result = null;
        String problem = response.problem();
        if (response.proposal() != null) {
            try {
                var compiled = review.compile(attempt.candidate(), response.proposal(), job,
                        rules -> preview.run(job, rules, preview.fixture(job, store), Map.of()));
                if (compiled.rules().isEmpty()) problem = "The candidate did not return a complete requirements list.";
                else result = compiled;
            } catch (RuntimeException invalid) {
                problem = "The candidate could not be compiled against the pinned runtime.";
            }
        }
        saveAttempt(current, received.phase(CONFIRMED, response.actualMicros(), problem), result, true);
    }

    private void saveAttempt(MigStore.Checkpoint checkpoint, MigJob.Attempt attempt, Proposal proposal, boolean settle) {
        var job = checkpoint.job();
        var attempts = job.attempts().stream().map(a -> a.id().equals(attempt.id()) ? attempt : a).toList();
        var results = proposal == null ? job.proposals() : Stream.concat(job.proposals().stream(), Stream.of(proposal)).toList();
        var writes = new ArrayList<TransactWriteItem>();
        writes.add(store.ledger(job.id(), attempt.ledger(), job.expiry()));
        if (settle) writes.add(store.settle(attempt.month(), attempt.maximum(), attempt.actual()));
        var next = job.move(job.state(), attempts, results, job.differences());
        store.save(checkpoint, next, now() + LEASE_SECONDS, writes);
        if (settle) meters.summary("edispark.mig.credits.micros", "candidate", attempt.candidate().name()).record(attempt.actual());
    }

    private void recover(MigStore.Checkpoint checkpoint) {
        var job = checkpoint.job();
        for (var attempt : job.attempts()) {
            if (attempt.phase() == DISPATCHED) {
                saveAttempt(checkpoint, attempt.phase(AMBIGUOUS, null, PRODUCTION_RECONCILIATION), null, false);
                checkpoint = store.loadWorker(job.id());
            }
        }
    }

    private MigJob finish(MigStore.Checkpoint checkpoint, boolean cancel) {
        var job = checkpoint.job();
        var attempts = new ArrayList<MigJob.Attempt>();
        var writes = new ArrayList<TransactWriteItem>();
        for (var attempt : job.attempts()) {
            var next = attempt;
            if (attempt.phase() == HELD) {
                next = attempt.phase(RELEASED, 0L, null);
                writes.add(store.ledger(job.id(), next.ledger(), job.expiry()));
            } else if (attempt.phase() == DISPATCHED) {
                next = attempt.phase(AMBIGUOUS, null, PRODUCTION_RECONCILIATION);
                writes.add(store.ledger(job.id(), next.ledger(), job.expiry()));
            }
            attempts.add(next);
        }
        job.attempts().stream().filter(a -> a.phase() == HELD).collect(java.util.stream.Collectors.groupingBy(
                MigJob.Attempt::month, java.util.stream.Collectors.summingLong(MigJob.Attempt::maximum)))
                .forEach((month, ceiling) -> writes.add(store.settle(month, ceiling, 0)));
        State state = attempts.stream().anyMatch(a -> a.phase() == AMBIGUOUS) ? State.RECONCILIATION
                : cancel ? State.CANCELLED : job.proposals().size() == 2 ? State.REVIEW : State.INCOMPLETE;
        var differences = review.compare(job.proposals());
        long gaps = job.proposals().stream().flatMap(proposal -> proposal.rules().stream()).filter(rule -> rule.gap() != null).count();
        meters.summary("edispark.mig.rule_gaps").record(gaps);
        var next = job.move(state, List.copyOf(attempts), job.proposals(), differences);
        writes.add(store.dequeue(job.id()));
        store.save(checkpoint, next, 0, writes);
        record(state.name().toLowerCase(Locale.ROOT));
        meters.summary("edispark.mig.disagreements").record(differences.stream().filter(diff -> !diff.preselected()).count());
        return next;
    }

    State reconcile(UUID jobId, UUID attemptId, int inputTokens, int outputTokens, String receipt) {
        var checkpoint = store.loadWorker(jobId);
        var job = checkpoint.job();
        var attempt = job.attempts().stream().filter(item -> item.id().equals(attemptId)).findFirst().orElseThrow(MigStore::missing);
        if (attempt.phase() == CONFIRMED && receipt.equals(attempt.receipt())) return job.state();
        require(job.state() == State.RECONCILIATION && attempt.phase() == AMBIGUOUS, "This invocation is unavailable for reconciliation.");
        long actual = Math.addExact(store.confirmedTurnCost(jobId, attemptId),
                job.rates().get(attempt.candidate()).cost(inputTokens, outputTokens));
        require(actual <= attempt.maximum(), "The reported usage exceeds the pinned reservation.");
        var confirmed = attempt.phase(CONFIRMED, actual, "Usage reconciled. Retry this candidate if its result is still missing.").receipt(receipt);
        var attempts = job.attempts().stream().map(item -> item.id().equals(attemptId) ? confirmed : item).toList();
        State nextState = attempts.stream().anyMatch(item -> item.phase() == AMBIGUOUS) ? State.RECONCILIATION
                : job.proposals().size() == 2 ? State.REVIEW : State.INCOMPLETE;
        if (job.expiry() <= now() && nextState == State.INCOMPLETE) nextState = State.CANCELLED;
        var next = job.move(nextState, attempts, job.proposals(), job.differences());
        store.save(checkpoint, next, 0, List.of(store.ledger(job.id(), confirmed.ledger(), job.expiry()),
                store.settle(confirmed.month(), confirmed.maximum(), actual)));
        record("usage_reconciled");
        return nextState;
    }

    @Override
    public Job review(UUID jobId, ReviewRequest request) {
        var checkpoint = store.load(jobId);
        var job = checkpoint.job();
        require(job.state() == State.REVIEW || job.state() == State.READY, "Two completed proposals are required for review.");
        var selected = review.selected(job, request);
        var validation = preview.run(job, selected, preview.fixture(job, store), request.referenceTableRevisions());
        var findings = Stream.of(job.pack().findings(), review.checkGraph(selected), validation.findings())
                .flatMap(List::stream).distinct().toList();
        var result = new FixtureResult(validation.check(), validation.expectedMatch(), findings, validation.generatedChecks(), validation.referenceFindings());
        var updated = job.withReview(findings.isEmpty() && !Boolean.FALSE.equals(validation.expectedMatch()) ? State.READY : State.REVIEW,
                request, result);
        store.save(checkpoint, updated, 0, List.of());
        record(updated.state() == State.READY ? "review_passed" : "review_blocked");
        return updated.view(evidence);
    }

    @Override
    public Job createDraft(UUID jobId, DraftRequest request) {
        var checkpoint = store.load(jobId);
        var job = checkpoint.job();
        if (job.state() == State.IMPORTED) return job.view(evidence);
        require(job.state() == State.READY || job.state() == State.IMPORTING, "Revalidate and resolve all blocking findings before creating a draft.");
        if (job.state() == State.READY) {
            require(job.pack().profile().runtimeDigest().equals(MigEvidence.runtimeFingerprint()),
                    "The deployed runtime changed. Prepare a new evidence pack before import.");
            var rules = review.selected(job, job.review()).stream().map(Rule::profileRule).filter(Objects::nonNull).toList();
            boolean includeProfile = Boolean.TRUE.equals(request.includePartnerProfile()) || !rules.isEmpty();
            if (includeProfile && (job.partnerId() == null || rules.isEmpty())) {
                throw new IllegalArgumentException("A partner and executable profile rules are required to create a profile draft.");
            }
            if (includeProfile && partnerships.findPartner(job.partnerId()).isEmpty()) {
                throw new IllegalStateException("The trading partner is no longer available.");
            }
            job = job.importAs(State.IMPORTING, UUID.randomUUID(), request.name().strip(), includeProfile ? UUID.randomUUID() : null);
            store.save(checkpoint, job, 0, List.of());
            checkpoint = store.load(jobId);
        }
        job = checkpoint.job();
        if (job.state() == State.IMPORTED) return job.view(evidence);
        var chosen = review.selected(job, job.review());
        var profile = job.pack().profile();
        mappings.createIfAbsent(new MappingRevision(job.draftId(), TenantAccess.currentId(), job.draftName(), 1,
                profile.sourceSchema(), profile.targetSchema(), MappingStatus.DRAFT, MigReview.nodes(chosen), null,
                clock.instant(), null, job.draftId(), null, null, 1, MappingScope.TENANT, null, null, profile.encoderRevision()));
        if (job.profileDraftId() != null) importProfile(job, chosen);
        var imported = job.importAs(State.IMPORTED, job.draftId(), job.draftName(), job.profileDraftId());
        store.save(checkpoint, imported, 0, List.of());
        record("draft_imported");
        meters.timer("edispark.mig.time_to_draft").record(Duration.ofSeconds(Math.max(0, now() - Objects.requireNonNullElse(job.created(), now()))));
        meters.summary("edispark.mig.review.edits").record(job.review().selections().stream()
                .filter(selection -> selection.override() != null || selection.reason() != null && !selection.reason().isBlank()).count());
        return imported.view(evidence);
    }

    private void importProfile(MigJob job, List<Rule> chosen) {
        if (partnerships.findPartner(job.partnerId()).isEmpty()) throw new IllegalStateException("The trading partner is unavailable.");
        var now = clock.instant();
        var format = job.pack().coordinates();
        var rules = chosen.stream().map(Rule::profileRule).filter(Objects::nonNull).toList();
        String name = job.draftName().substring(0, Math.min(120, job.draftName().length()));
        partnerships.createProfileIfAbsent(new DocumentProfile(job.profileDraftId(), job.profileDraftId(),
                TenantAccess.currentId(), job.partnerId(), 1, name, ExchangeDirection.valueOf(format.direction()),
                BusinessDocumentType.valueOf(format.documentType()), DocumentProfileStatus.DRAFT,
                rules, SsccGenerationMode.MANUAL, null, now, now));
    }

    private MigStore.File file(UUID id, FileRole role) {
        var file = store.file(id);
        if (file.upload().role() != role) throw new IllegalArgumentException("The uploaded file has a different assigned role.");
        return file;
    }

    private static long maximum(MigCandidateModel.Rate rate) {
        return Math.multiplyExact(MigCandidateModel.MAX_TURNS, rate.cost(MigCandidateModel.INPUT_TOKEN_LIMIT, MigCandidateModel.OUTPUT_TOKENS));
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    private long now() { return clock.instant().getEpochSecond(); }
    private void record(String outcome) { meters.counter("edispark.mig.jobs", "outcome", outcome).increment(); }
}
