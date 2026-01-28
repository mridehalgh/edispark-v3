package com.example.documents.api.rest;

import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.documents.api.dto.AddDocumentRequest;
import com.example.documents.api.dto.AddSchemaVersionRequest;
import com.example.documents.api.dto.AddVersionRequest;
import com.example.documents.api.dto.CreateDerivativeRequest;
import com.example.documents.api.dto.CreateDocumentSetRequest;
import com.example.documents.api.dto.CreateSchemaRequest;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaFormat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for missing required fields validation across all API request DTOs.
 *
 * <p><b>Property 4: Missing Required Fields Return 400</b></p>
 * <p>For any create request with one or more missing required fields, the API SHALL return
 * HTTP 400 Bad Request with an error response containing field-level validation details.</p>
 *
 * <p><b>Validates: Requirements 1.2, 6.2, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7</b></p>
 */
class MissingRequiredFieldsPropertyTest {

    private final Validator validator;

    MissingRequiredFieldsPropertyTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    // ========================================================================
    // Property 4: CreateDocumentSetRequest Missing Required Fields
    // ========================================================================

    /**
     * Property 4: CreateDocumentSetRequest with null documentType fails validation
     *
     * <p>For any CreateDocumentSetRequest with null documentType, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDocumentSetRequest with null documentType fails validation")
    void createDocumentSetRequestWithNullDocumentTypeFails(
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("validBase64Contents") String content,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        CreateDocumentSetRequest request = new CreateDocumentSetRequest(
            null, // documentType is null
            schemaId,
            schemaVersion,
            content,
            createdBy,
            null
        );

        Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null documentType should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("documentType");
    }

    /**
     * Property 4: CreateDocumentSetRequest with null schemaId fails validation
     *
     * <p>For any CreateDocumentSetRequest with null schemaId, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDocumentSetRequest with null schemaId fails validation")
    void createDocumentSetRequestWithNullSchemaIdFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("validBase64Contents") String content,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        CreateDocumentSetRequest request = new CreateDocumentSetRequest(
            documentType,
            null, // schemaId is null
            schemaVersion,
            content,
            createdBy,
            null
        );

        Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null schemaId should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("schemaId");
    }

    /**
     * Property 4: CreateDocumentSetRequest with null schemaVersion fails validation
     *
     * <p>For any CreateDocumentSetRequest with null schemaVersion, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDocumentSetRequest with null schemaVersion fails validation")
    void createDocumentSetRequestWithNullSchemaVersionFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("validBase64Contents") String content,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        CreateDocumentSetRequest request = new CreateDocumentSetRequest(
            documentType,
            schemaId,
            null, // schemaVersion is null
            content,
            createdBy,
            null
        );

        Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null schemaVersion should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("schemaVersion");
    }

    /**
     * Property 4: CreateDocumentSetRequest with blank schemaVersion fails validation
     *
     * <p>For any CreateDocumentSetRequest with blank schemaVersion, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.1, 9.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDocumentSetRequest with blank schemaVersion fails validation")
    void createDocumentSetRequestWithBlankSchemaVersionFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("blankStrings") String schemaVersion,
            @ForAll("validBase64Contents") String content,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        CreateDocumentSetRequest request = new CreateDocumentSetRequest(
            documentType,
            schemaId,
            schemaVersion,
            content,
            createdBy,
            null
        );

        Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with blank schemaVersion should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("schemaVersion");
    }

    /**
     * Property 4: CreateDocumentSetRequest with null content fails validation
     *
     * <p>For any CreateDocumentSetRequest with null content, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDocumentSetRequest with null content fails validation")
    void createDocumentSetRequestWithNullContentFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        CreateDocumentSetRequest request = new CreateDocumentSetRequest(
            documentType,
            schemaId,
            schemaVersion,
            null, // content is null
            createdBy,
            null
        );

        Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null content should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("content");
    }

    /**
     * Property 4: CreateDocumentSetRequest with blank content fails validation
     *
     * <p>For any CreateDocumentSetRequest with blank content, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.1, 9.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDocumentSetRequest with blank content fails validation")
    void createDocumentSetRequestWithBlankContentFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("blankStrings") String content,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        CreateDocumentSetRequest request = new CreateDocumentSetRequest(
            documentType,
            schemaId,
            schemaVersion,
            content,
            createdBy,
            null
        );

        Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with blank content should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("content");
    }

    /**
     * Property 4: CreateDocumentSetRequest with null createdBy fails validation
     *
     * <p>For any CreateDocumentSetRequest with null createdBy, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDocumentSetRequest with null createdBy fails validation")
    void createDocumentSetRequestWithNullCreatedByFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("validBase64Contents") String content) {
        
        CreateDocumentSetRequest request = new CreateDocumentSetRequest(
            documentType,
            schemaId,
            schemaVersion,
            content,
            null, // createdBy is null
            null
        );

        Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null createdBy should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("createdBy");
    }

    /**
     * Property 4: CreateDocumentSetRequest with blank createdBy fails validation
     *
     * <p>For any CreateDocumentSetRequest with blank createdBy, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.1, 9.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDocumentSetRequest with blank createdBy fails validation")
    void createDocumentSetRequestWithBlankCreatedByFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("validBase64Contents") String content,
            @ForAll("blankStrings") String createdBy) {
        
        CreateDocumentSetRequest request = new CreateDocumentSetRequest(
            documentType,
            schemaId,
            schemaVersion,
            content,
            createdBy,
            null
        );

        Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with blank createdBy should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("createdBy");
    }

    // ========================================================================
    // Property 4: AddDocumentRequest Missing Required Fields
    // ========================================================================

    /**
     * Property 4: AddDocumentRequest with null documentType fails validation
     *
     * <p>For any AddDocumentRequest with null documentType, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddDocumentRequest with null documentType fails validation")
    void addDocumentRequestWithNullDocumentTypeFails(
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("validBase64Contents") String content,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        AddDocumentRequest request = new AddDocumentRequest(
            null, // documentType is null
            schemaId,
            schemaVersion,
            content,
            createdBy,
            null
        );

        Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null documentType should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("documentType");
    }

    /**
     * Property 4: AddDocumentRequest with null schemaId fails validation
     *
     * <p>For any AddDocumentRequest with null schemaId, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddDocumentRequest with null schemaId fails validation")
    void addDocumentRequestWithNullSchemaIdFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("validBase64Contents") String content,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        AddDocumentRequest request = new AddDocumentRequest(
            documentType,
            null, // schemaId is null
            schemaVersion,
            content,
            createdBy,
            null
        );

        Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null schemaId should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("schemaId");
    }

    /**
     * Property 4: AddDocumentRequest with null content fails validation
     *
     * <p>For any AddDocumentRequest with null content, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddDocumentRequest with null content fails validation")
    void addDocumentRequestWithNullContentFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        AddDocumentRequest request = new AddDocumentRequest(
            documentType,
            schemaId,
            schemaVersion,
            null, // content is null
            createdBy,
            null
        );

        Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null content should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("content");
    }

    /**
     * Property 4: AddDocumentRequest with null createdBy fails validation
     *
     * <p>For any AddDocumentRequest with null createdBy, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddDocumentRequest with null createdBy fails validation")
    void addDocumentRequestWithNullCreatedByFails(
            @ForAll("validDocumentTypes") DocumentType documentType,
            @ForAll("validUUIDs") UUID schemaId,
            @ForAll("validVersionStrings") String schemaVersion,
            @ForAll("validBase64Contents") String content) {
        
        AddDocumentRequest request = new AddDocumentRequest(
            documentType,
            schemaId,
            schemaVersion,
            content,
            null, // createdBy is null
            null
        );

        Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null createdBy should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("createdBy");
    }

    // ========================================================================
    // Property 4: AddVersionRequest Missing Required Fields
    // ========================================================================

    /**
     * Property 4: AddVersionRequest with null content fails validation
     *
     * <p>For any AddVersionRequest with null content, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddVersionRequest with null content fails validation")
    void addVersionRequestWithNullContentFails(
            @ForAll("validCreatedByStrings") String createdBy) {
        
        AddVersionRequest request = new AddVersionRequest(
            null, // content is null
            createdBy
        );

        Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null content should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("content");
    }

    /**
     * Property 4: AddVersionRequest with blank content fails validation
     *
     * <p>For any AddVersionRequest with blank content, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.3, 9.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddVersionRequest with blank content fails validation")
    void addVersionRequestWithBlankContentFails(
            @ForAll("blankStrings") String content,
            @ForAll("validCreatedByStrings") String createdBy) {
        
        AddVersionRequest request = new AddVersionRequest(
            content,
            createdBy
        );

        Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with blank content should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("content");
    }

    /**
     * Property 4: AddVersionRequest with null createdBy fails validation
     *
     * <p>For any AddVersionRequest with null createdBy, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddVersionRequest with null createdBy fails validation")
    void addVersionRequestWithNullCreatedByFails(
            @ForAll("validBase64Contents") String content) {
        
        AddVersionRequest request = new AddVersionRequest(
            content,
            null // createdBy is null
        );

        Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null createdBy should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("createdBy");
    }

    /**
     * Property 4: AddVersionRequest with blank createdBy fails validation
     *
     * <p>For any AddVersionRequest with blank createdBy, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.3, 9.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddVersionRequest with blank createdBy fails validation")
    void addVersionRequestWithBlankCreatedByFails(
            @ForAll("validBase64Contents") String content,
            @ForAll("blankStrings") String createdBy) {
        
        AddVersionRequest request = new AddVersionRequest(
            content,
            createdBy
        );

        Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with blank createdBy should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("createdBy");
    }

    // ========================================================================
    // Property 4: CreateDerivativeRequest Missing Required Fields
    // ========================================================================

    /**
     * Property 4: CreateDerivativeRequest with invalid sourceVersionNumber fails validation
     *
     * <p>For any CreateDerivativeRequest with sourceVersionNumber less than 1, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDerivativeRequest with invalid sourceVersionNumber fails validation")
    void createDerivativeRequestWithInvalidSourceVersionNumberFails(
            @ForAll("invalidVersionNumbers") int sourceVersionNumber,
            @ForAll("validFormats") Format targetFormat) {
        
        CreateDerivativeRequest request = new CreateDerivativeRequest(
            sourceVersionNumber, // sourceVersionNumber is invalid (< 1)
            targetFormat
        );

        Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with sourceVersionNumber < 1 should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("sourceVersionNumber");
    }

    /**
     * Property 4: CreateDerivativeRequest with null targetFormat fails validation
     *
     * <p>For any CreateDerivativeRequest with null targetFormat, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateDerivativeRequest with null targetFormat fails validation")
    void createDerivativeRequestWithNullTargetFormatFails(
            @ForAll("validVersionNumbers") int sourceVersionNumber) {
        
        CreateDerivativeRequest request = new CreateDerivativeRequest(
            sourceVersionNumber,
            null // targetFormat is null
        );

        Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null targetFormat should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("targetFormat");
    }

    // ========================================================================
    // Property 4: CreateSchemaRequest Missing Required Fields
    // ========================================================================

    /**
     * Property 4: CreateSchemaRequest with null name fails validation
     *
     * <p>For any CreateSchemaRequest with null name, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 6.2, 9.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateSchemaRequest with null name fails validation")
    void createSchemaRequestWithNullNameFails(
            @ForAll("validSchemaFormats") SchemaFormat format) {
        
        CreateSchemaRequest request = new CreateSchemaRequest(
            null, // name is null
            format
        );

        Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null name should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("name");
    }

    /**
     * Property 4: CreateSchemaRequest with blank name fails validation
     *
     * <p>For any CreateSchemaRequest with blank name, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 6.2, 9.5, 9.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateSchemaRequest with blank name fails validation")
    void createSchemaRequestWithBlankNameFails(
            @ForAll("blankStrings") String name,
            @ForAll("validSchemaFormats") SchemaFormat format) {
        
        CreateSchemaRequest request = new CreateSchemaRequest(
            name,
            format
        );

        Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with blank name should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("name");
    }

    /**
     * Property 4: CreateSchemaRequest with null format fails validation
     *
     * <p>For any CreateSchemaRequest with null format, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 6.2, 9.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: CreateSchemaRequest with null format fails validation")
    void createSchemaRequestWithNullFormatFails(
            @ForAll("validSchemaNames") String name) {
        
        CreateSchemaRequest request = new CreateSchemaRequest(
            name,
            null // format is null
        );

        Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null format should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("format");
    }

    // ========================================================================
    // Property 4: AddSchemaVersionRequest Missing Required Fields
    // ========================================================================

    /**
     * Property 4: AddSchemaVersionRequest with null versionIdentifier fails validation
     *
     * <p>For any AddSchemaVersionRequest with null versionIdentifier, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 6.2, 9.6</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddSchemaVersionRequest with null versionIdentifier fails validation")
    void addSchemaVersionRequestWithNullVersionIdentifierFails(
            @ForAll("validBase64Contents") String definition) {
        
        AddSchemaVersionRequest request = new AddSchemaVersionRequest(
            null, // versionIdentifier is null
            definition
        );

        Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null versionIdentifier should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("versionIdentifier");
    }

    /**
     * Property 4: AddSchemaVersionRequest with blank versionIdentifier fails validation
     *
     * <p>For any AddSchemaVersionRequest with blank versionIdentifier, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 6.2, 9.6, 9.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddSchemaVersionRequest with blank versionIdentifier fails validation")
    void addSchemaVersionRequestWithBlankVersionIdentifierFails(
            @ForAll("blankStrings") String versionIdentifier,
            @ForAll("validBase64Contents") String definition) {
        
        AddSchemaVersionRequest request = new AddSchemaVersionRequest(
            versionIdentifier,
            definition
        );

        Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with blank versionIdentifier should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("versionIdentifier");
    }

    /**
     * Property 4: AddSchemaVersionRequest with null definition fails validation
     *
     * <p>For any AddSchemaVersionRequest with null definition, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 6.2, 9.6</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddSchemaVersionRequest with null definition fails validation")
    void addSchemaVersionRequestWithNullDefinitionFails(
            @ForAll("validVersionStrings") String versionIdentifier) {
        
        AddSchemaVersionRequest request = new AddSchemaVersionRequest(
            versionIdentifier,
            null // definition is null
        );

        Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with null definition should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("definition");
    }

    /**
     * Property 4: AddSchemaVersionRequest with blank definition fails validation
     *
     * <p>For any AddSchemaVersionRequest with blank definition, validation SHALL fail.</p>
     *
     * <p><b>Validates: Requirements 6.2, 9.6, 9.7</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 4: AddSchemaVersionRequest with blank definition fails validation")
    void addSchemaVersionRequestWithBlankDefinitionFails(
            @ForAll("validVersionStrings") String versionIdentifier,
            @ForAll("blankStrings") String definition) {
        
        AddSchemaVersionRequest request = new AddSchemaVersionRequest(
            versionIdentifier,
            definition
        );

        Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

        assertThat(violations)
            .as("Request with blank definition should have validation violations")
            .isNotEmpty()
            .extracting(v -> v.getPropertyPath().toString())
            .contains("definition");
    }

    // ========================================================================
    // Generators
    // ========================================================================

    @Provide
    Arbitrary<DocumentType> validDocumentTypes() {
        return Arbitraries.of(DocumentType.values());
    }

    @Provide
    Arbitrary<UUID> validUUIDs() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> validVersionStrings() {
        return Arbitraries.integers().between(0, 99)
            .tuple3()
            .map(tuple -> tuple.get1() + "." + tuple.get2() + "." + tuple.get3());
    }

    @Provide
    Arbitrary<String> validBase64Contents() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(100)
            .map(s -> Base64.getEncoder().encodeToString(s.getBytes()));
    }

    @Provide
    Arbitrary<String> validCreatedByStrings() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50)
            .map(s -> s + "@example.com");
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.oneOf(
            Arbitraries.just(""),
            Arbitraries.just("   "),
            Arbitraries.just("\t"),
            Arbitraries.just("\n"),
            Arbitraries.just("  \t  \n  ")
        );
    }

    @Provide
    Arbitrary<Integer> validVersionNumbers() {
        return Arbitraries.integers().between(1, 100);
    }

    @Provide
    Arbitrary<Integer> invalidVersionNumbers() {
        return Arbitraries.integers().between(-100, 0);
    }

    @Provide
    Arbitrary<Format> validFormats() {
        return Arbitraries.of(Format.values());
    }

    @Provide
    Arbitrary<SchemaFormat> validSchemaFormats() {
        return Arbitraries.of(SchemaFormat.values());
    }

    @Provide
    Arbitrary<String> validSchemaNames() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50)
            .map(s -> "Schema-" + s);
    }
}
