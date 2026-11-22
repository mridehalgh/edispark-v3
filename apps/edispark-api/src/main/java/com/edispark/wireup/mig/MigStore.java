package com.edispark.wireup.mig;

import static com.edispark.integrationapplication.mig.MigGeneration.*;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromN;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import com.edispark.crypto.TenantCipher;
import io.cottn.core.tenancy.TenantAccess;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import tools.jackson.databind.ObjectMapper;

final class MigStore {
    static final long MICROS_PER_CREDIT = 5_000;
    private static final int CHUNK = 240_000;
    private final DynamoDbClient db;
    private final TenantCipher cipher;
    private final ObjectMapper json;
    private final String table;
    private final String allowanceTable;
    private final long defaultAllowance;
    private final Clock clock;

    MigStore(DynamoDbClient db, TenantCipher cipher, ObjectMapper json, String table, String allowanceTable,
            long defaultAllowance, Clock clock) {
        this.db = db;
        this.cipher = cipher;
        this.json = json;
        this.table = table;
        this.allowanceTable = allowanceTable;
        this.defaultAllowance = defaultAllowance;
        this.clock = clock;
    }

    record File(Upload upload, int chunks, long expiresAt) { }
    record Work(String tenant, UUID id, String digest) { }
    record Ledger(UUID id, Candidate candidate, String phase, long reserved, Long settled, String month, String receipt) { }
    record Checkpoint(long version, long leaseUntil, MigJob job) { }

    void upload(Upload document, byte[] raw, long expiry) {
        int blocks = (raw.length + CHUNK - 1) / CHUNK;
        var changes = new ArrayList<TransactWriteItem>();
        changes.add(insert(fileKey(document.id()), new File(document, blocks, expiry), expiry));
        for (int block = 0; block < blocks; block++) {
            var data = Arrays.copyOfRange(raw, block * CHUNK, Math.min(raw.length, (block + 1) * CHUNK));
            var item = encrypted(fileKey(document.id()) + "#" + block, data, expiry);
            changes.add(put(item, null));
        }
        transact(changes);
    }

    File file(UUID id) {
        var item = read(table, key(partition(), fileKey(id)));
        var file = readEncrypted(item, File.class);
        if (file.expiresAt() <= now()) throw missing();
        return file;
    }

    byte[] raw(File file) {
        var output = new ByteArrayOutputStream();
        for (int block = 0; block < file.chunks(); block++) {
            var item = read(table, key(partition(), fileKey(file.upload().id()) + "#" + block));
            output.writeBytes(decrypt(item));
        }
        byte[] bytes = output.toByteArray();
        if (!MigEvidence.digest(bytes).equals(file.upload().digest())) throw new IllegalStateException("Guide content changed.");
        return bytes;
    }

    void create(MigJob job) {
        transact(List.of(writeJob(job, null, 0)));
    }

    boolean demoMatches(UUID id, List<String> digests, ModelMode mode) {
        var item = read(table, key(partition(), jobKey(id)));
        if (item.isEmpty()) return false;
        var previous = readEncrypted(item, MigJob.class);
        if (previous.state() == State.IMPORTED || previous.state() == State.IMPORTING) return true;
        if (previous.state() != State.ESTIMATED && (previous.expiry() > now()
                || previous.attempts().stream().anyMatch(attempt -> attempt.actual() == null))) return true;
        return previous.expiry() > now() && mode == previous.recordedMode() && previous.uploads().stream().map(Upload::digest).toList().equals(digests)
                && previous.pack().profile() != null && MigEvidence.runtimeFingerprint().equals(previous.pack().profile().runtimeDigest());
    }

    void replaceDemo(MigJob job) {
        var item = read(table, key(partition(), jobKey(job.id())));
        if (item.isEmpty()) { create(job); return; }
        var previous = readEncrypted(item, MigJob.class);
        if (previous.state() == State.IMPORTED || previous.state() == State.IMPORTING) return;
        if (previous.state() != State.ESTIMATED && (previous.expiry() > now()
                || previous.attempts().stream().anyMatch(attempt -> attempt.actual() == null))) return;
        save(new Checkpoint(number(item, "revision"), number(item, "leaseUntil"), previous), job, 0, List.of());
    }

    Checkpoint load(UUID id) {
        Checkpoint checkpoint = loadWorker(id);
        var job = checkpoint.job();
        if (job.expiry() <= now() && job.state() != State.IMPORTED && job.state() != State.IMPORTING) throw missing();
        return checkpoint;
    }

    Checkpoint loadWorker(UUID id) {
        var item = read(table, key(partition(), jobKey(id)));
        var job = readEncrypted(item, MigJob.class);
        return new Checkpoint(number(item, "revision"), number(item, "leaseUntil"), job);
    }

