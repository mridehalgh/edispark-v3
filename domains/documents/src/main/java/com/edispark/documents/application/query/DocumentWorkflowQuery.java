package com.edispark.documents.application.query;

import com.edispark.documents.application.handler.DocumentNotFoundException;
import com.edispark.documents.domain.model.Document;
import com.edispark.documents.domain.model.DocumentId;
import com.edispark.documents.domain.model.DocumentLifecycleStatus;
import com.edispark.documents.domain.model.DocumentRelation;
import com.edispark.documents.domain.model.DocumentRelationType;
import com.edispark.documents.domain.model.DocumentSet;
import com.edispark.documents.domain.model.DocumentSetId;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.edispark.pagination.PaginatedResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class DocumentWorkflowQuery {
    private static final int SET_PAGE_SIZE = 4;
    private static final int DETAIL_LIMIT = 32;

    private final DocumentSetRepository sets;
    private final DocumentSetQueryHandler listing;
    private final DocumentContentQueryService content;

    public DocumentWorkflowQuery(DocumentSetRepository sets, DocumentSetQueryHandler listing,
                                 DocumentContentQueryService content) {
        this.sets = sets;
        this.listing = listing;
        this.content = content;
    }

    public PaginatedResult<Summary> find(Search search, String position) {
        var page = listing.handle(ListDocumentSetsQuery.of(SET_PAGE_SIZE, position));
        // shortcut: searches read bounded document-set pages until reference and lineage indexes are available
        var matches = page.items().stream().filter(search::includesSet)
                .flatMap(set -> set.getAllDocuments().stream()
                        .filter(search::includesDocument)
                        .filter(document -> search.businessReference() == null
                                || search.businessReference().equals(content.businessReference(document).orElse(null)))
                        .map(document -> summary(set.id(), document, search.businessReference(), partner(set))))
                .toList();
        return new PaginatedResult<>(matches, page.continuationToken());
    }

    public DocumentContext describe(DocumentSetId setId, DocumentId docId) {
        var document = requireDocument(setId, docId);
        return new DocumentContext(summary(setId, document, null, null),
                content.currentRepresentation(document).orElse(null),
                document.derivatives().stream().limit(DETAIL_LIMIT)
                        .map(item -> new Artifact(item.id().value(), item.targetFormat())).toList(),
                document.derivatives().size() > DETAIL_LIMIT);
    }

    public Workflow follow(DocumentSetId setId, DocumentId docId, String position) {
        var document = requireDocument(setId, docId);
        var page = listing.handle(ListDocumentSetsQuery.of(SET_PAGE_SIZE, position));
        var outgoing = position == null
                ? document.relations().stream().limit(DETAIL_LIMIT).map(relation -> outgoingLink(setId, relation))
                : Stream.<Link>empty();
        var incoming = page.items().stream().flatMap(set -> incomingLinks(set, setId, docId));
        return new Workflow(Stream.concat(outgoing, incoming).toList(), document.relations().size() > DETAIL_LIMIT,
                page.continuationToken().orElse(null));
    }

    public AuthoringSource orderResponseSource(DocumentSetId setId, DocumentId docId) {
        var document = requireDocument(setId, docId);
        if (document.type() != DocumentType.ORDER) throw new IllegalArgumentException("An Order Response requires an Order");
        return authoringSource(document, DocumentType.ORDER_RESPONSE);
    }

    public AuthoringSource correctionSource(DocumentSetId setId, DocumentId docId) {
        var document = requireDocument(setId, docId);
        boolean supported = switch (document.type()) {
            case ORDER, ORDER_RESPONSE, DESPATCH_ADVICE, RECEIPT_ADVICE, INVOICE, REMITTANCE_ADVICE -> true;
            default -> false;
        };
        if (document.lifecycleStatus() != DocumentLifecycleStatus.FINAL || !supported) {
            throw new IllegalArgumentException("A correction requires a supported final document");
        }
        return authoringSource(document, document.type());
    }

    private AuthoringSource authoringSource(Document document, DocumentType target) {
        return new AuthoringSource(target, content.currentRepresentation(document).orElse(null));
    }

    private Link outgoingLink(DocumentSetId setId, DocumentRelation relation) {
        var targetSet = targetSet(setId, relation);
        var target = sets.findById(targetSet).flatMap(set -> set.getDocument(relation.targetDocumentId()));
        return new Link(relation.type(), LinkDirection.OUTGOING,
                target.map(document -> summary(targetSet, document, null, null)).orElse(null));
    }

    private Stream<Link> incomingLinks(DocumentSet set, DocumentSetId targetSet, DocumentId targetDocument) {
        return set.getAllDocuments().stream().flatMap(candidate -> candidate.relations().stream()
                .filter(relation -> relation.targetDocumentId().equals(targetDocument)
                        && targetSet(set.id(), relation).equals(targetSet))
                .map(relation -> new Link(relation.type(), LinkDirection.INCOMING, summary(set.id(), candidate, null, null))));
    }

    private Document requireDocument(DocumentSetId setId, DocumentId docId) {
        return sets.findById(setId).flatMap(set -> set.getDocument(docId))
                .orElseThrow(() -> new DocumentNotFoundException(setId, docId));
    }

    private static DocumentSetId targetSet(DocumentSetId owner, DocumentRelation relation) {
        return relation.targetDocumentSetId() == null ? owner : relation.targetDocumentSetId();
    }

    private static UUID partner(DocumentSet set) {
        String value = set.metadata().get("partnerId");
        return value != null && value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                ? UUID.fromString(value) : null;
    }

    private static Summary summary(DocumentSetId setId, Document document, String reference, UUID partnerId) {
        return new Summary(setId, document.id(), document.type(), document.lifecycleStatus(), reference, partnerId);
    }

    public record Search(String businessReference, Instant from, Instant to, UUID partnerId,
                         DocumentType documentType, DocumentLifecycleStatus lifecycleStatus) {
        public Search {
            if ((from == null) != (to == null) || from == null && businessReference == null
                    || from != null && (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0)) {
                throw new IllegalArgumentException("Search requires an exact reference or a date window of up to 31 days");
            }
        }

        boolean includesSet(DocumentSet set) {
            return (from == null || !set.createdAt().isBefore(from) && set.createdAt().isBefore(to))
                    && (partnerId == null || partnerId.equals(partner(set)));
        }

        boolean includesDocument(Document document) {
            return (documentType == null || documentType == document.type())
                    && (lifecycleStatus == null || lifecycleStatus == document.lifecycleStatus());
        }
    }

    public record Summary(DocumentSetId documentSetId, DocumentId documentId, DocumentType documentType,
                          DocumentLifecycleStatus lifecycleStatus, String businessReference, UUID partnerId) { }
    public record Artifact(UUID representationId, Format format) { }
    public record DocumentContext(Summary document, DocumentContentQueryService.CanonicalRepresentation representation,
                                  List<Artifact> artifacts, boolean artifactsTruncated) { }
    public enum LinkDirection { INCOMING, OUTGOING }
    public record Link(DocumentRelationType relation, LinkDirection direction, Summary document) { }
    public record Workflow(List<Link> links, boolean outgoingTruncated, String nextPosition) { }
    public record AuthoringSource(DocumentType target, DocumentContentQueryService.CanonicalRepresentation representation) { }
}
