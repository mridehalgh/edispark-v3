/**
 * Command handlers for the Documents application layer.
 * 
 * <p>Handlers orchestrate domain operations:
 * <ul>
 *   <li>{@link DocumentSetCommandHandler} - Handles document set commands</li>
 *   <li>{@link SchemaCommandHandler} - Handles schema commands</li>
 * </ul>
 * 
 * <p>Exception classes for error handling:
 * <ul>
 *   <li>{@link DocumentSetNotFoundException} - DocumentSet not found</li>
 *   <li>{@link DocumentNotFoundException} - Document not found</li>
 *   <li>{@link VersionNotFoundException} - Version not found</li>
 *   <li>{@link SchemaNotFoundException} - Schema not found</li>
 *   <li>{@link SchemaVersionNotFoundException} - Schema version not found</li>
 *   <li>{@link ValidationException} - Validation failed</li>
 *   <li>{@link UnsupportedFormatException} - Format transformation not supported</li>
 * </ul>
 */
package com.example.documents.application.handler;
