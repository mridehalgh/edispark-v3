package com.example.documents.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.common.pagination.Page;
import com.example.common.pagination.PaginatedResult;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.ContentStore;
import com.example.documents.domain.repository.DocumentSetRepository;
import com.example.documents.tradacoms.TradacomsInboundFixtures;

@DisplayName("Document content query service")
class DocumentContentQueryServiceTest {

    @Test
    @DisplayName("returns stored source bytes with EDI metadata")
    void returnsStoredSourceBytesWithEdiMetadata() {
        InMemoryDocumentSetRepository repository = new InMemoryDocumentSetRepository();
        InMemoryContentStore contentStore = new InMemoryContentStore();
        DocumentContentQueryService service = new DocumentContentQueryService(repository, contentStore);

        byte[] payload = TradacomsInboundFixtures.validSupportedPayload();
        Content content = Content.of(payload, Format.EDI);
        contentStore.store(content);

        DocumentSet documentSet = DocumentSet.createWithDocument(
                DocumentType.CREDIT_NOTE,
                SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("source")),
                ContentRef.of(content.hash()),
                content.hash(),
                "[email]",
                Map.of("sourceFileName", TradacomsInboundFixtures.sourceFileName()),
                Format.EDI,
                "SUCCESS",
                "CREDIT",
                java.util.List.of());
        repository.save(documentSet);

        var document = documentSet.getAllDocuments().getFirst();
        RetrievedContent retrieved = service.getVersionContent(documentSet.id(), document.id(), 1);

        assertThat(retrieved.bytes()).isEqualTo(payload);
        assertThat(retrieved.format()).isEqualTo(Format.EDI);
        assertThat(retrieved.contentType()).isEqualTo("application/edi");
        assertThat(retrieved.fileName()).isEqualTo(TradacomsInboundFixtures.sourceFileName());
    }

    private static final class InMemoryDocumentSetRepository implements DocumentSetRepository {

        private final Map<DocumentSetId, DocumentSet> store = new HashMap<>();

        @Override
        public Optional<DocumentSet> findById(DocumentSetId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public java.util.List<DocumentSet> findAll() {
            return java.util.List.copyOf(store.values());
        }

        @Override
        public PaginatedResult<DocumentSet> findAll(Page page) {
            return PaginatedResult.of(findAll(), null);
        }

        @Override
        public void save(DocumentSet documentSet) {
            store.put(documentSet.id(), documentSet);
        }

        @Override
        public void delete(DocumentSetId id) {
            store.remove(id);
        }

        @Override
        public java.util.List<DocumentSet> findByContentHash(ContentHash contentHash) {
            return java.util.List.of();
        }
    }

    private static final class InMemoryContentStore implements ContentStore {

        private final Map<ContentHash, byte[]> store = new HashMap<>();

        @Override
        public void store(Content content) {
            store.put(content.hash(), content.data());
        }

        @Override
        public Optional<byte[]> retrieve(ContentHash hash) {
            return Optional.ofNullable(store.get(hash));
        }

        @Override
        public boolean exists(ContentHash hash) {
            return store.containsKey(hash);
        }

        @Override
        public void delete(ContentHash hash) {
            store.remove(hash);
        }
    }
}
