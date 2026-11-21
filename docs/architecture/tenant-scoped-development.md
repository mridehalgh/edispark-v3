# Developing tenant-scoped features

Business code uses scoped domain ports. Do not accept a tenant selector on an ordinary repository/store method, read execution context, depend on infrastructure, or bind a new tenant in an application service. Keep permissions, partner qualification, and other business authorization explicit.

For example, `MappingService.create` creates and persists a tenant-owned draft without resolving ownership itself:

```java
return repository.save(repository.draft(name, sourceSchemaId, targetSchemaId, nodes, clock.instant()));
```

Creation is a domain capability implemented by an infrastructure adapter. It supplies ownership to pure domain factories. Repository adapters resolve the current tenant on every operation, scope identity with tenant plus entity ID, reject foreign-owned writes, and fail when context is absent. Ownership on records and durable events remains explicit data. The shared mapping catalog is a separate read-only capability; copying a shared definition creates a tenant-owned draft.

Trusted ingress and workers establish context from verified JWTs or durable ownership evidence. Global AS2 resolution and pending-work discovery belong to infrastructure capabilities, never ordinary business ports. Bind before acquiring a lease, keep the scope through processing and lease cleanup, and restore it after every item:

```java
TenantExecution.run(work.tenantId(), () -> processWithLease(work));
```

`TenantExecution.capture` captures the submitting scope for executor handoffs including absence of tenant authority. Unscoped tasks run with no tenant and cannot borrow the executor thread's enclosing scope. The unified application registers its task decorator. Custom executors must apply the same propagation explicitly; durable queue envelopes must carry authenticated ownership. The current context implementation uses lexical `ThreadLocal` scopes, restores MDC and enclosing context, and clears it after completion or failure. It does not automatically propagate through arbitrary threads.

Documents local and S3 content stores independently enforce tenant namespaces, authenticated encryption, canonical content hashes, and integrity verification. Public bundled schemas remain classpath reference resources. Missing production backend/key configuration fails startup. See [storage migration](documents-storage-migration.md) before deploying over retained data.

The [ArchUnit checks](../../tooling/architecture-tests/README.md) enforce these boundaries with an empty violation baseline. Behavioral contracts cover absent context, identical IDs in different tenants, rejected foreign writes, independent deletion, and durable/disposable adapter parity. Add equivalent tests for each new storage capability; structural checks cannot prove runtime isolation or deployment IAM.
