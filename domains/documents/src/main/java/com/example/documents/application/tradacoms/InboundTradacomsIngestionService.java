package com.example.documents.application.tradacoms;

import java.util.HashMap;
import java.util.Map;

import com.example.documents.application.command.StoreSourceDocumentCommand;
import com.example.documents.application.handler.DocumentSetCommandHandler;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.Format;

public class InboundTradacomsIngestionService {

    private final DocumentSetCommandHandler commandHandler;
    private final TradacomsMessageParser parser;

    public InboundTradacomsIngestionService(DocumentSetCommandHandler commandHandler, TradacomsMessageParser parser) {
        this.commandHandler = commandHandler;
        this.parser = parser;
    }

    public TradacomsIngestionResult ingest(TradacomsInboundRequest request) {
        TradacomsParseResult parseResult = parser.parse(request.payload());
        Content sourceContent = Content.of(request.payload(), Format.EDI);
        DocumentSet documentSet = commandHandler.handle(new StoreSourceDocumentCommand(
                documentTypeFor(parseResult.messageType()),
                sourceContent,
                request.receivedBy(),
                metadataFor(request, parseResult),
                parseResult.status().name(),
                parseResult.messageType(),
                parseResult.errors()));

        var document = documentSet.getAllDocuments().getFirst();
        return new TradacomsIngestionResult(
                documentSet.id().value(),
                document.id().value(),
                document.getCurrentVersion().versionNumber(),
                parseResult.status(),
                parseResult.messageType(),
                parseResult.errors());
    }

    private Map<String, String> metadataFor(TradacomsInboundRequest request, TradacomsParseResult parseResult) {
        Map<String, String> metadata = new HashMap<>(request.interchangeMetadata());
        metadata.put("tenantId", request.tenantId());
        metadata.put("sourceFileName", request.sourceFileName());
        if (parseResult.businessDocumentNumber() != null) {
            metadata.put("businessDocumentNumber", parseResult.businessDocumentNumber());
        }
        return metadata;
    }

    private DocumentType documentTypeFor(String messageType) {
        if ("CREDIT".equals(messageType)) {
            return DocumentType.CREDIT_NOTE;
        }
        if ("ORDERS".equals(messageType)) {
            return DocumentType.ORDER;
        }
        return DocumentType.APPLICATION_RESPONSE;
    }
}
