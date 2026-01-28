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
 * Property-based tests for pagination token encoding.
 */
class TokenEncodingPropertyTest {

    @Property
    void encodingSameKeyProducesIdenticalTokens(@ForAll("dynamoDbKeys") Map<String, AttributeValue> key) {
        String token1 = PaginationTokenCodec.encode(key);
        String token2 = PaginationTokenCodec.encode(key);
        
        assertThat(token1).isEqualTo(token2);
    }

    @Property
    void encodedTokensCanBeDecodedBackToOriginal(@ForAll("dynamoDbKeys") Map<String, AttributeValue> originalKey) {
        String token = PaginationTokenCodec.encode(originalKey);
        Map<String, AttributeValue> decodedKey = PaginationTokenCodec.decode(token);
        
        assertThat(decodedKey).isEqualTo(originalKey);
    }

    @Property
    void encodingNullOrEmptyKeyReturnsNull() {
        assertThat(PaginationTokenCodec.encode(null)).isNull();
        assertThat(PaginationTokenCodec.encode(Map.of())).isNull();
    }

    @Property
    void decodingNullOrEmptyTokenReturnsNull() {
        assertThat(PaginationTokenCodec.decode(null)).isNull();
        assertThat(PaginationTokenCodec.decode("")).isNull();
        assertThat(PaginationTokenCodec.decode("   ")).isNull();
    }

    @Property
    void encodedTokensAreNonEmpty(@ForAll("dynamoDbKeys") Map<String, AttributeValue> key) {
        String token = PaginationTokenCodec.encode(key);
        
        assertThat(token).isNotNull().isNotBlank();
    }

    @Property
    void encodedTokensAreBase64UrlSafe(@ForAll("dynamoDbKeys") Map<String, AttributeValue> key) {
        String token = PaginationTokenCodec.encode(key);
        
        assertThat(token).matches("^[A-Za-z0-9_-]+$");
    }

    @Provide
    Arbitrary<Map<String, AttributeValue>> dynamoDbKeys() {
        return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10),
            Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(30)
                .map(s -> AttributeValue.builder().s(s).build())
        ).ofMinSize(1).ofMaxSize(4);
    }
}
