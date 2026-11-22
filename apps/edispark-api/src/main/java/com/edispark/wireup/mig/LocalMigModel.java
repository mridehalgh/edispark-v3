package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import com.edispark.transformations.domain.MappingOperation;
import java.util.*;
import java.util.regex.Pattern;

final class LocalMigModel implements MigCandidateModel {
    private static final Pattern RULE = Pattern.compile("^RULE (COPY|OPTIONAL_COPY|OMIT) (\\S+) -> (\\S+)(?: \\| (.+))?$");

    @Override public ModelMode mode() { return ModelMode.LOCAL_FIXTURE; }

    static final Rate FIXTURE_RATE = new Rate(100, 300);

    @Override public Rate rate(Candidate candidate) { return FIXTURE_RATE; }

    @Override
    public Result propose(Candidate candidate, java.util.UUID invocationId, MigEvidence.Pack pack, byte[] evidence, Rate rate) {
        var rules = new ArrayList<Requirement>();
        for (var section : pack.sections()) {
            var match = RULE.matcher(section.excerpt());
            if (!match.matches()) continue;
            var operation = MappingOperation.valueOf(match.group(1));
            String detail = Objects.requireNonNullElse(match.group(4), match.group(3));
            rules.add(new Requirement(detail, List.of(section.anchor()), operation == MappingOperation.OPTIONAL_COPY ? "OPTIONAL" : "MANDATORY",
                    null, null, null, null, null, List.of(), null,
                    new Operation(operation, List.of(match.group(2)), match.group(3),
                            operation == MappingOperation.OMIT ? detail : null, null, Map.of(), null, null), null, null, List.of()));
        }
        if (rules.isEmpty()) {
            pack.sections().stream().findFirst().ifPresent(section -> rules.add(new Requirement(
                    "Guide analysis needs the London Bedrock runner", List.of(section.anchor()),
                    null, null, null, null, null, null, List.of(), null, null,
                    "Local fixture mode only supports synthetic guides with RULE declarations.", null, List.of())));
        }
        return new Result(new ModelRules(rules), rate.cost(evidence.length / 4, rules.size() * 30), null);
    }
}
