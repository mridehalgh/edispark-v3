package com.example.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * Generic paginated response for REST APIs following best practices.
 * 
 * <p>This provides a consistent pagination structure across all API endpoints with:</p>
 * <ul>
 *   <li>{@code items} - the actual data for this page</li>
 *   <li>{@code pageSize} - number of items in this page</li>
 *   <li>{@code hasNext} - indicates a next page may exist (use nextToken to fetch)</li>
 *   <li>{@code nextToken} - opaque continuation token (null if definitely no more pages)</li>
 *   <li>{@code nextUrl} - full URL for next page (null if definitely no more pages)</li>
 * </ul>
 * 
 * <p>Note: {@code hasNext} being true indicates there may be more results. Due to how
 * some databases (like DynamoDB) handle pagination, the next page could be empty.
 * The definitive signal that pagination is complete is when {@code hasNext} is false.</p>
 * 
 * <p>Fields with null values are omitted from JSON output.</p>
 * 
 * @param <T> the type of items in the response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"items", "pageSize", "hasNext", "nextToken", "nextUrl"})
public record PaginatedResponse<T>(
    List<T> items,
    int pageSize,
    boolean hasNext,
    String nextToken,
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
     * Creates a paginated response from domain PaginatedResult without URL generation.
     * Use {@link #from(PaginatedResult, String, int)} when you need nextUrl.
     */
    public static <T> PaginatedResponse<T> from(
            com.example.common.pagination.PaginatedResult<T> result) {
        String token = result.continuationToken().orElse(null);
        return new PaginatedResponse<>(
            result.items(),
            result.size(),
            result.hasMore(),
            token,
            null
        );
    }
    
    /**
     * Creates a paginated response with nextUrl generation.
     * 
     * @param result the domain paginated result
     * @param baseUrl the base URL for the endpoint (e.g., "/api/document-sets")
     * @param limit the page size used in the request
     * @return paginated response with nextUrl populated if there are more pages
     */
    public static <T> PaginatedResponse<T> from(
            com.example.common.pagination.PaginatedResult<T> result,
            String baseUrl,
            int limit) {
        String token = result.continuationToken().orElse(null);
        String nextUrl = null;
        
        if (token != null && baseUrl != null) {
            nextUrl = buildNextUrl(baseUrl, limit, token);
        }
        
        return new PaginatedResponse<>(
            result.items(),
            result.size(),
            result.hasMore(),
            token,
            nextUrl
        );
    }
    
    private static String buildNextUrl(String baseUrl, int limit, String nextToken) {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("?limit=").append(limit);
        url.append("&nextToken=").append(nextToken);
        return url.toString();
    }
}
