package com.example.documents.infrastructure.persistence;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for pagination token encoding stability.
 * 
 * <p><strong>Validates: Requirement 2.2</strong></p>
 * 
 * <p>Properties:
 * <ul>
 *   <li>Encoding the same key produces identical tokens</li>
 *   <li>Encoded tokens can be decoded back to original</li>
 * </ul>
 * </p>
 */
class TokenEncodingPropertyTest {

    @Property
    void encodingSameKeyProducesIdenticalTokens(@ForAll("dynamoDbKeys") Map<String, AttributeValue> key) {
        // When - encode the same key twice
        String token1 = PaginationTokenCodec.encode(key);
        String token2 = PaginationTokenCodec.encode(key);
        
        // Then - tokens should be identical
        assertThat(token1)
            .as("Encoding same key should produce identical tokens")
            .isEqualTo(token2);
    }

    @Property
    void encodedTokensCanBeDecodedBackToOriginal(@ForAll("dynamoDbKeys") Map<String, AttributeValue> originalKey) {
        // When - encode and then decode
        String token = PaginationTokenCodec.encode(originalKey);
        Map<String, AttributeValue> decodedKey = PaginationTokenCodec.decode(token);
        
        // Then - decoded key should equal original
        assertThat(decodedKey)
            .as("Decoded key should equal original key")
            .isEqualTo(originalKey);
    }

    @Property
    void encodingNullOrEmptyKeyReturnsNull() {
        // When/Then
        assertThat(PaginationTokenCodec.encode(null)).isNull();
        assertThat(PaginationTokenCodec.encode(Map.of())).isNull();
    }

    @Property
    void decodingNullOrEmptyTokenReturnsNull() {
        // When/Then
        assertThat(PaginationTokenCodec.decode(null)).isNull();
        assertThat(PaginationTokenCodec.decode("")).isNull();
        assertThat(PaginationTokenCodec.decode("   ")).isNull();
    }

    @Property
    void encodedTokensAreNonEmpty(@ForAll("dynamoDbKeys") Map<String, AttributeValue> key) {
        // When
        String token = PaginationTokenCodec.encode(key);
        
        // Then
        assertThat(token)
            .as("Encoded token should not be null or empty")
            .isNotNull()
            .isNotBlank();
    }

    @Property
    void encodedTokensAreBase64UrlSafe(@ForAll("dynamoDbKeys") Map<String, AttributeValue> key) {
        // When
        String token = PaginationTokenCodec.encode(key);
        
        // Then - should only contain Base64 URL-safe characters
        assertThat(token)
            .as("Token should only contain Base64 URL-safe characters")
            .matches("^[A-Za-z0-9_-]+$");
    }

    @Provide
    Arbitrary<Map<String, AttributeValue>> dynamoDbKeys() {
        // Generate maps with 1-5 string key-value pairs
        // Keys are alphanumeric strings (2-20 chars)
        // Values are DynamoDB string AttributeValues (5-50 chars)
        return Arbitraries.maps(
            Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(2)
                .ofMaxLength(20),
            Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(50)
                .map(s -> AttributeValue.builder().s(s).build())
        ).ofMinSize(1).ofMaxSize(5);
    }
}
