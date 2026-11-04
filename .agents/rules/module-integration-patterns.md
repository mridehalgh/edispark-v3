# Module Integration Patterns 🔌

## Architectural baseline

EdiSpark is a modular monolith. `apps/edispark-api` is the single Spring Boot
composition root. Bounded contexts remain separate Maven modules but run in one JVM and
one Spring application context by default.

Read `docs/architecture/modular-monolith-integration.md` before adding or changing
cross-module communication.

## Terminology

- **Network port** means a TCP listener such as `8080`.
- **Application port** means a Java interface at a module boundary. It is an ordinary
  in-process method call and does not imply networking.

Use the qualified term when ambiguity is possible.

## Required decision order

1. If the caller needs an immediate result, use a caller-owned Java application port or
   ACL with an in-process adapter in `apps/edispark-api`.
2. If work must survive a crash or be load-levelled, use durable messaging, normally SQS.
3. If an immutable business fact has multiple consumers, use EventBridge or SNS with an
   SQS queue per durable consumer.
4. Use Spring events only when loss is explicitly acceptable.
5. Use HTTP only for external systems or an intentionally separate deployment.

Do not use localhost HTTP, SQS, SNS or EventBridge merely to communicate between modules
already running in the same application context.

## Java boundary rules

- The caller owns the port and expresses it in the caller's language.
- The composition root owns adapters that know both modules.
- Adapters should call the provider's public application service and translate types.
- Do not call another module's controller or reuse transport DTOs as domain contracts.
- Do not mutate another module's aggregates or bypass its invariants.
- Prefer a narrow use-case facade when the provider lacks a suitable application API.
- Keep ports coarse-grained. Do not add one interface per implementation class.
- Avoid circular Maven dependencies and cross-context transactions.
- Keep business logic out of `apps/edispark-api`.

## Messaging rules

- Treat delivery as at least once and make consumers idempotent.
- Include tenant, event, correlation, causation and schema-version identifiers.
- Use bounded retries with exponential backoff and jitter plus a DLQ.
- Put large EDI payloads in owned storage and send references with integrity metadata.
- Do not dual-write business state and an event. Use an outbox or equivalent atomic
  publication pattern when delivery is required.
- Define ordering only when required by the business.

## Extraction rule

Replacing an in-process adapter with HTTP or messaging is a deployment decision, not a
domain rewrite. Require a concrete scaling, security, ownership or failure-isolation
reason and document latency, partial failure, timeout, retry, idempotency and operational
consequences before extracting a module.

