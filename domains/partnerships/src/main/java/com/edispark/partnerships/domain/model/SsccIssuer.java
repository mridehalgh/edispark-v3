package com.edispark.partnerships.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SsccIssuer(
        UUID id,
        String tenantId,
        String name,
        String gs1CompanyPrefix,
        int extensionDigit,
        long serialStart,
        long serialEnd,
        long nextSerial,
        int reuseAfterMonths,
        boolean defaultIssuer,
        boolean ownershipAttested,
        SsccIssuerStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public SsccIssuer {
        Objects.requireNonNull(id);
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (name == null || name.isBlank() || name.length() > 120) throw new IllegalArgumentException("name is invalid");
        if (gs1CompanyPrefix == null || !gs1CompanyPrefix.matches("\\d{4,12}")) {
            throw new IllegalArgumentException("GS1 Company Prefix must contain 4 to 12 digits");
        }
        if (extensionDigit < 0 || extensionDigit > 9) throw new IllegalArgumentException("extensionDigit must be 0 to 9");
        long maximum = maximumSerial(gs1CompanyPrefix.length());
        if (serialStart < 0 || serialEnd < serialStart || serialEnd > maximum) {
            throw new IllegalArgumentException("serial range does not fit the GS1 Company Prefix");
        }
        if (nextSerial < serialStart || nextSerial > serialEnd + 1) throw new IllegalArgumentException("nextSerial is outside the range");
        if (reuseAfterMonths < 12) throw new IllegalArgumentException("SSCC reuse period must be at least 12 months");
        Objects.requireNonNull(status);
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static SsccIssuer draft(String tenantId, String name, String prefix, int extensionDigit,
            long serialStart, long serialEnd, int reuseAfterMonths, boolean defaultIssuer,
            boolean ownershipAttested, Instant now) {
        return new SsccIssuer(UUID.randomUUID(), tenantId, name, prefix, extensionDigit, serialStart,
                serialEnd, serialStart, reuseAfterMonths, defaultIssuer, ownershipAttested,
                SsccIssuerStatus.DRAFT, 0, now, now);
    }

    public SsccIssuer activate(Instant now) {
        if (status != SsccIssuerStatus.DRAFT) throw new IllegalStateException("Only a draft SSCC issuer can be activated");
        if (!ownershipAttested) throw new IllegalStateException("Confirm ownership of the GS1 Company Prefix before activation");
        return copy(defaultIssuer, SsccIssuerStatus.ACTIVE, nextSerial, version + 1, now);
    }

    public SsccIssuer retire(Instant now) {
        if (status != SsccIssuerStatus.ACTIVE) throw new IllegalStateException("Only an active SSCC issuer can be retired");
        return copy(false, SsccIssuerStatus.RETIRED, nextSerial, version + 1, now);
    }

    public SsccIssuer withDefault(boolean value, Instant now) {
        return copy(value, status, nextSerial, version + 1, now);
    }

    public Allocation reserve(int count, Instant now) {
        if (status != SsccIssuerStatus.ACTIVE) throw new IllegalStateException("SSCC issuer is not active");
        if (count < 1 || count > 100) throw new IllegalArgumentException("Reserve between 1 and 100 SSCCs");
        if (nextSerial + count - 1 > serialEnd) throw new IllegalStateException("SSCC number range is exhausted");
        int width = 16 - gs1CompanyPrefix.length();
        List<String> values = new ArrayList<>(count);
        for (long serial = nextSerial; serial < nextSerial + count; serial++) {
            String body = extensionDigit + gs1CompanyPrefix + String.format("%0" + width + "d", serial);
            values.add(SsccNumber.fromBody(body));
        }
        SsccIssuer updated = copy(defaultIssuer, status, nextSerial + count, version + 1, now);
        return new Allocation(updated, List.copyOf(values));
    }

    public long remaining() {
        return Math.max(0, serialEnd - nextSerial + 1);
    }

    public long capacity() {
        return serialEnd - serialStart + 1;
    }

    public boolean overlaps(SsccIssuer other) {
        return firstBody() <= other.lastBody() && other.firstBody() <= lastBody();
    }

    private long firstBody() {
        return body(serialStart);
    }

    private long lastBody() {
        return body(serialEnd);
    }

    private long body(long serial) {
        int width = 16 - gs1CompanyPrefix.length();
        return Long.parseLong(extensionDigit + gs1CompanyPrefix + String.format("%0" + width + "d", serial));
    }

    private SsccIssuer copy(boolean isDefault, SsccIssuerStatus nextStatus, long serial,
            long nextVersion, Instant now) {
        return new SsccIssuer(id, tenantId, name, gs1CompanyPrefix, extensionDigit, serialStart,
                serialEnd, serial, reuseAfterMonths, isDefault, ownershipAttested, nextStatus,
                nextVersion, createdAt, now);
    }

    private static long maximumSerial(int prefixLength) {
        long value = 1;
        for (int index = 0; index < 16 - prefixLength; index++) value *= 10;
        return value - 1;
    }

    public record Allocation(SsccIssuer issuer, List<String> values) { }
}
