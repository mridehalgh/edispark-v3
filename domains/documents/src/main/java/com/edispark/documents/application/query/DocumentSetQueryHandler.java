package com.edispark.documents.application.query;

import com.edispark.pagination.NewestFirstPagination;
import com.edispark.pagination.PaginatedResult;
import com.edispark.documents.domain.model.DocumentSet;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;

/**
 * Handles queries for document set listing.
 */
@RequiredArgsConstructor
public class DocumentSetQueryHandler {
    
    private final DocumentSetRepository repository;
    
    /**
     * Executes paginated document set listing query.
     * 
     * @param query the pagination query
     * @return paginated result with document sets
     */
    @NewestFirstPagination
    public PaginatedResult<DocumentSet> handle(ListDocumentSetsQuery query) {
        PaginatedResult<DocumentSet> result = repository.findAll(query.page());
        var sortedItems = result.items().stream()
                .sorted(Comparator.comparing(DocumentSet::createdAt)
                        .thenComparing(documentSet -> documentSet.id().toString())
                        .reversed())
                .toList();
        return new PaginatedResult<>(sortedItems, result.continuationToken());
    }
}