    void save(Checkpoint previous, MigJob next, long lease, List<TransactWriteItem> changes) {
        var transaction = new ArrayList<TransactWriteItem>();
        transaction.add(writeJob(next, previous, lease));
        transaction.addAll(changes);
        try {
            transact(transaction);
        } catch (TransactionCanceledException failure) {
            if (conditional(failure, 0)) throw new IllegalStateException("The job changed. Refresh its status and try again.");
            if (conditional(failure, 1) && next.state() == State.QUEUED) {
                throw new IllegalStateException("Your organisation has insufficient available AI credits or its allowance changed.");
            }
            throw failure;
        }
    }

    private TransactWriteItem writeJob(MigJob job, Checkpoint previous, long lease) {
        var item = encrypted(jobKey(job.id()), encode(job),
                job.state() == State.IMPORTED || job.state() == State.IMPORTING ? 0 : job.expiry() + 86_400);
        item.put("revision", numeric(previous == null ? 0 : previous.version() + 1));
        item.put("leaseUntil", numeric(lease));
        return put(item, previous == null ? null : previous.version());
    }

    TransactWriteItem enqueue(MigJob job) {
        var item = key("MIG#OUTBOX", workKey(TenantAccess.currentId(), job.id()));
        item.put("digest", fromS(job.pack().digest()));
        item.put("expiresAt", numeric(job.expiry() + 86_400));
        return put(item, null);
    }

    TransactWriteItem replaceQueueDigest(UUID job, String digest) {
        return TransactWriteItem.builder().update(u -> u.tableName(table)
                .key(key("MIG#OUTBOX", workKey(TenantAccess.currentId(), job)))
                .updateExpression("SET digest = :digest")
                .expressionAttributeValues(Map.of(":digest", fromS(digest)))).build();
    }

    List<Work> work() {
        var request = QueryRequest.builder().tableName(table).keyConditionExpression("pk = :partition")
                .expressionAttributeValues(Map.of(":partition", fromS("MIG#OUTBOX"))).limit(50).build();
        var list = new ArrayList<Work>();
        for (var page : db.queryPaginator(request)) {
            for (var item : page.items()) {
                String sort = item.get("sk").s();
                String[] ids = sort.split("#", 3);
                list.add(new Work(ids[1], UUID.fromString(ids[2]), item.get("digest").s()));
                if (list.size() == 200) return list;
            }
        }
        return list;
    }

    TransactWriteItem dequeue(UUID job) {
        return TransactWriteItem.builder().delete(d -> d.tableName(table)
                .key(key("MIG#OUTBOX", workKey(TenantAccess.currentId(), job)))).build();
    }

    List<TransactWriteItem> reserve(String month, long ceiling) {
        var policyKey = allowanceKey("POLICY", "ALLOWANCE");
        var policy = read(allowanceTable, policyKey);
        long limit = policy.containsKey("monthlyCredits") ? Math.multiplyExact(number(policy, "monthlyCredits"), MICROS_PER_CREDIT)
                : policy.containsKey("monthlyCents") ? Math.multiplyExact(number(policy, "monthlyCents"), 10_000)
                : defaultAllowance;
        if (ceiling > limit) throw new IllegalStateException("Your organisation has insufficient AI credits.");
        var balance = TransactWriteItem.builder().update(u -> u.tableName(allowanceTable)
                .key(allowanceKey("USAGE", month))
                .updateExpression("SET expiresAt = :expiry ADD amount :charge, migReserved :charge")
                .conditionExpression("attribute_not_exists(amount) OR amount <= :remaining")
                .expressionAttributeValues(Map.of(":expiry", numeric(now() + 400L * 86_400), ":charge", numeric(ceiling),
                        ":remaining", numeric(limit - ceiling)))).build();
        var pin = ConditionCheck.builder().tableName(allowanceTable).key(policyKey);
        if (policy.isEmpty()) pin.conditionExpression("attribute_not_exists(pk)");
        else if (policy.containsKey("monthlyCredits")) pin.conditionExpression("monthlyCredits = :policy")
                .expressionAttributeValues(Map.of(":policy", policy.get("monthlyCredits")));
        else pin.conditionExpression("attribute_not_exists(monthlyCredits) AND monthlyCents = :policy")
                .expressionAttributeValues(Map.of(":policy", policy.get("monthlyCents")));
        return List.of(balance, TransactWriteItem.builder().conditionCheck(pin.build()).build());
    }

    TransactWriteItem settle(String month, long reserved, long actual) {
        return TransactWriteItem.builder().update(u -> u.tableName(allowanceTable).key(allowanceKey("USAGE", month))
                .updateExpression("ADD amount :refund, migReserved :release")
                .conditionExpression("migReserved >= :held AND amount >= :held")
                .expressionAttributeValues(Map.of(":refund", numeric(actual - reserved), ":release", numeric(-reserved),
                        ":held", numeric(reserved)))).build();
    }

