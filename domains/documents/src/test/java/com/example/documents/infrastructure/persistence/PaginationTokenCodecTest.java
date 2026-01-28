package com.example.documents.infrastructure.persistence;

import com.example.documents.application.handler.InvalidPaginationTokenException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PaginationTokenCodecTest {
    
    @Test
    void encodeAndDecodeRoundTrip() {
        Map<String, AttributeValue> original = Map.of(
            "PK", AttributeValue.builder().s("TENANT#DEFAULT#DOCSETS").build(),
            "SK", AttributeValue.builder().s("2024-01-15T10:30:00Z#docset-123").build()
        );
        
        String token = PaginationTokenCodec.encode(original);
        Map<String, AttributeValue> decoded = PaginationTokenCodec.decode(token);
        
        assertThat(decoded).isEqualTo(original);
    }
    
    @Test
    void encodeReturnsNullForNullInput() {
        String token = PaginationTokenCodec.encode(null);
        assertThat(token).isNull();
    }
    
    @Test
    void encodeReturnsNullForEmptyMap() {
        String token = PaginationTokenCodec.encode(Map.of());
        assertThat(token).isNull();
    }
    
    @Test
    void decodeReturnsNullForNullInput() {
        Map<String, AttributeValue> decoded = PaginationTokenCodec.decode(null);
        assertThat(decoded).isNull();
    }
    
    @Test
    void decodeReturnsNullForBlankInput() {
        Map<String, AttributeValue> decoded = PaginationTokenCodec.decode("   ");
        assertThat(decoded).isNull();
    }
    
    @Test
    void decodeInvalidTokenThrowsException() {
        assertThatThrownBy(() -> PaginationTokenCodec.decode("invalid-token"))
            .isInstanceOf(InvalidPaginationTokenException.class)
            .hasMessageContaining("Invalid or corrupted pagination token");
    }
    
    @Test
    void encodedTokensAreStable() {
        Map<String, AttributeValue> key = Map.of(
            "PK", AttributeValue.builder().s("TEST").build()
        );
        
        String token1 = PaginationTokenCodec.encode(key);
        String token2 = PaginationTokenCodec.encode(key);
        
        assertThat(token1).isEqualTo(token2);
    }
    
    @Test
    void encodedTokenIsBase64() {
        Map<String, AttributeValue> key = Map.of(
            "PK", AttributeValue.builder().s("TEST").build()
        );
        
        String token = PaginationTokenCodec.encode(key);
        
        // Base64 URL-safe characters only
        assertThat(token).matches("[A-Za-z0-9_-]+");
    }
}
