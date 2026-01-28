package com.example.common.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class PaginatedResultTest {
    
    @Test
    void paginatedResultRejectsNullItems() {
        assertThatThrownBy(() -> new PaginatedResult<String>(null, Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }
    
    @Test
    void paginatedResultAcceptsEmptyList() {
        PaginatedResult<String> result = new PaginatedResult<>(List.of(), Optional.empty());
        assertThat(result.items()).isEmpty();
    }
    
    @Test
    void hasMoreReturnsTrueWhenTokenPresent() {
        PaginatedResult<String> result = PaginatedResult.of(List.of("a", "b"), "token");
        assertThat(result.hasMore()).isTrue();
    }
    
    @Test
    void hasMoreReturnsFalseWhenNoToken() {
        PaginatedResult<String> result = PaginatedResult.lastPage(List.of("a", "b"));
        assertThat(result.hasMore()).isFalse();
    }
    
    @Test
    void sizeReturnsItemCount() {
        PaginatedResult<String> result = PaginatedResult.of(List.of("a", "b", "c"), null);
        assertThat(result.size()).isEqualTo(3);
    }
    
    @Test
    void isEmptyReturnsTrueForEmptyList() {
        PaginatedResult<String> result = PaginatedResult.empty();
        assertThat(result.isEmpty()).isTrue();
    }
    
    @Test
    void isEmptyReturnsFalseForNonEmptyList() {
        PaginatedResult<String> result = PaginatedResult.of(List.of("a"), null);
        assertThat(result.isEmpty()).isFalse();
    }
    
    @Test
    void mapTransformsItemsPreservingToken() {
        PaginatedResult<Integer> numbers = PaginatedResult.of(List.of(1, 2, 3), "token");
        PaginatedResult<String> strings = numbers.map(Object::toString);
        
        assertThat(strings.items()).containsExactly("1", "2", "3");
        assertThat(strings.continuationToken()).hasValue("token");
    }
    
    @Test
    void mapPreservesEmptyToken() {
        PaginatedResult<Integer> numbers = PaginatedResult.lastPage(List.of(1, 2));
        PaginatedResult<String> strings = numbers.map(Object::toString);
        
        assertThat(strings.items()).containsExactly("1", "2");
        assertThat(strings.continuationToken()).isEmpty();
    }
    
    @Test
    void ofFactoryMethodWithNullToken() {
        PaginatedResult<String> result = PaginatedResult.of(List.of("a"), null);
        assertThat(result.items()).containsExactly("a");
        assertThat(result.continuationToken()).isEmpty();
    }
    
    @Test
    void ofFactoryMethodWithToken() {
        PaginatedResult<String> result = PaginatedResult.of(List.of("a"), "token");
        assertThat(result.items()).containsExactly("a");
        assertThat(result.continuationToken()).hasValue("token");
    }
    
    @Test
    void lastPageFactoryMethod() {
        PaginatedResult<String> result = PaginatedResult.lastPage(List.of("a", "b"));
        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.hasMore()).isFalse();
    }
    
    @Test
    void emptyFactoryMethod() {
        PaginatedResult<String> result = PaginatedResult.empty();
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.hasMore()).isFalse();
    }
}
