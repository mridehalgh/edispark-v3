package com.example.common.pagination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PageTest {
    
    @Test
    void pageValidatesPositiveLimit() {
        assertThatThrownBy(() -> new Page(0, java.util.Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
        
        assertThatThrownBy(() -> new Page(-1, java.util.Optional.empty()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
    }
    
    @Test
    void pageAcceptsPositiveLimit() {
        Page page = new Page(10, java.util.Optional.empty());
        assertThat(page.limit()).isEqualTo(10);
    }
    
    @Test
    void firstPageWithDefaultLimit() {
        Page page = Page.first();
        assertThat(page.limit()).isEqualTo(20);
        assertThat(page.isFirstPage()).isTrue();
        assertThat(page.continuationToken()).isEmpty();
    }
    
    @Test
    void firstPageWithCustomLimit() {
        Page page = Page.first(50);
        assertThat(page.limit()).isEqualTo(50);
        assertThat(page.isFirstPage()).isTrue();
        assertThat(page.continuationToken()).isEmpty();
    }
    
    @Test
    void nextPageWithToken() {
        Page page = Page.next(10, "token123");
        assertThat(page.limit()).isEqualTo(10);
        assertThat(page.isFirstPage()).isFalse();
        assertThat(page.continuationToken()).hasValue("token123");
    }
    
    @Test
    void isFirstPageReturnsTrueWhenNoToken() {
        Page page = Page.first(10);
        assertThat(page.isFirstPage()).isTrue();
    }
    
    @Test
    void isFirstPageReturnsFalseWhenTokenPresent() {
        Page page = Page.next(10, "token");
        assertThat(page.isFirstPage()).isFalse();
    }
}
