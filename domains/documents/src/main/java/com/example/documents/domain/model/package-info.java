/**
 * Domain model classes for the Documents bounded context.
 * 
 * <p>Contains aggregate roots, entities, and value objects:
 * <ul>
 *   <li>{@code DocumentSet} - Aggregate root managing related documents</li>
 *   <li>{@code Document} - Entity representing a business document</li>
 *   <li>{@code DocumentVersion} - Immutable version of a document</li>
 *   <li>{@code Derivative} - Transformed representation of a document</li>
 *   <li>{@code Schema} - Aggregate root for schema definitions</li>
 *   <li>{@code SchemaVersion} - Immutable version of a schema</li>
 *   <li>Value objects: identifiers, Content, ContentHash, etc.</li>
 * </ul>
 */
package com.example.documents.domain.model;
