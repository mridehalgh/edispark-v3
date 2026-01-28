package com.example.documents.application.query;

import com.example.common.pagination.PaginatedResult;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.repository.DocumentSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Handles queries for document set listing.
 */
@Service
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
