package com.edispark.mft.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class MftRepositoryPropertyTest {
    private io.cottn.core.tenancy.TenantContext.Scope fixtureScope;

    @org.junit.jupiter.api.BeforeEach
    @net.jqwik.api.lifecycle.BeforeTry
    void bindFixtureTenant() {
        fixtureScope = io.cottn.core.tenancy.TenantContext.where(io.cottn.core.tenancy.TenantContextHolder.of(
                com.edispark.identifiers.tenant.TenantId.fromString("2OnHVzeAJKwm0ThFLcy1HfLHGPm")));
    }

    @org.junit.jupiter.api.AfterEach
    @net.jqwik.api.lifecycle.AfterTry
    void restoreFixtureTenant() {
        if (fixtureScope != null) fixtureScope.close();
    }

    @Test
    void processingTransferBecomesRecoverableAfterItsWorkerDeadline() {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Transfer transfer = Transfer.outbound("2OnHVzeAJKwm0ThFLcy1HfLHGPm", UUID.randomUUID(), "order.edi", "application/edi-x12",
                "<message@example.test>", UUID.randomUUID().toString(), null, now);
        transfer.startAttempt(now);
        com.edispark.testing.FixtureTenants.persist(transfer, record -> record.tenantId(), repository::save);

        assertThat(repository.listPendingTransfers(now.plusSeconds(119), 10)).isEmpty();
        assertThat(repository.listPendingTransfers(now.plusSeconds(120), 10)).containsExactly(transfer);

    }

    /** Feature: mft-as2, Property 1: repeated outbound submissions are idempotent. */
    @Property(tries = 100)
    void outboundIdempotencyReturnsOneTransfer(@ForAll String generatedKey) {
        UUID idempotencyKey = UUID.nameUUIDFromBytes(generatedKey.getBytes());
        InMemoryMftRepository repository = new InMemoryMftRepository();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Transfer first = Transfer.outbound("2OnHVzeAJKwm0ThFLcy1HfLHGPm", UUID.randomUUID(), "order.edi", "application/edi-x12",
                "<first@example.test>", idempotencyKey.toString(), null, now);
        Transfer duplicate = Transfer.outbound("2OnHVzeAJKwm0ThFLcy1HfLHGPm", first.partnerId(), "order.edi", "application/edi-x12",
                "<second@example.test>", idempotencyKey.toString(), null, now);

        assertThat(com.edispark.testing.FixtureTenants.persist(first, record -> record.tenantId(), repository::saveOutboundIfAbsent)).isSameAs(first);
        assertThat(com.edispark.testing.FixtureTenants.persist(duplicate, record -> record.tenantId(), repository::saveOutboundIfAbsent)).isSameAs(first);
        assertThat(io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.listTransfers())).containsExactly(first);
    }

    /** Feature: mft-as2, Property 3: tenant queries do not cross boundaries. */
    @Property(tries = 100)
    void tenantQueriesRemainIsolated(@ForAll String generatedSeed) {
        UUID seed = UUID.nameUUIDFromBytes(generatedSeed.getBytes());
        InMemoryMftRepository repository = new InMemoryMftRepository();
        PartnerProfile first = partner("2OnHVzeAJKwm0ThFLcy1HfLHGPm", "A-" + seed);
        PartnerProfile second = partner("3PnIWafBKLxn1UiGMdz2IgMIRNn", "B-" + seed);
        com.edispark.testing.FixtureTenants.persist(first, record -> record.tenantId(), repository::savePartner);
        com.edispark.testing.FixtureTenants.persist(second, record -> record.tenantId(), repository::savePartner);

        assertThat(io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.listPartners())).containsExactly(first);
        assertThat(io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.findPartner(second.id()))).isEmpty();
    }

    @Property(tries = 100)
    void deliveryLeaseHasOneOwnerAndCanOnlyBeReclaimedAfterExpiry(@ForAll String ownerSeed) {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        TransferId transferId = TransferId.generate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String firstOwner = "first-" + ownerSeed;

        assertThat(io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.tryAcquireDeliveryLease(transferId, firstOwner, now, now.plusSeconds(60)))).isTrue();
        assertThat(io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.tryAcquireDeliveryLease(transferId, "second", now.plusSeconds(30), now.plusSeconds(90)))).isFalse();
        io.cottn.core.tenancy.TenantExecution.run("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.releaseDeliveryLease(transferId, "wrong-owner"));
        assertThat(io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.tryAcquireDeliveryLease(transferId, "second", now.plusSeconds(30), now.plusSeconds(90)))).isFalse();
        assertThat(io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.tryAcquireDeliveryLease(transferId, "second", now.plusSeconds(61), now.plusSeconds(121)))).isTrue();
    }

    private PartnerProfile partner(String tenant, String as2Id) {
        return new PartnerProfile(UUID.randomUUID(), tenant, "Partner", "LOCAL-" + as2Id, as2Id,
                URI.create("https://partner.example/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, true, false,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
