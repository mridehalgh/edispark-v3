package com.edispark.documents.infrastructure.seed;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.documents.application.command.AddSchemaVersionCommand;
import com.edispark.documents.application.command.CreateDocumentSetCommand;
import com.edispark.documents.application.command.CreateSchemaCommand;
import com.edispark.documents.application.command.StoreSourceDocumentCommand;
import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.application.handler.SchemaCommandHandler;
import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.ContentRef;
import com.edispark.documents.domain.model.DocumentSet;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.model.Schema;
import com.edispark.documents.domain.model.SchemaFormat;
import com.edispark.documents.domain.model.SchemaVersionRef;
import com.edispark.documents.domain.model.TransformationMethod;
import com.edispark.documents.domain.model.VersionIdentifier;
import com.edispark.documents.domain.repository.ContentStore;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds a small, realistic UBL 2.1 document catalogue for local development.
 *
 * <p>Runs after {@link com.edispark.documents.infrastructure.config.LocalDynamoDbInitializer}
 * to ensure the table exists before seeding data.</p>
 */
@Component
@Profile("local & !openapi-export")
@RequiredArgsConstructor
@Slf4j
public class DocumentSetSeeder {

    private static final String CREATED_BY = "local-seeder";
    private static final String RESOURCE_ROOT = "/seed/documents/";
    private static final VersionIdentifier INITIAL_VERSION = VersionIdentifier.of("2.1");
    private static final List<DocumentType> SEEDED_TYPES = List.of(
            DocumentType.ORDER,
            DocumentType.INVOICE,
            DocumentType.CREDIT_NOTE);

    private final DocumentSetCommandHandler documentSetCommandHandler;
    private final SchemaCommandHandler schemaCommandHandler;
    private final DocumentSetRepository documentSetRepository;
    private final ContentStore contentStore;

    @Value("${documents.seed.tenant-id}")
    private String localTenantId;

