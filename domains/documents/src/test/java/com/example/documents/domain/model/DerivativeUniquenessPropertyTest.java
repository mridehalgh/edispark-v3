package com.example.documents.domain.model;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for derivative uniqueness.
 * 
 * <p><b>Property 12: Derivative Uniqueness</b></p>
 * <p>For any DocumentVersion and target Format combination, the DocumentSet SHALL 
 * contain at most one Derivative.</p>
 * 
 * <p><b>Validates: Requirements 7.5</b></p>
 */
class DerivativeUniquenessPropertyTest {

    @Provide
    Arbitrary<SchemaVersionRef> schemaVersionRefs() {
        return Arbitraries.of(
                SchemaVersionRef.of(
                        new SchemaId(UUID.randomUUID()),
                        new VersionIdentifier("1.0.0")),
                SchemaVersionRef.of(
                        new SchemaId(UUID.randomUUID()),
                        new VersionIdentifier("2.1.0")));
    }

    @Provide
    Arbitrary<DocumentType> documentTypes() {
        return Arbitraries.of(DocumentType.values());
    }

    @Provide
    Arbitrary<Format> formats() {
        return Arbitraries.of(Format.values());
    }

    @Provide
    Arbitrary<TransformationMethod> transformationMethods() {
        return Arbitraries.of(TransformationMethod.values());
    }

