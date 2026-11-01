package com.edispark.transformations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import com.edispark.transformations.domain.MappingStatus;
import com.edispark.transformations.infrastructure.InMemoryMappingRepository;
import com.edispark.transformations.infrastructure.ClasspathGlobalMappingCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MappingServiceTest {
    private static final String TENANT = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";

    @Test
    void publishesOnlyAfterAFixturePassesAndThenBecomesImmutable() {
        ObjectMapper mapper = new ObjectMapper();
        var service = new MappingService(new InMemoryMappingRepository(), new MappingRuntime(mapper), mapper,
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC), new ClasspathGlobalMappingCatalog());
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var node = new MappingNode(UUID.randomUUID(), MappingOperation.COPY, List.of("/id"),
                    "/document/id", null, null);
            var mapping = service.create("Order reference", UUID.randomUUID(), UUID.randomUUID(), List.of(node));
            byte[] input = bytes("{\"id\":\"PO-1001\"}");
            byte[] expected = bytes("{\"document\":{\"id\":\"PO-1001\"}}");

            assertThat(service.test(mapping.id(), input, expected).passed()).isTrue();
            var published = service.publish(mapping.id(), List.of(new MappingService.Fixture("order", input, expected)));

            assertThat(published.status()).isEqualTo(MappingStatus.PUBLISHED);
            assertThat(published.contentHash()).hasSize(64);
            assertThatThrownBy(() -> service.update(mapping.id(), List.of(node)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("immutable");
        });
    }

    @Test
    void refusesToPublishARegressionFixture() {
        ObjectMapper mapper = new ObjectMapper();
        var service = new MappingService(new InMemoryMappingRepository(), new MappingRuntime(mapper), mapper,
                Clock.systemUTC(), new ClasspathGlobalMappingCatalog());
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var node = new MappingNode(UUID.randomUUID(), MappingOperation.UPPERCASE, List.of("/name"),
                    "/name", null, null);
            var mapping = service.create("Uppercase name", UUID.randomUUID(), UUID.randomUUID(), List.of(node));
            assertThatThrownBy(() -> service.publish(mapping.id(), List.of(new MappingService.Fixture("wrong",
                    bytes("{\"name\":\"alpha\"}"), bytes("{\"name\":\"alpha\"}")))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fixture failed");
        });
    }

    @Test
    void schedulesPromotionAndOnlyDeletesUnusedDrafts() {
        ObjectMapper mapper = new ObjectMapper();
        var repository = new InMemoryMappingRepository();
        var service = new MappingService(repository, new MappingRuntime(mapper), mapper, CLOCK,
                new ClasspathGlobalMappingCatalog());
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var node = new MappingNode(UUID.randomUUID(), MappingOperation.COPY, List.of("/id"),
                    "/document/id", null, null);
            var mapping = service.create("Scheduled order", UUID.randomUUID(), UUID.randomUUID(), List.of(node));
            byte[] input = bytes("{\"id\":\"PO-1001\"}");
            byte[] expected = bytes("{\"document\":{\"id\":\"PO-1001\"}}");
            var scheduled = service.publish(mapping.id(), List.of(new MappingService.Fixture("order", input, expected)),
                    CLOCK.instant().plusSeconds(60));

            assertThat(scheduled.status()).isEqualTo(MappingStatus.SCHEDULED);
            assertThat(scheduled.familyId()).isEqualTo(mapping.id());
            assertThat(service.usage(scheduled.id()).deletable()).isFalse();
            assertThatThrownBy(() -> service.deleteDraft(scheduled.id()))
                    .hasMessageContaining("unused draft");

            var disposable = service.create("Disposable", UUID.randomUUID(), UUID.randomUUID(), List.of(node));
            service.deleteDraft(disposable.id());
            assertThat(repository.find(TENANT, disposable.id())).isEmpty();
        });
    }

    @Test
    void exposesGlobalMappingsToEveryTenantAndForksThemIntoTenantOwnedDrafts() {
        ObjectMapper mapper = new ObjectMapper();
        var service = new MappingService(new InMemoryMappingRepository(), new MappingRuntime(mapper), mapper, CLOCK,
                new ClasspathGlobalMappingCatalog());
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var global = service.list().getFirst();

            assertThat(global.scope().name()).isEqualTo("GLOBAL");
            assertThat(global.vendor()).isEqualTo("EdiSpark");
            assertThat(global.sample().sourceDocument()).isNotBlank();
            assertThat(service.usage(global.id()).deletable()).isFalse();
            assertThatThrownBy(() -> service.update(global.id(), global.nodes()))
                    .hasMessageContaining("global mappings are immutable");

            var draft = service.createRevision(global.id());
            assertThat(draft.tenantId()).isEqualTo(TENANT);
            assertThat(draft.status()).isEqualTo(MappingStatus.DRAFT);
            assertThat(draft.derivedFromMappingId()).isEqualTo(global.id());
            assertThat(draft.sample()).isEqualTo(global.sample());
        });
    }

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
