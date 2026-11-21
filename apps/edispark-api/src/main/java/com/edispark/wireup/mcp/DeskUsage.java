package com.edispark.wireup.mcp;

import io.cottn.core.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.metadata.Usage;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/** All amounts in the ledger are integer millionths of a US dollar. */
final class DeskUsage {
    static final int OUTPUT_TOKENS = 768;
    private static final int INPUT_TOKEN_CEILING = 128_000;
    private static final long MICROS_PER_CENT = 10_000;
    private static final long MICROS_PER_CREDIT = 5_000;
    private static final long MAX_ALLOWANCE_MICROS = 1_000_000_000_000L;
    private final DynamoDbClient db;
    private final String table;
    private final long defaultMicros;
    private final int roundsPerMinute;
    private final Clock clock;

    DeskUsage(DynamoDbClient db, String table, long defaultMicros, int roundsPerMinute, Clock clock) {
        if (defaultMicros < 0 || defaultMicros > MAX_ALLOWANCE_MICROS || roundsPerMinute < 1 || roundsPerMinute > 10_000)
            throw new IllegalArgumentException("Invalid organisation AI allowance");
        this.db = db;
        this.table = table;
        this.defaultMicros = defaultMicros;
        this.roundsPerMinute = roundsPerMinute;
        this.clock = clock;
    }

    record Summary(BigDecimal monthlyCredits, BigDecimal usedCredits, BigDecimal remainingCredits, int roundsPerMinute, Instant resetsAt) {}
    record Reservation(Map<String, AttributeValue> monthKey, String settlementId) {}
    static final class Refusal extends RuntimeException {
        final String code;
        Refusal(String code, String message) { super(message); this.code = code; }
    }

