/**
 * Domain events for the Documents bounded context.
 * 
 * <p>Events are emitted when significant state changes occur:
 * <ul>
 *   <li>{@link DocumentSetCreated} - New document set created (Req 9.1)</li>
 *   <li>{@link DocumentAdded} - Document added to a set (Req 9.2)</li>
 *   <li>{@link DocumentVersionAdded} - New version added to a document (Req 9.2)</li>
 *   <li>{@link DerivativeCreated} - Derivative created from a version (Req 9.3)</li>
 *   <li>{@link DocumentValidated} - Document validated against schema (Req 9.4)</li>
 *   <li>{@link SchemaVersionCreated} - New schema version published</li>
 * </ul>
 * 
 * <p>All events include identifiers and timestamps as per Requirements 9.5 and 9.6.</p>
 * 
 * @see DomainEvent
 */
package com.example.documents.domain.event;
