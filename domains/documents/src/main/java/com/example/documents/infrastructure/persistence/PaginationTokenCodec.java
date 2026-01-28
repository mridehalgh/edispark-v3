package com.example.documents.infrastructure.persistence;

import com.example.documents.application.handler.InvalidPaginationTokenException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes and decodes DynamoDB pagination tokens.
 * 
 * <p>Tokens are Base64-encoded JSON representations of DynamoDB's LastEvaluatedKey.
 * This abstraction hides DynamoDB implementation details from API clients.</p>
 */
public class PaginationTokenCodec {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * Encodes a DynamoDB LastEvaluatedKey into an opaque token string.
     * 
     * @param lastEvaluatedKey the DynamoDB key map
     * @return Base64-encoded token, or null if key is null/empty
     */
    public static String encode(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        
        try {
            // Convert AttributeValue map to simple string map for JSON serialization
            Map<String, String> simpleMap = new HashMap<>();
            lastEvaluatedKey.forEach((key, value) -> {
                if (value.s() != null) {
                    simpleMap.put(key, value.s());
                }
            });
            
            String json = MAPPER.writeValueAsString(simpleMap);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode pagination token", e);
        }
    }
    
    /**
     * Decodes an opaque token string into a DynamoDB ExclusiveStartKey.
     * 
     * @param token the Base64-encoded token
     * @return DynamoDB key map
     * @throws InvalidPaginationTokenException if token is malformed
     */
    public static Map<String, AttributeValue> decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String json = new String(decoded);
            
            @SuppressWarnings("unchecked")
            Map<String, String> simpleMap = MAPPER.readValue(json, Map.class);
            
            // Convert back to AttributeValue map
            Map<String, AttributeValue> attributeMap = new HashMap<>();
            simpleMap.forEach((key, value) -> 
                attributeMap.put(key, AttributeValue.builder().s(value).build())
            );
            
            return attributeMap;
        } catch (Exception e) {
            throw new InvalidPaginationTokenException("Invalid or corrupted pagination token", e);
        }
    }
}
