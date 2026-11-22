package com.edispark.wireup.managed;

import com.edispark.integrationapplication.persistence.ManagedMetadata;
import com.edispark.wireup.managed.ManagedTemplate.Definition;
import com.edispark.wireup.managed.ManagedTemplate.Status;
import com.edispark.wireup.managed.ManagedTemplate.Verification;
import com.edispark.transformations.domain.GlobalMappingCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@Profile("!openapi-export")
public final class ManagedCatalogue {
    private final ManagedMetadata metadata;
    private final GlobalMappingCatalog mappings;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean local;

    ManagedCatalogue(ManagedMetadata metadata, GlobalMappingCatalog mappings, ObjectMapper json,
            Clock clock, Environment environment) {
        this.metadata = metadata;
        this.mappings = mappings;
        this.json = json;
        this.clock = clock;
        this.local = environment.matchesProfiles("local");
    }

    public List<ManagedTemplate> revisions() {
        return metadata.catalogue().stream().map(row -> read(row, ManagedTemplate.class))
                .sorted(Comparator.comparing(ManagedTemplate::id).thenComparing(ManagedTemplate::revision).reversed())
                .toList();
    }

    List<ManagedTemplate> published() {
        return publishedRevisions().stream()
                .filter(item -> local || item.definition().verification() != Verification.LOCAL_DEMO).toList();
    }

    private List<ManagedTemplate> publishedRevisions() {
        var latest = new LinkedHashMap<UUID, ManagedTemplate>();
        revisions().stream().filter(item -> item.status() != Status.DRAFT)
                .forEach(item -> latest.putIfAbsent(item.id(), item));
        return latest.values().stream().filter(item -> item.status() == Status.PUBLISHED)
                .sorted(Comparator.comparing(item -> item.definition().name())).toList();
    }

    ManagedTemplate create(Definition definition, String actor) {
        return insert(UUID.randomUUID(), 1, definition, actor, Status.DRAFT);
    }

    ManagedTemplate edit(UUID id, int revision, long expectedVersion, Definition definition, String actor) {
        var latest = latest(id);
        checkVersion(latest, revision, expectedVersion);
        if (latest.status() != Status.DRAFT) {
            return insert(id, revision + 1, definition, actor, Status.DRAFT);
        }
        return update(latest, definition, Status.DRAFT, actor);
    }

    ManagedTemplate publish(UUID id, int revision, long expectedVersion, String actor) {
        var draft = latest(id);
        checkVersion(draft, revision, expectedVersion);
        if (draft.status() != Status.DRAFT) throw new IllegalStateException("Only a draft template can be published.");
        validate(draft.definition());
        return update(draft, draft.definition(), Status.PUBLISHED, actor);
    }

    ManagedTemplate retire(UUID id, int revision, long expectedVersion, String actor) {
        var current = publishedRevisions().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("A published template was not found."));
        checkVersion(current, revision, expectedVersion);
        return update(current, current.definition(), Status.RETIRED, actor);
    }

    void seed(UUID id, Definition definition) {
        if (metadata.template(key(id, 1)).isPresent()) return;
        validate(definition);
        var entry = new ManagedTemplate(id, 1, 0, Status.PUBLISHED, definition, "managed-catalogue-seed", clock.instant());
        metadata.writeTemplate(key(id, 1), write(entry), 0);
    }

    ManagedTemplate published(UUID id, int revision) {
        return published().stream().filter(item -> item.id().equals(id) && item.revision() == revision)
                .findFirst().orElseThrow(() -> new IllegalStateException("This revision is no longer available for setup."));
    }

    private ManagedTemplate latest(UUID id) {
        return revisions().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The managed template was not found."));
    }

    private ManagedTemplate insert(UUID id, int revision, Definition definition, String actor, Status status) {
        var entry = new ManagedTemplate(id, revision, 0, status, definition, actor, clock.instant());
        return save(entry);
    }

    private ManagedTemplate update(ManagedTemplate current, Definition definition, Status status, String actor) {
        return save(new ManagedTemplate(current.id(), current.revision(), current.version() + 1, status,
                definition, actor, clock.instant()));
    }

    private ManagedTemplate save(ManagedTemplate entry) {
        if (!metadata.writeTemplate(key(entry.id(), entry.revision()), write(entry), entry.version())) {
            throw new IllegalStateException("The managed template changed. Reload it and retry.");
        }
        return entry;
    }

    private void checkVersion(ManagedTemplate current, int revision, long version) {
        if (current.revision() != revision || current.version() != version) {
            throw new IllegalStateException("The managed template changed. Reload it and retry.");
        }
    }

    private void validate(Definition definition) {
        var transport = definition.transport();
        if (definition.verification() != Verification.LOCAL_DEMO
                && (!transport.signingRequired() || !transport.encryptionRequired() || transport.compressionEnabled())) {
            throw new IllegalArgumentException("Live AS2 requires signing and encryption with compression disabled.");
        }
        var routes = new java.util.HashSet<String>();
        for (var capability : definition.capabilities()) {
            var mapping = mappings.find(capability.mappingRevisionId())
                    .orElseThrow(() -> new IllegalArgumentException("Select an EdiSpark-published mapping revision."));
            var compatibility = mapping.compatibility();
            if (!mapping.sourceSchemaId().equals(capability.sourceSchemaId())
                    || !mapping.targetSchemaId().equals(capability.targetSchemaId())
                    || compatibility == null || !compatibility.direction().equals(capability.direction().name())
                    || !compatibility.documentType().equals(capability.documentType().name())
                    || !compatibility.standard().equals(capability.standard().name())
                    || !compatibility.release().equals(capability.release())
                    || !compatibility.messageType().equals(capability.messageType())) {
                throw new IllegalArgumentException("The published mapping does not match the selected capability.");
            }
            if (!routes.add(capability.bind(UUID.randomUUID(), UUID.randomUUID()).routingKey())) {
                throw new IllegalArgumentException("A managed template cannot contain duplicate routes.");
            }
        }
    }

    private static String key(UUID id, int revision) {
        return id + "#" + revision;
    }

    String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist managed partner data.", exception);
        }
    }

    <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Could not read managed partner data.", exception);
        }
    }
}
