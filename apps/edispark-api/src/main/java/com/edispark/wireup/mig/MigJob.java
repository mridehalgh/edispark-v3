package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

record MigJob(UUID id, State state, Long created, long expiry, MigEvidence.Pack pack, List<Upload> uploads,
        long estimate, Map<Candidate, MigCandidateModel.Rate> rates, List<Attempt> attempts, List<Proposal> proposals,
        List<Difference> differences, ReviewRequest review, FixtureResult reviewed,
        UUID draftId, String draftName, UUID partnerId, UUID profileDraftId, ModelMode modelMode,
        UUID catalogueRevisionId, UUID rerunOf) {
    ModelMode recordedMode() {
        if (modelMode != null) return modelMode;
        return rates.size() == Candidate.values().length && rates.values().stream().allMatch(LocalMigModel.FIXTURE_RATE::equals)
                ? ModelMode.LOCAL_FIXTURE : ModelMode.UNRECORDED;
    }

    record Attempt(UUID id, Candidate candidate, long maximum, Long actual, String month, Phase phase, String problem, String receipt) {
        enum Phase { HELD, DISPATCHED, CONFIRMED, AMBIGUOUS, RELEASED }
        Attempt phase(Phase next, Long cost, String detail) { return new Attempt(id, candidate, maximum, cost, month, next, detail, receipt); }
        Attempt receipt(String receiptId) { return new Attempt(id, candidate, maximum, actual, month, phase, problem, receiptId); }
        MigStore.Ledger ledger() { return new MigStore.Ledger(id, candidate, phase.name(), maximum, actual, month, receipt); }
    }

    Job view(MigEvidence evidence) {
        long reserved = attempts.stream().filter(a -> a.actual() == null).mapToLong(Attempt::maximum).sum();
        long settled = attempts.stream().map(Attempt::actual).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        return new Job(id, state, pack.coordinates(), uploads, pack.digest(), credits(estimate), credits(reserved),
                credits(settled), state == State.IMPORTED ? null : Instant.ofEpochSecond(expiry).toString(),
                pack.sections(), java.util.stream.Stream.concat(pack.findings().stream(), attempts.stream()
                        .filter(attempt -> attempt.problem() != null && proposals.stream().noneMatch(p -> p.candidate() == attempt.candidate()))
                        .map(attempt -> new MigFinding("CANDIDATE_" + attempt.candidate(), attempt.problem(), List.of())))
                        .distinct().toList(), proposals, differences, review == null ? List.of() : review.selections(),
                review == null || review.supersessions() == null ? List.of() : review.supersessions(), reviewed, draftId, partnerId, profileDraftId,
                review == null || review.referenceTableRevisions() == null ? Map.of() : review.referenceTableRevisions(),
                evidence.manifest(pack), attempts.stream().map(attempt -> new Invocation(attempt.id(), attempt.candidate(),
                        attempt.phase().name(), credits(attempt.actual() == null ? attempt.maximum() : 0),
                        credits(attempt.actual() == null ? 0 : attempt.actual()))).toList(), pack.standardsReview(), recordedMode(), rerunOf, Instant.ofEpochSecond(expiry).toString());
    }

    MigJob move(State next, List<Attempt> nextAttempts, List<Proposal> nextProposals, List<Difference> nextDifferences) {
        return new MigJob(id, next, created, expiry, pack, uploads, estimate, rates, nextAttempts, nextProposals, nextDifferences,
                review, reviewed, draftId, draftName, partnerId, profileDraftId, modelMode, catalogueRevisionId, rerunOf);
    }

    MigJob replacePack(MigEvidence.Pack nextPack) {
        return new MigJob(id, state, created, expiry, nextPack, uploads, estimate, rates, attempts, proposals, differences, review,
                reviewed, draftId, draftName, partnerId, profileDraftId, modelMode, catalogueRevisionId, rerunOf);
    }

    MigJob withReview(State next, ReviewRequest request, FixtureResult result) {
        return new MigJob(id, next, created, expiry, pack, uploads, estimate, rates, attempts, proposals, differences,
                request, result, draftId, draftName, partnerId, profileDraftId, modelMode, catalogueRevisionId, rerunOf);
    }

    MigJob importAs(State next, UUID mapping, String name, UUID profileDraft) {
        return new MigJob(id, next, created, expiry, pack, uploads, estimate, rates, attempts, proposals, differences,
                review, reviewed, mapping, name, partnerId, profileDraft, modelMode, catalogueRevisionId, rerunOf);
    }

    private static BigDecimal credits(long micros) {
        return BigDecimal.valueOf(micros).divide(BigDecimal.valueOf(MigStore.MICROS_PER_CREDIT));
    }
}
