package com.edispark.documents.application.reconciliation;

import com.edispark.documents.application.query.DocumentContentQueryService;
import com.edispark.documents.application.query.DocumentContentQueryService.CanonicalDocument;
import com.edispark.documents.application.query.DocumentContentQueryService.RepresentationPin;
import com.edispark.documents.domain.model.DocumentId;
import java.util.List;

public final class DocumentReconciliation {
    private static final int MAX_RELATED_DOCUMENTS = 8;
    private final DocumentContentQueryService documents;

    public DocumentReconciliation(DocumentContentQueryService documents) {
        this.documents = documents;
    }

    public UblReconciliation.Report compare(UblReconciliation.Comparison comparison, RepresentationPin primary,
                                           List<RepresentationPin> related) {
        if (!supports(comparison, primary, related)) {
            throw new IllegalArgumentException("The comparison requires distinct documents and a supported number of counterparts");
        }

        var source = documents.readPinnedCanonical(primary);
        var counterparts = related.stream().map(documents::readPinnedCanonical).toList();
        if (source.partnerId() != null && counterparts.stream().anyMatch(counterpart ->
                counterpart.partnerId() != null && !source.partnerId().equals(counterpart.partnerId()))) {
            throw new IllegalArgumentException("Documents from different trading partners cannot be reconciled");
        }
        return UblReconciliation.compare(comparison, source.content(),
                counterparts.stream().map(CanonicalDocument::content).toList());
    }

    private static boolean supports(UblReconciliation.Comparison comparison, RepresentationPin primary,
                                    List<RepresentationPin> related) {
        if (related.isEmpty() || related.size() > MAX_RELATED_DOCUMENTS) return false;
        if (comparison == UblReconciliation.Comparison.ORDER_RESPONSE && related.size() != 1) return false;
        List<DocumentId> relatedIds = related.stream().map(RepresentationPin::documentId).toList();
        return !relatedIds.contains(primary.documentId()) && relatedIds.stream().distinct().count() == relatedIds.size();
    }
}
