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
    @DisplayName("creates three focused sets instead of placeholder data")
    void createsSmallRealisticCatalogue() {
        seeder.seedTenantData();

        assertThat(documentSets.findAll()).hasSize(3);
        assertThat(schemas.findAll()).hasSize(3);
        assertThat(documentSets.findAll())
                .extracting(set -> set.metadata().get("businessDocumentNumber"))
                .containsExactlyInAnyOrder("PO-2026-0042", "INV-100045", "CN-1007");
        assertThat(directionOf("PO-2026-0042")).isEqualTo("INBOUND");
        assertThat(directionOf("INV-100045")).isEqualTo("OUTBOUND");
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
                .containsExactlyInAnyOrder("PO-2026-0042", "INV-100045", "CN-1007");
    }

    @Test
    @DisplayName("replaces an older named local fixture with the comprehensive sample")
    void upgradesStaleFixtureShape() {
        documentSets.save(DocumentSet.create("local-seeder", Map.of(
                "businessDocumentNumber", "PO-2026-0042", "name", "Older sample")));

        seeder.seedTenantData();
        seeder.seedTenantData();

        assertThat(documentSets.findAll()).hasSize(3);
        DocumentSet order = documentSets.findAll().stream()
                .filter(set -> "PO-2026-0042".equals(set.metadata().get("businessDocumentNumber")))
                .findFirst().orElseThrow();
        assertThat(order.metadata()).containsEntry("fixtureVersion", "supplier-order-invoice-v3");
        Document source = order.getAllDocuments().getFirst();
        assertThat(asString(source)).contains("BUY-WATER-750", "HERBAL-TEA-80", "OTR=8");
        assertThat(source.derivatives()).hasSize(1);
        assertThat(asString(source.derivatives().getFirst()))
                .contains("CommodityClassification", "PaymentTerms", "AllowanceCharge");
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

        private List<Schema> findAll() {
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
