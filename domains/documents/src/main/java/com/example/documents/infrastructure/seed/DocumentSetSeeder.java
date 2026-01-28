package com.example.documents.infrastructure.seed;

import com.example.common.pagination.Page;
import com.example.common.pagination.PaginatedResult;
import com.example.documents.application.command.AddSchemaVersionCommand;
import com.example.documents.application.command.CreateDocumentSetCommand;
import com.example.documents.application.command.CreateSchemaCommand;
import com.example.documents.application.handler.DocumentSetCommandHandler;
import com.example.documents.application.handler.SchemaCommandHandler;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaFormat;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.DocumentSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Seeds sample schemas and document sets for local development.
 * 
 * <p>Runs after {@link com.example.documents.infrastructure.config.LocalDynamoDbInitializer}
 * to ensure the table exists before seeding data.</p>
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class DocumentSetSeeder {
    
    private static final VersionIdentifier INITIAL_VERSION = VersionIdentifier.of("1.0.0");
    
    private final DocumentSetCommandHandler documentSetCommandHandler;
    private final SchemaCommandHandler schemaCommandHandler;
    private final DocumentSetRepository documentSetRepository;
    private final Random random = new Random();
    
    private final Map<DocumentType, SchemaVersionRef> schemaRefs = new EnumMap<>(DocumentType.class);

    @EventListener(ApplicationReadyEvent.class)
    @Order(10) // Run after LocalDynamoDbInitializer (HIGHEST_PRECEDENCE)
    public void seed() {
        if (dataAlreadyExists()) {
            log.info("Data already exists, skipping seeding");
            return;
        }
        
        log.info("Starting data seeding...");
        
        createSchemas();
        createDocumentSets();
        
        log.info("Data seeding completed");
    }
    
    private boolean dataAlreadyExists() {
        PaginatedResult<DocumentSet> existing = documentSetRepository.findAll(Page.first(1));
        return !existing.isEmpty();
    }
    
    private void createSchemas() {
        log.info("Creating sample schemas...");
        
        for (DocumentType type : DocumentType.values()) {
            SchemaVersionRef ref = createSchemaForType(type);
            schemaRefs.put(type, ref);
        }
        
        log.info("Created {} schemas", schemaRefs.size());
    }
    
    private SchemaVersionRef createSchemaForType(DocumentType type) {
        // Create the schema
        CreateSchemaCommand createCommand = CreateSchemaCommand.of(
            type.name() + " Schema",
            SchemaFormat.JSON_SCHEMA
        );
        Schema schema = schemaCommandHandler.handle(createCommand);
        
        // Add initial version with content
        String schemaContent = generateSchemaContent(type);
        Content content = Content.of(
            schemaContent.getBytes(StandardCharsets.UTF_8),
            Format.JSON
        );
        
        AddSchemaVersionCommand versionCommand = new AddSchemaVersionCommand(
            schema.id(),
            INITIAL_VERSION,
            content
        );
        schemaCommandHandler.handle(versionCommand);
        
        log.debug("Created schema: {} ({})", schema.name(), schema.id().value());
        
        return SchemaVersionRef.of(schema.id(), INITIAL_VERSION);
    }
    
    private String generateSchemaContent(DocumentType type) {
        return String.format("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "title": "%s",
              "type": "object",
              "properties": {
                "documentType": { "type": "string", "const": "%s" },
                "documentNumber": { "type": "string" },
                "issueDate": { "type": "string", "format": "date-time" }
              },
              "required": ["documentType", "documentNumber"]
            }
            """, type.name(), type.name());
    }
    
    private void createDocumentSets() {
        log.info("Creating sample document sets...");
        
        int invoiceCount = 20;
        int orderCount = 15;
        int quotationCount = 10;
        int creditNoteCount = 5;
        
        seedDocumentSets(DocumentType.INVOICE, invoiceCount, "Invoice Set");
        seedDocumentSets(DocumentType.ORDER, orderCount, "Order Set");
        seedDocumentSets(DocumentType.QUOTATION, quotationCount, "Quotation Set");
        seedDocumentSets(DocumentType.CREDIT_NOTE, creditNoteCount, "Credit Note Set");
        
        int total = invoiceCount + orderCount + quotationCount + creditNoteCount;
        log.info("Created {} document sets", total);
    }
    
    private void seedDocumentSets(DocumentType type, int count, String namePrefix) {
        SchemaVersionRef schemaRef = schemaRefs.get(type);
        
        for (int i = 1; i <= count; i++) {
            try {
                createSampleDocumentSet(type, schemaRef, namePrefix, i);
            } catch (Exception e) {
                log.error("Failed to seed {} {}: {}", namePrefix, i, e.getMessage());
            }
        }
    }
    
    private void createSampleDocumentSet(DocumentType type, SchemaVersionRef schemaRef, 
                                          String namePrefix, int number) {
        String sampleContent = generateSampleContent(type, number);
        Content content = Content.of(
            sampleContent.getBytes(StandardCharsets.UTF_8),
            Format.JSON
        );
        
        Map<String, String> metadata = Map.of(
            "name", String.format("%s %03d", namePrefix, number),
            "description", generateDescription(type, number),
            "category", type.name(),
            "fiscalYear", "2024"
        );
        
        CreateDocumentSetCommand command = new CreateDocumentSetCommand(
            type,
            schemaRef,
            content,
            "seeder",
            metadata
        );
        
        documentSetCommandHandler.handle(command);
        log.debug("Created {} {}", namePrefix, number);
    }
    
    private String generateSampleContent(DocumentType type, int number) {
        return String.format("""
            {
              "documentType": "%s",
              "documentNumber": "%s-%03d",
              "issueDate": "%s",
              "description": "Sample %s document for testing"
            }
            """,
            type.name(),
            type.name().substring(0, 3),
            number,
            Instant.now().minus(random.nextInt(90), ChronoUnit.DAYS),
            type.name().toLowerCase()
        );
    }
    
    private String generateDescription(DocumentType type, int number) {
        String[] templates = {
            "Q4 2024 %s for customer ABC-%03d",
            "Annual %s processing batch %03d",
            "Standard %s document %03d",
            "Automated %s generation %03d"
        };
        String template = templates[random.nextInt(templates.length)];
        return String.format(template, type.name().toLowerCase(), number);
    }
}
