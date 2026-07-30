package com.example.documents.infrastructure.persistence;

import com.example.common.pagination.Page;
import com.example.common.pagination.PaginatedResult;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.repository.DocumentSetRepository;
import io.cottn.core.tenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Resolves the authenticated tenant for every repository operation. */
public final class TenantScopedDocumentSetRepository implements DocumentSetRepository {

    private final DynamoDbClient client;
    private final String tableName;

    public TenantScopedDocumentSetRepository(DynamoDbClient client, String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    @Override
    public Optional<DocumentSet> findById(DocumentSetId id) {
        return delegate().findById(id);
    }

    @Override
    public List<DocumentSet> findAll() {
        return delegate().findAll();
    }

    @Override
    public PaginatedResult<DocumentSet> findAll(Page page) {
        return delegate().findAll(page);
    }

    @Override
    public void save(DocumentSet documentSet) {
        delegate().save(documentSet);
    }

    @Override
    public void delete(DocumentSetId id) {
        delegate().delete(id);
    }

    @Override
    public List<DocumentSet> findByContentHash(ContentHash contentHash) {
        return delegate().findByContentHash(contentHash);
    }

    private DynamoDbDocumentSetRepository delegate() {
        return new DynamoDbDocumentSetRepository(client, tableName, TenantContext.getCurrentTenant().asString());
    }
}
