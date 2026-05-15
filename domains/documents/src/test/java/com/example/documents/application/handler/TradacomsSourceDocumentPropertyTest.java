package com.example.documents.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import com.example.documents.application.query.DocumentContentQueryService;
import com.example.documents.application.query.RetrievedContent;
import com.example.documents.application.tradacoms.InboundTradacomsIngestionService;
import com.example.documents.application.tradacoms.ParseStatus;
import com.example.documents.application.tradacoms.TradacomsInboundRequest;
import com.example.documents.application.tradacoms.TradacomsIngestionResult;
import com.example.documents.application.tradacoms.TradacomsMessageParser;
import com.example.documents.domain.model.Document;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentVersion;
import com.example.documents.domain.model.Format;
import com.example.documents.tradacoms.TradacomsPayloadArbitraries;
import com.example.documents.tradacoms.TradacomsPayloadArbitraries.FailingTradacomsPayload;
import com.example.documents.tradacoms.TradacomsPayloadArbitraries.SupportedTradacomsPayload;
import com.example.documents.tradacoms.TradacomsInboundFixtures;

/**
 * Property-based tests for TRADACOMS source document ingestion.
 */
class TradacomsSourceDocumentPropertyTest {

    /**
     * Feature: tradacoms-documents-ingestion, Property 1: Source payload round trip preserves bytes and format
     */
    @Property(tries = 100)
    @Label("Feature: tradacoms-documents-ingestion, Property 1: Source payload round trip preserves bytes and format")
    void sourcePayloadRoundTripPreservesBytesAndFormat(
            @ForAll("supportedTradacomsPayloads") SupportedTradacomsPayload payload) {
        IngestionOutcome outcome = ingest(payload.bytes());

        assertThat(outcome.documentSet().getAllDocuments()).hasSize(1);
        assertThat(outcome.document().versions()).hasSize(1);
        assertThat(outcome.result().sourceVersionNumber()).isEqualTo(1);
        assertThat(outcome.version().format()).isEqualTo(Format.EDI);
        assertThat(outcome.retrievedContent().bytes()).containsExactly(payload.bytes());
        assertThat(outcome.retrievedContent().format()).isEqualTo(Format.EDI);
    }

    /**
     * Feature: tradacoms-documents-ingestion, Property 2: Successful parsing records source metadata consistently
     */
    @Property(tries = 100)
    @Label("Feature: tradacoms-documents-ingestion, Property 2: Successful parsing records source metadata consistently")
    void successfulParsingRecordsSourceMetadataConsistently(
            @ForAll("supportedTradacomsPayloads") SupportedTradacomsPayload payload) {
        TradacomsMessageParser parser = new TradacomsMessageParser();
        var parseResult = parser.parse(payload.bytes());

        assertThat(parseResult.status()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(parseResult.messageType()).isEqualTo(payload.messageType());

        IngestionOutcome outcome = ingest(payload.bytes());

        assertThat(outcome.result().parseStatus()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(outcome.result().messageType()).isEqualTo(parseResult.messageType());
        assertThat(outcome.result().parseErrors()).isEmpty();
        assertThat(outcome.version().parseStatus()).isEqualTo(ParseStatus.SUCCESS.name());
        assertThat(outcome.version().messageType()).isEqualTo(parseResult.messageType());
        assertThat(outcome.version().parseErrors()).isEmpty();
    }

    /**
     * Feature: tradacoms-documents-ingestion, Property 3: Parse failures preserve source evidence and surface errors
     */
    @Property(tries = 100)
    @Label("Feature: tradacoms-documents-ingestion, Property 3: Parse failures preserve source evidence and surface errors")
    void parseFailuresPreserveSourceEvidenceAndSurfaceErrors(
            @ForAll("failingTradacomsPayloads") FailingTradacomsPayload payload) {
        IngestionOutcome outcome = ingest(payload.bytes());

        assertThat(outcome.documentSet().getAllDocuments()).hasSize(1);
        assertThat(outcome.document().derivatives()).isEmpty();
        assertThat(outcome.retrievedContent().bytes()).containsExactly(payload.bytes());
        assertThat(outcome.retrievedContent().format()).isEqualTo(Format.EDI);
        assertThat(outcome.result().parseStatus()).isEqualTo(payload.expectedStatus());
        assertThat(outcome.result().parseStatus()).isNotEqualTo(ParseStatus.SUCCESS);
        assertThat(outcome.result().parseErrors()).isNotEmpty();
        assertThat(outcome.version().parseStatus()).isEqualTo(payload.expectedStatus().name());
        assertThat(outcome.version().parseErrors()).isEqualTo(outcome.result().parseErrors());
        if (payload.expectedMessageType() != null) {
            assertThat(outcome.version().messageType()).isEqualTo(payload.expectedMessageType());
        }
    }

    @Provide
    Arbitrary<SupportedTradacomsPayload> supportedTradacomsPayloads() {
        return TradacomsPayloadArbitraries.supportedTradacomsPayloads();
    }

    @Provide
    Arbitrary<FailingTradacomsPayload> failingTradacomsPayloads() {
        return TradacomsPayloadArbitraries.failingTradacomsPayloads();
    }

    private IngestionOutcome ingest(byte[] payload) {
        InMemoryDocumentSetRepository repository = new InMemoryDocumentSetRepository();
        InMemorySchemaRepository schemaRepository = new InMemorySchemaRepository();
        InMemoryContentStore contentStore = new InMemoryContentStore();
        DocumentSetCommandHandler commandHandler = new DocumentSetCommandHandler(
                repository,
                schemaRepository,
                contentStore,
                List.of(),
                List.of());

        InboundTradacomsIngestionService service = new InboundTradacomsIngestionService(
                commandHandler,
                new TradacomsMessageParser());

        TradacomsIngestionResult result = service.ingest(new TradacomsInboundRequest(
                payload,
                "tenant-test",
                TradacomsInboundFixtures.sourceFileName(),
                "[email]",
                TradacomsInboundFixtures.interchangeMetadata()));

        DocumentSetId setId = new DocumentSetId(result.documentSetId());
        DocumentId documentId = new DocumentId(result.documentId());
        DocumentSet documentSet = repository.findById(setId).orElseThrow();
        Document document = documentSet.getDocument(documentId).orElseThrow();
        DocumentVersion version = document.getVersion(result.sourceVersionNumber()).orElseThrow();
        RetrievedContent retrievedContent = new DocumentContentQueryService(repository, contentStore)
                .getVersionContent(setId, documentId, result.sourceVersionNumber());

        return new IngestionOutcome(result, documentSet, document, version, retrievedContent);
    }
    private record IngestionOutcome(
            TradacomsIngestionResult result,
            DocumentSet documentSet,
            Document document,
            DocumentVersion version,
            RetrievedContent retrievedContent) {
    }
}
