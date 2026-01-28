package com.example.documents.api.rest;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.example.documents.api.dto.AddSchemaVersionRequest;
import com.example.documents.api.dto.CreateSchemaRequest;
import com.example.documents.api.dto.SchemaResponse;
import com.example.documents.api.dto.SchemaVersionResponse;
import com.example.documents.application.command.AddSchemaVersionCommand;
import com.example.documents.application.command.CreateSchemaCommand;
import com.example.documents.application.handler.SchemaCommandHandler;
import com.example.documents.application.handler.SchemaNotFoundException;
import com.example.documents.application.handler.SchemaVersionNotFoundException;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersion;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.SchemaRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for schema operations.
 *
 * <p>Handles HTTP requests for creating and retrieving schemas,
 * as well as operations on schema versions.</p>
 *
 * <p>Requirements: 6.1, 6.2, 6.3, 6.4, 7.1, 7.2, 7.3</p>
 */
@RestController
@RequestMapping("/api/schemas")
@RequiredArgsConstructor
@Tag(name = "Schemas", description = "Manage schemas and schema versions for document validation")
public class SchemaController {

    private final SchemaCommandHandler commandHandler;
    private final SchemaRepository repository;

    /**
     * Creates a new schema.
     *
     * @param request the create schema request
     * @return 201 Created with the schema details
     */
    @PostMapping
    @Operation(summary = "Create a new schema", 
               description = "Creates a new schema for document validation. Schemas define structure and validation rules.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Schema created successfully",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SchemaResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<SchemaResponse> createSchema(
            @Valid @RequestBody CreateSchemaRequest request) {
        
        // Build command
        CreateSchemaCommand command = CreateSchemaCommand.of(
                request.name(),
                request.format()
        );
        
        // Execute command
        Schema schema = commandHandler.handle(command);
        
        // Map to response
        SchemaResponse response = mapToResponse(schema);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a schema by its unique identifier.
     *
     * @param id the schema identifier
     * @return 200 OK with the schema details if found
     * @throws SchemaNotFoundException if the schema does not exist
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a schema", description = "Retrieves a schema by its unique identifier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Schema found",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SchemaResponse.class))),
        @ApiResponse(responseCode = "404", description = "Schema not found")
    })
    public ResponseEntity<SchemaResponse> getSchema(
            @Parameter(description = "Schema UUID") @PathVariable UUID id) {
        SchemaId schemaId = new SchemaId(id);
        
        Schema schema = repository.findById(schemaId)
                .orElseThrow(() -> new SchemaNotFoundException(schemaId));
        
        SchemaResponse response = mapToResponse(schema);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Adds a new version to an existing schema.
     *
     * @param schemaId the schema identifier
     * @param request the add schema version request
     * @return 201 Created with the schema version details
     * @throws SchemaNotFoundException if the schema does not exist
     */
    @PostMapping("/{schemaId}/versions")
    @Operation(summary = "Add a schema version", 
               description = "Adds a new version to an existing schema. Schema definition must be Base64 encoded.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Schema version added successfully",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SchemaVersionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Schema not found")
    })
    public ResponseEntity<SchemaVersionResponse> addVersion(
            @Parameter(description = "Schema UUID") @PathVariable UUID schemaId,
            @Valid @RequestBody AddSchemaVersionRequest request) {
        
        // Decode Base64 content
        byte[] definitionBytes = java.util.Base64.getDecoder().decode(request.definition());
        
        // Create content (using XML as default format for schema definitions)
        Content definition = Content.of(definitionBytes, Format.XML);
        
        // Build command
        AddSchemaVersionCommand command = AddSchemaVersionCommand.of(
                new SchemaId(schemaId),
                VersionIdentifier.of(request.versionIdentifier()),
                definition
        );
        
        // Execute command
        SchemaVersion schemaVersion = commandHandler.handle(command);
        
        // Map to response
        SchemaVersionResponse response = mapToVersionResponse(schemaVersion);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a specific version of a schema.
     *
     * @param schemaId the schema identifier
     * @param versionId the version identifier
     * @return 200 OK with the schema version details if found
     * @throws SchemaNotFoundException if the schema does not exist
     * @throws SchemaVersionNotFoundException if the version does not exist
     */
    @GetMapping("/{schemaId}/versions/{versionId}")
    @Operation(summary = "Get a schema version", description = "Retrieves a specific version of a schema")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Schema version found",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SchemaVersionResponse.class))),
        @ApiResponse(responseCode = "404", description = "Schema or schema version not found")
    })
    public ResponseEntity<SchemaVersionResponse> getVersion(
            @Parameter(description = "Schema UUID") @PathVariable UUID schemaId,
            @Parameter(description = "Version identifier (e.g., '1.0', '2.1')") @PathVariable String versionId) {
        
        SchemaId schemaIdObj = new SchemaId(schemaId);
        
        // Find the schema
        Schema schema = repository.findById(schemaIdObj)
                .orElseThrow(() -> new SchemaNotFoundException(schemaIdObj));
        
        // Find the specific version
        VersionIdentifier versionIdentifier = VersionIdentifier.of(versionId);
        SchemaVersion schemaVersion = schema.getVersion(versionIdentifier)
                .orElseThrow(() -> new SchemaVersionNotFoundException(
                        SchemaVersionRef.of(schemaIdObj, versionIdentifier)));
        
        // Map to response
        SchemaVersionResponse response = mapToVersionResponse(schemaVersion);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Maps a Schema domain object to a SchemaResponse DTO.
     *
     * @param schema the domain object
     * @return the response DTO
     */
    private SchemaResponse mapToResponse(Schema schema) {
        var versionSummaries = schema.versions().stream()
                .map(version -> new SchemaResponse.SchemaVersionSummary(
                        version.id().value(),
                        version.versionIdentifier().value(),
                        version.createdAt(),
                        version.isDeprecated()
                ))
                .toList();

        return new SchemaResponse(
                schema.id().value(),
                schema.name(),
                schema.format(),
                versionSummaries
        );
    }

    /**
     * Maps a SchemaVersion domain object to a SchemaVersionResponse DTO.
     *
     * @param schemaVersion the domain object
     * @return the response DTO
     */
    private SchemaVersionResponse mapToVersionResponse(SchemaVersion schemaVersion) {
        return new SchemaVersionResponse(
                schemaVersion.id().value(),
                schemaVersion.versionIdentifier().value(),
                schemaVersion.createdAt(),
                schemaVersion.isDeprecated()
        );
    }
}
