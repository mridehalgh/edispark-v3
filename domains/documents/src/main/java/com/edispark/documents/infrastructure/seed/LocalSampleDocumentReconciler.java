package com.edispark.documents.infrastructure.seed;

import com.edispark.documents.domain.model.*;
import com.edispark.documents.domain.repository.ContentStore;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.edispark.identifiers.tenant.TenantId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Repairs reference metadata and unambiguous UBL links on repository-owned simulator samples only. */
@Component
@Profile("local & !openapi-export")
public final class LocalSampleDocumentReconciler {
    private final DocumentSetRepository sets;
    private final ContentStore content;
    private final ObjectMapper json;
    private final String tenant;

    public LocalSampleDocumentReconciler(DocumentSetRepository sets, ContentStore content, ObjectMapper json,
            @Value("${documents.seed.tenant-id}") String tenant) {
        this.sets = sets;
        this.content = content;
        this.json = json;
        this.tenant = tenant;
    }

    @Scheduled(initialDelayString = "${documents.seed.lineage-initial-delay-ms:5000}",
            fixedDelayString = "${documents.seed.lineage-delay-ms:30000}")
    public void reconcile() {
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenant)), this::reconcileTenantData);
    }

    void reconcileTenantData() {
        List<Sample> samples = sets.findAll().stream().filter(this::ownedSample)
                .flatMap(set -> set.getAllDocuments().stream()
                        .filter(document -> "integration:mft-inbound".equals(document.getCurrentVersion().createdBy()))
                        .map(document -> sample(set, document)).flatMap(Optional::stream))
                .toList();
        Map<String, List<Sample>> index = samples.stream().collect(Collectors.groupingBy(Sample::key));
        for (Sample source : samples) reconcile(source, index);
    }

    private boolean ownedSample(DocumentSet set) {
        if (!"integration:mft-inbound".equals(set.createdBy())) return false;
        String message = set.metadata().getOrDefault("as2MessageId", "");
        return message.startsWith("<demo-lifecycle-")
                || message.equals("<local-inbound@northstar.test>")
                || message.equals("<local-inbound@harbour.test>");
    }

    private Optional<Sample> sample(DocumentSet set, Document document) {
        String scope = set.metadata().get("mftPartnerId");
        if (scope == null || scope.isBlank()) return Optional.empty();
        List<ContentHash> candidates = new ArrayList<>();
        if (document.getCurrentVersion().format() == Format.JSON) candidates.add(document.getCurrentVersion().contentHash());
        document.derivatives().stream().filter(derivative -> derivative.targetFormat() == Format.JSON)
                .filter(derivative -> derivative.sourceVersionId().equals(document.getCurrentVersion().id()))
                .sorted(Comparator.comparing(Derivative::createdAt).reversed())
                .forEach(derivative -> candidates.add(derivative.contentHash()));
        for (ContentHash hash : candidates) {
            var parsed = parse(set, document, scope, hash);
            if (parsed.isPresent()) return parsed;
        }
        return Optional.empty();
    }

    private Optional<Sample> parse(DocumentSet set, Document document, String scope, ContentHash hash) {
        try {
            byte[] bytes = content.retrieve(hash).orElse(null);
            if (bytes == null) return Optional.empty();
            JsonNode envelope = json.readTree(bytes);
            String root = rootName(document.type());
            if (!envelope.path("_D").asText().equals("urn:oasis:names:specification:ubl:schema:xsd:" + root + "-2")) return Optional.empty();
            JsonNode body = envelope.path(root).path(0);
            String reference = scalar(body.path("ID"));
            return reference.isBlank() ? Optional.empty() : Optional.of(new Sample(set, document, scope, reference, body));
        } catch (IOException exception) {
            return Optional.empty(); // Retain malformed source evidence; never guess a relationship.
        }
    }

    private String rootName(DocumentType type) {
        return switch (type) {
            case ORDER -> "Order";
            case ORDER_RESPONSE -> "OrderResponse";
            case DESPATCH_ADVICE -> "DespatchAdvice";
            case INVOICE -> "Invoice";
            case RECEIPT_ADVICE -> "ReceiptAdvice";
            case REMITTANCE_ADVICE -> "RemittanceAdvice";
            default -> "Unsupported";
        };
    }

    private void reconcile(Sample source, Map<String, List<Sample>> index) {
        int previousCount = source.document().relations().size();
        references(source.document().type()).forEach(rule -> source.body().findValues(rule.aggregate())
                .forEach(values -> values.forEach(value -> link(source, rule, scalar(value.path("ID")), index))));
        Map<String, String> metadata = new HashMap<>(source.set().metadata());
        metadata.put("businessDocumentNumber", source.reference());
        metadata.put("name", rootName(source.document().type()) + " " + source.reference());
        if (metadata.equals(source.set().metadata()) && previousCount == source.document().relations().size()) return;
        sets.save(DocumentSet.reconstitute(source.set().id(), source.set().getAllDocuments().stream()
                .collect(Collectors.toMap(Document::id, document -> document)), source.set().createdAt(),
                source.set().createdBy(), metadata));
    }

    private void link(Sample source, Reference rule, String number, Map<String, List<Sample>> index) {
        List<Sample> targets = index.getOrDefault(source.scope() + ":" + rule.target() + ":" + number, List.of());
        if (targets.size() != 1) return;
        Sample target = targets.getFirst();
        if (target.document().id().equals(source.document().id())) return;
        source.document().addRelation(new DocumentRelation(rule.relation(), target.document().id(), target.set().id()));
    }

    private List<Reference> references(DocumentType type) {
        return switch (type) {
            case ORDER_RESPONSE -> List.of(new Reference("OrderReference", DocumentType.ORDER, DocumentRelationType.RESPONDS_TO));
            case DESPATCH_ADVICE -> List.of(new Reference("OrderReference", DocumentType.ORDER, DocumentRelationType.DESPATCHES));
            case INVOICE -> List.of(new Reference("OrderReference", DocumentType.ORDER, DocumentRelationType.INVOICES));
            case RECEIPT_ADVICE -> List.of(new Reference("DespatchDocumentReference", DocumentType.DESPATCH_ADVICE, DocumentRelationType.RECEIVES));
            case REMITTANCE_ADVICE -> List.of(new Reference("InvoiceDocumentReference", DocumentType.INVOICE, DocumentRelationType.REMITS));
            default -> List.of();
        };
    }

    private String scalar(JsonNode values) { return values.path(0).path("_").asText("").trim(); }

    private record Reference(String aggregate, DocumentType target, DocumentRelationType relation) {}
    private record Sample(DocumentSet set, Document document, String scope, String reference, JsonNode body) {
        String key() { return scope + ":" + document.type() + ":" + reference; }
    }
}
