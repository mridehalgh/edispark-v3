package com.edispark.documents.infrastructure.seed;

import com.edispark.documents.application.command.CreateDocumentSetCommand;
import com.edispark.documents.application.command.CreateSchemaCommand;
import com.edispark.documents.application.command.AddSchemaVersionCommand;
import com.edispark.documents.application.handler.SchemaCommandHandler;
import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.domain.model.*;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.util.Map;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Versioned partner examples, reconciled independently of developer-authored documents. */
@Component
@Profile("local & !openapi-export")
public final class BqAcknowledgementSeeder {
    static final String FIXTURE = "bq-ack-v1.4-full-acceptance-v1";
    private final DocumentSetRepository sets;
    private final DocumentSetCommandHandler commands;
    private final SchemaCommandHandler schemas;
    private final String tenant;
    public BqAcknowledgementSeeder(DocumentSetRepository sets, DocumentSetCommandHandler commands, SchemaCommandHandler schemas,
            @Value("${documents.seed.tenant-id}") String tenant) {
        this.sets = sets; this.commands = commands; this.schemas = schemas; this.tenant = tenant;
    }
    @EventListener(ApplicationReadyEvent.class) @Order(20)
    public void seed() {
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenant)), this::reconcile);
    }
    void reconcile() {
        var order = ensure("order", DocumentType.ORDER, "bq-order-local-42.json", "B&Q purchase order PO-42", "INBOUND");
        var ready = ensure("ready", DocumentType.ORDER_RESPONSE, "bq-ack-local-42.json", "B&Q acknowledgement · ready to send", "OUTBOUND");
        var blocked = ensure("missing-vendor", DocumentType.ORDER_RESPONSE, "bq-ack-missing-vendor.json", "B&Q acknowledgement · vendor number needed", "OUTBOUND");
        link(ready, order); link(blocked, order);
    }
    private DocumentSet ensure(String key, DocumentType type, String resource, String name, String direction) {
        var existing = sets.findAll().stream().filter(set -> FIXTURE.equals(set.metadata().get("fixtureVersion"))
                && key.equals(set.metadata().get("fixtureKey"))).findFirst();
        if (existing.isPresent()) return existing.get();
        var createdSchema = schemas.handle(CreateSchemaCommand.of("B&Q local " + type + " UBL 2.1", SchemaFormat.JSON_SCHEMA));
        var version = VersionIdentifier.of("2.1");
        String root = type == DocumentType.ORDER ? "Order" : "OrderResponse";
        var schemaBytes = ("{\"type\":\"object\",\"required\":[\"" + root + "\"],\"properties\":{\"" + root + "\":{}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        schemas.handle(new AddSchemaVersionCommand(createdSchema.id(), version, Content.of(schemaBytes, Format.JSON)));
        var schema = SchemaVersionRef.of(createdSchema.id(), version);
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/seed/documents/" + resource)) {
            if (input == null) throw new IllegalStateException("Missing B&Q sample");
            bytes = input.readAllBytes();
        } catch (java.io.IOException exception) { throw new IllegalStateException("Could not read B&Q sample", exception); }
        if (!commands.validateCanonical(type, bytes).valid()) throw new IllegalStateException("B&Q sample does not pass canonical validation");
        return commands.handle(new CreateDocumentSetCommand(type, schema, Content.of(bytes, Format.JSON), "local-seeder",
                Map.of("fixtureVersion", FIXTURE, "fixtureKey", key, "name", name, "direction", direction,
                        "businessDocumentNumber", type == DocumentType.ORDER ? "PO-42" : key.equals("ready") ? "BQ-ACK-LOCAL-42" : "BQ-ACK-BLOCKED-42",
                        "description", "Synthetic B&Q ACK v1.4 example for the local AS2 simulator", "standard", "UBL", "ublVersion", "2.1")));
    }
    private void link(DocumentSet source, DocumentSet target) {
        var document = source.getAllDocuments().getFirst();
        if (!"local-seeder".equals(document.getCurrentVersion().createdBy())) return;
        var relation = new DocumentRelation(DocumentRelationType.RESPONDS_TO, target.getAllDocuments().getFirst().id(), target.id());
        if (document.relations().contains(relation)) return;
        List.copyOf(document.relations()).stream().filter(item -> item.type() == DocumentRelationType.RESPONDS_TO)
                .forEach(document::removeRelation);
        document.addRelation(relation); sets.save(source);
    }
}
