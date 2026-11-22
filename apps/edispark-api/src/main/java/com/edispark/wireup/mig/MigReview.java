package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import com.edispark.transformations.domain.*;
import com.edispark.partnerships.domain.model.DocumentFieldRule;
import com.edispark.partnerships.domain.model.FieldRequirement;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MigReview {
    Proposal compile(Candidate candidate, MigCandidateModel.ModelRules output, MigJob job, Function<List<Rule>, FixtureResult> preview) {
        var raw = output.requirements();
        if (raw == null || raw.isEmpty() || raw.size() > 300 || raw.stream().anyMatch(Objects::isNull)) {
            var finding = new MigFinding("MODEL_REQUIREMENTS_MISSING", "The candidate did not return a bounded requirements list.", List.of());
            return new Proposal(candidate, List.of(), List.of(finding), null);
        }
        var rules = raw.stream().map(requirement -> rule(requirement, job.pack(), job.partnerId() != null)).toList();
        var findings = checkGraph(rules);
        return new Proposal(candidate, rules, findings, preview.apply(rules));
    }

    private Rule rule(MigCandidateModel.Requirement item, MigEvidence.Pack pack, boolean partnerSelected) {
        var facts = new RuleFacts(limit(item.presence(), 40), limit(item.segmentOrGroup(), 140),
                limit(item.qualifier(), 120), limit(item.cardinality(), 120), limit(item.valueType(), 100),
                limit(item.length(), 100), item.codes() == null ? List.of() : item.codes().stream().filter(Objects::nonNull)
                        .limit(50).map(code -> limit(code, 100)).toList(), limit(item.condition(), 500));
        String description = limit(item.description(), 500);
        var sections = pack.sections().stream().map(Section::anchor).collect(Collectors.toSet());
        List<String> citations = item.citations() == null ? List.of() : item.citations().stream()
                .filter(sections::contains).distinct().limit(10).toList();
        String gap = limit(item.unsupported(), 500);
        MappingNode node = null;
        DocumentFieldRule partnerRule = null;
        boolean correctionAllowed = false;
        if (description.isBlank() || citations.isEmpty() || item.citations().stream().anyMatch(anchor -> !sections.contains(anchor))) {
            gap = "The requirement needs resolvable guide citations and a description.";
        } else if (pack.profile() == null) {
            gap = "The runtime has no compatible profile for this release and direction.";
        } else if (gap.isBlank()) {
            try {
                if (item.operation() != null) node = operation(item.operation(), pack.profile());
                if (item.profileRule() != null && (partnerSelected || node == null)) {
                    partnerRule = profileRule(item.profileRule(), pack.profile());
                }
                if (node == null && partnerRule == null) throw new IllegalArgumentException("No supported operation or partner rule was proposed.");
                gap = unsupportedConstraint(facts, node, partnerRule);
                correctionAllowed = gap.isBlank();
            } catch (IllegalArgumentException invalid) {
                gap = invalid.getMessage();
                correctionAllowed = facts.condition().isBlank();
            }
        }
        return new Rule(description, citations, gap.isBlank() ? node : null, gap.isBlank() ? null : gap, facts,
                gap.isBlank() ? partnerRule : null, suggestedRows(item.suggestedLookupRows()), correctionAllowed);
    }

    private String unsupportedConstraint(RuleFacts facts, MappingNode operation, DocumentFieldRule profileRule) {
        if (!facts.condition().isBlank()) return "The runtime cannot enforce the cited cross-field condition.";
        if (!facts.length().isBlank() && (profileRule == null || profileRule.pattern() == null)) {
            return "An executable length constraint is required for this cited field.";
        }
        if (!facts.codes().isEmpty()) {
            boolean codesCovered = profileRule != null && profileRule.allowedValues().containsAll(facts.codes())
                    || operation != null && (operation.codeMap().keySet().containsAll(facts.codes())
                            || operation.codeMap().values().containsAll(facts.codes()));
            if (!codesCovered) return "The mapping or profile must enforce the complete cited code set.";
        }
        return "";
    }

    private MappingNode operation(MigCandidateModel.Operation proposal, MigEvidence.Profile profile) {
        if (proposal == null || proposal.operation() == null) throw new IllegalArgumentException("No supported operation was proposed.");
        var sources = proposal.sources() == null ? List.<String>of() : proposal.sources();
        String destination = proposal.destination();
        var allowed = proposal.operation() == MappingOperation.OMIT ? profile.sources() : profile.destinations();
        if (destination == null || !allowed.contains(destination) || sources.size() > 8 || !profile.sources().containsAll(sources)) {
            throw new IllegalArgumentException("The source or destination field is outside the verified profile.");
        }
        if (proposal.operation() != MappingOperation.OMIT && (sources.stream().mapToInt(MigReview::depth).max().orElse(0) != depth(destination)
                || sources.stream().filter(pointer -> depth(pointer) > 0).map(MigReview::scope).distinct().count() > 1)) {
            throw new IllegalArgumentException("The repeated collections do not share a verified loop scope.");
        }
        int count = sources.size();
        boolean validArity = switch (proposal.operation()) {
            case CONSTANT -> count == 0;
            case CONCAT, COALESCE, OMIT -> count > 0;
            default -> count == 1;
        };
        if (!validArity || proposal.constant() != null && proposal.constant().length() > 500
                || proposal.separator() != null && proposal.separator().length() > 30
                || proposal.codeMap() != null && proposal.codeMap().size() > 200) {
            throw new IllegalArgumentException("The operation has invalid operands.");
        }
        String constant = proposal.operation() == MappingOperation.CONSTANT || proposal.operation() == MappingOperation.OMIT
                ? proposal.constant() : null;
        String separator = proposal.operation() == MappingOperation.CONCAT ? proposal.separator() : null;
        Map<String, String> codes = switch (proposal.operation()) {
            case CODE_LOOKUP, OPTIONAL_CODE_LOOKUP -> proposal.codeMap();
            default -> Map.of();
        };
        try {
            var id = UUID.nameUUIDFromBytes(destination.getBytes(StandardCharsets.UTF_8));
            return new MappingNode(id, proposal.operation(), sources, destination, constant, separator, codes, null, null, null,
                    proposal.operation() == MappingOperation.LOOKUP_TABLE
                            ? new MappingNode.TableLookup(proposal.tableAlias(), proposal.resultColumn()) : null);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("A mapping operand is missing or invalid.");
        }
    }

    private DocumentFieldRule profileRule(MigCandidateModel.ProfileRule proposed, MigEvidence.Profile profile) {
        if (proposed.fieldKey() == null || !profile.partnerFields().containsKey(proposed.fieldKey())) {
            throw new IllegalArgumentException("The partner profile cannot enforce the proposed field.");
        }
        Integer min = proposed.minimumLength();
        Integer max = proposed.maximumLength();
        if (min != null && (min < 0 || min > 256) || max != null && (max < 1 || max > 256)
                || min != null && max != null && min > max) {
            throw new IllegalArgumentException("The proposed length constraint is outside supported bounds.");
        }
        var allowed = proposed.allowedValues() == null ? List.<String>of() : proposed.allowedValues();
        if (allowed.size() > 50 || allowed.stream().anyMatch(v -> v == null || v.isBlank() || v.length() > 120)
                || tooLong(proposed.fixedValue()) || tooLong(proposed.defaultValue())) {
            throw new IllegalArgumentException("The proposed fixed values, defaults, or code list exceed supported bounds.");
        }
        var presence = switch (Objects.toString(proposed.presence(), "")) {
            case "REQUIRED", "MANDATORY" -> FieldRequirement.REQUIRED;
            case "OPTIONAL", "" -> FieldRequirement.OPTIONAL;
            default -> throw new IllegalArgumentException("The proposed profile presence rule is unsupported.");
        };
        String pattern = min == null && max == null ? null
                : "(?s).{" + Objects.requireNonNullElse(min, 0) + "," + Objects.requireNonNullElse(max, 256) + "}";
        return new DocumentFieldRule(proposed.fieldKey(), presence, proposed.defaultValue(), proposed.fixedValue(),
                allowed, null, null, pattern, null);
    }

    private List<Map<String, String>> suggestedRows(List<Map<String, String>> rows) {
        if (rows == null) return List.of();
        return rows.stream().filter(Objects::nonNull).filter(row -> row.size() <= 10 && row.entrySet().stream()
                .allMatch(cell -> cell.getKey() != null && cell.getKey().length() <= 64 && !tooLong(cell.getValue())))
                .limit(20).toList();
    }

    private boolean tooLong(String value) { return value != null && value.length() > 120; }

    List<MigFinding> checkGraph(List<Rule> rules) {
        var findings = new ArrayList<MigFinding>();
        var destinations = new ArrayList<String>();
        var profileFields = new HashSet<String>();
        for (var rule : rules) {
            if (rule.gap() != null) findings.add(new MigFinding("RULE_GAP", rule.gap(), rule.citations()));
            if (rule.profileRule() != null && !profileFields.add(rule.profileRule().fieldKey())) {
                findings.add(new MigFinding("DUPLICATE_PROFILE_FIELD", "Conflicting writes to " + rule.profileRule().fieldKey(), rule.citations()));
            }
            if (rule.operation() == null || rule.operation().operation() == MappingOperation.OMIT) continue;
            String destination = rule.operation().targetPointer();
            if (destinations.stream().anyMatch(other -> overlap(other, destination))) {
                findings.add(new MigFinding("DUPLICATE_DESTINATION", "Conflicting writes to " + destination, rule.citations()));
            }
            destinations.add(destination);
        }
        return List.copyOf(findings);
    }

    private boolean overlap(String first, String second) {
        String[] a = first.split("/");
        String[] b = second.split("/");
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i].equals(b[i])) continue;
            if (a[i].equals("*") && b[i].matches("[0-9]+") || b[i].equals("*") && a[i].matches("[0-9]+")) continue;
            return false;
        }
        return true;
    }

    List<Difference> compare(List<Proposal> candidates) {
        var first = byDestination(candidates, Candidate.SONNET);
        boolean useJev = candidates.stream().anyMatch(proposal -> proposal.candidate() == Candidate.JEV);
        var second = byDestination(candidates, useJev ? Candidate.JEV : Candidate.NOVA);
        var ids = new TreeSet<>(first.keySet());
        ids.addAll(second.keySet());
        boolean checked = candidates.size() == 2 && candidates.stream().allMatch(branch -> branch.fixture() != null
                && branch.fixture().findings().isEmpty() && !Boolean.FALSE.equals(branch.fixture().expectedMatch())
                && branch.findings().stream().noneMatch(f -> f.code().startsWith("DUPLICATE_")));
        return ids.stream().map(id -> {
            var a = first.get(id); var b = second.get(id);
            boolean agree = checked && a != null && b != null && executable(a) && executable(b) && Objects.equals(a.operation(), b.operation())
                    && Objects.equals(a.profileRule(), b.profileRule())
                    && a.facts().equals(b.facts()) && !Collections.disjoint(a.citations(), b.citations());
            return new Difference(id, a, useJev ? null : b, agree, useJev ? b : null);
        }).toList();
    }

    private Map<String, Rule> byDestination(List<Proposal> candidates, Candidate candidate) {
        var result = new LinkedHashMap<String, Rule>();
        candidates.stream().filter(model -> model.candidate() == candidate).flatMap(proposal -> proposal.rules().stream()).forEach(rule -> {
            String key = rule.operation() != null
                    ? "field/" + rule.operation().operation().equals(MappingOperation.OMIT) + "/" + rule.operation().targetPointer()
                    : rule.profileRule() != null ? "profile/" + rule.profileRule().fieldKey()
                    : "gap/" + UUID.nameUUIDFromBytes((rule.requirement().strip().toLowerCase(Locale.ROOT) + rule.citations()).getBytes(StandardCharsets.UTF_8));
            if (result.containsKey(key)) {
                if (result.get(key).equals(rule)) return;
                key = key + "/" + result.size();
            }
            result.put(key, rule);
        });
        return result;
    }

    List<Rule> selected(MigJob job, ReviewRequest review) {
        var selections = unique(review.selections(), Selection::id);
        var supersessions = unique(review.supersessions() == null ? List.of() : review.supersessions(), Supersession::differenceId);
        var differences = unique(job.differences(), Difference::id);
        if (!differences.keySet().containsAll(selections.keySet()) || !selections.keySet().containsAll(supersessions.keySet())) {
            throw new IllegalArgumentException("Review decisions must refer to this job's evidence.");
        }
        return job.differences().stream().map(difference -> choose(difference, job, selections.get(difference.id()),
                supersessions.get(difference.id()))).toList();
    }

    private Rule choose(Difference difference, MigJob job, Selection selection, Supersession supersession) {
        var available = difference.sonnet() == null ? secondary(difference) : difference.sonnet();
        if (selection == null) return blocked(available, "Select an operation and resolve this cited requirement.");
        Rule chosen = switch (selection.candidate()) {
            case SONNET -> difference.sonnet();
            case NOVA -> difference.nova();
            case JEV -> difference.jev();
        };
        if (chosen == null) return blocked(available, "Choose an existing candidate for the requirement.");
        if (selection.override() != null) chosen = override(chosen, selection, job);
        if (!executable(chosen)) return blocked(chosen, "The selected requirement has no executable operation.");
        if ((!difference.preselected() || selection.override() != null) && (selection.reason() == null || selection.reason().isBlank())) {
            return blocked(chosen, "Explain the analyst decision for this disagreement.");
        }
        if (requiresSupersession(difference, job) && (supersession == null
                || !chosen.citations().contains(supersession.prevailingAnchor()))) {
            return blocked(chosen, "Confirm which cited edition prevails and why.");
        }
        return chosen;
    }

    private Rule override(Rule chosen, Selection selection, MigJob job) {
        if (job.pack().profile() == null || chosen.citations().isEmpty() || chosen.facts() == null) return chosen;
        if (!Boolean.TRUE.equals(chosen.correctionAllowed())) return blocked(chosen, "This requirement has an unresolved runtime constraint.");
        var changed = selection.override();
        try {
            var lookup = changed.tableLookup();
            var replacement = operation(new MigCandidateModel.Operation(changed.operation(), changed.sourcePointers(),
                    changed.targetPointer(), changed.constant(), changed.separator(), changed.codeMap(),
                    lookup == null ? null : lookup.alias(), lookup == null ? null : lookup.resultColumn()), job.pack().profile());
            String gap = unsupportedConstraint(chosen.facts(), replacement, chosen.profileRule());
            if (!gap.isBlank()) return blocked(chosen, gap);
            return new Rule(chosen.requirement(), chosen.citations(), replacement, null, chosen.facts(),
                    chosen.profileRule(), chosen.suggestedLookupRows(), true);
        } catch (IllegalArgumentException invalid) {
            return blocked(chosen, invalid.getMessage());
        }
    }

    private Rule secondary(Difference difference) {
        return difference.jev() == null ? difference.nova() : difference.jev();
    }

    private boolean requiresSupersession(Difference diff, MigJob job) {
        var second = secondary(diff);
        if (diff.preselected() || diff.sonnet() == null || second == null) return false;
        var sources = java.util.stream.Stream.concat(diff.sonnet().citations().stream(), second.citations().stream())
                .map(anchor -> anchor.split("/", 3)).filter(parts -> parts.length == 3).map(parts -> parts[1]).distinct().toList();
        return job.pack().guides().size() > 1 && sources.size() > 1;
    }

    private Rule blocked(Rule base, String reason) {
        return new Rule(base.requirement(), base.citations(), null, base.gap() == null ? reason : base.gap(), base.facts(),
                null, base.suggestedLookupRows(), base.correctionAllowed());
    }

    private static <T> Map<String, T> unique(List<T> input, Function<T, String> id) {
        var result = new HashMap<String, T>();
        for (var item : input) {
            if (result.putIfAbsent(id.apply(item), item) != null) throw new IllegalArgumentException("Use one review decision per requirement.");
        }
        return result;
    }

    private static boolean executable(Rule rule) { return rule.operation() != null || rule.profileRule() != null; }

    static List<MappingNode> nodes(List<Rule> rules) {
        return rules.stream().map(Rule::operation).filter(Objects::nonNull).toList();
    }

    private static int depth(String pointer) { return (int) Arrays.stream(pointer.split("/")).filter("*"::equals).count(); }
    private static String scope(String pointer) { return pointer.substring(0, pointer.lastIndexOf("/*") + 2); }
    private static String limit(String value, int size) { return value == null ? "" : value.strip().substring(0, Math.min(size, value.strip().length())); }
}
