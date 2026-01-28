package com.example.documents.application.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ListDocumentSetsQueryTest {
    
    @Test
    void queryValidatesLimitRange() {
        assertThatThrownBy(() -> ListDocumentSetsQuery.of(0, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 100");
        
        assertThatThrownBy(() -> ListDocumentSetsQuery.of(101, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 1 and 100");
    }
    
    @Test
    void queryAcceptsValidLimit() {
        var query = ListDocumentSetsQuery.of(50, null);
        assertThat(query.page().limit()).isEqualTo(50);
    }
    
    @Test
    void queryUsesDefaultLimit() {
        var query = ListDocumentSetsQuery.of(null, null);
        assertThat(query.page().limit()).isEqualTo(20);
    }
    
    @Test
    void queryCreatesCorrectPageObject() {
        var query = ListDocumentSetsQuery.of(50, "token123");
        assertThat(query.page().limit()).isEqualTo(50);
        assertThat(query.page().continuationToken()).hasValue("token123");
    }
    
    @Test
    void queryCreatesFirstPageWhenNoToken() {
        var query = ListDocumentSetsQuery.of(30, null);
        assertThat(query.page().limit()).isEqualTo(30);
        assertThat(query.page().isFirstPage()).isTrue();
    }
}
