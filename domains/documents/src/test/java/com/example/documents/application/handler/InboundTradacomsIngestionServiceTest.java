package com.example.documents.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.documents.tradacoms.TradacomsInboundFixtures;

@DisplayName("Inbound TRADACOMS ingestion")
class InboundTradacomsIngestionServiceTest {

    @Test
    @DisplayName("stores a supported CREDIT payload as an EDI source document with successful parse metadata")
    void storesSupportedCreditPayloadAsEdiSourceDocument() throws Exception {
        IngestionInvocation invocation = invokeIngestion(TradacomsInboundFixtures.validSupportedPayload());

        assertThat(access(invocation.result(), "documentSetId")).isNotNull();
        assertThat(access(invocation.result(), "documentId")).isNotNull();
        assertThat(access(invocation.result(), "sourceVersionNumber")).isEqualTo(1);
        assertThat(String.valueOf(access(invocation.result(), "parseStatus"))).isEqualTo("SUCCESS");
        assertThat(access(invocation.result(), "messageType")).isEqualTo("CREDIT");
        assertThat((List<?>) access(invocation.result(), "parseErrors")).isEmpty();

        assertThat(invocation.repository().findAll()).hasSize(1);
        assertThat(invocation.contentStore().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("preserves invalid source content and records INVALID_SYNTAX parse metadata")
    void preservesInvalidSourceContentWithParseFailure() throws Exception {
        IngestionInvocation invocation = invokeIngestion(TradacomsInboundFixtures.invalidSourcePayload());

        assertThat(String.valueOf(access(invocation.result(), "parseStatus"))).isEqualTo("INVALID_SYNTAX");
        assertThat((List<?>) access(invocation.result(), "parseErrors")).isNotEmpty();
        assertThat(invocation.repository().findAll()).hasSize(1);
        assertThat(invocation.contentStore().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("preserves unsupported ORDERS source content and records UNSUPPORTED_MESSAGE metadata")
    void preservesUnsupportedOrdersSourceContent() throws Exception {
        IngestionInvocation invocation = invokeIngestion(TradacomsInboundFixtures.unsupportedSourcePayload());

        assertThat(String.valueOf(access(invocation.result(), "parseStatus"))).isEqualTo("UNSUPPORTED_MESSAGE");
        assertThat(access(invocation.result(), "messageType")).isEqualTo("ORDERS");
        assertThat((List<?>) access(invocation.result(), "parseErrors")).isNotEmpty();
        assertThat(invocation.repository().findAll()).hasSize(1);
        assertThat(invocation.contentStore().size()).isEqualTo(1);
    }

    private IngestionInvocation invokeIngestion(byte[] payload) throws Exception {
        InMemoryDocumentSetRepository repository = new InMemoryDocumentSetRepository();
        InMemorySchemaRepository schemaRepository = new InMemorySchemaRepository();
        InMemoryContentStore contentStore = new InMemoryContentStore();
        DocumentSetCommandHandler commandHandler = new DocumentSetCommandHandler(
                repository,
                schemaRepository,
                contentStore,
                List.of(),
                List.of());

        Class<?> parserClass = Class.forName("com.example.documents.application.tradacoms.TradacomsMessageParser");
        Object parser = parserClass.getConstructor().newInstance();

        Class<?> serviceClass = Class.forName("com.example.documents.application.tradacoms.InboundTradacomsIngestionService");
        Object service = serviceClass.getConstructor(DocumentSetCommandHandler.class, parserClass)
                .newInstance(commandHandler, parser);

        Class<?> requestClass = Class.forName("com.example.documents.application.tradacoms.TradacomsInboundRequest");
        Object request = requestClass
                .getConstructor(byte[].class, String.class, String.class, String.class, Map.class)
                .newInstance(
                        payload,
                        "tenant-test",
                        TradacomsInboundFixtures.sourceFileName(),
                        "[email]",
                        TradacomsInboundFixtures.interchangeMetadata());

        Method ingest = serviceClass.getMethod("ingest", requestClass);
        Object result = ingest.invoke(service, request);
        return new IngestionInvocation(result, repository, contentStore);
    }

    private Object access(Object target, String accessorName) throws Exception {
        return target.getClass().getMethod(accessorName).invoke(target);
    }

    private record IngestionInvocation(
            Object result,
            InMemoryDocumentSetRepository repository,
            InMemoryContentStore contentStore) {
    }
}
