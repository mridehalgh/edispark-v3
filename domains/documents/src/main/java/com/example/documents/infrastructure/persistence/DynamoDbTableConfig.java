package com.example.documents.infrastructure.persistence;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * DynamoDB table configuration for the Documents bounded context.
 * 
 * <p>This class defines the single-table design schema for storing all document-related
 * entities: DocumentSet, Document, DocumentVersion, Derivative, Schema, and SchemaVersion.</p>
 * 
 * <h2>Table Schema</h2>
 * <ul>
 *   <li>PK (Partition Key): String - Entity key with tenant prefix</li>
 *   <li>SK (Sort Key): String - Varies by entity type</li>
 * </ul>
 * 
 * <h2>Global Secondary Indexes</h2>
 * <ul>
 *   <li>GSI1 (TenantDocumentSetsIndex): List all document sets for a tenant</li>
 *   <li>GSI2 (ContentHashIndex): Find documents by content hash for duplicate detection</li>
 * </ul>
 * 
 * <h2>Key Patterns</h2>
 * <table>
 *   <tr><th>Entity</th><th>PK</th><th>SK</th></tr>
 *   <tr><td>DocumentSet</td><td>TENANT#{tenantId}#DOCSET#{id}</td><td>METADATA</td></tr>
 *   <tr><td>Document</td><td>TENANT#{tenantId}#DOCSET#{docSetId}</td><td>DOC#{docId}</td></tr>
 *   <tr><td>DocumentVersion</td><td>TENANT#{tenantId}#DOCSET#{docSetId}</td><td>DOC#{docId}#VER#{versionNumber}</td></tr>
 *   <tr><td>Derivative</td><td>TENANT#{tenantId}#DOCSET#{docSetId}</td><td>DOC#{docId}#DER#{derivativeId}</td></tr>
 *   <tr><td>Schema</td><td>TENANT#{tenantId}#SCHEMA#{schemaId}</td><td>METADATA</td></tr>
 *   <tr><td>SchemaVersion</td><td>TENANT#{tenantId}#SCHEMA#{schemaId}</td><td>VER#{versionIdentifier}</td></tr>
 * </table>
 */
public final class DynamoDbTableConfig {

    // Table and index names
    public static final String DEFAULT_TABLE_NAME = "documents";
    public static final String GSI1_NAME = "GSI1-TenantDocumentSetsIndex";
    public static final String GSI2_NAME = "GSI2-ContentHashIndex";

    // Attribute names
    public static final String PK = "PK";
    public static final String SK = "SK";
    public static final String GSI1_PK = "GSI1PK";
    public static final String GSI1_SK = "GSI1SK";
    public static final String GSI2_PK = "GSI2PK";
    public static final String GSI2_SK = "GSI2SK";

    // Key prefixes
    public static final String TENANT_PREFIX = "TENANT#";
    public static final String DOCSET_PREFIX = "DOCSET#";
    public static final String DOC_PREFIX = "DOC#";
    public static final String VER_PREFIX = "VER#";
    public static final String DER_PREFIX = "DER#";
    public static final String SCHEMA_PREFIX = "SCHEMA#";
    public static final String HASH_PREFIX = "HASH#";
    public static final String DOCSETS_SUFFIX = "#DOCSETS";

    // Sort key constants
    public static final String METADATA_SK = "METADATA";

    // Default tenant for single-tenant scenarios
    public static final String DEFAULT_TENANT = "DEFAULT";

    private final String tableName;

    public DynamoDbTableConfig() {
        this(DEFAULT_TABLE_NAME);
    }

    public DynamoDbTableConfig(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }

