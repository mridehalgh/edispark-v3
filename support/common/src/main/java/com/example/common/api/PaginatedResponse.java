package com.example.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * Generic paginated response for REST APIs.
 * 
 * <p>Fields with null values are omitted from JSON output.</p>
 * 
 * @param <T> the type of items in the response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"items", "pageSize", "hasPrevious", "hasNext", "previousToken", "nextToken", "previousUrl", "nextUrl"})
public record PaginatedResponse<T>(
    List<T> items,
    int pageSize,
    boolean hasPrevious,
    boolean hasNext,
    String previousToken,
    String nextToken,
    String previousUrl,
    String nextUrl
) {
    public PaginatedResponse {
        if (items == null) {
            throw new IllegalArgumentException("Items cannot be null");
        }
        if (pageSize < 0) {
            throw new IllegalArgumentException("Page size cannot be negative");
        }
    }
    
    /**
     * Creates a paginated response with URL generation.
     * 
     * @param result the domain paginated result
     * @param baseUrl the base URL for the endpoint
     * @param limit the page size
     * @param currentToken the token used for the current request (becomes previousToken for next page)
     */
    public static <T> PaginatedResponse<T> from(
            com.example.common.pagination.PaginatedResult<T> result,
            String baseUrl,
            int limit,
            String currentToken) {
        
        String nextToken = result.continuationToken().orElse(null);
        String nextUrl = nextToken != null ? buildUrl(baseUrl, limit, nextToken) : null;
        String previousUrl = currentToken != null ? buildUrl(baseUrl, limit, currentToken) : null;
        
        return new PaginatedResponse<>(
            result.items(),
            result.size(),
            currentToken != null,
            result.hasMore(),
            currentToken,
            nextToken,
            previousUrl,
            nextUrl
        );
    }
    
    /**
     * Creates a paginated response without previous token tracking.
     */
    public static <T> PaginatedResponse<T> from(
            com.example.common.pagination.PaginatedResult<T> result,
            String baseUrl,
            int limit) {
        return from(result, baseUrl, limit, null);
    }
    
    private static String buildUrl(String baseUrl, int limit, String token) {
        return baseUrl + "?limit=" + limit + "&nextToken=" + token;
    }
}
