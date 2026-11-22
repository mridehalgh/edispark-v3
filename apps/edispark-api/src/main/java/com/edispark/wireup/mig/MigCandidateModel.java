package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import com.edispark.transformations.domain.MappingOperation;
import java.util.List;
import java.util.Map;

interface MigCandidateModel {
    int OUTPUT_TOKENS = 8_000;
    int INPUT_TOKEN_LIMIT = 230_000;
    int MAX_TURNS = 2;

    record Rate(long inputPerMillionMicros, long outputPerMillionMicros) {
        long cost(int input, int output) {
            return Math.ceilDiv(Math.addExact(Math.multiplyExact(input, inputPerMillionMicros),
                    Math.multiplyExact(output, outputPerMillionMicros)), 1_000_000);
        }
    }
    record ModelRules(List<Requirement> requirements) { }
    record Requirement(String description, List<String> citations, String presence, String segmentOrGroup,
            String qualifier, String cardinality, String valueType, String length, List<String> codes, String condition,
            Operation operation, String unsupported, ProfileRule profileRule, List<Map<String, String>> suggestedLookupRows) { }
    record Operation(MappingOperation operation, List<String> sources, String destination, String constant,
            String separator, Map<String, String> codeMap, String tableAlias, String resultColumn) { }
    record ProfileRule(String fieldKey, String presence, String fixedValue, String defaultValue,
            List<String> allowedValues, Integer minimumLength, Integer maximumLength) { }
    record Result(ModelRules proposal, Long actualMicros, String problem, String receipt) {
        Result(ModelRules proposal, Long actualMicros, String problem) { this(proposal, actualMicros, problem, null); }
    }

    interface InvocationJournal {
        void checkpoint(int turn, String phase, Long actual, String receipt);
    }

    ModelMode mode();
    Rate rate(Candidate candidate);
    Result propose(Candidate candidate, java.util.UUID invocationId, MigEvidence.Pack pack, byte[] evidence, Rate rate);

    default Result propose(Candidate candidate, java.util.UUID invocationId, MigEvidence.Pack pack, byte[] evidence,
            Rate rate, MigCandidateTools tools, InvocationJournal journal) {
        return propose(candidate, invocationId, pack, evidence, rate);
    }

}
