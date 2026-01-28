package com.example.documents.application.query;

import com.example.common.pagination.Page;

/**
 * Query for retrieving paginated document sets.
 * 
 * @param page pagination parameters
 */
public record ListDocumentSetsQuery(Page page) {
    
    /**
     * Creates a query with validated parameters.
     * 
     * @param limit number of items per page (1-100, null defaults to 20)
     * @param nextToken optional continuation token from previous page
     * @return validated query
     * @throws IllegalArgumentException if limit is out of range
     */
    public static ListDocumentSetsQuery of(Integer limit, String nextToken) {
        int pageLimit = (limit != null) ? limit : 20;
        
        if (pageLimit < 1 || pageLimit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        
        Page page = (nextToken != null) 
            ? Page.next(pageLimit, nextToken)
            : Page.first(pageLimit);
            
        return new ListDocumentSetsQuery(page);
    }
}
