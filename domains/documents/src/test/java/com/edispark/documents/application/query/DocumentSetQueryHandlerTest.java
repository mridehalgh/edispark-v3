package com.edispark.documents.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.edispark.documents.domain.model.DocumentSet;
import com.edispark.documents.domain.model.DocumentSetId;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.edispark.pagination.Page;
import com.edispark.pagination.PaginatedResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentSetQueryHandlerTest {

    @Mock
    private DocumentSetRepository repository;

    @Test
    void returnsEveryPageFromMostRecentlyCreatedToOldest() {
        DocumentSet oldest = documentSetAt("2026-01-01T00:00:00Z");
        DocumentSet newest = documentSetAt("2026-03-01T00:00:00Z");
        DocumentSet middle = documentSetAt("2026-02-01T00:00:00Z");
        Page page = Page.first(3);
        when(repository.findAll(page)).thenReturn(PaginatedResult.of(List.of(oldest, newest, middle), "next"));

        PaginatedResult<DocumentSet> result = new DocumentSetQueryHandler(repository)
                .handle(new ListDocumentSetsQuery(page));

        assertThat(result.items()).containsExactly(newest, middle, oldest);
        assertThat(result.continuationToken()).contains("next");
    }

    private DocumentSet documentSetAt(String createdAt) {
        return DocumentSet.reconstitute(
                DocumentSetId.generate(),
                Map.of(),
                Instant.parse(createdAt),
                "[name]",
                Map.of());
    }
}
