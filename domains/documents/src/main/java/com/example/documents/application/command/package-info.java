/**
 * Command objects for the Documents application layer.
 * 
 * <p>Commands represent intentions to change state:
 * <ul>
 *   <li>{@link CreateDocumentSetCommand} - Create a new document set with an initial document</li>
 *   <li>{@link AddDocumentCommand} - Add a document to an existing set</li>
 *   <li>{@link AddVersionCommand} - Add a new version to a document</li>
 *   <li>{@link CreateDerivativeCommand} - Create a derivative from a version</li>
 *   <li>{@link ValidateDocumentCommand} - Validate a document against its schema</li>
 *   <li>{@link CreateSchemaCommand} - Create a new schema</li>
 *   <li>{@link AddSchemaVersionCommand} - Add a version to a schema</li>
 * </ul>
 */
package com.example.documents.application.command;
