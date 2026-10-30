package com.edispark.documents.infrastructure.persistence;

import com.edispark.documents.domain.model.Schema;
import com.edispark.documents.domain.model.SchemaId;
import com.edispark.documents.domain.model.SchemaVersionRef;
import com.edispark.documents.domain.repository.SchemaRepository;
import io.cottn.core.tenancy.TenantContext;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Resolves the authenticated tenant for every repository operation. */
public final class TenantScopedSchemaRepository implements SchemaRepository {

    private final DynamoDbClient client;
    private final String tableName;

    public TenantScopedSchemaRepository(DynamoDbClient client, String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    @Override
    public Optional<Schema> findById(SchemaId id) {
        return delegate().findById(id);
    }

    @Override
    public void save(Schema schema) {
        delegate().save(schema);
    }

    @Override
    public boolean isVersionReferenced(SchemaVersionRef schemaVersionRef) {
        return delegate().isVersionReferenced(schemaVersionRef);
    }

    private DynamoDbSchemaRepository delegate() {
        return new DynamoDbSchemaRepository(client, tableName, TenantContext.getCurrentTenant().asString());
    }
}
