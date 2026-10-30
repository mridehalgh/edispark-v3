package io.cottn.core.tenancy;

public record TenantAwareDomainEvent<T>(Metadata metadata, T data) {
}
