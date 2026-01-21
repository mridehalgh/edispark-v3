/**
 * Repository interfaces (ports) for the Documents bounded context.
 * 
 * <p>Defines abstractions for aggregate persistence:
 * <ul>
 *   <li>{@code DocumentSetRepository} - Persistence for DocumentSet aggregates</li>
 *   <li>{@code SchemaRepository} - Persistence for Schema aggregates</li>
 *   <li>{@code ContentStore} - Content-addressable storage for document and schema content</li>
 * </ul>
 * 
 * <p>Implementations are provided in the infrastructure layer.
 */
package com.example.documents.domain.repository;