    Summary summary() {
        var month = YearMonth.now(clock);
        var used = read(key("USAGE", month.toString())).get("amount");
        long balance = used == null ? 0 : Long.parseLong(used.n());
        long allowance = allowanceMicros(read(key("POLICY", "ALLOWANCE")));
        return new Summary(credits(allowance), credits(balance), credits(Math.max(0, allowance - balance)), roundsPerMinute,
                month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    static long configuredAllowance(String credits, long legacyCents) {
        return credits.isBlank() ? Math.multiplyExact(legacyCents, MICROS_PER_CENT)
                : Math.multiplyExact(Long.parseLong(credits), MICROS_PER_CREDIT);
    }

    private static BigDecimal credits(long micros) {
        return BigDecimal.valueOf(micros).divide(BigDecimal.valueOf(MICROS_PER_CREDIT));
    }

    private long allowanceMicros(Map<String, AttributeValue> policy) {
        var credits = policy.get("monthlyCredits");
        var cents = policy.get("monthlyCents");
        long allowance = credits != null ? configuredAllowance(credits.n(), 0)
                : cents != null ? configuredAllowance("", Long.parseLong(cents.n())) : defaultMicros;
        if (allowance < 0 || allowance > MAX_ALLOWANCE_MICROS) throw new IllegalStateException("Invalid stored allowance");
        return allowance;
    }

    private Map<String, AttributeValue> read(Map<String, AttributeValue> key) {
        return db.getItem(r -> r.tableName(table).key(key).consistentRead(true)).item();
    }

    // Allowance rates include headroom: $0.10/M input tokens, $0.30/M output tokens.
    private static long tokenCost(int input, int output) { return (input + 3L * output + 9) / 10; }

    Reservation reserve(long guardUnits) {
        long ceiling = tokenCost(INPUT_TOKEN_CEILING, OUTPUT_TOKENS) + Math.multiplyExact(guardUnits, 500L);
        var policyKey = key("POLICY", "ALLOWANCE");
        var policy = read(policyKey);
        long limit = allowanceMicros(policy);
        var pin = ConditionCheck.builder().tableName(table).key(policyKey);
        if (policy.isEmpty()) pin.conditionExpression("attribute_not_exists(pk)");
        else if (policy.containsKey("monthlyCredits")) {
            pin.conditionExpression("monthlyCredits = :credits")
                    .expressionAttributeValues(Map.of(":credits", policy.get("monthlyCredits")));
        } else {
            pin.conditionExpression("attribute_not_exists(monthlyCredits) AND monthlyCents = :cents")
                    .expressionAttributeValues(Map.of(":cents", policy.get("monthlyCents")));
        }
        if (ceiling > limit) throw monthlyLimit();
        var now = clock.instant();
        var monthKey = key("USAGE", YearMonth.from(now.atZone(ZoneOffset.UTC)).toString());
        var minuteKey = key("USAGE", "minute#" + now.getEpochSecond() / 60);
        try {
            db.transactWriteItems(r -> r.clientRequestToken(UUID.randomUUID().toString()).transactItems(
                    increment(monthKey, ceiling, limit - ceiling, now.plusSeconds(400L * 86400)),
                    increment(minuteKey, 1, roundsPerMinute - 1, now.plusSeconds(86400)),
                    TransactWriteItem.builder().conditionCheck(pin.build()).build()));
        } catch (TransactionCanceledException ex) {
            var reasons = ex.cancellationReasons();
            if (!reasons.isEmpty() && "ConditionalCheckFailed".equals(reasons.getFirst().code())) throw monthlyLimit();
            if (reasons.size() > 1 && "ConditionalCheckFailed".equals(reasons.get(1).code()))
                throw new Refusal("AI_RATE_LIMIT", "Your organisation has reached its AI limit for this minute. Try again next minute.");
            throw ex;
        }
        return new Reservation(monthKey, UUID.randomUUID().toString());
    }

    private Refusal monthlyLimit() {
        return new Refusal("AI_MONTHLY_LIMIT", "Your organisation has insufficient AI credits for this request. Credits reset on the first of the month (UTC).");
    }

    private TransactWriteItem increment(Map<String, AttributeValue> key, long charge, long remaining, Instant expiry) {
        return TransactWriteItem.builder().update(u -> u.tableName(table).key(key)
                .updateExpression("SET expiresAt = :expiry ADD amount :charge")
                .conditionExpression("attribute_not_exists(amount) OR amount <= :remaining")
                .expressionAttributeValues(Map.of(":expiry", number(expiry.getEpochSecond()), ":charge", number(charge),
                        ":remaining", number(remaining)))).build();
    }

    void settle(Reservation hold, Usage usage) {
        if (usage == null || usage.getPromptTokens() == null || usage.getCompletionTokens() == null
                || usage.getPromptTokens() <= 0 || usage.getCompletionTokens() < 0) return;
        long credit = tokenCost(INPUT_TOKEN_CEILING, OUTPUT_TOKENS) - tokenCost(usage.getPromptTokens(), usage.getCompletionTokens());
        credit(hold, credit);
    }

    void releaseUnusedModel(Reservation hold) {
        credit(hold, tokenCost(INPUT_TOKEN_CEILING, OUTPUT_TOKENS));
    }

    private void credit(Reservation hold, long credit) {
        if (credit <= 0) return;
        // The transaction token makes a retry after an ambiguous network failure safe.
        var update = TransactWriteItem.builder().update(u -> u.tableName(table).key(hold.monthKey())
                .updateExpression("ADD amount :credit").conditionExpression("amount >= :minimum")
                .expressionAttributeValues(Map.of(":credit", number(-credit), ":minimum", number(credit)))).build();
        db.transactWriteItems(r -> r.clientRequestToken(hold.settlementId()).transactItems(update));
    }

    private static Map<String, AttributeValue> key(String namespace, String period) {
        return Map.of("pk", AttributeValue.fromS(namespace + "#" + TenantContext.getCurrentTenant().asString()),
                "sk", AttributeValue.fromS(period));
    }

    private static AttributeValue number(long value) { return AttributeValue.fromN(Long.toString(value)); }
}
