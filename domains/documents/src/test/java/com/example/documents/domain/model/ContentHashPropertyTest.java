package com.example.documents.domain.model;

import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.ByteRange;
import net.jqwik.api.constraints.Size;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for ContentHash consistency.
 * 
 * <p><b>Validates: Requirements 1.5, 5.7, 8.3</b></p>
 */
class ContentHashPropertyTest {

    /**
     * Property 5: Content Hash Consistency
     * 
     * <p>For any Content, computing the hash of the same byte array SHALL always 
     * produce the same ContentHash value.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-domain, Property 5: Content hash consistency")
    void contentHashIsConsistentForSameData(@ForAll @Size(min = 0, max = 10000) byte[] data) {
        Content content1 = Content.of(data, Format.XML);
        Content content2 = Content.of(data, Format.XML);

        assertThat(content1.hash()).isEqualTo(content2.hash());
    }

    /**
     * Verifies that hash computation is deterministic across multiple calls.
     */
    @Property(tries = 100)
    @Label("Feature: documents-domain, Property 5: Hash computation is deterministic")
    void hashComputationIsDeterministic(@ForAll @Size(min = 1, max = 5000) byte[] data) {
        ContentHash hash1 = Content.computeHash(data);
        ContentHash hash2 = Content.computeHash(data);
        ContentHash hash3 = Content.computeHash(data);

        assertThat(hash1).isEqualTo(hash2).isEqualTo(hash3);
    }

    /**
     * Verifies that different data produces different hashes (with high probability).
     */
    @Property(tries = 100)
    @Label("Feature: documents-domain, Property 5: Different data produces different hashes")
    void differentDataProducesDifferentHashes(
            @ForAll @Size(min = 1, max = 1000) byte[] data1,
            @ForAll @Size(min = 1, max = 1000) byte[] data2) {
        
        // Skip if data happens to be identical
        if (java.util.Arrays.equals(data1, data2)) {
            return;
        }

        Content content1 = Content.of(data1, Format.JSON);
        Content content2 = Content.of(data2, Format.JSON);

        assertThat(content1.hash()).isNotEqualTo(content2.hash());
    }

    /**
     * Verifies that hash is independent of format.
     */
    @Property(tries = 100)
    @Label("Feature: documents-domain, Property 5: Hash is computed from data only, not format")
    void hashIsComputedFromDataOnly(@ForAll @Size(min = 0, max = 5000) byte[] data) {
        Content xmlContent = Content.of(data, Format.XML);
        Content jsonContent = Content.of(data, Format.JSON);

        assertThat(xmlContent.hash()).isEqualTo(jsonContent.hash());
    }

    /**
     * Verifies that the hash algorithm is SHA-256.
     */
    @Property(tries = 100)
    @Label("Feature: documents-domain, Property 5: Hash uses SHA-256 algorithm")
    void hashUsesSha256Algorithm(@ForAll @Size(min = 0, max = 1000) byte[] data) {
        Content content = Content.of(data, Format.XML);

        assertThat(content.hash().algorithm()).isEqualTo("SHA-256");
        // SHA-256 produces 64 hex characters
        assertThat(content.hash().hash()).hasSize(64);
    }
}
