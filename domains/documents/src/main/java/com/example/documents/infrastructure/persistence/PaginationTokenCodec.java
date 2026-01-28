package com.example.documents.infrastructure.persistence;

import com.example.documents.application.handler.InvalidPaginationTokenException;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Base64 encodes DynamoDB LastEvaluatedKey for use as opaque pagination token.
 */
public final class PaginationTokenCodec {

    private PaginationTokenCodec() {
    }

    public static String encode(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        String json = EnhancedDocument.fromAttributeValueMap(lastEvaluatedKey).toJson();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static Map<String, AttributeValue> decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            return EnhancedDocument.fromJson(json).toMap();
        } catch (Exception e) {
            throw new InvalidPaginationTokenException("Invalid pagination token", e);
        }
    }
}
