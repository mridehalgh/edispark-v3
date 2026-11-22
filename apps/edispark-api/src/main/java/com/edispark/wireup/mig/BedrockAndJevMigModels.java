package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import java.util.Map;
import java.util.UUID;

record BedrockAndJevMigModels(LondonMigModel bedrock, JevMigModel jev) implements MigCandidateModel {
    @Override public ModelMode mode() { return ModelMode.BEDROCK_AND_JEV; }

    @Override
    public Map<Candidate, String> modelIds() {
        return Map.of(Candidate.SONNET, bedrock.modelIds().get(Candidate.SONNET), Candidate.JEV, jev.model());
    }

    @Override
    public Rate rate(Candidate candidate) {
        return candidate == Candidate.JEV ? JevMigModel.RATE : bedrock.rate(candidate);
    }

    @Override
    public Result propose(Candidate candidate, UUID invocation, MigEvidence.Pack pack, byte[] evidence, Rate rate) {
        return propose(candidate, invocation, pack, evidence, rate, null, (turn, phase, actual, receipt) -> { });
    }

    @Override
    public Result propose(Candidate candidate, UUID invocation, MigEvidence.Pack pack, byte[] evidence,
            Rate rate, MigCandidateTools tools, InvocationJournal journal) {
        return candidate == Candidate.JEV ? jev.propose(pack, rate, journal)
                : bedrock.propose(candidate, invocation, pack, evidence, rate, tools, journal);
    }
}
