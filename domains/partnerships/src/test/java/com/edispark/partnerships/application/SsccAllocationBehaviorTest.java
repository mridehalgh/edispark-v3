package com.edispark.partnerships.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.partnerships.domain.model.SsccIssuer;
import com.edispark.partnerships.domain.model.SsccNumber;
import com.edispark.partnerships.infrastructure.InMemorySsccRepository;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class SsccAllocationBehaviorTest {
    private static final String TENANT = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void allocatesCheckDigitValidSsccsAndReturnsTheSameReservationForARetry() {
        var service = new SsccService(new InMemorySsccRepository(), CLOCK);

        TenantContext.runWhere(context(TENANT), () -> {
            var issuer = service.createIssuer("Warehouse range", "1234567", 0,
                    1, 100, 12, true, true);
            service.activate(issuer.id());

            var first = service.reserve(null, 2, "authoring-session:hu-1", "ASN draft");
            var retry = service.reserve(null, 2, "authoring-session:hu-1", "ASN draft");

            assertThat(first.id()).isEqualTo(retry.id());
            assertThat(first.ssccs()).hasSize(2).allMatch(SsccNumber::isValid);
            assertThat(first.ssccs()).containsExactly("012345670000000015", "012345670000000022");
        });
    }

    @Test
    void preventsOverlappingRangesAndRequiresPrefixOwnershipBeforeActivation() {
        var service = new SsccService(new InMemorySsccRepository(), CLOCK);

        TenantContext.runWhere(context(TENANT), () -> {
            service.createIssuer("First range", "1234567", 0, 1, 100, 12, false, true);
            assertThatThrownBy(() -> service.createIssuer("Overlap", "1234567", 0,
                    100, 200, 12, false, true)).hasMessageContaining("overlaps");
            assertThatThrownBy(() -> service.createIssuer("Shorter prefix overlap", "1234", 0,
                    567_000_000_001L, 567_000_000_100L, 12, false, true)).hasMessageContaining("overlaps");

            var unattested = service.createIssuer("Unattested", "7654321", 0,
                    1, 100, 12, false, false);
            assertThatThrownBy(() -> service.activate(unattested.id())).hasMessageContaining("ownership");
        });
    }

    @Test
    void aDraftDefaultDoesNotReplaceTheActiveDefaultUntilItIsActivated() {
        var service = new SsccService(new InMemorySsccRepository(), CLOCK);

        TenantContext.runWhere(context(TENANT), () -> {
            var active = service.createIssuer("Current", "1234567", 0, 1, 100, 12, true, true);
            service.activate(active.id());
            var replacement = service.createIssuer("Replacement", "7654321", 0, 1, 100, 12, true, true);

            assertThat(service.reserve(null, 1, "before-activation", "ASN").issuerId()).isEqualTo(active.id());
            service.activate(replacement.id());
            assertThat(service.reserve(null, 1, "after-activation", "ASN").issuerId()).isEqualTo(replacement.id());
            assertThat(service.listIssuers()).filteredOn(SsccIssuer::defaultIssuer).singleElement()
                    .extracting(SsccIssuer::id).isEqualTo(replacement.id());
        });
    }

    @Test
    void concurrentReservationsNeverReturnTheSameSscc() throws Exception {
        var service = new SsccService(new InMemorySsccRepository(), CLOCK);
        TenantContext.runWhere(context(TENANT), () -> {
            var issuer = service.createIssuer("Concurrent range", "1234567", 1,
                    1, 1000, 12, true, true);
            service.activate(issuer.id());
        });
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 40)
                    .mapToObj(index -> (Callable<String>) () -> TenantContext.getWhere(context(TENANT),
                            () -> service.reserve(null, 1, "workflow:" + index, "ASN").ssccs().getFirst()))
                    .toList();
            var values = executor.invokeAll(tasks).stream().map(future -> {
                try { return future.get(); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();

            assertThat(new HashSet<>(values)).hasSize(40);
            assertThat(values).allMatch(SsccNumber::isValid);
        }
    }

    private static TenantContextHolder context(String tenant) {
        return TenantContextHolder.of(TenantId.fromString(tenant));
    }
}
