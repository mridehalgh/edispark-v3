/**
 * Persistence implementations for the Documents bounded context.
 * 
 * <p>DynamoDB-based repository implementations:
 * <ul>
 *   <li>{@code DynamoDbDocumentSetRepository} - DocumentSet persistence</li>
 *   <li>{@code DynamoDbSchemaRepository} - Schema persistence</li>
 * </ul>
 * 
 * <p>Content storage implementations:
 * <ul>
 *   <li>{@code S3ContentStore} - S3-based content storage for production</li>
 *   <li>{@code FileSystemContentStore} - File-based content storage for local development</li>
 * </ul>
 */
package com.example.documents.infrastructure.persistence;
