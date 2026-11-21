package com.edispark.partnerships.infrastructure;
import com.edispark.partnerships.domain.model.*;
import java.util.*;
import java.time.Instant;
import com.edispark.partnerships.domain.repository.SsccCreation;
import io.cottn.core.tenancy.TenantAccess;
public interface SsccCreationAdapter extends SsccCreation {
    default SsccIssuer draft(String name, String prefix, int extension, long start, long end, int reuseMonths, boolean isDefault, boolean attested, Instant now) { return SsccIssuer.draft(TenantAccess.currentId(), name, prefix, extension, start, end, reuseMonths, isDefault, attested, now); }
    default SsccReservation reservation(SsccIssuer issuer, String idempotencyKey, String context, List<String> values, Instant now, Instant expiresAt) { return new SsccReservation(UUID.randomUUID(), TenantAccess.requireOwner(issuer.tenantId()), issuer.id(), idempotencyKey, context, values, now, expiresAt); }
}
