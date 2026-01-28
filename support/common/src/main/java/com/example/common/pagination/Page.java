package com.example.common.pagination;

import java.util.Optional;

/**
 * Represents pagination parameters for a query.
 * 
 * <p>This is a framework-agnostic representation of page request parameters
 * that can be used across all bounded contexts.</p>
 * 
 * @param limit maximum number of items to return (must be positive)
 * @param continuationToken optional token from previous page
 */
public record Page(
    int limit,
    Optional<String> continuationToken
) {
    public Page {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
    }
    
    /**
     * Creates a page request with default limit of 20.
     */
    public static Page first() {
        return new Page(20, Optional.empty());
    }
    
    /**
     * Creates a page request with specified limit.
     */
    public static Page first(int limit) {
        return new Page(limit, Optional.empty());
    }
    
    /**
     * Creates a page request for the next page.
     */
    public static Page next(int limit, String continuationToken) {
        return new Page(limit, Optional.of(continuationToken));
    }
    
    public boolean isFirstPage() {
        return continuationToken.isEmpty();
    }
}
