package com.example.documents.infrastructure.persistence;

import com.example.documents.application.handler.InvalidPaginationTokenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaginationTokenCodec")
class PaginationTokenCodecTest {

    @Nested
    @DisplayName("encode")
    class EncodeTests {
        
        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNullInput() {
            assertThat(PaginationTokenCodec.encode(null)).isNull();
        }
        
        @Test
        @DisplayName("returns null for empty map")
        void returnsNullForEmptyMap() {
            assertThat(PaginationTokenCodec.encode(Map.of())).isNull();
        }
        
        @Test
        @DisplayName("produces URL-safe Base64 token")
        void producesUrlSafeBase64() {
            Map<String, AttributeValue> key = Map.of(
                "PK", AttributeValue.builder().s("TENANT#default#DOCSET#123").build(),
                "SK", AttributeValue.builder().s("METADATA").build()
            );
            
            String token = PaginationTokenCodec.encode(key);
            
            assertThat(token).matches("^[A-Za-z0-9_-]+$");
        }
    }
    
    @Nested
    @DisplayName("decode")
    class DecodeTests {
        
        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNullInput() {
            assertThat(PaginationTokenCodec.decode(null)).isNull();
        }
        
        @Test
        @DisplayName("returns null for blank input")
        void returnsNullForBlankInput() {
            assertThat(PaginationTokenCodec.decode("")).isNull();
            assertThat(PaginationTokenCodec.decode("   ")).isNull();
        }
        
        @Test
        @DisplayName("throws InvalidPaginationTokenException for invalid Base64")
        void throwsForInvalidBase64() {
            assertThatThrownBy(() -> PaginationTokenCodec.decode("!!!invalid!!!"))
                .isInstanceOf(InvalidPaginationTokenException.class);
        }
        
        @Test
        @DisplayName("throws InvalidPaginationTokenException for invalid JSON")
        void throwsForInvalidJson() {
            String invalidToken = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("not-json".getBytes());
            
            assertThatThrownBy(() -> PaginationTokenCodec.decode(invalidToken))
                .isInstanceOf(InvalidPaginationTokenException.class);
        }
    }
    
    @Nested
    @DisplayName("round-trip")
    class RoundTripTests {
        
        @Test
        @DisplayName("decodes back to original key")
        void decodesToOriginalKey() {
            Map<String, AttributeValue> originalKey = Map.of(
                "PK", AttributeValue.builder().s("TENANT#default#DOCSET#abc-123").build(),
                "SK", AttributeValue.builder().s("METADATA").build(),
                "GSI1PK", AttributeValue.builder().s("TENANT#default#DOCSETS").build(),
                "GSI1SK", AttributeValue.builder().s("2024-01-15T10:30:00Z#abc-123").build()
            );
            
            String token = PaginationTokenCodec.encode(originalKey);
            Map<String, AttributeValue> decoded = PaginationTokenCodec.decode(token);
            
            assertThat(decoded).hasSize(4);
            assertThat(decoded.get("PK").s()).isEqualTo("TENANT#default#DOCSET#abc-123");
            assertThat(decoded.get("SK").s()).isEqualTo("METADATA");
            assertThat(decoded.get("GSI1PK").s()).isEqualTo("TENANT#default#DOCSETS");
            assertThat(decoded.get("GSI1SK").s()).isEqualTo("2024-01-15T10:30:00Z#abc-123");
        }
    }
}
