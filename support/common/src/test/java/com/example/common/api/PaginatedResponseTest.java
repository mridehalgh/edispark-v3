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
        assertThatThrownBy(() -> new PaginatedResponse<String>(null, 0, false, false, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }
    
    @Test
    void paginatedResponseRejectsNegativePageSize() {
        assertThatThrownBy(() -> new PaginatedResponse<String>(List.of(), -1, false, false, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Page size cannot be negative");
    }
    
    @Test
    void paginatedResponseAcceptsNullTokenAndUrl() {
        PaginatedResponse<String> response = new PaginatedResponse<>(
            List.of("a"), 1, false, false, null, null, null, null
        );
        assertThat(response.items()).containsExactly("a");
        assertThat(response.pageSize()).isEqualTo(1);
        assertThat(response.hasPrevious()).isFalse();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.previousToken()).isNull();
        assertThat(response.nextToken()).isNull();
        assertThat(response.previousUrl()).isNull();
        assertThat(response.nextUrl()).isNull();
    }
    
    @Test
    void fromConvertsResultWithToken() {
        PaginatedResult<String> result = PaginatedResult.of(List.of("a", "b"), "token123");
        PaginatedResponse<String> response = PaginatedResponse.from(result, "/api/items", 10);
        
        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.hasPrevious()).isFalse();
        assertThat(response.hasNext()).isTrue();
        assertThat(response.previousToken()).isNull();
        assertThat(response.nextToken()).isEqualTo("token123");
        assertThat(response.previousUrl()).isNull();
        assertThat(response.nextUrl()).isEqualTo("/api/items?limit=10&nextToken=token123");
    }
    
    @Test
    void fromConvertsResultWithoutToken() {
        PaginatedResult<String> result = PaginatedResult.lastPage(List.of("a", "b"));
        PaginatedResponse<String> response = PaginatedResponse.from(result, "/api/items", 10);
        
        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.hasPrevious()).isFalse();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.previousToken()).isNull();
        assertThat(response.nextToken()).isNull();
        assertThat(response.previousUrl()).isNull();
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
            List.of("a"), 1, true, true, "prev-token", "token", "/api/items?nextToken=prev-token", "/api/items?nextToken=token"
        );
        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).contains("\"items\":[\"a\"]");
        assertThat(json).contains("\"pageSize\":1");
        assertThat(json).contains("\"hasPrevious\":true");
        assertThat(json).contains("\"hasNext\":true");
        assertThat(json).contains("\"previousToken\":\"prev-token\"");
        assertThat(json).contains("\"nextToken\":\"token\"");
        assertThat(json).contains("\"previousUrl\":\"/api/items?nextToken=prev-token\"");
        assertThat(json).contains("\"nextUrl\":\"/api/items?nextToken=token\"");
    }
    
    @Test
    void jsonSerializationOmitsNullFields() throws Exception {
        PaginatedResponse<String> response = new PaginatedResponse<>(
            List.of("a"), 1, false, false, null, null, null, null
        );
        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).contains("\"items\":[\"a\"]");
        assertThat(json).contains("\"pageSize\":1");
        assertThat(json).contains("\"hasPrevious\":false");
        assertThat(json).contains("\"hasNext\":false");
        assertThat(json).doesNotContain("previousToken");
        assertThat(json).doesNotContain("nextToken");
        assertThat(json).doesNotContain("previousUrl");
        assertThat(json).doesNotContain("nextUrl");
    }
    
    @Test
    void jsonFieldOrderIsCorrect() throws Exception {
        PaginatedResponse<String> response = new PaginatedResponse<>(
            List.of("a"), 1, true, true, "prev-token", "token", "/api/previous", "/api/next"
        );
        String json = objectMapper.writeValueAsString(response);
        
        // Verify field order: items, pageSize, hasPrevious, hasNext, previousToken, nextToken, previousUrl, nextUrl
        int itemsIndex = json.indexOf("\"items\"");
        int pageSizeIndex = json.indexOf("\"pageSize\"");
        int hasPreviousIndex = json.indexOf("\"hasPrevious\"");
        int hasNextIndex = json.indexOf("\"hasNext\"");
        int previousTokenIndex = json.indexOf("\"previousToken\"");
        int nextTokenIndex = json.indexOf("\"nextToken\"");
        int previousUrlIndex = json.indexOf("\"previousUrl\"");
        int nextUrlIndex = json.indexOf("\"nextUrl\"");
        
        assertThat(itemsIndex).isLessThan(pageSizeIndex);
        assertThat(pageSizeIndex).isLessThan(hasPreviousIndex);
        assertThat(hasPreviousIndex).isLessThan(hasNextIndex);
        assertThat(hasNextIndex).isLessThan(previousTokenIndex);
        assertThat(previousTokenIndex).isLessThan(nextTokenIndex);
        assertThat(nextTokenIndex).isLessThan(previousUrlIndex);
        assertThat(previousUrlIndex).isLessThan(nextUrlIndex);
    }
    
    @Test
    void emptyResultProducesValidResponse() {
        PaginatedResult<String> result = PaginatedResult.empty();
        PaginatedResponse<String> response = PaginatedResponse.from(result, "/api/items", 20);
        
        assertThat(response.items()).isEmpty();
        assertThat(response.pageSize()).isZero();
        assertThat(response.hasPrevious()).isFalse();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.previousToken()).isNull();
        assertThat(response.nextToken()).isNull();
        assertThat(response.previousUrl()).isNull();
        assertThat(response.nextUrl()).isNull();
    }
}
