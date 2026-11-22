package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;

import com.edispark.integrationapplication.inbound.InboundEdiTranslator;
import com.edispark.integrationapplication.acl.EdiPayloadWriter;
import com.edispark.integrationapplication.mapping.MappingExampleFiles;
import com.edispark.integrationapplication.mapping.MappingEdiValidator;
import com.edispark.integrationapplication.acl.BqAcknowledgementTargetContract;
import com.edispark.integrationapplication.acl.OrderResponseTargetContract;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.DocumentProfile;
import com.edispark.partnerships.domain.model.DocumentProfileStatus;
import com.edispark.partnerships.application.PartnershipService;
import com.edispark.exchange.application.PartnerProfileValidation;
import com.edispark.integrationapplication.persistence.ReferenceTables;
import com.edispark.transformations.application.MappingRuntime;
import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.domain.*;
import java.io.IOException;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
final class MigEvidence {
    static final int CONTRACT_VERSION = 2;
    private static final int MAX_MODEL_STRUCTURE_ROWS = 60;
    private static final int MAX_MODEL_EVIDENCE_WITH_COMPARISON = 190_000;
    private static final Pattern EDIFACT_RELEASE = Pattern.compile("(?i)(?:\\b(?:directory|release|version|ORDERS|INVOIC|ORDRSP|DESADV|RECADV|REMADV)\\s*[:=]?\\s*D[.: -]?|:D:)([0-9]{2}[AB])\\b");
    private static final List<String> OPERATIONS = Arrays.stream(MappingOperation.values()).map(Enum::name).toList();

    private final MappingService mappings;
    private final MappingExampleFiles examples;
    private final ObjectMapper json;
    private final PartnershipService partnerships;
    private final ReferenceTables referenceTables;

    record Reference(UUID id, int revision, String digest, String vendor, List<MappingNode> nodes) { }
    record Profile(UUID sourceSchema, UUID targetSchema, String ublSchemaDigest, String runtimeDigest,
            List<Reference> revisions, Set<String> sources, Set<String> destinations,
            MappingExampleFiles.Example example, List<DocumentProfile> partnerProfiles,
            List<ReferenceTables.Summary> referenceTables, Map<String, String> partnerFields, UUID encoderRevision) { }
    record Pack(int version, Coordinates coordinates, Profile profile, List<Upload> guides, List<Section> sections,
            List<MigFinding> findings, List<BusinessCodeListDefinition> codes, String digest, StandardsReview standardsReview) {
        Pack(int version, Coordinates coordinates, Profile profile, List<Upload> guides, List<Section> sections,
                List<MigFinding> findings, List<BusinessCodeListDefinition> codes, String digest) {
            this(version, coordinates, profile, guides, sections, findings, codes, digest, null);
        }
    }
    record ModelEvidence(int version, Coordinates format, Profile runtime, List<Upload> guides, List<Section> guide,
            List<String> operations, List<BusinessCodeListDefinition> codeLists, StandardsReview guideStructureComparison, boolean partnerSelected) { }

