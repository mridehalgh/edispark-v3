package com.example.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Generic paginated response for REST APIs.
 * 
 * <p>This provides a consistent pagination structure across all API endpoints.
 * The nextToken field is omitted from JSON when null (last page).</p>
 * 
 * @param <T> the type of items in the response
 * @param items the list of items in this page
 * @param nextToken optional opaque token for fetching next page (null if last page)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginatedResponse<T>(
    List<T> items,
    String nextToken
) {
    public PaginatedResponse {
        if (items == null) {
            throw new IllegalArgumentException("Items cannot be null");
        }
    }
    
    /**
     * Creates a paginated response from domain PaginatedResult.
     */
    public static <T> PaginatedResponse<T> from(
            com.example.common.pagination.PaginatedResult<T> result) {
        return new PaginatedResponse<>(
            result.items(),
            result.continuationToken().orElse(null)
        );
    }
}
