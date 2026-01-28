package com.example.common.api;

import com.example.common.pagination.PaginatedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PaginatedResponseTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void paginatedResponseRejectsNullItems() {
        assertThatThrownBy(() -> new PaginatedResponse<String>(null, 0, false, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }
    
    @Test
    void paginatedResponseRejectsNegativePageSize() {
        assertThatThrownBy(() -> new PaginatedResponse<String>(List.of(), -1, false, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Page size cannot be negative");
    }
    
    @Test
    void paginatedResponseAcceptsNullTokenAndUrl() {
        PaginatedResponse<String> response = new PaginatedResponse<>(List.of("a"), 1, false, null, null);
        assertThat(response.items()).containsExactly("a");
        assertThat(response.pageSize()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextToken()).isNull();
        assertThat(response.nextUrl()).isNull();
    }
    
    @Test
    void fromConvertsResultWithToken() {
        PaginatedResult<String> result = PaginatedResult.of(List.of("a", "b"), "token123");
        PaginatedResponse<String> response = PaginatedResponse.from(result);
        
        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextToken()).isEqualTo("token123");
        assertThat(response.nextUrl()).isNull();
    }
    
    @Test
    void fromConvertsResultWithoutToken() {
        PaginatedResult<String> result = PaginatedResult.lastPage(List.of("a", "b"));
        PaginatedResponse<String> response = PaginatedResponse.from(result);
        
        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextToken()).isNull();
        assertThat(response.nextUrl()).isNull();
    }
    
    @Test
    void fromWithBaseUrlGeneratesNextUrl() {
        PaginatedResult<String> result = PaginatedResult.of(List.of("a", "b"), "token123");
        PaginatedResponse<String> response = PaginatedResponse.from(result, "/api/items", 10);
        
        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextToken()).isEqualTo("token123");
        assertThat(response.nextUrl()).isEqualTo("/api/items?limit=10&nextToken=token123");
    }
    
    @Test
    void fromWithBaseUrlOmitsNextUrlOnLastPage() {
        PaginatedResult<String> result = PaginatedResult.lastPage(List.of("a", "b"));
        PaginatedResponse<String> response = PaginatedResponse.from(result, "/api/items", 10);
        
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextToken()).isNull();
        assertThat(response.nextUrl()).isNull();
    }
    
    @Test
    void jsonSerializationIncludesAllFields() throws Exception {
        PaginatedResponse<String> response = new PaginatedResponse<>(
            List.of("a"), 1, true, "token", "/api/items?nextToken=token"
        );
        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).contains("\"items\":[\"a\"]");
        assertThat(json).contains("\"pageSize\":1");
        assertThat(json).contains("\"hasNext\":true");
        assertThat(json).contains("\"nextToken\":\"token\"");
        assertThat(json).contains("\"nextUrl\":\"/api/items?nextToken=token\"");
    }
    
    @Test
    void jsonSerializationOmitsNullFields() throws Exception {
        PaginatedResponse<String> response = new PaginatedResponse<>(List.of("a"), 1, false, null, null);
        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).contains("\"items\":[\"a\"]");
        assertThat(json).contains("\"pageSize\":1");
        assertThat(json).contains("\"hasNext\":false");
        assertThat(json).doesNotContain("nextToken");
        assertThat(json).doesNotContain("nextUrl");
    }
    
    @Test
    void jsonFieldOrderIsCorrect() throws Exception {
        PaginatedResponse<String> response = new PaginatedResponse<>(
            List.of("a"), 1, true, "token", "/api/next"
        );
        String json = objectMapper.writeValueAsString(response);
        
        // Verify field order: items, pageSize, hasNext, nextToken, nextUrl
        int itemsIndex = json.indexOf("\"items\"");
        int pageSizeIndex = json.indexOf("\"pageSize\"");
        int hasNextIndex = json.indexOf("\"hasNext\"");
        int nextTokenIndex = json.indexOf("\"nextToken\"");
        int nextUrlIndex = json.indexOf("\"nextUrl\"");
        
        assertThat(itemsIndex).isLessThan(pageSizeIndex);
        assertThat(pageSizeIndex).isLessThan(hasNextIndex);
        assertThat(hasNextIndex).isLessThan(nextTokenIndex);
        assertThat(nextTokenIndex).isLessThan(nextUrlIndex);
    }
    
    @Test
    void emptyResultProducesValidResponse() {
        PaginatedResult<String> result = PaginatedResult.empty();
        PaginatedResponse<String> response = PaginatedResponse.from(result, "/api/items", 20);
        
        assertThat(response.items()).isEmpty();
        assertThat(response.pageSize()).isZero();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextToken()).isNull();
        assertThat(response.nextUrl()).isNull();
    }
}