    Profile profile(Coordinates format, UUID partnerId, UUID catalogueRevisionId) {
        var profiles = partnerId == null ? List.<DocumentProfile>of() : partnerships.listDocumentProfiles(partnerId).stream()
                .filter(profile -> profile.status() == DocumentProfileStatus.PUBLISHED && profile.direction().name().equals(format.direction())
                        && profile.documentType().name().equals(format.documentType()))
                .sorted(Comparator.comparing(DocumentProfile::id)).limit(4).toList();
        var compatible = mappings.listPersisted().stream().filter(mapping -> format.compatibility().equals(mapping.compatibility()))
                .filter(mapping -> mapping.status() == MappingStatus.PUBLISHED)
                .sorted(Comparator.comparing((MappingRevision mapping) -> !mapping.id().equals(catalogueRevisionId))
                        .thenComparing(mapping -> mapping.scope() != MappingScope.GLOBAL).thenComparing(MappingRevision::id))
                .limit(8).toList();
        var baseline = compatible.stream().filter(mapping -> mapping.scope() == MappingScope.GLOBAL)
                .filter(mapping -> catalogueRevisionId == null ? !EdiPayloadWriter.usesCatalogueEncoder(mapping.id())
                        : catalogueRevisionId.equals(mapping.id())).findFirst().orElse(null);
        if (catalogueRevisionId != null && baseline == null) {
            throw new IllegalArgumentException("The catalogue revision does not support these exact exchange coordinates.");
        }
        if (baseline == null) return null;
        var references = compatible.stream().map(mapping -> new Reference(mapping.id(), mapping.revision(),
                digest(json.writeValueAsBytes(mapping.nodes())), mapping.vendor(), mapping.nodes().stream().limit(120).toList())).toList();
        var sources = new TreeSet<String>();
        var destinations = new TreeSet<String>();
        UUID encoder = EdiPayloadWriter.usesCatalogueEncoder(baseline.id()) ? baseline.id() : null;
        compatible.stream().filter(mapping -> mapping.scope() == MappingScope.GLOBAL)
                .filter(mapping -> encoder == null ? !EdiPayloadWriter.usesCatalogueEncoder(mapping.id()) : mapping.id().equals(encoder))
                .flatMap(mapping -> mapping.nodes().stream())
                .forEach(node -> {
                    node.sourcePointers().forEach(pointer -> {
                        sources.add(pointer);
                        sources.add(repeat(pointer));
                    });
                    if (node.operation() != MappingOperation.OMIT && (encoder != null || represented(format, node.targetPointer()))) {
                        destinations.add(node.targetPointer());
                        destinations.add(repeat(node.targetPointer()));
                    }
                });
        String schemaName = Arrays.stream(format.documentType().split("_")).map(word ->
                word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT)).reduce("", String::concat);
        String ublDigest = resourceDigest("ubl/2.1/json-schema/maindoc/UBL-" + schemaName + "-2.1.json");
        String runtime = runtimeFingerprint();
        var sample = encoder == null ? mappings.bundledSample(baseline) : baseline.sample();
        var example = encoder != null && sample == null ? new MappingExampleFiles.Example(null, null, null, null)
                : examples.load(format.compatibility(), sample);
        if (encoder == null) example = previewExample(format, example);
        return new Profile(baseline.sourceSchemaId(), baseline.targetSchemaId(), ublDigest, runtime, references,
                sources, destinations, example, profiles,
                referenceTables.list().stream().sorted(Comparator.comparing(ReferenceTables.Summary::id)).limit(20).toList(),
                PartnerProfileValidation.supportedFields(), encoder);
    }

    private MappingExampleFiles.Example previewExample(Coordinates format, MappingExampleFiles.Example example) {
        if (!format.equals(new Coordinates("OUTBOUND", "ORDER", "EDIFACT", "D96A", "ORDERS"))) return example;
        // Keep the preview input independent of the held-out demo fixture.
        try (var input = new ClassPathResource("mig-examples/ubl-order-d96a.json").getInputStream()) {
            String order = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return new MappingExampleFiles.Example(order, null, null, null);
        } catch (IOException error) {
            throw new java.io.UncheckedIOException("Could not read the bundled D96A order preview input.", error);
        }
    }

    private boolean represented(Coordinates format, String pointer) {
        return !"OUTBOUND".equals(format.direction()) || "ORDER_RESPONSE".equals(format.documentType())
                || EdiPayloadWriter.supportsMappingDestination(EdiStandard.valueOf(format.standard()),
                        BusinessDocumentType.valueOf(format.documentType()), pointer);
    }

    private static String repeat(String pointer) {
        return pointer.replaceAll("/(lines|items|packages|allocations|OrderLine|OrderLineResponse|InvoiceLine|DespatchLine|ReceiptLine|RemittanceAdviceLine|TransportHandlingUnit|GoodsItem)/[0-9]+(?=/|$)",
                "/$1/*");
    }

    static String runtimeFingerprint() {
        var files = List.of(MappingRuntime.class, InboundEdiTranslator.class, EdiPayloadWriter.class,
                PartnerProfileValidation.class, MappingEdiValidator.class, BqAcknowledgementTargetContract.class,
                OrderResponseTargetContract.class, MigReview.class, LondonMigModel.class, MigCandidateTools.class,
                com.edispark.edifact.OrderResponseD96a.class,
                com.edispark.tradacoms.ack.BqAcknowledgementV14.class);
        return digest(String.join("\n", files.stream().map(MigEvidence::runtimeDigest).toList()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    Pack pack(Coordinates coordinates, Profile profile, List<Upload> guides, List<MigExtractor.Extraction> extracts) {
        var sections = new ArrayList<Section>();
        var findings = new ArrayList<MigFinding>();
        int budget = MigExtractor.MAX_EVIDENCE;
        for (var extract : extracts) {
            findings.addAll(extract.findings());
            for (var section : extract.sections()) {
                budget -= section.anchor().length() + section.excerpt().length();
                if (budget < 0) {
                    findings.add(new MigFinding("EVIDENCE_LIMIT", "The combined guide text exceeds the review budget.", List.of(section.anchor())));
                    break;
                }
                sections.add(section);
            }
        }
        if (profile == null) {
            findings.add(new MigFinding("UNSUPPORTED_RUNTIME_PROFILE", "No deployed catalogue mapping matches these exact coordinates.",
                    sections.stream().limit(3).map(Section::anchor).toList()));
        } else if (profile.ublSchemaDigest().equals("unavailable")) {
            findings.add(new MigFinding("UBL_SCHEMA_UNAVAILABLE", "The exact UBL schema could not be pinned.", List.of()));
        }
        if ("EDIFACT".equals(coordinates.standard())) checkRelease(coordinates, sections, findings);
        var structure = MigStructureComparison.compare(coordinates, sections,
                findings.stream().anyMatch(finding -> finding.code().equals("GUIDE_RELEASE_MISMATCH")));
        var manifest = new Pack(CONTRACT_VERSION, coordinates, profile, guides, sections, findings,
                new BusinessCodeCatalog().list(), "", structure);
        return new Pack(manifest.version(), coordinates, profile, List.copyOf(guides), List.copyOf(sections),
                List.copyOf(findings), manifest.codes(), digest(json.writeValueAsBytes(manifest)), structure);
    }

    EvidenceManifest manifest(Pack pack) {
        var profile = pack.profile();
        if (profile == null) return new EvidenceManifest(pack.version(), null, null, List.of());
        var revisions = new ArrayList<EvidenceRevision>();
        profile.revisions().forEach(r -> revisions.add(new EvidenceRevision(r.id(), r.revision(), r.digest(), "MAPPING")));
        profile.partnerProfiles().forEach(r -> revisions.add(new EvidenceRevision(r.id(), r.revision(),
                digest(json.writeValueAsBytes(r)), "PARTNER_PROFILE")));
        return new EvidenceManifest(pack.version(), profile.ublSchemaDigest(), profile.runtimeDigest(), List.copyOf(revisions));
    }

    private void checkRelease(Coordinates coordinates, List<Section> sections, List<MigFinding> findings) {
        for (var section : sections) {
            if (!section.anchor().startsWith("PRIMARY/")) continue;
            var release = EDIFACT_RELEASE.matcher(section.excerpt());
            while (release.find()) {
                if (!coordinates.release().equals("D" + release.group(1).toUpperCase(Locale.ROOT))) {
                    findings.add(new MigFinding("GUIDE_RELEASE_MISMATCH",
                            "The primary guide names a different EDIFACT release.", List.of(section.anchor())));
                    break;
                }
            }
        }
    }

    byte[] forModel(Pack pack, boolean partnerSelected) {
        var report = pack.standardsReview();
        var withComparison = encodeForModel(pack, limitRows(report, MAX_MODEL_STRUCTURE_ROWS), partnerSelected);
        if (report == null || withComparison.length <= MAX_MODEL_EVIDENCE_WITH_COMPARISON) return withComparison;
        return encodeForModel(pack, limitRows(report, 0), partnerSelected);
    }

    private byte[] encodeForModel(Pack pack, StandardsReview report, boolean partnerSelected) {
        return json.writeValueAsBytes(new ModelEvidence(pack.version(), pack.coordinates(), pack.profile(),
                pack.guides(), pack.sections(), OPERATIONS, pack.codes(), report, partnerSelected));
    }

    private static StandardsReview limitRows(StandardsReview report, int maxRows) {
        if (report == null || report.rows().size() <= maxRows) return report;
        return new StandardsReview(report.state(), report.source(), report.digest(),
                report.omittedRows() + report.rows().size() - maxRows, report.rows().subList(0, maxRows));
    }

    private static String runtimeDigest(Class<?> type) { return resourceDigest(type.getName().replace('.', '/') + ".class"); }

    private static String resourceDigest(String path) {
        try (var resource = new ClassPathResource(path).getInputStream()) {
            return digest(resource.readAllBytes());
        } catch (IOException missing) {
            return "unavailable";
        }
    }

    static String digest(byte[] value) {
        try {
            return "sha-256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
