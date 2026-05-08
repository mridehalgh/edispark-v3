package com.example.documents.infrastructure.persistence;

import com.example.common.pagination.Page;
import com.example.common.pagination.PaginatedResult;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.VersionIdentifier;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for pagination completeness.
 * 
 * <p><strong>Validates: Requirements 2.1, 2.3</strong></p>
 * 
 * <p>Property: Paginating through all results returns every item exactly once,
 * regardless of page size.</p>
 */
@Tag("dynamodb-local")
class PaginationCompletenessPropertyTest {

    private static DynamoDbLocalTestSupport dynamoDbLocal;
    private static DynamoDbTableConfig tableConfig;
    private DynamoDbDocumentSetRepository repository;

    @BeforeContainer
    static void startDynamoDbLocal() throws Exception {
        dynamoDbLocal = DynamoDbLocalTestSupport.acquireShared();
        tableConfig = new DynamoDbTableConfig("test-documents-pagination");
    }

    @AfterContainer
    static void stopDynamoDbLocal() throws Exception {
        DynamoDbLocalTestSupport.releaseShared();
    }

    @BeforeTry
    void setUp() {
        tableConfig.deleteTableIfExists(dynamoDbLocal.client());
        tableConfig.createTableIfNotExists(dynamoDbLocal.client());
        repository = new DynamoDbDocumentSetRepository(dynamoDbLocal.client(), tableConfig.tableName());
    }

    @Property(tries = 25)
    void paginationReturnsAllItemsExactlyOnce(@ForAll @IntRange(min = 1, max = 100) int pageSize) {
        int totalSets = 50;
        List<DocumentSetId> seededIds = seedDocumentSets(totalSets);
        
        Set<DocumentSetId> retrievedIds = new HashSet<>();
        Optional<String> nextToken = Optional.empty();
        int pageCount = 0;
        int totalRetrieved = 0;
        
        do {
            Page page = nextToken.isPresent() 
                ? Page.next(pageSize, nextToken.get())
                : Page.first(pageSize);
                
            PaginatedResult<DocumentSet> result = repository.findAll(page);
            
            for (DocumentSet ds : result.items()) {
                retrievedIds.add(ds.id());
            }
            
            totalRetrieved += result.items().size();
            nextToken = result.continuationToken();
            pageCount++;
            
            assertThat(pageCount).isLessThan(200);
            
        } while (nextToken.isPresent());
        
        assertThat(retrievedIds)
            .as("All seeded document sets should be retrieved")
            .containsExactlyInAnyOrderElementsOf(seededIds);
        
        assertThat(totalRetrieved)
            .as("Total retrieved should equal total seeded")
            .isEqualTo(totalSets);
        
        assertThat(pageCount)
            .as("Should have at least one page")
            .isGreaterThan(0);
    }

    @Property(tries = 25)
    void paginationWithVaryingPageSizesReturnsConsistentResults(
            @ForAll @IntRange(min = 1, max = 20) int pageSize1,
            @ForAll @IntRange(min = 1, max = 20) int pageSize2) {
        int totalSets = 30;
        List<DocumentSetId> seededIds = seedDocumentSets(totalSets);
        
        Set<DocumentSetId> retrievedIds1 = paginateThroughAll(pageSize1);
        Set<DocumentSetId> retrievedIds2 = paginateThroughAll(pageSize2);
        
        assertThat(retrievedIds1)
            .as("Different page sizes should retrieve same items")
            .containsExactlyInAnyOrderElementsOf(retrievedIds2);
        
        assertThat(retrievedIds1)
            .as("Should retrieve all seeded items")
            .containsExactlyInAnyOrderElementsOf(seededIds);
    }

    private List<DocumentSetId> seedDocumentSets(int count) {
        List<DocumentSetId> ids = new ArrayList<>();
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            SchemaId.generate(), 
            VersionIdentifier.of("1.0.0")
        );
        
        for (int i = 0; i < count; i++) {
            ContentHash hash = ContentHash.sha256("test-content-" + i);
            ContentRef contentRef = ContentRef.of(hash);
            
            DocumentSet docSet = DocumentSet.createWithDocument(
                DocumentType.INVOICE,
                schemaRef,
                contentRef,
                hash,
                "test-user",
                Map.of("index", String.valueOf(i))
            );
            
            repository.save(docSet);
            ids.add(docSet.id());
        }
        
        return ids;
    }

    private Set<DocumentSetId> paginateThroughAll(int pageSize) {
        Set<DocumentSetId> retrievedIds = new HashSet<>();
        Optional<String> nextToken = Optional.empty();
        int pageCount = 0;
        
        do {
            Page page = nextToken.isPresent() 
                ? Page.next(pageSize, nextToken.get())
                : Page.first(pageSize);
                
            PaginatedResult<DocumentSet> result = repository.findAll(page);
            
            for (DocumentSet ds : result.items()) {
                retrievedIds.add(ds.id());
            }
            
            nextToken = result.continuationToken();
            pageCount++;
            
            assertThat(pageCount).isLessThan(200);
            
        } while (nextToken.isPresent());
        
        return retrievedIds;
    }
}
