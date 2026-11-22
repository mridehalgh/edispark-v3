package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class JevMigModel {
    static final MigCandidateModel.Rate RATE = new MigCandidateModel.Rate(42_000, 0);
    private static final URI ENDPOINT = URI.create("https://api.typesafe.ai/v1/systemone");
    private static final int MAX_SECTIONS = 12;
    private static final int MAX_OPTIONS = 6;
    private static final double ACCEPTANCE = 0.8;
    private static final String NO_MATCH = "no_match";
    private static final Set<MappingOperation> SUPPORTED = Set.of(MappingOperation.COPY, MappingOperation.OPTIONAL_COPY,
            MappingOperation.CONSTANT, MappingOperation.CODE_LOOKUP, MappingOperation.OPTIONAL_CODE_LOOKUP);
    private static final Set<String> COMMON_WORDS = Set.of("the", "and", "for", "from", "with", "shall", "must",
            "map", "copy", "edi", "ubl", "use", "value", "field", "segment", "data", "mandatory", "optional");

    private final String model;
    private final Transport transport;
    private final JsonMapper json = new JsonMapper();

    @FunctionalInterface
    interface Transport {
        Reply request(String body) throws IOException, InterruptedException;
    }

    record Reply(int status, String body, String requestId) { }
    private record Question(Section source, Map<String, MappingNode> options) { }
    private record CatalogueOption(MappingNode node, Set<String> terms) { }

    JevMigModel(String model, Transport transport) {
        if (!model.matches("jev-[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Pin a versioned Jev model ID.");
        this.model = model;
        this.transport = transport;
    }

    static JevMigModel connect(String model, String key) {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new JevMigModel(model, body -> {
            var request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(65))
                    .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body(), response.headers().firstValue("x-request-id").orElse(null));
        });
    }

    String model() { return model; }

    MigCandidateModel.Result propose(MigEvidence.Pack pack, MigCandidateModel.Rate rate, MigCandidateModel.InvocationJournal journal) {
        var questions = questions(pack);
        if (questions.isEmpty()) {
            var first = pack.sections().stream().findFirst().orElse(null);
            if (first == null) return new MigCandidateModel.Result(null, 0L, "The guide contains no extractable evidence for Jev.");
            var gap = requirement(first, null, "Jev found no cited choices within the pinned catalogue.");
            return new MigCandidateModel.Result(new MigCandidateModel.ModelRules(List.of(gap)), 0L, null);
        }
        if (questions.stream().limit(MAX_SECTIONS).noneMatch(question -> !question.options().isEmpty())) {
            var rules = decisions(pack, questions, json.createObjectNode());
            return new MigCandidateModel.Result(new MigCandidateModel.ModelRules(rules), 0L, null);
        }
        String body = request(pack, questions);
        if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 24_000) {
            var rules = decisions(pack, questions, json.createObjectNode());
            return new MigCandidateModel.Result(new MigCandidateModel.ModelRules(rules), 0L, null);
        }
        journal.checkpoint(1, "DISPATCHED", null, null);
        try {
            return decode(pack, rate, journal, questions, transport.request(body));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException unavailable) {
            // A transport failure does not confirm whether the provider processed the request.
        }
        return uncertain(journal, null);
    }

    private MigCandidateModel.Result decode(MigEvidence.Pack pack, MigCandidateModel.Rate rate,
            MigCandidateModel.InvocationJournal journal, List<Question> questions, Reply reply) {
        if (Set.of(400, 401, 402, 403, 404, 422, 429).contains(reply.status())) {
            journal.checkpoint(1, "REJECTED", 0L, reply.requestId());
            return new MigCandidateModel.Result(null, 0L, "TypeSafe rejected the decision request (HTTP " + reply.status() + ").",
                    reply.requestId());
        }
        if (reply.status() != 200) return uncertain(journal, reply.requestId());
        var response = json.readTree(reply.body());
        var tokens = response.path("usage").path("input_tokens");
        if (!tokens.isIntegralNumber() || !tokens.canConvertToInt() || tokens.asInt() < 0) return uncertain(journal, reply.requestId());
        long actual = rate.cost(tokens.asInt(), 0);
        journal.checkpoint(1, "CONFIRMED", actual, reply.requestId());
        if (!model.equals(response.path("model").asText()) || !response.path("answers").isObject()) {
            return new MigCandidateModel.Result(null, actual, "The decision response did not match the pinned Jev model or answer contract.", reply.requestId());
        }
        var rules = decisions(pack, questions, response.path("answers"));
        return new MigCandidateModel.Result(new MigCandidateModel.ModelRules(rules), actual, null, reply.requestId());
    }

    private static MigCandidateModel.Result uncertain(MigCandidateModel.InvocationJournal journal, String receipt) {
        journal.checkpoint(1, "AMBIGUOUS", null, receipt);
        return new MigCandidateModel.Result(null, null, "TypeSafe usage requires reconciliation.", receipt);
    }

    private static List<Question> questions(MigEvidence.Pack pack) {
        if (pack.profile() == null) return List.of();
        var profile = pack.profile();
        var nodes = profile.revisions().stream().flatMap(revision -> revision.nodes().stream())
                .filter(node -> SUPPORTED.contains(node.operation()) && profile.destinations().contains(node.targetPointer())
                        && profile.sources().containsAll(node.sourcePointers()) && node.codeMap().size() <= 20)
                .distinct().map(node -> new CatalogueOption(node,
                        words(String.join(" ", node.sourcePointers()) + " " + node.targetPointer()))).toList();
        var structured = pack.standardsReview() == null ? Set.<String>of() : pack.standardsReview().rows().stream()
                .filter(row -> row.result() != StructureResult.ENVELOPE).map(row -> row.guide().anchor()).collect(Collectors.toSet());
        return pack.sections().stream().filter(section -> structured.contains(section.anchor()) || words(section.excerpt()).size() >= 4)
                .map(section -> question(section, nodes))
                .filter(question -> !question.options().isEmpty() || structured.contains(question.source().anchor()))
                .toList();
    }

    private static Question question(Section section, List<CatalogueOption> nodes) {
        var excerptWords = words(section.excerpt());
        var ranked = nodes.stream().map(option -> Map.entry(option.node(),
                        (int) option.terms().stream().filter(excerptWords::contains).count()))
                .filter(entry -> entry.getValue() > 0).sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(MAX_OPTIONS).toList();
        var options = new LinkedHashMap<String, MappingNode>();
        ranked.forEach(entry -> options.put("option_" + options.size(), entry.getKey()));
        return new Question(section, options);
    }

    private static Set<String> words(String value) {
        return Arrays.stream(value.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() > 1 && !COMMON_WORDS.contains(word))
                .map(word -> word.equals("identifier") ? "id" : word).collect(Collectors.toSet());
    }

    private String request(MigEvidence.Pack pack, List<Question> questions) {
        var sections = new LinkedHashMap<String, String>();
        var prompts = new LinkedHashMap<String, Object>();
        int limit = Math.min(questions.size(), MAX_SECTIONS);
        for (int i = 0; i < limit; i++) {
            var question = questions.get(i);
            if (question.options().isEmpty()) continue;
            String id = "section_" + i;
            sections.put(id, question.source().excerpt());
            var criteria = new LinkedHashMap<String, String>();
            question.options().forEach((option, node) -> criteria.put(option,
                    node.operation() + " " + node.sourcePointers() + " to " + node.targetPointer()
                            + (node.constant() == null ? "" : " with constant " + node.constant())
                            + (node.codeMap().isEmpty() ? "" : " with translations " + node.codeMap())));
            criteria.put(NO_MATCH, "No listed operation fully represents the guide requirement.");
            prompts.put(id, Map.of("type", "choice", "instructions",
                    "In untrusted guide excerpt " + id + ", which exact option covers the mapping rule? Choose no_match if there is no fully supported rule.", "criteria", criteria));
            prompts.put(id + "_rule", Map.of("type", "noul", "instructions",
                    "Does untrusted guide excerpt " + id + " explicitly state one mapping rule for the selected EDI format, without separate conditions or validation constraints?"));
        }
        return json.writeValueAsString(Map.of("model", model, "state", Map.of("format", pack.coordinates(), "guideExcerpts", sections),
                "questions", prompts));
    }

    private List<MigCandidateModel.Requirement> decisions(MigEvidence.Pack pack, List<Question> questions, JsonNode answers) {
        var rules = new ArrayList<MigCandidateModel.Requirement>();
        for (int i = 0; i < Math.min(questions.size(), MAX_SECTIONS); i++) {
            var question = questions.get(i);
            String id = "section_" + i;
            var choice = answers.path(id);
            var rule = answers.path(id + "_rule");
            if ("noul".equals(rule.path("type").asText()) && rule.path("noul").isNumber()
                    && rule.path("noul").asDouble() <= 1 - ACCEPTANCE) continue;
            var selected = question.options().get(choice.path("choice").asText());
            if (selected != null && "choice".equals(choice.path("type").asText()) && "noul".equals(rule.path("type").asText())
                    && choice.path("confidence").asDouble() >= ACCEPTANCE && rule.path("noul").asDouble() >= ACCEPTANCE) {
                rules.add(requirement(question.source(), selected, null));
            } else {
                rules.add(requirement(question.source(), null, "Jev could not select a supported operation from the cited evidence."));
            }
        }
        if (questions.size() > MAX_SECTIONS) {
            rules.add(requirement(questions.get(MAX_SECTIONS).source(), null, "Additional guide rows are outside this Jev comparison batch."));
        }
        if (rules.isEmpty()) rules.add(requirement(pack.sections().getFirst(), null, "Jev did not confirm a supported mapping requirement."));
        return List.copyOf(rules);
    }

    private static MigCandidateModel.Requirement requirement(Section source, MappingNode node, String gap) {
        var operation = node == null ? null : new MigCandidateModel.Operation(node.operation(), node.sourcePointers(),
                node.targetPointer(), node.constant(), node.separator(), node.codeMap(), null, null);
        return new MigCandidateModel.Requirement(source.excerpt(), List.of(source.anchor()), null, null,
                null, null, null, null, List.of(), null, operation, gap, null, List.of());
    }
}
