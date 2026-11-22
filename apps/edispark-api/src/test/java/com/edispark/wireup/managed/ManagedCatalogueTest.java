package com.edispark.wireup.managed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.edispark.integrationapplication.persistence.ManagedMetadata;
import com.edispark.transformations.infrastructure.ClasspathGlobalMappingCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ManagedCatalogueTest {
    private final Map<String, String> rows = new HashMap<>();
    private final Map<String, Long> versions = new HashMap<>();
    private final MockEnvironment environment = new MockEnvironment();
    private final ClasspathGlobalMappingCatalog mappings = new ClasspathGlobalMappingCatalog();
    private final ManagedCatalogue catalogue = new ManagedCatalogue(metadata(), mappings,
            new ObjectMapper().registerModule(new JavaTimeModule()), Clock.systemUTC(), environment);

    @Test void publicationKeepsThePreviousRevisionAvailableUntilItsReplacementPublishes() {
        var definition = new ManagedCatalogueSeed(catalogue, mappings, environment).tesco();
        var first = catalogue.create(definition, "system-admin");
        assertThat(catalogue.published()).isEmpty();
        var published = catalogue.publish(first.id(), first.revision(), first.version(), "system-admin");
        var draft = catalogue.edit(first.id(), published.revision(), published.version(), definition, "system-admin");
        assertThat(catalogue.published()).containsExactly(published);
        assertThatThrownBy(() -> catalogue.edit(first.id(), first.revision(), first.version(), definition, "system-admin"))
                .hasMessageContaining("changed");
        var replacement = catalogue.publish(first.id(), draft.revision(), draft.version(), "system-admin");
        assertThat(catalogue.published()).containsExactly(replacement);
        catalogue.retire(first.id(), replacement.revision(), replacement.version(), "system-admin");
        assertThat(catalogue.published()).isEmpty();
        assertThat(catalogue.revisions()).hasSize(2).contains(published);
    }

    @Test void fixtureReconciliationRestoresMissingTemplatesAndPreservesChanges() {
        environment.setActiveProfiles("local");
        var local = new ManagedCatalogue(metadata(), mappings, new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.systemUTC(), environment);
        var seed = new ManagedCatalogueSeed(local, mappings, environment);
        seed.reconcile();
        var tesco = local.published().stream().filter(row -> row.id().equals(ManagedCatalogueSeed.TESCO)).findFirst().orElseThrow();
        local.retire(tesco.id(), tesco.revision(), tesco.version(), "system-admin");
        var removed = local.published().getFirst();
        var removedKey = removed.id() + "#" + removed.revision();
        rows.remove(removedKey);
        versions.remove(removedKey);

        seed.reconcile();
        seed.reconcile();

        assertThat(local.revisions()).hasSize(4);
        assertThat(local.published()).hasSize(3).noneMatch(row -> row.id().equals(tesco.id()));
        assertThat(local.published()).anyMatch(row -> row.id().equals(removed.id()));
    }

    private ManagedMetadata metadata() {
        var metadata = mock(ManagedMetadata.class);
        when(metadata.catalogue()).thenAnswer(invocation -> List.copyOf(rows.values()));
        when(metadata.template(anyString())).thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.getArgument(0))));
        when(metadata.writeTemplate(anyString(), anyString(), anyLong())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long version = invocation.getArgument(2);
            if (version != versions.getOrDefault(key, -1L) + 1) return false;
            rows.put(key, invocation.getArgument(1));
            versions.put(key, version);
            return true;
        });
        return metadata;
    }
}