    /**
     * Creates the DynamoDB table if it does not exist.
     * 
     * <p>This method is intended for local development and testing with DynamoDB Local.</p>
     * 
     * @param client the DynamoDB client
     */
    public void createTableIfNotExists(DynamoDbClient client) {
        if (tableExists(client)) {
            return;
        }

        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName(PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(SK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(GSI1_PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(GSI1_SK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(GSI2_PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(GSI2_SK)
                                .attributeType(ScalarAttributeType.S)
                                .build())
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName(PK)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(SK)
                                .keyType(KeyType.RANGE)
                                .build())
                .globalSecondaryIndexes(
                        createGsi1(),
                        createGsi2())
                .build();

        client.createTable(request);
    }

    /**
     * Deletes the DynamoDB table if it exists.
     * 
     * <p>This method is intended for testing cleanup.</p>
     * 
     * @param client the DynamoDB client
     */
    public void deleteTableIfExists(DynamoDbClient client) {
        if (tableExists(client)) {
            client.deleteTable(r -> r.tableName(tableName));
        }
    }

    private boolean tableExists(DynamoDbClient client) {
        try {
            client.describeTable(DescribeTableRequest.builder()
                    .tableName(tableName)
                    .build());
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private GlobalSecondaryIndex createGsi1() {
        return GlobalSecondaryIndex.builder()
                .indexName(GSI1_NAME)
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName(GSI1_PK)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(GSI1_SK)
                                .keyType(KeyType.RANGE)
                                .build())
                .projection(Projection.builder()
                        .projectionType(ProjectionType.ALL)
                        .build())
                .build();
    }

    private GlobalSecondaryIndex createGsi2() {
        return GlobalSecondaryIndex.builder()
                .indexName(GSI2_NAME)
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName(GSI2_PK)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(GSI2_SK)
                                .keyType(KeyType.RANGE)
                                .build())
                .projection(Projection.builder()
                        .projectionType(ProjectionType.ALL)
                        .build())
                .build();
    }

    // Key construction helpers

    /**
     * Constructs the partition key for a DocumentSet.
     */
    public static String documentSetPk(String tenantId, String documentSetId) {
        return TENANT_PREFIX + tenantId + "#" + DOCSET_PREFIX + documentSetId;
    }

    /**
     * Constructs the sort key for DocumentSet metadata.
     */
    public static String documentSetMetadataSk() {
        return METADATA_SK;
    }

    /**
     * Constructs the sort key for a Document.
     */
    public static String documentSk(String documentId) {
        return DOC_PREFIX + documentId;
    }

    /**
     * Constructs the sort key for a DocumentVersion.
     */
    public static String documentVersionSk(String documentId, int versionNumber) {
        return DOC_PREFIX + documentId + "#" + VER_PREFIX + versionNumber;
    }

    /**
     * Constructs the sort key for a Derivative.
     */
    public static String derivativeSk(String documentId, String derivativeId) {
        return DOC_PREFIX + documentId + "#" + DER_PREFIX + derivativeId;
    }

    /**
     * Constructs the partition key for a Schema.
     */
    public static String schemaPk(String tenantId, String schemaId) {
        return TENANT_PREFIX + tenantId + "#" + SCHEMA_PREFIX + schemaId;
    }

    /**
     * Constructs the sort key for Schema metadata.
     */
    public static String schemaMetadataSk() {
        return METADATA_SK;
    }

    /**
     * Constructs the sort key for a SchemaVersion.
     */
    public static String schemaVersionSk(String versionIdentifier) {
        return VER_PREFIX + versionIdentifier;
    }

    /**
     * Constructs the GSI1 partition key for listing document sets by tenant.
     */
    public static String gsi1PkForTenantDocumentSets(String tenantId) {
        return TENANT_PREFIX + tenantId + DOCSETS_SUFFIX;
    }

    /**
     * Constructs the GSI1 sort key for document set ordering.
     */
    public static String gsi1SkForDocumentSet(String createdAt, String documentSetId) {
        return createdAt + "#" + documentSetId;
    }

    /**
     * Constructs the GSI2 partition key for content hash lookup.
     */
    public static String gsi2PkForContentHash(String tenantId, String contentHash) {
        return TENANT_PREFIX + tenantId + "#" + HASH_PREFIX + contentHash;
    }

    /**
     * Constructs the GSI2 sort key for content hash lookup.
     */
    public static String gsi2SkForDocumentVersion(String documentSetId, String documentId) {
        return documentSetId + "#" + documentId;
    }
}
