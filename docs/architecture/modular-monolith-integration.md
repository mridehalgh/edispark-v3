# Modular Monolith Integration Pattern

## Decision

EdiSpark is deployed as one Spring Boot application, `apps/edispark-api`, with one
Spring application context and one HTTP listener. Documents, MFT, Partnerships,
Transformations and Exchange remain separate bounded-context modules. The composition
root supplies shared runtime infrastructure and adapters, but business behaviour,
controllers and persistence ownership remain inside their modules.

This is a modular monolith, not a collection of services communicating with themselves
over localhost. A module boundary is a code and ownership boundary. It does not require
a network boundary.

## Terminology

The word **port** has two meanings in this repository:

| Term | Meaning |
|---|---|
| Network port | A TCP listener such as the unified API on `8080` |
| Application port | A small Java interface describing a capability required across a module boundary |

Java application ports do not open sockets. Their in-process adapters make ordinary
method calls in the same JVM.

## Default module call pattern

When a use case needs an immediate result, call the providing module in-process through
an explicit application port or anti-corruption layer (ACL):

```text
Calling module
    │
    ▼
Caller-owned Java port or ACL
    │
    ▼
Composition-root adapter
    │
    ▼
Providing module's public application service
```

The caller owns the contract because the interface expresses what the caller needs,
using the caller's language. The adapter belongs in `apps/edispark-api` because it knows
both modules and translates between their types. The providing module must not depend
back on the caller.

Current examples include:

- `DocumentAcl`, implemented by `InProcessDocumentAcl`
- `TransportAcl`, implemented by `InProcessTransportAcl`
- `QualificationAcl`, implemented by `InProcessQualificationAcl`
- `InboundModuleAccess`, implemented by `InProcessInboundModuleAccess`

Prefer a provider's public application service. Do not call another module's controller,
construct an HTTP request to the same process or mutate another module's aggregates
directly. If no suitable application service exists, add a narrow use-case facade rather
than exposing general repository access. Existing composition adapters that use a
repository should be treated as migration seams, not as the preferred model for new work.

Keep ports coarse-grained and use-case-oriented. Do not create an interface for every
class or mirror a provider service method-for-method without adding a real boundary.

## Choosing the communication mechanism

Use the simplest mechanism that satisfies the required delivery semantics:

| Requirement | Mechanism | Reason |
|---|---|---|
| Immediate answer required in the current request | Direct Java application port | Lowest latency, typed contract and simple failure behaviour |
| Durable background work with one logical consumer | SQS | Buffering, retry, backpressure and dead-letter handling |
| A business fact has multiple independent consumers | EventBridge or SNS with an SQS queue per durable consumer | Publisher does not coordinate subscribers and each consumer has isolated retries |
| Local, non-critical notification where loss is acceptable | In-process Spring event | Lightweight decoupling without claiming durability |
| Integration with an external system or an intentionally separate deployment | HTTPS API or webhook | A real process and trust boundary exists |

Do not introduce SQS, SNS, EventBridge or HTTP merely to make two modules in the same JVM
look like microservices. Distribution is justified by delivery, scaling, ownership,
security or failure-isolation requirements.

### Direct Java calls

Use a synchronous port when the caller cannot continue without the result. Exceptions
remain synchronous and the whole use case is visible in one trace. Cross-module calls
must preserve tenant context and must not create circular Maven dependencies.

A direct call does not imply a shared database transaction. Each module still owns its
state and invariants. Avoid transactions that span bounded contexts. Where a workflow
contains multiple durable steps, record explicit workflow state and make retries safe.

### SQS

Use SQS for work that may complete later, must survive process restarts or needs load
levelling. Consumers must assume at-least-once delivery:

- require a stable event or idempotency identifier
- make mutations idempotent with conditional writes or deduplication state
- bound retries and use exponential backoff with jitter
- configure a dead-letter queue and an operational redrive procedure
- carry tenant, correlation, causation and schema-version metadata
- define ordering only where the business requires it, using FIFO message groups when
  appropriate

Do not place large EDI payloads directly on a queue. Retain the payload in owned storage
and send a tenant-scoped reference plus integrity metadata.

### EventBridge and SNS

Publish immutable, past-tense business facts such as `InboundPayloadReceived` or
`ExchangeDelivered`. EventBridge is preferred when consumers need rule-based routing or
cross-account integration. SNS is suitable for simple fan-out. Durable consumers should
receive events through their own SQS queues so one slow or failing consumer does not
affect the others.

Publishing a database mutation and an event as two independent writes is forbidden for
reliability-critical behaviour. Use an outbox or another atomic publication mechanism
before treating the event as guaranteed.

### In-process events

Spring application events are suitable only for same-process side effects that may be
lost during a crash, such as optional cache invalidation or local instrumentation. They
must not be used for business work that requires delivery, replay, dead-letter handling
or independent scaling.

## HTTP and future extraction

HTTP adapters may exist for compatibility or a future distributed deployment, but the
unified runtime must select in-process adapters by default. Every outbound network call
requires an explicit timeout, bounded retry policy and observable failure behaviour.

A module is ready for extraction when its application contract, data ownership and
operational boundary are already clear. Extraction replaces the composition adapter with
an HTTP or messaging adapter. It must not force domain callers to depend on transport
DTOs. Splitting a deployment also requires an explicit decision covering latency,
partial failure, idempotency, observability, security and operational ownership.

## Composition-root responsibilities

`apps/edispark-api` may contain:

- the Spring Boot entry point and component discovery
- shared authentication, tenant-context propagation and runtime configuration
- OpenAPI grouping, health and observability configuration
- adapters that translate between bounded contexts

It must not contain business controllers, aggregates or business rules. Each module owns
its REST endpoints and persistent state.

## Testing expectations

- Test each port from the caller's perspective, including translations and failure cases.
- Test in-process adapters against the provider's public application behaviour.
- Keep one unified application-context test that proves all modules load with one security
  chain and without self-referential HTTP clients.
- For queues and published events, test duplicate delivery, retry exhaustion, tenant
  isolation and incompatible schema versions.
- Do not replace behaviour tests with tests that merely assert which implementation class
  was called.

## Review checklist

Before adding cross-module communication, confirm:

1. Does the caller require an immediate answer? If yes, prefer a direct Java port.
2. Must the work survive a process crash? If yes, use durable messaging.
3. Is this a command for one owner or a fact for multiple consumers?
4. Who owns the contract, data and retry policy?
5. How are tenant context, idempotency and correlation preserved?
6. Does the design introduce a circular dependency or bypass module invariants?
7. If a network boundary is proposed, what concrete scaling, security, ownership or
   failure-isolation requirement justifies it?

