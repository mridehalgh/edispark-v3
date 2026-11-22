package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import com.edispark.integrationapplication.acl.EdiPayloadWriter;
import com.edispark.exchange.application.PartnerProfileValidation;
import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import com.edispark.integrationapplication.mapping.*;
import com.edispark.partnerships.domain.model.*;
import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.domain.*;
import com.fasterxml.jackson.databind.*;
import io.cottn.core.tenancy.TenantAccess;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class MigPreview {
    private static final UUID PREVIEW_ID = UUID.fromString("5fe1c2df-ec2d-4caf-8716-06016cc06f68");
    private final InboundEdiTranslator ediInput;
    private final EdiPayloadWriter ediOutput;
    private final MappingUblValidator ubl;
    private final MappingEdiValidator edi;
    private final MappingService mappings;
    private final ObjectMapper json;

    record Fixture(byte[] input, byte[] expected, boolean catalogueInput, boolean referenceOnly) {
        boolean paired() { return expected != null && !referenceOnly; }

        String check() {
            if (paired()) return "UPLOADED_EXPECTED";
            if (catalogueInput) return referenceOnly ? "CATALOGUE_STRUCTURE_WITH_REFERENCE" : "CATALOGUE_STRUCTURE";
            return referenceOnly ? "UPLOADED_STRUCTURE_WITH_REFERENCE" : "UPLOADED_STRUCTURE";
        }
    }

    Fixture fixture(MigJob job, MigStore store) {
        var input = uploaded(job, store, FileRole.INPUT);
        var expected = uploaded(job, store, FileRole.EXPECTED);
        var reference = uploaded(job, store, FileRole.REFERENCE);
        if (input != null) return new Fixture(input, expected == null ? reference : expected, false, reference != null);
        var output = expected == null ? reference : expected;
        return new Fixture(catalogueFixture(job).input(), output, true, output != null);
    }

    private byte[] uploaded(MigJob job, MigStore store, FileRole role) {
        return job.uploads().stream().filter(item -> item.role() == role).findFirst()
                .map(item -> store.raw(store.file(item.id()))).orElse(null);
    }

    Fixture catalogueFixture(MigJob job) {
        var profile = job.pack().profile();
        if (profile == null || profile.example() == null) return new Fixture(null, null, true, false);
        var example = profile.example();
        return new Fixture(bytes(inbound(job) ? example.sourceRaw() : example.sourceDocument()), null, true, false);
    }

    FixtureResult run(MigJob job, List<Rule> rules, Fixture fixture, Map<String, UUID> bindings) {
        var result = preview(job, rules, fixture, bindings);
        if (!fixture.referenceOnly()) return result;
        return new FixtureResult(result.check(), result.expectedMatch(), result.findings(), result.generatedChecks(),
                validateReference(job, fixture.expected()));
    }

    private FixtureResult preview(MigJob job, List<Rule> rules, Fixture fixture, Map<String, UUID> bindings) {
        var nodes = MigReview.nodes(rules);
        var findings = new ArrayList<MigFinding>();
        String check = fixture.check();
        if (job.pack().profile() == null || nodes.isEmpty() || fixture.input() == null) {
            String detail = job.pack().profile() != null && fixture.input() == null
                    ? "No compatible catalogue input is available for this profile. Upload an input to run the mapping preview."
                    : "The selected profile and compiled graph are required for a mapping preview.";
            findings.add(finding("PREVIEW_UNAVAILABLE", detail));
            return new FixtureResult(check, null, List.copyOf(findings));
        }
        if (!job.pack().profile().runtimeDigest().equals(MigEvidence.runtimeFingerprint())) {
            findings.add(finding("RUNTIME_CHANGED", "The deployed runtime changed. Prepare a new evidence pack."));
            return new FixtureResult(check, null, List.copyOf(findings));
        }
        var profileRules = rules.stream().map(Rule::profileRule).filter(Objects::nonNull).distinct().toList();
        if (!profileRules.isEmpty() && job.partnerId() == null) {
            findings.add(finding("PARTNER_REQUIRED", "Select a partner before preparing evidence to import partner requirements."));
        }
        try {
            return execute(job, nodes, fixture, findings, check, bindings, profileRules);
        } catch (Exception invalid) {
            findings.add(finding("PREVIEW_FAILED", "The parser, mapping runtime or encoder rejected the fixture. Check the selected release and candidate operations."));
            return new FixtureResult(check, fixture.paired() ? false : null, List.copyOf(findings));
        }
    }

    private FixtureResult execute(MigJob job, List<MappingNode> nodes, Fixture fixture, List<MigFinding> findings,
            String check, Map<String, UUID> bindings, List<DocumentFieldRule> profileRules) throws Exception {
        var format = job.pack().coordinates();
        var documentType = BusinessDocumentType.valueOf(format.documentType());
        var profile = job.pack().profile();
        byte[] source = fixture.input();
        if (inbound(job)) {
            append(findings, edi.validate(format.compatibility(), text(source)), "INPUT");
            var parsed = ediInput.translate(source);
            if (!parsed.standard().name().equals(format.standard()) || !parsed.release().equals(format.release())
                    || !parsed.messageType().equals(format.messageType())) {
                findings.add(finding("FIXTURE_RELEASE_MISMATCH", "The input has different exchange coordinates."));
                return new FixtureResult(check, fixture.paired() ? false : null, findings);
            }
            source = parsed.mappingInputJson();
        } else append(findings, ubl.validate(documentType, source), "INPUT");
        var mapping = new MappingRevision(PREVIEW_ID, TenantAccess.currentId(), "MIG preview", 1, profile.sourceSchema(),
                profile.targetSchema(), MappingStatus.DRAFT, nodes, null, Instant.EPOCH, null);
        var lookupAliases = nodes.stream().map(MappingNode::tableLookup).filter(Objects::nonNull)
                .map(MappingNode.TableLookup::alias).collect(java.util.stream.Collectors.toSet());
        var approved = bindings == null ? Map.<String, UUID>of() : bindings;
        if (!approved.keySet().equals(lookupAliases)) {
            findings.add(finding("REFERENCE_BINDINGS_REQUIRED", "Bind one approved reference table revision to each lookup alias: " + lookupAliases));
            return new FixtureResult(check, null, List.copyOf(findings));
        }
        var execution = mappings.executeWithReferenceTables(mapping, source, approved);
        execution.warnings().stream().limit(60).forEach(warning ->
                findings.add(finding(warning.code(), warning.sourcePath() + " " + warning.message())));
        byte[] result = execution.output();
        if (inbound(job)) append(findings, ubl.validate(documentType, result), "OUTPUT");
        else {
            var capability = new ExchangeCapability(PREVIEW_ID, ExchangeDirection.OUTBOUND, documentType,
                    EdiStandard.valueOf(format.standard()), format.release(), format.messageType(), profile.sourceSchema(),
                    profile.targetSchema(), PREVIEW_ID, PREVIEW_ID, AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
            var written = ediOutput.previewMapping(capability, result, profile.encoderRevision());
            written.warnings().stream().limit(60).forEach(warning -> findings.add(finding("ENCODER_FIELD_LOSS", warning)));
            result = written.output();
            append(findings, edi.validate(format.compatibility(), text(result)), "OUTPUT");
        }
        if (!profileRules.isEmpty() && job.partnerId() != null) {
            try {
                var partner = DocumentProfile.draft(TenantAccess.currentId(), job.partnerId(), "MIG preview",
                        ExchangeDirection.valueOf(format.direction()), documentType, profileRules, Instant.EPOCH);
                PartnerProfileValidation.validate(partner, inbound(job) ? result : fixture.input()).stream().limit(40)
                        .forEach(item -> findings.add(finding(item.code(), item.path() + " " + item.message())));
            } catch (IllegalArgumentException invalid) {
                findings.add(finding("PROFILE_RULES_INVALID", "Select one valid rule per partner field."));
            }
        }
        Boolean matches = fixture.paired() ? match(job, fixture.expected(), result, documentType, findings) : null;
        var generated = generatedCases(mapping, source, approved);
        generated.stream().filter(item -> !item.passed()).forEach(item ->
                findings.add(finding("GENERATED_CASE_FAILED", item.name() + ". " + item.detail())));
        return new FixtureResult(check, matches, List.copyOf(findings), generated);
    }

    private List<GeneratedCase> generatedCases(MappingRevision graph, byte[] input, Map<String, UUID> bindings) throws java.io.IOException {
        var cases = new ArrayList<GeneratedCase>();
        JsonNode source = json.readTree(input);
        var repeated = graph.nodes().stream().flatMap(node -> node.sourcePointers().stream())
                .filter(pointer -> pointer.indexOf("/*/") >= 0).distinct().limit(20).toList();
        if (!repeated.isEmpty()) {
            JsonNode expanded = source.deepCopy();
            boolean changed = false;
            for (String pointer : repeated) changed |= duplicate(expanded, pointer);
            if (changed) cases.add(checkGenerated("Generated repeat expansion", graph, expanded, bindings));
        }
        graph.nodes().stream().filter(node -> node.operation() == MappingOperation.OPTIONAL_COPY
                || node.operation() == MappingOperation.OPTIONAL_CODE_LOOKUP).filter(node -> node.sourcePointers().size() == 1)
                .limit(3).forEach(node -> {
                    var without = source.deepCopy();
                    String pointer = node.sourcePointers().getFirst().replace("/*/", "/0/");
                    var parent = without.at(pointer.substring(0, pointer.lastIndexOf('/')));
                    if (parent instanceof com.fasterxml.jackson.databind.node.ObjectNode object
                            && object.remove(pointer.substring(pointer.lastIndexOf('/') + 1)) != null) {
                        boolean sharedRequiredInput = graph.nodes().stream().anyMatch(other -> other != node
                                && other.operation() != MappingOperation.OPTIONAL_COPY && other.operation() != MappingOperation.OPTIONAL_CODE_LOOKUP
                                && other.sourcePointers().contains(node.sourcePointers().getFirst()));
                        if (!sharedRequiredInput) cases.add(checkGenerated("Generated absent optional field " + node.targetPointer(), graph, without, bindings));
                    }
                });
        return List.copyOf(cases);
    }

    private boolean duplicate(JsonNode root, String pointer) {
        int wildcard = pointer.indexOf("/*/");
        if (wildcard < 0 || !(root.at(pointer.substring(0, wildcard)) instanceof com.fasterxml.jackson.databind.node.ArrayNode values)
                || values.isEmpty()) return false;
        duplicate(values.get(0), pointer.substring(wildcard + 2));
        if (values.size() < 2) values.add(values.get(0).deepCopy());
        return true;
    }

    private GeneratedCase checkGenerated(String name, MappingRevision graph, JsonNode source, Map<String, UUID> bindings) {
        try {
            mappings.executeWithReferenceTables(graph, json.writeValueAsBytes(source), bindings);
            return new GeneratedCase(name, true, "The production mapping runtime accepted the generated variant.");
        } catch (java.io.IOException | RuntimeException failed) {
            return new GeneratedCase(name, false, "The mapping runtime rejected the generated variant.");
        }
    }

    private List<MigFinding> validateReference(MigJob job, byte[] expected) {
        var findings = new ArrayList<MigFinding>();
        try {
            var format = job.pack().coordinates();
            var report = inbound(job) ? ubl.validate(BusinessDocumentType.valueOf(format.documentType()), expected)
                    : edi.validate(format.compatibility(), text(expected));
            append(findings, report, "REFERENCE");
        } catch (RuntimeException invalid) {
            findings.add(finding("REFERENCE_INVALID", "The reference output could not be checked against the selected format."));
        }
        return List.copyOf(findings);
    }

    private boolean match(MigJob job, byte[] expected, byte[] actual, BusinessDocumentType type,
            List<MigFinding> findings) throws java.io.IOException {
        boolean matches;
        if (inbound(job)) {
            append(findings, ubl.validate(type, expected), "EXPECTED");
            matches = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(expected).equals(
                    (left, right) -> left.isNumber() && right.isNumber() ? left.decimalValue().compareTo(right.decimalValue())
                            : left.equals(right) ? 0 : 1, json.readTree(actual));
        } else {
            append(findings, edi.validate(job.pack().coordinates().compatibility(), text(expected)), "EXPECTED");
            boolean bq = "TRADACOMS".equals(job.pack().coordinates().standard())
                    && "ORDRSP".equals(job.pack().coordinates().messageType());
            var want = segments(text(expected), bq);
            var got = segments(text(actual), bq);
            matches = want.equals(got);
            if (!matches) {
                int position = 0;
                while (position < want.size() && position < got.size() && want.get(position).equals(got.get(position))) position++;
                findings.add(finding("EDI_SEGMENT_DIFFERENCE", "First segment difference at position " + (position + 1) + "."));
            }
        }
        if (!matches) findings.add(finding("EXPECTED_OUTPUT_MISMATCH", "The candidate output differs from the uploaded expected file."));
        return matches;
    }

    private void append(List<MigFinding> target, MappingValidationReport report, String phase) {
        if (report.valid()) return;
        report.findings().stream().filter(item -> "ERROR".equals(item.severity())).limit(40)
                .forEach(item -> target.add(finding(phase + "_" + item.code(), item.location() + " " + item.message())));
    }

    static List<List<String>> segments(String value) { return segments(value, false); }

    static List<List<String>> segments(String value, boolean bq) {
        String content = value.strip();
        boolean advice = content.startsWith("UNA") && content.length() >= 9;
        char terminator = advice ? content.charAt(8) : '\'';
        char element = advice ? content.charAt(4) : '+';
        char escape = advice ? content.charAt(6) : '?';
        return split(advice ? content.substring(9) : content, terminator, escape).stream().map(String::strip)
                .filter(line -> !line.isEmpty()).map(line -> normalise(line, element, escape, bq)).toList();
    }

    private static List<String> normalise(String line, char separator, char escape, boolean bq) {
        var parts = split(line, separator, escape);
        int[] controls = switch (parts.getFirst()) {
            case "UNB" -> new int[]{4, 5};
            case "UNZ" -> new int[]{2};
            default -> parts.getFirst().startsWith("STX=") ? new int[]{3, 4} : new int[0];
        };
        for (int index : controls) if (index < parts.size() && !parts.get(index).isEmpty()) parts.set(index, "[generated]");
        if (bq && parts.getFirst().matches("FIL=[0-9]+")) {
            parts.set(0, "FIL=[generated]");
            if (parts.size() > 2 && !parts.get(2).isEmpty()) parts.set(2, "[generated]");
        }
        return parts;
    }

    private static List<String> split(String value, char separator, char escape) {
        var parts = new ArrayList<String>();
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == escape) index++;
            else if (value.charAt(index) == separator) {
                parts.add(value.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    private static MigFinding finding(String code, String detail) { return new MigFinding(code, detail, List.of()); }
    private static boolean inbound(MigJob job) { return "INBOUND".equals(job.pack().coordinates().direction()); }
    private static String text(byte[] content) { return new String(content, StandardCharsets.UTF_8); }
    private static byte[] bytes(String value) { return value == null || value.isBlank() ? null : value.getBytes(StandardCharsets.UTF_8); }
}
