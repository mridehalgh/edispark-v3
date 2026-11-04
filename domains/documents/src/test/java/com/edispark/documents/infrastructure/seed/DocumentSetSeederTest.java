package com.edispark.documents.infrastructure.seed;

import com.edispark.pagination.Page;
import com.edispark.pagination.PaginatedResult;
import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.application.handler.SchemaCommandHandler;
import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.ContentHash;
import com.edispark.documents.domain.model.Derivative;
import com.edispark.documents.domain.model.Document;
import com.edispark.documents.domain.model.DocumentSet;
import com.edispark.documents.domain.model.DocumentSetId;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.model.Schema;
import com.edispark.documents.domain.model.SchemaId;
import com.edispark.documents.domain.model.SchemaVersionRef;
import com.edispark.documents.domain.model.TransformationMethod;
import com.edispark.documents.domain.repository.ContentStore;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.edispark.documents.domain.repository.SchemaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Document set seeder")
class DocumentSetSeederTest {

    private InMemoryDocumentSetRepository documentSets;
    private InMemorySchemaRepository schemas;
    private InMemoryContentStore content;
    private DocumentSetSeeder seeder;

    @Test
    void linksAllCatalogueDocumentsAndRepairsMissingLinksWithoutChangingSourceFiles() {
        seeder.seedTenantData();
        var order = fixture("PO-2026-0042");
        var invoice = fixture("INV-100045");
        var receipt = fixture("REC-2026-0042");
        var remittance = fixture("REM-2026-0042");
        var originalHash = invoice.getAllDocuments().getFirst().getCurrentVersion().contentHash();
        assertThat(invoice.getAllDocuments().getFirst().relations()).singleElement().satisfies(relation -> {
            assertThat(relation.targetDocumentSetId()).isEqualTo(order.id());
            assertThat(relation.targetDocumentId()).isEqualTo(order.getAllDocuments().getFirst().id());
        });
        assertThat(receipt.getAllDocuments().getFirst().relations()).singleElement()
                .satisfies(relation -> assertThat(relation.targetDocumentSetId()).isEqualTo(fixture("DES-2026-0042").id()));
        var missing = remittance.getAllDocuments().getFirst();
        List.copyOf(missing.relations()).forEach(missing::removeRelation);
        documentSets.save(remittance);

        seeder.seedTenantData();
        seeder.seedTenantData();

        assertThat(documentSets.findAll()).hasSize(7);
        assertThat(fixture("REM-2026-0042").getAllDocuments().getFirst().relations()).hasSize(1);
        assertThat(fixture("INV-100045").getAllDocuments().getFirst().getCurrentVersion().contentHash()).isEqualTo(originalHash);
        assertThat(order.getAllDocuments().getFirst().derivatives()).hasSize(1);
        Map.of("OR-2026-0042", "PO-2026-0042", "DES-2026-0042", "PO-2026-0042",
                "INV-100045", "PO-2026-0042", "REM-2026-0042", "INV-100045",
                "REC-2026-0042", "DES-2026-0042", "CN-1007", "INV-100045")
                .forEach((source, target) -> assertThat(fixture(source).getAllDocuments().getFirst().relations())
                        .singleElement().satisfies(relation -> assertThat(relation.targetDocumentSetId()).isEqualTo(fixture(target).id())));
    }

    @Test
    void repairsLinksWhenAReferencedFixtureIsRecreatedAndLeavesDeveloperDataAlone() {
        seeder.seedTenantData();
        var developerSet = DocumentSet.create("developer", Map.of("businessDocumentNumber", "INV-100045"));
        documentSets.save(developerSet);
        var previous = fixture("INV-100045").id();
        documentSets.delete(previous);

        seeder.seedTenantData();
        seeder.seedTenantData();

        var current = fixture("INV-100045").id();
        assertThat(current).isNotEqualTo(previous);
        assertThat(fixture("REM-2026-0042").getAllDocuments().getFirst().relations()).singleElement()
                .satisfies(relation -> assertThat(relation.targetDocumentSetId()).isEqualTo(current));
        assertThat(documentSets.findById(developerSet.id())).isPresent();
        assertThat(documentSets.findAll()).hasSize(8);
    }

    private DocumentSet fixture(String number) {
        return documentSets.findAll().stream()
                .filter(set -> "local-seeder".equals(set.createdBy()))
                .filter(set -> number.equals(set.metadata().get("businessDocumentNumber")))
                .findFirst().orElseThrow();
    }

    @BeforeEach
    void setUp() {
        documentSets = new InMemoryDocumentSetRepository();
        schemas = new InMemorySchemaRepository();
        content = new InMemoryContentStore();
        DocumentSetCommandHandler documentHandler = new DocumentSetCommandHandler(
                documentSets,
                schemas,
                content,
                List.of(),
                List.of());
        SchemaCommandHandler schemaHandler = new SchemaCommandHandler(schemas, content);
        seeder = new DocumentSetSeeder(documentHandler, schemaHandler, documentSets, content);
    }

