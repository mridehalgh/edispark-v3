package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.edispark.crypto.LocalTenantCipher;
import com.edispark.transformations.domain.*;
import com.edispark.transformations.application.MappingService;
import com.edispark.integrationapplication.mapping.MappingExampleFiles;
import io.cottn.core.tenancy.TenantExecution;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import tools.jackson.databind.json.JsonMapper;

class MigWorkflowTest {
    private static final String TENANT = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final String OTHER = "2OnHVzeAJKwm0ThFLcy1HfLHGPn";
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

    @TempDir Path files;

    @Test
    void retryKeepsCompletedCandidateAndSettlesEachConfirmedInvocationOnlyOnce() {
        var db = new DynamoFixture();
        var model = new ScenarioModel();
        var service = setup(db, model, 1_000);
        TenantExecution.run(TENANT, () -> {
            var job = sample(service.store());
            assertThat(service.workflow().start(job.id()).reservedCredits()).isPositive();
            var delivery = new MigStore.Work(TENANT, job.id(), job.pack().digest());
            service.workflow().process(delivery);
            var incomplete = service.workflow().status(job.id());
            assertThat(incomplete.state()).isEqualTo(State.INCOMPLETE);
            assertThat(incomplete.proposals()).extracting(Proposal::candidate).containsExactly(Candidate.SONNET);
            assertThat(incomplete.reservedCredits()).isZero();
            assertThat(db.amount()).isEqualTo(1);

            service.workflow().process(delivery);
            assertThat(db.amount()).isEqualTo(1);
            service.workflow().retry(job.id());
            service.workflow().process(delivery);

            var reviewed = service.workflow().status(job.id());
            assertThat(reviewed.state()).isEqualTo(State.REVIEW);
            assertThat(reviewed.proposals()).extracting(Proposal::candidate).containsExactly(Candidate.SONNET, Candidate.NOVA);
            assertThat(model.sonnetCalls).isEqualTo(1);
            assertThat(model.novaCalls).isEqualTo(2);
            assertThat(reviewed.reservedCredits()).isZero();
            assertThat(db.amount()).isEqualTo(2);
        });
    }

    @Test
    void rerunsWithTheSameUploadsAndSettingsOnTheCurrentRunnerWithoutReservingCredits() {
        var db = new DynamoFixture();
        var model = new ScenarioModel();
        var service = setup(db, model, 1_000);
        UUID originalId = TenantExecution.get(TENANT, () -> {
            var primary = upload(service, FileRole.PRIMARY, "guide.txt", "EDIFACT release D01B");
            var supplement = upload(service, FileRole.SUPPLEMENT, "changes.txt", "BGM reference change");
            var input = upload(service, FileRole.INPUT, "example.edi", "BGM+220+EXAMPLE'");
            var reference = upload(service, FileRole.REFERENCE, "reference.json", "{}");
            var target = new Coordinates("INBOUND", "ORDER", "EDIFACT", "D01B", "ORDERS");
            var source = service.workflow().estimate(new EstimateRequest(primary, List.of(supplement), input, null,
                    null, null, target, reference));
            service.workflow().cancel(source.id());
            model.mode = ModelMode.LONDON_BEDROCK;

            var rerun = service.workflow().rerun(source.id());

            assertThat(rerun.id()).isNotEqualTo(source.id());
            assertThat(rerun.rerunOf()).isEqualTo(source.id());
            assertThat(rerun.uploads()).isEqualTo(source.uploads());
            assertThat(rerun.target()).isEqualTo(source.target());
            assertThat(rerun.uploadsExpireAt()).isEqualTo(source.uploadsExpireAt());
            assertThat(rerun.modelMode()).isEqualTo(ModelMode.LONDON_BEDROCK);
            assertThat(rerun.state()).isEqualTo(State.ESTIMATED);
            assertThat(rerun.proposals()).isEmpty();
            assertThat(rerun.invocations()).isEmpty();
            assertThat(rerun.reservedCredits()).isZero();
            assertThat(service.workflow().status(source.id()).state()).isEqualTo(State.CANCELLED);
            assertThat(db.amount()).isZero();
            return source.id();
        });
        TenantExecution.run(OTHER, () -> assertThatThrownBy(() -> service.workflow().rerun(originalId))
                .isInstanceOf(ResponseStatusException.class));
    }