    /**
     * Property 12: Derivative Uniqueness
     * 
     * <p>Creating a duplicate derivative (same source version and target format) SHALL throw an exception.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 12: Duplicate derivative creation throws exception")
    void duplicateDerivativeCreationThrowsException(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType,
            @ForAll("formats") Format targetFormat,
            @ForAll("transformationMethods") TransformationMethod method) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        DocumentSet documentSet = DocumentSet.create("user1", null);
        
        Document document = documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user1");
        
        DocumentVersionId versionId = document.getCurrentVersion().id();

        // Create first derivative
        Content derivativeContent1 = Content.of(("derivative1").getBytes(), targetFormat);
        documentSet.createDerivative(
                document.id(),
                versionId,
                targetFormat,
                ContentRef.of(derivativeContent1.hash()),
                derivativeContent1.hash(),
                method);

        // Attempt to create duplicate derivative
        Content derivativeContent2 = Content.of(("derivative2").getBytes(), targetFormat);
        assertThatThrownBy(() -> documentSet.createDerivative(
                document.id(),
                versionId,
                targetFormat,
                ContentRef.of(derivativeContent2.hash()),
                derivativeContent2.hash(),
                method))
                .as("Creating duplicate derivative should throw DuplicateDerivativeException")
                .isInstanceOf(DuplicateDerivativeException.class);
    }

    /**
     * Property 12: Derivative Uniqueness
     * 
     * <p>Different formats for the same version are allowed.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 12: Different formats for same version are allowed")
    void differentFormatsForSameVersionAreAllowed(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType,
            @ForAll("transformationMethods") TransformationMethod method) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        DocumentSet documentSet = DocumentSet.create("user1", null);
        
        Document document = documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user1");
        
        DocumentVersionId versionId = document.getCurrentVersion().id();

        // Create derivatives in all different formats
        int derivativeCount = 0;
        for (Format format : Format.values()) {
            Content derivativeContent = Content.of(("derivative-" + format).getBytes(), format);
            documentSet.createDerivative(
                    document.id(),
                    versionId,
                    format,
                    ContentRef.of(derivativeContent.hash()),
                    derivativeContent.hash(),
                    method);
            derivativeCount++;
        }

        // Verify all derivatives were created
        assertThat(document.derivatives())
                .as("All format derivatives should be created")
                .hasSize(derivativeCount);
    }

    /**
     * Property 12: Derivative Uniqueness
     * 
     * <p>Same format for different versions is allowed.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 12: Same format for different versions is allowed")
    void sameFormatForDifferentVersionsIsAllowed(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("formats") Format targetFormat,
            @ForAll("transformationMethods") TransformationMethod method,
            @ForAll @IntRange(min = 2, max = 5) int versionCount) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        DocumentSet documentSet = DocumentSet.create("user1", null);
        
        Document document = documentSet.addDocument(
                DocumentType.INVOICE,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user1");

        // Create multiple versions
        for (int i = 1; i < versionCount; i++) {
            Content newContent = Content.of(("version" + (i + 1)).getBytes(), Format.XML);
            documentSet.addVersion(
                    document.id(),
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user");
        }

        // Create derivative with same format for each version
        for (int i = 1; i <= versionCount; i++) {
            DocumentVersion version = document.getVersion(i).orElseThrow();
            Content derivativeContent = Content.of(("derivative-v" + i).getBytes(), targetFormat);
            documentSet.createDerivative(
                    document.id(),
                    version.id(),
                    targetFormat,
                    ContentRef.of(derivativeContent.hash()),
                    derivativeContent.hash(),
                    method);
        }

        // Verify all derivatives were created
        assertThat(document.derivatives())
                .as("Derivatives for all versions should be created")
                .hasSize(versionCount);
    }

    /**
     * Property 12: Derivative Uniqueness
     * 
     * <p>At most one derivative exists per version-format combination.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 12: At most one derivative per version-format combination")
    void atMostOneDerivativePerVersionFormatCombination(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("transformationMethods") TransformationMethod method,
            @ForAll @IntRange(min = 1, max = 3) int versionCount) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        DocumentSet documentSet = DocumentSet.create("user1", null);
        
        Document document = documentSet.addDocument(
                DocumentType.ORDER,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user1");

        // Create versions
        for (int i = 1; i < versionCount; i++) {
            Content newContent = Content.of(("v" + (i + 1)).getBytes(), Format.XML);
            documentSet.addVersion(
                    document.id(),
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user");
        }

        // Create derivatives for all version-format combinations
        for (int v = 1; v <= versionCount; v++) {
            DocumentVersion version = document.getVersion(v).orElseThrow();
            for (Format format : Format.values()) {
                Content derivativeContent = Content.of(("d-v" + v + "-" + format).getBytes(), format);
                documentSet.createDerivative(
                        document.id(),
                        version.id(),
                        format,
                        ContentRef.of(derivativeContent.hash()),
                        derivativeContent.hash(),
                        method);
            }
        }

        // Verify uniqueness: for each version-format combination, exactly one derivative exists
        for (int v = 1; v <= versionCount; v++) {
            DocumentVersion version = document.getVersion(v).orElseThrow();
            for (Format format : Format.values()) {
                long count = document.derivatives().stream()
                        .filter(d -> d.matches(version.id(), format))
                        .count();
                assertThat(count)
                        .as("Exactly one derivative should exist for version %d and format %s", v, format)
                        .isEqualTo(1);
            }
        }
    }

    /**
     * Property 12: Derivative Uniqueness
     * 
     * <p>Derivative uniqueness is enforced across multiple documents in a DocumentSet.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 12: Derivative uniqueness per document")
    void derivativeUniquenessPerDocument(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("formats") Format targetFormat,
            @ForAll("transformationMethods") TransformationMethod method) {
        
        DocumentSet documentSet = DocumentSet.create("user1", null);

        // Create two documents
        Content content1 = Content.of(new byte[]{1}, Format.XML);
        Document doc1 = documentSet.addDocument(
                DocumentType.INVOICE,
                schemaRef,
                ContentRef.of(content1.hash()),
                content1.hash(),
                "user1");

        Content content2 = Content.of(new byte[]{2}, Format.XML);
        Document doc2 = documentSet.addDocument(
                DocumentType.ORDER,
                schemaRef,
                ContentRef.of(content2.hash()),
                content2.hash(),
                "user1");

        // Create derivative with same format for both documents (should succeed)
        Content derivativeContent1 = Content.of(("d1").getBytes(), targetFormat);
        documentSet.createDerivative(
                doc1.id(),
                doc1.getCurrentVersion().id(),
                targetFormat,
                ContentRef.of(derivativeContent1.hash()),
                derivativeContent1.hash(),
                method);

        Content derivativeContent2 = Content.of(("d2").getBytes(), targetFormat);
        documentSet.createDerivative(
                doc2.id(),
                doc2.getCurrentVersion().id(),
                targetFormat,
                ContentRef.of(derivativeContent2.hash()),
                derivativeContent2.hash(),
                method);

        // Verify both derivatives exist
        assertThat(doc1.derivatives()).hasSize(1);
        assertThat(doc2.derivatives()).hasSize(1);

        // Verify duplicate on doc1 throws exception
        Content duplicateContent = Content.of(("dup").getBytes(), targetFormat);
        assertThatThrownBy(() -> documentSet.createDerivative(
                doc1.id(),
                doc1.getCurrentVersion().id(),
                targetFormat,
                ContentRef.of(duplicateContent.hash()),
                duplicateContent.hash(),
                method))
                .isInstanceOf(DuplicateDerivativeException.class);
    }
}