    TransactWriteItem ledger(UUID job, Ledger ledger, long expiry) {
        var item = encrypted(jobKey(job) + "#INVOCATION#" + ledger.id(), encode(ledger), expiry + 370L * 86_400);
        return TransactWriteItem.builder().put(p -> p.tableName(table).item(item)).build();
    }

    void invocationTurn(UUID job, UUID attempt, int turn, String phase, Long actual, String receipt, long expiry) {
        var item = encrypted(turnKey(job, attempt, turn), encode(new InvocationTurn(phase, actual, receipt)),
                expiry + 370L * 86_400);
        db.putItem(request -> request.tableName(table).item(item));
    }

    long confirmedTurnCost(UUID job, UUID attempt) {
        long total = 0;
        for (int turn = 1; turn <= MigCandidateModel.MAX_TURNS; turn++) {
            var item = read(table, key(partition(), turnKey(job, attempt, turn)));
            if (!item.isEmpty()) {
                var receipt = readEncrypted(item, InvocationTurn.class);
                if (receipt.actual() != null) total = Math.addExact(total, receipt.actual());
            }
        }
        return total;
    }

    private static String turnKey(UUID job, UUID attempt, int turn) {
        return jobKey(job) + "#INVOCATION#" + attempt + "#TURN#" + turn;
    }

    private record InvocationTurn(String phase, Long actual, String receipt) { }

    private TransactWriteItem insert(String sort, Object value, long expiry) {
        return put(encrypted(sort, encode(value), expiry), null);
    }

    private TransactWriteItem put(Map<String, AttributeValue> item, Long expected) {
        var builder = Put.builder().tableName(table).item(item);
        if (expected == null) builder.conditionExpression("attribute_not_exists(pk)");
        else builder.conditionExpression("revision = :revision").expressionAttributeValues(Map.of(":revision", numeric(expected)));
        return TransactWriteItem.builder().put(builder.build()).build();
    }

    private Map<String, AttributeValue> encrypted(String sort, byte[] bytes, long expiry) {
        String tenant = TenantAccess.currentId();
        var result = key(partition(), sort);
        var encrypted = cipher.encrypt(tenant, bytes, context(sort));
        if (encrypted.length > 350_000) throw new IllegalArgumentException("The job evidence exceeds its storage budget.");
        result.put("payload", AttributeValue.fromB(SdkBytes.fromByteArray(encrypted)));
        if (expiry > 0) result.put("expiresAt", numeric(expiry));
        return result;
    }

    private byte[] decrypt(Map<String, AttributeValue> item) {
        if (item.isEmpty()) throw missing();
        return cipher.decrypt(TenantAccess.currentId(), item.get("payload").b().asByteArray(), context(item.get("sk").s()));
    }

    private <T> T readEncrypted(Map<String, AttributeValue> item, Class<T> type) {
        try (var input = new GZIPInputStream(new ByteArrayInputStream(decrypt(item)))) {
            return json.readValue(input, type);
        } catch (IOException invalid) {
            throw new IllegalStateException("The encrypted MIG artifact could not be read.");
        }
    }

    private byte[] encode(Object value) {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
            gzip.write(json.writeValueAsBytes(value));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return output.toByteArray();
    }

    private Map<String, AttributeValue> read(String fromTable, Map<String, AttributeValue> key) {
        return db.getItem(r -> r.tableName(fromTable).key(key).consistentRead(true)).item();
    }

    private void transact(List<TransactWriteItem> changes) {
        db.transactWriteItems(r -> r.transactItems(changes));
    }

    private boolean conditional(TransactionCanceledException failure, int index) {
        return failure.cancellationReasons().size() > index
                && "ConditionalCheckFailed".equals(failure.cancellationReasons().get(index).code());
    }

    private Map<String, AttributeValue> allowanceKey(String namespace, String period) {
        return key(namespace + "#" + TenantAccess.currentId(), period);
    }

    private static Map<String, AttributeValue> key(String partition, String sort) {
        return new HashMap<>(Map.of("pk", fromS(partition), "sk", fromS(sort)));
    }

    private static Map<String, String> context(String sort) {
        return Map.of("tenant_id", TenantAccess.currentId(), "resource_type", "mig-generation", "resource_id", sort);
    }

    private static String partition() { return "TENANT#" + TenantAccess.currentId(); }
    private static String fileKey(UUID id) { return "MIG#UPLOAD#" + id; }
    private static String jobKey(UUID id) { return "MIG#JOB#" + id; }
    private static String workKey(String tenant, UUID id) { return id.toString().substring(0, 8) + "#" + tenant + "#" + id; }
    private static AttributeValue numeric(long value) { return fromN(Long.toString(value)); }
    private static long number(Map<String, AttributeValue> item, String name) { return Long.parseLong(item.getOrDefault(name, numeric(0)).n()); }
    private long now() { return clock.instant().getEpochSecond(); }

    static org.springframework.web.server.ResponseStatusException missing() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
