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
        assertThatThrownBy(() -> new PaginatedResponse<String>(null, "token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }
    
    @Test
    void paginatedResponseAcceptsNullToken() {
        PaginatedResponse<String> response = new PaginatedResponse<>(List.of("a"), null);
        assertThat(response.items()).containsExactly("a");
        assertThat(response.nextToken()).isNull();
    }
    
    @Test
    void fromConvertsResultWithToken() {
        PaginatedResult<String> result = PaginatedResult.of(List.of("a", "b"), "token123");
        PaginatedResponse<String> response = PaginatedResponse.from(result);
        
        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.nextToken()).isEqualTo("token123");
    }
    
    @Test
    void fromConvertsResultWithoutToken() {
        PaginatedResult<String> result = PaginatedResult.lastPage(List.of("a", "b"));
        PaginatedResponse<String> response = PaginatedResponse.from(result);
        
        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.nextToken()).isNull();
    }
    
    @Test
    void jsonSerializationIncludesNextToken() throws Exception {
        PaginatedResponse<String> response = new PaginatedResponse<>(List.of("a"), "token");
        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).contains("\"nextToken\":\"token\"");
        assertThat(json).contains("\"items\":[\"a\"]");
    }
    
    @Test
    void jsonSerializationOmitsNullNextToken() throws Exception {
        PaginatedResponse<String> response = new PaginatedResponse<>(List.of("a"), null);
        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).doesNotContain("nextToken");
        assertThat(json).contains("\"items\":[\"a\"]");
    }
}
