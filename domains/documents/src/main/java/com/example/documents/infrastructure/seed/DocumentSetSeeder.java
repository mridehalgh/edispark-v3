package com.example.documents.infrastructure.seed;

import com.example.common.pagination.Page;
import com.example.common.pagination.PaginatedResult;
import com.example.documents.application.command.CreateDocumentSetCommand;
import com.example.documents.application.handler.DocumentSetCommandHandler;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.DocumentSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Random;

/**
 * Seeds sample document sets for testing pagination.
 * 
 * <p>Automatically enabled in local profile. Can be controlled via
 * application property: documents.seed.enabled=true/false</p>
 */
@Component
@ConditionalOnProperty(
    name = "documents.seed.enabled", 
    havingValue = "true",
    matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
public class DocumentSetSeeder implements CommandLineRunner {
    
    private final DocumentSetCommandHandler commandHandler;
    private final DocumentSetRepository repository;
    private final Random random = new Random();
    
    @Override
    public void run(String... args) {
        // Check if data already exists (idempotency)
        Page firstPage = Page.first(1);
        PaginatedResult<DocumentSet> existing = repository.findAll(firstPage);
        
        if (!existing.isEmpty()) {
            log.info("Document sets already exist (found at least {}), skipping seeding", existing.size());
            return;
        }
        
        log.info("Starting document set seeding...");
        
        int totalSets = 50;
        int invoiceCount = 20;
        int orderCount = 15;
        int quotationCount = 10;
        int creditNoteCount = 5;
        
        seedDocumentSets(DocumentType.INVOICE, invoiceCount, "Invoice Set");
        seedDocumentSets(DocumentType.ORDER, orderCount, "Order Set");
        seedDocumentSets(DocumentType.QUOTATION, quotationCount, "Quotation Set");
        seedDocumentSets(DocumentType.CREDIT_NOTE, creditNoteCount, "Credit Note Set");
        
        log.info("Seeded {} document sets successfully", totalSets);
    }
    
    private void seedDocumentSets(DocumentType type, int count, String namePrefix) {
        for (int i = 1; i <= count; i++) {
            try {
                createSampleDocumentSet(type, namePrefix, i);
            } catch (Exception e) {
                log.error("Failed to seed document set {} {}: {}", namePrefix, i, e.getMessage());
            }
        }
    }
    
    private void createSampleDocumentSet(DocumentType type, String namePrefix, int number) {
        // Create sample content
        String sampleContent = generateSampleContent(type, number);
        Content content = Content.of(
            sampleContent.getBytes(StandardCharsets.UTF_8),
            Format.JSON
        );
        
        // Create schema reference (using dummy schema for now)
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            SchemaId.generate(),
            VersionIdentifier.of("1.0.0")
        );
        
        // Create metadata
        Map<String, String> metadata = Map.of(
            "name", String.format("%s %03d", namePrefix, number),
            "description", generateDescription(type, number),
            "category", type.name(),
            "fiscalYear", "2024"
        );
        
        // Create command
        CreateDocumentSetCommand command = new CreateDocumentSetCommand(
            type,
            schemaRef,
            content,
            "seeder",
            metadata
        );
        
        // Execute
        commandHandler.handle(command);
        
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