    private UUID upload(Service service, FileRole role, String name, String content) {
        return service.workflow().upload(new UploadRequest(role, name, "1.0",
                Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))).id();
    }

    @Test
    void releasesQueuedFixtureCreditsWhenTheServerSwitchesToBedrock() {
        var db = new DynamoFixture();
        var model = new ScenarioModel();
        var service = setup(db, model, 1_000);
        TenantExecution.run(TENANT, () -> {
            var job = sample(service.store());
            assertThat(service.workflow().start(job.id()).reservedCredits()).isPositive();
            model.mode = ModelMode.LONDON_BEDROCK;
            service.workflow().process(new MigStore.Work(TENANT, job.id(), job.pack().digest()));

            var paused = service.workflow().status(job.id());
            assertThat(paused.state()).isEqualTo(State.INCOMPLETE);
            assertThat(paused.modelMode()).isEqualTo(ModelMode.LOCAL_FIXTURE);
            assertThat(paused.reservedCredits()).isZero();
            assertThat(model.sonnetCalls + model.novaCalls).isZero();
            assertThatThrownBy(() -> service.workflow().retry(job.id())).hasMessageContaining("model runner changed");
        });
    }

    @Test
    void ambiguousUsageRemainsReservedUntilAConfirmedReceiptReleasesIt() {
        var db = new DynamoFixture();
        var model = new ScenarioModel();
        model.ambiguous = true;
        var service = setup(db, model, 1_000);
        TenantExecution.run(TENANT, () -> {
            var job = sample(service.store());
            service.workflow().start(job.id());
            service.workflow().process(new MigStore.Work(TENANT, job.id(), job.pack().digest()));
            var uncertain = service.workflow().status(job.id());
            assertThat(uncertain.state()).isEqualTo(State.RECONCILIATION);
            assertThat(uncertain.reservedCredits()).isPositive();
            assertThatThrownBy(() -> service.workflow().retry(job.id())).isInstanceOf(IllegalStateException.class);
            var attempt = uncertain.invocations().stream().filter(a -> a.candidate() == Candidate.NOVA).findFirst().orElseThrow();
            assertThat(service.workflow().reconcile(job.id(), attempt.id(), 0, 0, "confirmed-no-usage"))
                    .isEqualTo(State.INCOMPLETE);
            assertThat(service.workflow().reconcile(job.id(), attempt.id(), 0, 0, "confirmed-no-usage"))
                    .isEqualTo(State.INCOMPLETE);
            assertThat(db.amount()).isEqualTo(1);
        });
    }

    @Test
    void insufficientCreditsPreventsDispatchAndAnotherTenantCannotOpenTheJob() {
        var db = new DynamoFixture();
        var model = new ScenarioModel();
        var service = setup(db, model, 0);
        UUID id = TenantExecution.get(TENANT, () -> {
            var job = sample(service.store());
            assertThatThrownBy(() -> service.workflow().start(job.id())).hasMessageContaining("insufficient AI credits");
            assertThat(service.workflow().status(job.id()).state()).isEqualTo(State.ESTIMATED);
            return job.id();
        });
        TenantExecution.run(OTHER, () -> assertThatThrownBy(() -> service.workflow().status(id))
                .isInstanceOf(ResponseStatusException.class));
        assertThat(model.sonnetCalls).isZero();
        assertThat(model.novaCalls).isZero();
        assertThat(db.amount()).isZero();
    }

    @Test
    void aCrashAfterSettlementCommitDoesNotCauseAnotherModelCallOrDuplicateCharge() {
        var db = new DynamoFixture();
        var model = new ScenarioModel();
        var service = setup(db, model, 1_000);
        TenantExecution.run(TENANT, () -> {
            var job = sample(service.store());
            service.workflow().start(job.id());
            var delivery = new MigStore.Work(TENANT, job.id(), job.pack().digest());
            db.failAfterSettlement = true;
            assertThatThrownBy(() -> service.workflow().process(delivery)).hasMessage("simulated response loss");
            assertThat(service.workflow().status(job.id()).proposals()).extracting(Proposal::candidate).containsExactly(Candidate.SONNET);
            assertThat(model.sonnetCalls).isEqualTo(1);

            var restarted = setup(db, model, 1_000, NOW.plusSeconds(250));
            restarted.workflow().process(delivery);
            assertThat(restarted.workflow().status(job.id()).state()).isEqualTo(State.INCOMPLETE);
            assertThat(model.sonnetCalls).isEqualTo(1);
            assertThat(db.amount()).isEqualTo(1);
        });
    }

    @Test
    void aCrashAfterDraftPersistenceResumesTheSameDraftIdentity() {
        var db = new DynamoFixture();
        var imported = new DraftFixture();
        var service = setup(db, new ScenarioModel(), 1_000, NOW, imported);
        TenantExecution.run(TENANT, () -> {
            var job = importable(service.store());

            assertThatThrownBy(() -> service.workflow().createDraft(job.id(), new DraftRequest("Reviewed order", false)))
                    .hasMessage("simulated crash after draft save");
            assertThat(service.workflow().status(job.id()).state()).isEqualTo(State.IMPORTING);
            var second = service.workflow().createDraft(job.id(), new DraftRequest("Reviewed order", false));
            assertThat(second.state()).isEqualTo(State.IMPORTED);
            assertThat(second.draftId()).isEqualTo(imported.revision.id());
            assertThat(imported.revision.status()).isEqualTo(MappingStatus.DRAFT);
            assertThat(imported.created).isEqualTo(1);
        });
    }

    private MigJob importable(MigStore store) {
        var source = "/source";
        var target = "/destination";
        var anchor = "PRIMARY/fixture/line 1";
        var operation = new MappingNode(UUID.randomUUID(), MappingOperation.COPY, List.of(source), target, null, null);
        var rule = new Rule("Copy the field", List.of(anchor), operation, null, null, null, List.of(), true);
        var profile = new MigEvidence.Profile(UUID.randomUUID(), UUID.randomUUID(), "schema", MigEvidence.runtimeFingerprint(),
                List.of(), Set.of(source), Set.of(target), new MappingExampleFiles.Example(null, null, null, null),
                List.of(), List.of(), Map.of(), null);
        var coordinates = new Coordinates("OUTBOUND", "ORDER", "EDIFACT", "D96A", "ORDERS");
        var pack = new MigEvidence.Pack(1, coordinates, profile, List.of(), List.of(new Section(anchor, "Copy the field")),
                List.of(), List.of(), "digest");
        var review = new ReviewRequest(List.of(new Selection("field", Candidate.SONNET, "", null)), List.of(), Map.of());
        var job = new MigJob(UUID.randomUUID(), State.READY, NOW.getEpochSecond(), NOW.plusSeconds(3_600).getEpochSecond(),
                pack, List.of(), 0, Map.of(), List.of(), List.of(), List.of(new Difference("field", rule, rule, true)),
                review, new FixtureResult("UPLOADED_STRUCTURE", null, List.of()), null, null, null, null, ModelMode.LOCAL_FIXTURE, null, null);
        store.create(job);
        return job;
    }

    private static final class DraftFixture implements MappingRepository {
        MappingRevision revision;
        int created;
        @Override public MappingRevision createIfAbsent(MappingRevision draft) {
            if (revision != null) {
                assertThat(draft.id()).isEqualTo(revision.id());
                return revision;
            }
            revision = draft;
            created++;
            throw new IllegalStateException("simulated crash after draft save");
        }
        @Override public MappingRevision save(MappingRevision m) { throw new UnsupportedOperationException(); }
        @Override public Optional<MappingRevision> find(UUID id) { throw new UnsupportedOperationException(); }
        @Override public List<MappingRevision> list() { throw new UnsupportedOperationException(); }
        @Override public void delete(UUID id) { throw new UnsupportedOperationException(); }
        @Override public MappingRevision draft(String name, UUID source, UUID target, List<MappingNode> nodes, Instant at) {
            throw new UnsupportedOperationException();
        }
        @Override public MappingRevision fork(MappingRevision source, Instant at) { throw new UnsupportedOperationException(); }
    }

    private record Service(MigStore store, MigWorkflow workflow) { }

    private Service setup(DynamoFixture db, ScenarioModel model, long allowance) {
        return setup(db, model, allowance, NOW);
    }

    private Service setup(DynamoFixture db, ScenarioModel model, long allowance, Instant now) {
        return setup(db, model, allowance, now, null);
    }

    private Service setup(DynamoFixture db, ScenarioModel model, long allowance, Instant now, MappingRepository mappings) {
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var json = new JsonMapper();
        var cipher = new LocalTenantCipher(files.resolve("root"), "edispark-mig");
        var store = new MigStore(db.client(), cipher, json, "integration", "allowance", allowance, clock);
        var evidence = new MigEvidence(mock(MappingService.class), null, json, null, null);
        var preview = new MigPreview(null, null, null, null, null, null);
        var service = new MigWorkflow(store, new MigExtractor(), null, evidence, new MigReview(), preview, model, mappings,
                null, new SimpleMeterRegistry(), clock, "");
        return new Service(store, service);
    }

    private MigJob sample(MigStore store) {
        var id = UUID.randomUUID();
        var target = new Coordinates("INBOUND", "ORDER", "EDIFACT", "D01B", "ORDERS");
        var text = List.of(new Section("PRIMARY/fixture/line 1", "BGM: mandatory order number"));
        var pack = new MigEvidence.Pack(1, target, null, List.of(), text, List.of(), List.of(), "digest");
        var rates = Map.of(Candidate.SONNET, new MigCandidateModel.Rate(100, 300), Candidate.NOVA, new MigCandidateModel.Rate(100, 300));
        var job = new MigJob(id, State.ESTIMATED, NOW.getEpochSecond(), NOW.plusSeconds(3_600).getEpochSecond(),
                pack, List.of(), 52, rates, List.of(), List.of(), List.of(), null, null, null, null, null, null, ModelMode.LOCAL_FIXTURE, null, null);
        store.create(job);
        return job;
    }

    private static final class ScenarioModel implements MigCandidateModel {
        ModelMode mode = ModelMode.LOCAL_FIXTURE;
        @Override public ModelMode mode() { return mode; }
        int sonnetCalls;
        int novaCalls;
        boolean ambiguous;

        @Override public Rate rate(Candidate candidate) { return new Rate(100, 300); }

        @Override
        public Result propose(Candidate candidate, UUID invocation, MigEvidence.Pack pack, byte[] evidence, Rate rate) {
            assertThat(new String(evidence, StandardCharsets.UTF_8)).doesNotContain("held-out payload");
            if (candidate == Candidate.SONNET) sonnetCalls++;
            else if (++novaCalls == 1) return new Result(null, ambiguous ? null : 0L, "model unavailable");
            var requirement = new Requirement("Mandatory order number", List.of(pack.sections().getFirst().anchor()), "MANDATORY",
                    "BGM", null, null, null, null, List.of(), null, null, "Unsupported release", null, List.of());
            return new Result(new ModelRules(List.of(requirement)), 1L, null);
        }
    }

    private static final class DynamoFixture {
        private final Map<List<String>, Map<String, AttributeValue>> rows = new HashMap<>();
        boolean failAfterSettlement;

        DynamoDbClient client() {
            return (DynamoDbClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DynamoDbClient.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getItem" -> {
                            var builder = GetItemRequest.builder();
                            configure(args[0], builder);
                            var request = builder.build();
                            yield GetItemResponse.builder().item(rows.getOrDefault(key(request.tableName(), request.key()), Map.of())).build();
                        }
                        case "transactWriteItems" -> {
                            var builder = TransactWriteItemsRequest.builder();
                            configure(args[0], builder);
                            transact(builder.build().transactItems());
                            yield TransactWriteItemsResponse.builder().build();
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @SuppressWarnings("unchecked")
        private static <T> void configure(Object consumer, T builder) { ((Consumer<T>) consumer).accept(builder); }

        private void transact(List<TransactWriteItem> transaction) {
            var after = new HashMap<>(rows);
            boolean settled = false;
            for (var write : transaction) {
                if (write.put() != null) {
                    var put = write.put();
                    after.put(key(put.tableName(), put.item()), put.item());
                } else if (write.delete() != null) {
                    var delete = write.delete();
                    after.remove(key(delete.tableName(), delete.key()));
                } else if (write.update() != null) {
                    var update = write.update();
                    var k = key(update.tableName(), update.key());
                    var row = new HashMap<>(after.getOrDefault(k, update.key()));
                    var values = update.expressionAttributeValues();
                    var amount = values.get(":charge");
                    if (amount == null) { amount = values.get(":refund"); settled |= amount != null; }
                    if (amount == null) row.put("digest", values.get(":digest"));
                    else row.put("amount", AttributeValue.fromN(Long.toString(number(row.get("amount")) + number(amount))));
                    after.put(k, row);
                }
            }
            rows.clear();
            rows.putAll(after);
            if (settled && failAfterSettlement) {
                failAfterSettlement = false;
                throw new IllegalStateException("simulated response loss");
            }
        }

        long amount() {
            return number(rows.getOrDefault(List.of("allowance", "USAGE#" + TENANT, "2026-11"), Map.of()).get("amount"));
        }

        private static long number(AttributeValue value) { return value == null ? 0 : Long.parseLong(value.n()); }
        private static List<String> key(String table, Map<String, AttributeValue> row) {
            return List.of(table, row.get("pk").s(), row.get("sk").s());
        }
    }
}