    private final Map<DocumentType, SchemaVersionRef> schemaRefs = new EnumMap<>(DocumentType.class);

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void seed() {
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(localTenantId)), this::seedTenantData);
    }

    void seedTenantData() {
        Set<DocumentType> missingTypes = missingSeedTypes();
        if (missingTypes.isEmpty()) {
            log.info("Document demo catalogue is complete, skipping seeding");
            return;
        }

        log.info("Repairing document demo catalogue; missing types: {}", missingTypes);
        createSchemas(missingTypes);
        createDocumentSets(missingTypes);
        log.info("Document demo catalogue is complete");
    }

    private Set<DocumentType> missingSeedTypes() {
        Set<String> existingNumbers = documentSetRepository.findAll().stream()
                .map(DocumentSet::metadata)
                .map(metadata -> metadata.get("businessDocumentNumber"))
                .collect(java.util.stream.Collectors.toSet());
        Set<DocumentType> missingTypes = EnumSet.noneOf(DocumentType.class);
        for (DocumentType type : SEEDED_TYPES) {
            if (!existingNumbers.contains(businessDocumentNumber(type))) {
                missingTypes.add(type);
            }
        }
        return missingTypes;
    }

    private void createSchemas(Set<DocumentType> missingTypes) {
        schemaRefs.clear();
        for (DocumentType type : missingTypes) {
            schemaRefs.put(type, createSchemaForType(type));
        }
        log.info("Created {} missing UBL 2.1 schemas", schemaRefs.size());
    }

    private SchemaVersionRef createSchemaForType(DocumentType type) {
        CreateSchemaCommand createCommand = CreateSchemaCommand.of(
                "OASIS UBL 2.1 " + ublRootName(type),
                SchemaFormat.JSON_SCHEMA);
        Schema schema = schemaCommandHandler.handle(createCommand);

        Content content = Content.of(
                generateSchemaContent(type).getBytes(StandardCharsets.UTF_8),
                Format.JSON);
        schemaCommandHandler.handle(new AddSchemaVersionCommand(schema.id(), INITIAL_VERSION, content));

        return SchemaVersionRef.of(schema.id(), INITIAL_VERSION);
    }

    private void createDocumentSets(Set<DocumentType> missingTypes) {
        if (missingTypes.contains(DocumentType.ORDER)) {
            createInboundTradacomsOrder();
        }
        if (missingTypes.contains(DocumentType.INVOICE)) {
            createUblDocumentSet(
                    DocumentType.INVOICE,
                    "ubl-invoice-100045.json",
                    "Invoice INV-100045",
                    "UBL 2.1 invoice awaiting payment",
                    "INV-100045");
        }
        if (missingTypes.contains(DocumentType.CREDIT_NOTE)) {
            createUblDocumentSet(
                    DocumentType.CREDIT_NOTE,
                    "ubl-credit-note-1007.json",
                    "Credit note CN-1007",
                    "UBL 2.1 credit note against invoice INV-100045",
                    "CN-1007");
        }

        log.info("Created {} missing document sets", missingTypes.size());
    }

    private String businessDocumentNumber(DocumentType type) {
        return switch (type) {
            case ORDER -> "PO-2026-0042";
            case INVOICE -> "INV-100045";
            case CREDIT_NOTE -> "CN-1007";
            default -> throw new IllegalArgumentException("No demo document number for " + type);
        };
    }

    private void createInboundTradacomsOrder() {
        Map<String, String> metadata = Map.of(
                "name", "Inbound order PO-2026-0042",
                "description", "TRADACOMS order received and parsed into UBL 2.1 JSON",
                "direction", "INBOUND",
                "sourceStandard", "TRADACOMS",
                "sourceFileName", "supplier-po-2026-0042.edi",
                "businessDocumentNumber", "PO-2026-0042",
                "senderId", "5012345678901",
                "receiverId", "5098765432109");

        DocumentSet documentSet = documentSetCommandHandler.handle(new StoreSourceDocumentCommand(
                DocumentType.ORDER,
                contentFromResource("tradacoms-order-po-2026-0042.edi", Format.EDI),
                CREATED_BY,
                metadata,
                "SUCCESS",
                "ORDERS",
                List.of()));
        var sourceDocument = documentSet.getAllDocuments().getFirst();
        var sourceVersion = sourceDocument.getCurrentVersion();
        Content ublOrder = contentFromResource("ubl-order-po-2026-0042.json", Format.JSON);
        contentStore.store(ublOrder);
        documentSet.createDerivative(
                sourceDocument.id(),
                sourceVersion.id(),
                Format.JSON,
                ContentRef.of(ublOrder.hash()),
                ublOrder.hash(),
                TransformationMethod.PROGRAMMATIC);
        documentSetRepository.save(documentSet);
    }

    private void createUblDocumentSet(
            DocumentType type,
            String resourceName,
            String name,
            String description,
            String businessDocumentNumber) {
        Map<String, String> metadata = Map.of(
                "name", name,
                "description", description,
                "direction", "INBOUND",
                "standard", "UBL",
                "ublVersion", "2.1",
                "businessDocumentNumber", businessDocumentNumber);

        documentSetCommandHandler.handle(new CreateDocumentSetCommand(
                type,
                schemaRefs.get(type),
                contentFromResource(resourceName, Format.JSON),
                CREATED_BY,
                metadata));
    }

    private Content contentFromResource(String resourceName, Format format) {
        String resourcePath = RESOURCE_ROOT + resourceName;
        try (InputStream input = DocumentSetSeeder.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Seed resource not found: " + resourcePath);
            }
            return Content.of(input.readAllBytes(), format);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read seed resource: " + resourcePath, exception);
        }
    }

    private String generateSchemaContent(DocumentType type) {
        String rootName = ublRootName(type);
        String requiredFields = switch (type) {
            case ORDER -> "\"ID\", \"IssueDate\", \"BuyerCustomerParty\", \"SellerSupplierParty\", \"OrderLine\"";
            case INVOICE -> "\"ID\", \"IssueDate\", \"AccountingSupplierParty\", "
                    + "\"AccountingCustomerParty\", \"LegalMonetaryTotal\", \"InvoiceLine\"";
            case CREDIT_NOTE -> "\"ID\", \"IssueDate\", \"AccountingSupplierParty\", "
                    + "\"AccountingCustomerParty\", \"LegalMonetaryTotal\", \"CreditNoteLine\"";
            default -> throw new IllegalArgumentException("No UBL seed schema for " + type);
        };

        return """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$id": "urn:oasis:names:specification:ubl:schema:json:%1$s-2.1",
                  "title": "OASIS UBL 2.1 %1$s",
                  "type": "object",
                  "required": ["%1$s"],
                  "properties": {
                    "_D": { "type": "string" },
                    "_A": { "const": "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" },
                    "_B": { "const": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2" },
                    "%1$s": {
                      "type": "array",
                      "minItems": 1,
                      "maxItems": 1,
                      "items": {
                        "type": "object",
                        "required": [%2$s]
                      }
                    }
                  },
                  "additionalProperties": false
                }
                """.formatted(rootName, requiredFields);
    }

    private String ublRootName(DocumentType type) {
        return switch (type) {
            case ORDER -> "Order";
            case INVOICE -> "Invoice";
            case CREDIT_NOTE -> "CreditNote";
            default -> throw new IllegalArgumentException("No UBL root for " + type);
        };
    }
}
