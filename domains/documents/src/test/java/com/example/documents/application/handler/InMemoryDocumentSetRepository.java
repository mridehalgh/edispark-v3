package com.example.documents.application.handler;

import com.example.common.pagination.Page;
import com.example.common.pagination.PaginatedResult;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.repository.DocumentSetRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of DocumentSetRepository for testing.
 */
class InMemoryDocumentSetRepository implements DocumentSetRepository {

    private final Map<DocumentSetId, DocumentSet> store = new HashMap<>();

    @Override
    public Optional<DocumentSet> findById(DocumentSetId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<DocumentSet> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public PaginatedResult<DocumentSet> findAll(Page page) {
        List<DocumentSet> allSets = new ArrayList<>(store.values());
        
        // Simple in-memory pagination for testing
        int startIndex = 0;
        if (page.continuationToken().isPresent()) {
            // For simplicity, use the token as the start index
            try {
                startIndex = Integer.parseInt(page.continuationToken().get());
            } catch (NumberFormatException e) {
                startIndex = 0;
            }
        }
        
        int endIndex = Math.min(startIndex + page.limit(), allSets.size());
        List<DocumentSet> pageItems = allSets.subList(startIndex, endIndex);
        
        // Create continuation token if there are more items
        String nextToken = (endIndex < allSets.size()) ? String.valueOf(endIndex) : null;
        
        return PaginatedResult.of(pageItems, nextToken);
    }

    @Override
    public void save(DocumentSet documentSet) {
        store.put(documentSet.id(), documentSet);
    }

    @Override
    public void delete(DocumentSetId id) {
        store.remove(id);
    }

    @Override
    public List<DocumentSet> findByContentHash(ContentHash hash) {
        return store.values().stream()
                .filter(ds -> ds.getAllDocuments().stream()
                        .flatMap(d -> d.versions().stream())
                        .anyMatch(v -> v.contentHash().equals(hash)))
                .toList();
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
