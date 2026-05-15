package com.example.documents.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.documents.api.dto.DocumentResponse;
import com.example.documents.application.handler.DocumentSetCommandHandler;
import com.example.documents.application.query.DocumentContentQueryService;
import com.example.documents.application.query.RetrievedContent;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentVersion;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.DocumentSetRepository;
import com.example.documents.tradacoms.TradacomsInboundFixtures;

@ExtendWith(MockitoExtension.class)
@DisplayName("TRADACOMS source document API")
class TradacomsDocumentSourceApiTest {

    @Mock
    private DocumentSetCommandHandler commandHandler;

    @Mock
    private DocumentSetRepository repository;

    @Mock
    private com.example.documents.application.query.DocumentSetQueryHandler queryHandler;

    @Mock
    private DocumentContentQueryService documentContentQueryService;

    private DocumentSetController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        controller = new DocumentSetController(commandHandler, repository, queryHandler, documentContentQueryService);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("returns current source version metadata with EDI format and parse outcome")
    void returnsCurrentSourceVersionMetadataWithEdiFormatAndParseOutcome() {
        DocumentSet documentSet = createTradacomsDocumentSet();
        when(repository.findById(documentSet.id())).thenReturn(Optional.of(documentSet));

        ResponseEntity<DocumentResponse> response = controller.getDocument(
                documentSet.id().value(),
                documentSet.getAllDocuments().getFirst().id().value());

        var json = objectMapper.valueToTree(response.getBody());
        var currentVersion = json.get("currentVersion");

        assertThat(currentVersion.has("format")).isTrue();
        assertThat(currentVersion.get("format").asText()).isEqualTo("EDI");
        assertThat(currentVersion.has("parseStatus")).isTrue();
        assertThat(currentVersion.get("parseStatus").asText()).isEqualTo("SUCCESS");
        assertThat(currentVersion.has("messageType")).isTrue();
        assertThat(currentVersion.get("messageType").asText()).isEqualTo("CREDIT");
    }

    @Test
    @DisplayName("exposes a source-content download route that returns raw EDI bytes")
    void exposesSourceContentDownloadRoute() {
        Optional<Method> contentMethod = List.of(DocumentSetController.class.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .filter(method -> List.of(method.getAnnotation(GetMapping.class).value())
                        .contains("/{setId}/documents/{docId}/versions/{versionNumber}/content"))
                .findFirst();

        assertThat(contentMethod).isPresent();
        assertThat(contentMethod.orElseThrow().getReturnType()).isEqualTo(ResponseEntity.class);
        assertThat(((ParameterizedType) contentMethod.orElseThrow().getGenericReturnType())
                .getActualTypeArguments()[0].getTypeName()).isEqualTo("byte[]");
    }

    @Test
    @DisplayName("returns raw EDI bytes with traceability headers")
    void returnsRawEdiBytesWithTraceabilityHeaders() {
        DocumentSet documentSet = createTradacomsDocumentSet();
        DocumentVersion version = documentSet.getAllDocuments().getFirst().getCurrentVersion();
        byte[] payload = TradacomsInboundFixtures.validSupportedPayload();
        when(documentContentQueryService.getVersionContent(documentSet.id(), documentSet.getAllDocuments().getFirst().id(), 1))
                .thenReturn(new RetrievedContent(
                        payload,
                        Format.EDI,
                        version.contentHash().toFullString(),
                        "application/edi",
                        TradacomsInboundFixtures.sourceFileName()));

        ResponseEntity<byte[]> response = controller.getVersionContent(
                documentSet.id().value(),
                documentSet.getAllDocuments().getFirst().id().value(),
                1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(payload);
        assertThat(response.getHeaders().getContentType()).hasToString("application/edi");
        assertThat(response.getHeaders().getFirst("X-Document-Format")).isEqualTo("EDI");
        assertThat(response.getHeaders().getFirst("X-Content-Hash")).isEqualTo(version.contentHash().toFullString());
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains(TradacomsInboundFixtures.sourceFileName());
    }

    @Test
    @DisplayName("encodes content-disposition filenames safely")
    void encodesContentDispositionFilenamesSafely() {
        DocumentSet documentSet = createTradacomsDocumentSet();
        DocumentVersion version = documentSet.getAllDocuments().getFirst().getCurrentVersion();
        byte[] payload = TradacomsInboundFixtures.validSupportedPayload();
        when(documentContentQueryService.getVersionContent(documentSet.id(), documentSet.getAllDocuments().getFirst().id(), 1))
                .thenReturn(new RetrievedContent(
                        payload,
                        Format.EDI,
                        version.contentHash().toFullString(),
                        "application/edi",
                        "credit\"note\r\n.edi"));

        ResponseEntity<byte[]> response = controller.getVersionContent(
                documentSet.id().value(),
                documentSet.getAllDocuments().getFirst().id().value(),
                1);

        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(contentDisposition).isNotNull();
        assertThat(contentDisposition).doesNotContain("\r").doesNotContain("\n");
    }

    private DocumentSet createTradacomsDocumentSet() {
        Content content = Content.of(TradacomsInboundFixtures.validSupportedPayload(), Format.EDI);
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        return DocumentSet.createWithDocument(
                DocumentType.CREDIT_NOTE,
                schemaRef,
                ContentRef.of(content.hash()),
                content.hash(),
                "[email]",
                java.util.Map.of("source", "tradacoms", "sourceFileName", TradacomsInboundFixtures.sourceFileName()),
                Format.EDI,
                "SUCCESS",
                "CREDIT",
                List.of());
    }
}
