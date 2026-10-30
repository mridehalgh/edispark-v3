package com.edispark.documents.application.query;

import com.edispark.pagination.PaginatedResult;
import com.edispark.documents.domain.model.DocumentSet;
import com.edispark.documents.domain.repository.DocumentSetRepository;
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
    public PaginatedResult<DocumentSet> handle(ListDocumentSetsQuery query) {
        return repository.findAll(query.page());
    }
}