    @Test
    @DisplayName("creates the complete six-document workflow and credit-note follow-up")
    void createsSmallRealisticCatalogue() {
        seeder.seedTenantData();

        assertThat(documentSets.findAll()).hasSize(7);
        assertThat(schemas.findAll()).hasSize(7);
        assertThat(documentSets.findAll())
                .extracting(set -> set.metadata().get("businessDocumentNumber"))
                .containsExactlyInAnyOrder("PO-2026-0042", "OR-2026-0042", "DES-2026-0042",
                        "INV-100045", "REM-2026-0042", "REC-2026-0042", "CN-1007");
        assertThat(directionOf("PO-2026-0042")).isEqualTo("INBOUND");
        assertThat(directionOf("OR-2026-0042")).isEqualTo("OUTBOUND");
        assertThat(directionOf("DES-2026-0042")).isEqualTo("OUTBOUND");
        assertThat(directionOf("INV-100045")).isEqualTo("OUTBOUND");
        assertThat(directionOf("REM-2026-0042")).isEqualTo("INBOUND");
        assertThat(directionOf("REC-2026-0042")).isEqualTo("INBOUND");
        assertThat(directionOf("CN-1007")).isEqualTo("OUTBOUND");
    }

    @Test
    @DisplayName("repairs a partially populated demo catalogue without duplicating existing documents")
    void repairsPartialCatalogue() {
        seeder.seedTenantData();
        DocumentSet invoice = documentSets.findAll().stream()
                .filter(set -> "INV-100045".equals(set.metadata().get("businessDocumentNumber")))
                .findFirst()
                .orElseThrow();
        DocumentSet creditNote = documentSets.findAll().stream()
                .filter(set -> "CN-1007".equals(set.metadata().get("businessDocumentNumber")))
                .findFirst()
                .orElseThrow();
        documentSets.delete(invoice.id());
        documentSets.delete(creditNote.id());

        seeder.seedTenantData();
        seeder.seedTenantData();

        assertThat(documentSets.findAll())
                .extracting(set -> set.metadata().get("businessDocumentNumber"))
                .containsExactlyInAnyOrder("PO-2026-0042", "OR-2026-0042", "DES-2026-0042",
                        "INV-100045", "REM-2026-0042", "REC-2026-0042", "CN-1007");
    }

    @Test
    @DisplayName("replaces an older named local fixture with the comprehensive sample")
    void upgradesStaleFixtureShape() {
        documentSets.save(DocumentSet.create("local-seeder", Map.of(
                "businessDocumentNumber", "PO-2026-0042", "name", "Older sample")));

        seeder.seedTenantData();
        seeder.seedTenantData();

        assertThat(documentSets.findAll()).hasSize(7);
        DocumentSet order = documentSets.findAll().stream()
                .filter(set -> "PO-2026-0042".equals(set.metadata().get("businessDocumentNumber")))
                .findFirst().orElseThrow();
        assertThat(order.metadata()).containsEntry("fixtureVersion", "six-document-workflow-v5");
        Document source = order.getAllDocuments().getFirst();
        assertThat(asString(source)).contains("BUY-WATER-750", "HERBAL-TEA-80", "OTR=8");
        assertThat(source.derivatives()).hasSize(1);
        assertThat(asString(source.derivatives().getFirst()))
                .contains("CommodityClassification", "PaymentTerms", "AllowanceCharge");
    }

    @Test
    @DisplayName("upgrades the previous despatch fixture to the printable pallet-label sample")
    void upgradesDespatchFixtureWithoutSsccs() {
        documentSets.save(DocumentSet.create("local-seeder", Map.of(
                "businessDocumentNumber", "DES-2026-0042",
                "fixtureVersion", "six-document-workflow-v4",
                "name", "Older despatch sample")));

        seeder.seedTenantData();
        seeder.seedTenantData();

        DocumentSet despatch = documentSets.findAll().stream()
                .filter(set -> "DES-2026-0042".equals(set.metadata().get("businessDocumentNumber")))
                .findFirst().orElseThrow();
        assertThat(despatch.metadata()).containsEntry("fixtureVersion", "six-document-workflow-v5");
        assertThat(asString(despatch.getAllDocuments().getFirst()))
                .contains("TransportHandlingUnit", "TraceID", "123456789012345675");
    }

