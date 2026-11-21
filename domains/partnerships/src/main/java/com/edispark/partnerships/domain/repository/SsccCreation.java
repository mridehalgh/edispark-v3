package com.edispark.partnerships.domain.repository;
import com.edispark.partnerships.domain.model.*;
import java.util.*;
import java.time.Instant;
public interface SsccCreation {
    SsccIssuer draft(String name, String prefix, int extension, long start, long end, int reuseMonths, boolean isDefault, boolean attested, Instant now);
    SsccReservation reservation(SsccIssuer issuer, String idempotencyKey, String context, List<String> values, Instant now, Instant expiresAt);
}
