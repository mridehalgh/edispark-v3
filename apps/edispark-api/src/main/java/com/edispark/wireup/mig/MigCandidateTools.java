package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@RequiredArgsConstructor
public final class MigCandidateTools {
    private final MigJob job;
    private final MigReview review;
    private final MigPreview preview;

    @Tool(name = "read_mig_sections", description = "Read up to four cited excerpts from this job's pinned guide evidence.")
    public List<Section> sections(List<String> anchors) {
        if (anchors == null || anchors.size() > 4) throw new IllegalArgumentException("Choose up to four exact anchors.");
        return job.pack().sections().stream().filter(section -> anchors.contains(section.anchor())).limit(4).toList();
    }

    @Tool(name = "find_mig_fields", description = "Find verified source and destination pointers for this job's exact format.")
    public Map<String, List<String>> fields(@ToolParam(description = "Literal pointer substring") String query) {
        if (query == null || query.isBlank() || query.length() > 120 || job.pack().profile() == null) return Map.of();
        var profile = job.pack().profile();
        return Map.of("sources", matching(profile.sources(), query), "destinations", matching(profile.destinations(), query));
    }

    @Tool(name = "inspect_catalogue_fixture", description = "Inspect the safe bundled example and pinned revisions for this job's exact format.")
    public Map<String, Object> example() {
        var profile = job.pack().profile();
        if (profile == null) return Map.of("available", false);
        var example = profile.example();
        return Map.of("available", true, "source", excerpt(example.sourceDocument()), "sourceEdi", excerpt(example.sourceRaw()),
                "target", excerpt(example.targetDocument()), "targetEdi", excerpt(example.targetRaw()),
                "mappingIds", profile.revisions().stream().map(MigEvidence.Reference::id).toList());
    }

    @Tool(name = "check_mig_candidate", description = "Check up to twelve proposed requirements and preview their graph on the bundled example.")
    public Map<String, Object> check(MigCandidateModel.ModelRules proposal) {
        if (proposal == null || proposal.requirements() == null || proposal.requirements().size() > 12) {
            throw new IllegalArgumentException("Supply up to twelve proposed requirements.");
        }
        var compiled = review.compile(Candidate.SONNET, proposal, job,
                rules -> preview.run(job, rules, preview.catalogueFixture(job), Map.of()));
        var findings = compiled.findings().stream().limit(12).toList();
        var fixture = compiled.fixture() == null ? List.<MigFinding>of() : compiled.fixture().findings().stream().limit(8).toList();
        return Map.of("compileFindings", findings, "fixtureFindings", fixture);
    }

    private List<String> matching(Collection<String> paths, String search) {
        String query = search.toLowerCase(Locale.ROOT);
        return paths.stream().filter(path -> path.toLowerCase(Locale.ROOT).contains(query)).sorted().limit(20).toList();
    }

    private String excerpt(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), 1_600));
    }
}