    @Test
    @DisplayName("stores the parsed UBL JSON order as a derivative of the TRADACOMS source version")
    void storesParsedOrderAsDerivative() {
        seeder.seedTenantData();

        DocumentSet orderSet = documentSets.findAll().stream()
                .filter(set -> "PO-2026-0042".equals(set.metadata().get("businessDocumentNumber")))
                .findFirst()
                .orElseThrow();
        assertThat(orderSet.getAllDocuments()).hasSize(1);

        Document source = orderSet.getAllDocuments().getFirst();
        assertThat(source.getCurrentVersion().messageType()).isEqualTo("ORDERS");
        assertThat(source.getCurrentVersion().parseStatus()).isEqualTo("SUCCESS");
        assertThat(source.schemaRef().schemaId().value().version()).isEqualTo(4);
        assertThat(source.schemaRef().schemaId().value().variant()).isEqualTo(2);

        assertThat(source.derivatives()).hasSize(1);
        Derivative ublDerivative = source.derivatives().getFirst();
        assertThat(ublDerivative.sourceVersionId()).isEqualTo(source.getCurrentVersion().id());
        assertThat(ublDerivative.targetFormat()).isEqualTo(Format.JSON);
        assertThat(ublDerivative.method()).isEqualTo(TransformationMethod.PROGRAMMATIC);

        assertThat(asString(source)).contains("MHD=1+ORDERS:9'", "ORD=PO-2026-0042");
        assertThat(asString(ublDerivative))
                .contains("\"_D\": \"urn:oasis:names:specification:ubl:schema:xsd:Order-2\"")
                .contains("\"_\": \"PO-2026-0042\"");
    }

    @Test
    @DisplayName("uses UBL-shaped schemas with the official required business fields")
    void createsUblShapedSchemas() {
        seeder.seedTenantData();

        List<String> definitions = schemas.findAll().stream()
                .map(schema -> schema.versions().getFirst().definitionRef().hash())
                .map(content::retrieve)
                .map(Optional::orElseThrow)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .toList();

        assertThat(definitions).anySatisfy(schema -> assertThat(schema)
                .contains("\"required\": [\"Order\"]")
                .contains("\"BuyerCustomerParty\"", "\"SellerSupplierParty\"", "\"OrderLine\""));
        assertThat(definitions).anySatisfy(schema -> assertThat(schema)
                .contains("\"required\": [\"Invoice\"]")
                .contains("\"AccountingSupplierParty\"", "\"InvoiceLine\""));
        assertThat(definitions).anySatisfy(schema -> assertThat(schema)
                .contains("\"required\": [\"CreditNote\"]")
                .contains("\"AccountingCustomerParty\"", "\"CreditNoteLine\""));
    }

    private String asString(Document document) {
        byte[] bytes = content.retrieve(document.getCurrentVersion().contentHash()).orElseThrow();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String asString(Derivative derivative) {
        byte[] bytes = content.retrieve(derivative.contentHash()).orElseThrow();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String directionOf(String businessDocumentNumber) {
        return documentSets.findAll().stream()
                .filter(set -> businessDocumentNumber.equals(set.metadata().get("businessDocumentNumber")))
                .findFirst()
                .orElseThrow()
                .metadata()
                .get("direction");
    }

    private static final class InMemoryDocumentSetRepository implements DocumentSetRepository {
        private final Map<DocumentSetId, DocumentSet> values = new HashMap<>();

        @Override
        public Optional<DocumentSet> findById(DocumentSetId id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public List<DocumentSet> findAll() {
            return new ArrayList<>(values.values());
        }

        @Override
        public PaginatedResult<DocumentSet> findAll(Page page) {
            return PaginatedResult.lastPage(findAll().stream().limit(page.limit()).toList());
        }

        @Override
        public void save(DocumentSet documentSet) {
            values.put(documentSet.id(), documentSet);
        }

        @Override
        public void delete(DocumentSetId id) {
            values.remove(id);
        }

        @Override
        public List<DocumentSet> findByContentHash(ContentHash contentHash) {
            return values.values().stream()
                    .filter(set -> set.getAllDocuments().stream()
                            .anyMatch(document -> document.versions().stream()
                                    .anyMatch(version -> version.contentHash().equals(contentHash))))
                    .toList();
        }
    }

    private static final class InMemorySchemaRepository implements SchemaRepository {
        private final Map<SchemaId, Schema> values = new HashMap<>();

        @Override
        public Optional<Schema> findById(SchemaId id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public void save(Schema schema) {
            values.put(schema.id(), schema);
        }

        @Override
        public boolean isVersionReferenced(SchemaVersionRef schemaVersionRef) {
            return false;
        }

        @Override
        public List<Schema> findAll() {
            return new ArrayList<>(values.values());
        }
    }

    private static final class InMemoryContentStore implements ContentStore {
        private final Map<ContentHash, byte[]> values = new HashMap<>();

        @Override
        public void store(Content content) {
            values.put(content.hash(), content.data());
        }

        @Override
        public Optional<byte[]> retrieve(ContentHash hash) {
            return Optional.ofNullable(values.get(hash));
        }

        @Override
        public boolean exists(ContentHash hash) {
            return values.containsKey(hash);
        }

        @Override
        public void delete(ContentHash hash) {
            values.remove(hash);
        }
    }
}
