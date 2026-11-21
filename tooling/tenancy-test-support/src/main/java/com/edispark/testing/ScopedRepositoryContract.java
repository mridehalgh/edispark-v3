package com.edispark.testing;

import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.NoTenantSetException;
import io.cottn.core.tenancy.TenantExecution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.*;

/** Reused by durable and disposable adapters. This artifact is a test-scope dependency only. */
public abstract class ScopedRepositoryContract<T> {
    protected abstract Operations<T> operations();
    protected abstract T fixture(String owner, UUID id);

    public record Operations<T>(Consumer<T> save, Function<UUID, Optional<T>> find,
                                   Supplier<List<T>> list, Optional<Consumer<UUID>> delete) { }

    @Test
    void tenantAuthorityIsRequiredBeforeReadsOrWrites() {
        var port = operations();
        var id = UUID.randomUUID();
        var item = fixture(TenantId.newTenantId().asString(), id);
        assertThatThrownBy(() -> port.find().apply(id)).isInstanceOf(NoTenantSetException.class);
        assertThatThrownBy(() -> port.list().get()).isInstanceOf(NoTenantSetException.class);
        assertThatThrownBy(() -> port.save().accept(item)).isInstanceOf(NoTenantSetException.class);
        port.delete().ifPresent(delete -> assertThatThrownBy(() -> delete.accept(id)).isInstanceOf(NoTenantSetException.class));
    }

    @Test
    void identicalIdsAreIndependentAndForeignWritesAreRejected() {
        verifyIsolation(UUID.randomUUID());
    }

    @Property(tries = 100)
    public void tenantIsolationHoldsForReusedIds(@ForAll @IntRange(min = 1, max = 1000000) int sequence) {
        verifyIsolation(new UUID(0, sequence));
    }

    private void verifyIsolation(UUID id) {
        var port = operations();
        String a = TenantId.newTenantId().asString();
        String b = TenantId.newTenantId().asString();
        T first = fixture(a, id);
        T second = fixture(b, id);
        TenantExecution.run(a, () -> port.save().accept(first));
        TenantExecution.run(b, () -> {
            assertThat(port.find().apply(id)).isEmpty();
            port.save().accept(second);
            assertThat(port.find().apply(id)).hasValueSatisfying(value -> assertThat(value).usingRecursiveComparison().isEqualTo(second));
            assertThat(port.list().get()).usingRecursiveFieldByFieldElementComparator().containsExactly(second);
            assertThatThrownBy(() -> port.save().accept(first)).isInstanceOf(SecurityException.class);
        });
        TenantExecution.run(a, () -> {
            assertThat(port.find().apply(id)).hasValueSatisfying(value -> assertThat(value).usingRecursiveComparison().isEqualTo(first));
            assertThat(port.list().get()).usingRecursiveFieldByFieldElementComparator().containsExactly(first);
            port.delete().ifPresent(delete -> {
                delete.accept(id);
                assertThat(port.find().apply(id)).isEmpty();
            });
        });
        TenantExecution.run(b, () -> assertThat(port.find().apply(id))
                .hasValueSatisfying(value -> assertThat(value).usingRecursiveComparison().isEqualTo(second)));
    }
}
