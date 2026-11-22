package com.edispark.wireup.mcp;

import static org.assertj.core.api.Assertions.*;

import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantExecution;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.metadata.DefaultUsage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

class DeskUsageLocalTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "DESK_TEST_DYNAMO_PORT", matches = "[0-9]+")
    void parallelReservationsShareLimitsAcrossReplicasAndRollOverInUtc() throws Exception {
        var table = "desk-usage-test-" + UUID.randomUUID();
        try (var db = DynamoDbClient.builder().region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://127.0.0.1:" + System.getenv("DESK_TEST_DYNAMO_PORT")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))).build()) {
            db.createTable(r -> r.tableName(table).billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(attr("pk"), attr("sk")).keySchema(key("pk", KeyType.HASH), key("sk", KeyType.RANGE)));
            try {
                exerciseLimits(db, table);
            } finally {
                db.deleteTable(r -> r.tableName(table));
            }
        }
    }

    private void exerciseLimits(DynamoDbClient db, String table) throws Exception {
        var nov = Clock.fixed(Instant.parse("2026-11-30T23:59:00Z"), ZoneOffset.UTC);
        var dec = Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC);
        var first = new DeskUsage(db, table, 30_000, 8, nov);
        var second = new DeskUsage(db, table, 30_000, 8, nov);
        var tenant = TenantId.newTenantId().asString();
        var other = TenantId.newTenantId().asString();
        var start = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var calls = java.util.stream.Stream.of(first, second).map(replica -> workers.submit(() -> {
                start.await();
                return TenantExecution.get(tenant, () -> {
                    try { replica.reserve(8); return true; }
                    catch (DeskUsage.Refusal denied) { return false; }
                });
            })).toList();
            start.countDown();
            assertThat(calls.get(0).get() ^ calls.get(1).get()).isTrue();
        }
        assertThat(TenantExecution.get(tenant, first::summary).usedCredits()).isEqualByComparingTo("3.4062");
        assertThat(TenantExecution.get(other, first::summary).usedCredits()).isZero();
        assertThat(TenantExecution.get(tenant, new DeskUsage(db, table, 30_000, 8, dec)::summary).usedCredits()).isZero();
        assertThat(DeskUsage.configuredAllowance("1000", 0)).isEqualTo(5_000_000);
        assertThat(DeskUsage.configuredAllowance("", 500)).isEqualTo(5_000_000);
        var burst = new DeskUsage(db, table, DeskUsage.configuredAllowance("1000", 0), 1, nov);
        TenantExecution.run(other, () -> {
            var hold = burst.reserve(8);
            burst.settle(hold, new DefaultUsage(1000, 100));
            burst.settle(hold, new DefaultUsage(1000, 100));
            assertThat(burst.summary().usedCredits()).isEqualByComparingTo("0.826");
            assertThat(burst.summary().remainingCredits()).isEqualByComparingTo("999.174");
            assertThatThrownBy(() -> burst.reserve(8)).hasMessageContaining("minute");
        });
        var policyKey = Map.of("pk", AttributeValue.fromS("POLICY#" + other), "sk", AttributeValue.fromS("ALLOWANCE"));
        db.updateItem(r -> r.tableName(table).key(policyKey).updateExpression("SET monthlyCredits = :limit")
                .expressionAttributeValues(Map.of(":limit", AttributeValue.fromN("3"))));
        assertThat(TenantExecution.get(other, burst::summary).remainingCredits()).isEqualByComparingTo("2.174");

        db.putItem(r -> r.tableName(table).item(Map.of("pk", AttributeValue.fromS("POLICY#" + other),
                "sk", AttributeValue.fromS("ALLOWANCE"), "monthlyCents", AttributeValue.fromN("0"))));
        TenantExecution.run(other, () -> assertThatThrownBy(() -> burst.reserve(8)).hasMessageContaining("insufficient"));
    }

    private static AttributeDefinition attr(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }
    private static KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }
}
