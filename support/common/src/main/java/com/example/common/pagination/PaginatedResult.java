package com.example.common.pagination;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Represents a paginated result set with continuation token.
 * 
 * <p>This is a domain-layer abstraction that is independent of any
 * persistence technology or API framework.</p>
 * 
 * @param <T> the type of items in the result
 * @param items the list of items in this page (never null)
 * @param continuationToken optional opaque token for fetching next page
 */
public record PaginatedResult<T>(
    List<T> items,
    Optional<String> continuationToken
) {
    public PaginatedResult {
        if (items == null) {
            throw new IllegalArgumentException("Items cannot be null");
        }
    }
    
    /**
     * Returns true if there are more pages available.
     */
    public boolean hasMore() {
        return continuationToken.isPresent();
    }
    
    /**
     * Returns the number of items in this page.
     */
    public int size() {
        return items.size();
    }
    
    /**
     * Returns true if this page is empty.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    /**
     * Maps items to a different type while preserving pagination metadata.
     */
    public <R> PaginatedResult<R> map(Function<T, R> mapper) {
        List<R> mappedItems = items.stream()
            .map(mapper)
            .toList();
        return new PaginatedResult<>(mappedItems, continuationToken);
    }
    
    /**
     * Creates a paginated result with items and continuation token.
     */
    public static <T> PaginatedResult<T> of(List<T> items, String continuationToken) {
        return new PaginatedResult<>(items, Optional.ofNullable(continuationToken));
    }
    
    /**
     * Creates a paginated result for the last page (no more results).
     */
    public static <T> PaginatedResult<T> lastPage(List<T> items) {
        return new PaginatedResult<>(items, Optional.empty());
    }
    
    /**
     * Creates an empty result (no items, no more pages).
     */
    public static <T> PaginatedResult<T> empty() {
        return new PaginatedResult<>(List.of(), Optional.empty());
    }
}
