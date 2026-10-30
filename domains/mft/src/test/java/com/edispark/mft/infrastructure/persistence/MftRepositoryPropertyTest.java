package com.edispark.mft.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class MftRepositoryPropertyTest {
    /** Feature: mft-as2, Property 1: repeated outbound submissions are idempotent. */
    @Property(tries = 100)
    void outboundIdempotencyReturnsOneTransfer(@ForAll String generatedKey) {
        UUID idempotencyKey = UUID.nameUUIDFromBytes(generatedKey.getBytes());
        InMemoryMftRepository repository = new InMemoryMftRepository();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Transfer first = Transfer.outbound("tenant", UUID.randomUUID(), "order.edi", "application/edi-x12",
                "<first@example.test>", idempotencyKey.toString(), null, now);
        Transfer duplicate = Transfer.outbound("tenant", first.partnerId(), "order.edi", "application/edi-x12",
                "<second@example.test>", idempotencyKey.toString(), null, now);

        assertThat(repository.saveOutboundIfAbsent(first)).isSameAs(first);
        assertThat(repository.saveOutboundIfAbsent(duplicate)).isSameAs(first);
        assertThat(repository.listTransfers("tenant")).containsExactly(first);
    }

    /** Feature: mft-as2, Property 3: tenant queries do not cross boundaries. */
    @Property(tries = 100)
    void tenantQueriesRemainIsolated(@ForAll String generatedSeed) {
        UUID seed = UUID.nameUUIDFromBytes(generatedSeed.getBytes());
        InMemoryMftRepository repository = new InMemoryMftRepository();
        PartnerProfile first = partner("tenant-a", "A-" + seed);
        PartnerProfile second = partner("tenant-b", "B-" + seed);
        repository.savePartner(first);
        repository.savePartner(second);

        assertThat(repository.listPartners("tenant-a")).containsExactly(first);
        assertThat(repository.findPartner("tenant-a", second.id())).isEmpty();
    }

    private PartnerProfile partner(String tenant, String as2Id) {
        return new PartnerProfile(UUID.randomUUID(), tenant, "Partner", "LOCAL-" + as2Id, as2Id,
                URI.create("https://partner.example/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, true, false,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
