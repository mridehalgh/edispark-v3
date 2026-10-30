---
name: distributed-systems-design
description: Use when designing, building, or reviewing distributed systems — services communicating over networks, multi-tenant architectures, queue-based processing, caching layers, retry logic, consistency across datastores, or any system where partial failure, concurrency, and latency are concerns. Also use when someone mentions race conditions, idempotency, circuit breakers, rate limiting, backpressure, deployment safety, or blast radius reduction. Even if the user doesn't say "distributed systems," use this skill whenever the architecture involves multiple processes, services, or data stores that must coordinate.
---

# Distributed Systems Design

Production-grade patterns for building reliable distributed systems. Covers concurrency, consistency, resilience, operational safety, and blast radius containment — informed by the Amazon Builders' Library, Google SRE, and industry literature (Kleppmann, Brewer, Dean).

## When to Use

Use this skill when:
- Designing services that communicate over a network
- Implementing retry logic, timeouts, or circuit breakers
- Building multi-tenant systems with fairness requirements
- Choosing consistency models across datastores
- Reviewing code for concurrency bugs or failure mode gaps
- Designing caching, queuing, or event-driven architectures
- Planning deployment strategies for distributed services

## Universal Rules

These apply to every distributed system. Violating any of them will eventually cause an outage.

1. **Set timeouts on every outbound call.** No call should wait indefinitely. Choose timeouts based on the dependency's p99 latency plus margin, not framework defaults (which are usually too long).

2. **Make mutations idempotent.** Assume every message will be delivered at least twice. Use idempotency keys, conditional writes, or naturally idempotent operations.

3. **Add jitter to everything periodic.** Retries, cron jobs, cache TTLs, health check intervals, reconnection delays. Without jitter, synchronized actions cause thundering herds.

4. **Propagate deadlines, not just timeouts.** Pass an end-to-end deadline through the call chain. Each service deducts time already spent. Don't start work the caller has already given up on.

5. **Measure and alert on percentiles** (p99, p99.9), not averages. At scale, most users experience the tail — if a page makes 100 backend calls, each with 1% chance of being slow, 63% of page loads hit the tail.

6. **Design for graceful degradation.** Classify dependencies as critical vs non-critical. When a non-critical dependency fails, serve a degraded response, not an error.

7. **Test failure modes explicitly.** If you haven't tested what happens when a dependency is down, you don't know what happens. Untested fallback paths fail when needed most.

8. **Limit blast radius at every layer.** Cells, shuffle sharding, bulkheads, canary deployments — contain failures to the smallest possible subset of users.

9. **Prefer constant work over event-driven reactions** for reliability-critical paths. Systems that do constant work have predictable resource usage and degrade gracefully under load.

10. **Keep the data plane statically stable.** It should operate with what it already has, even if the control plane is unavailable.

## Decision Framework

When facing a distributed systems design decision, use this table to pick the right pattern. Read the relevant reference file for implementation details.

| Problem | First Choice | Escalate To | Reference |
|---|---|---|---|
| Concurrent writes to same record | Optimistic locking (version column) | Pessimistic locking if contention is high | `references/concurrency.md` |
| Duplicate requests | Idempotency keys + conditional writes | Dedup table if operations have side effects | `references/concurrency.md` |
| Per-entity ordering | FIFO queue with entity ID as group key | Total ordering only if truly required (rare) | `references/concurrency.md` |
| Protecting against overload | Token bucket rate limiting | Load shedding + circuit breakers | `references/resilience.md` |
| Dependency failure | Timeout + circuit breaker + degradation | Bulkhead if multiple dependencies | `references/resilience.md` |
| Transient failure | Exponential backoff with decorrelated jitter | Retry budget if amplification risk | `references/resilience.md` |
| Queue processing | Bounded queue + DLQ + backpressure | Message deadlines for load shedding | `references/resilience.md` |
| Cross-service consistency | Saga with compensating transactions | Outbox pattern for reliable events | `references/consistency.md` |
| Caching hot data | Cache-aside with TTL | Request coalescing + early expiry for stampede | `references/consistency.md` |
| Data scaling | Partition by entity ID | Consistent hashing if topology changes often | `references/consistency.md` |
| Multi-tenant fairness | Per-tenant rate + concurrency limits | Shuffle sharding or cell architecture | `references/blast-radius.md` |
| Deployment safety | Canary with automated rollback | Cell-based deployment for isolation | `references/operations.md` |
| Observability | Structured logs + distributed tracing | Percentile-based SLOs + error budgets | `references/operations.md` |

## Code Review Checklist

When reviewing distributed systems code, walk through these checks systematically. Each is a common source of production incidents.

### Retry Logic
- [ ] Uses exponential backoff with jitter (not fixed delay)? Decorrelated jitter: `sleep = min(cap, random(base, prev * 3))`
- [ ] Only retries transient errors (5xx, 429, timeouts, network)? Never retries 400/401/403/404?
- [ ] Retry amplification controlled? If service A retries 3x to B which retries 3x to C, one failure at C causes 27 requests. Fix: retry only at outermost layer, or use retry budgets (retries < 10% of total traffic)
- [ ] Mutating operations have idempotency keys? Especially payments, order creation, inventory changes
- [ ] Overall deadline propagated through the call chain? (Distinct from per-call timeouts — a deadline is end-to-end; a timeout is per-hop. Without deadline propagation, retries continue past the caller's patience)

### Dependencies
- [ ] Every outbound call has an explicit timeout? (Framework defaults are usually 30-60s — too long)
- [ ] Dependencies classified as critical vs non-critical? Non-critical failures (notifications, analytics, recommendations) should degrade gracefully, never fail the request
- [ ] Circuit breakers on critical dependencies? Per-endpoint, not global
- [ ] Bulkheads isolating dependency pools? A slow dependency should exhaust only its resource pool

### Data Consistency
- [ ] No dual writes? (Writing to DB + publishing event must use outbox pattern or CDC, not two independent operations)
- [ ] Multi-step operations use saga pattern with compensating transactions?
- [ ] Compensating transactions are themselves idempotent and retriable?

### Caching
- [ ] Cache stampede protection? (Request coalescing, probabilistic early expiry, or background refresh)
- [ ] TTLs have jitter to prevent synchronized expiration?
- [ ] Error responses are not cached (or have very short TTL)?
- [ ] Cold-cache scenario handled? (Pre-warming, gradual traffic shift)

### Multi-Tenancy
- [ ] Per-tenant rate limits AND concurrency limits? (Rate limits cap throughput; concurrency limits cap parallelism)
- [ ] Fairness enforced at every bottleneck, not just the API gateway?

## Common Mistakes (Quick Reference)

| Mistake | Why It's Dangerous | Fix |
|---|---|---|
| Retrying without backoff + jitter | Creates retry storms that prevent recovery | Decorrelated jitter: `sleep = min(cap, random(base, prev * 3))` |
| Retrying non-idempotent operations | Duplicates payments, orders, side effects | Add idempotency keys or make operations naturally idempotent |
| Retry at every layer (3^depth amplification) | Single failure at depth 3 causes 27 requests | Retry only at the outermost layer, or use retry budgets |
| No deadline propagation (only per-call timeouts) | Retries continue past the caller's patience, wasting resources | Pass end-to-end deadline through the call chain; each hop deducts time spent |
| Unbounded queues | Grow until OOM, then fail catastrophically | Bound queue depth, reject or shed when full |
| Cache without stampede protection | Hot key expires, all clients hit DB simultaneously | Request coalescing, probabilistic early expiry, or background refresh |
| Distributed lock without fencing token | GC pause lets two holders write concurrently | Use consensus-based locks (ZooKeeper, etcd) with fencing tokens |
| Health checks that call dependencies | Dependency failure cascades through health checks | Health checks should be local-only |
| Fallback code paths never tested | Fail exactly when needed — during actual outages | Prefer degradation over untested alternate paths |
| Dual writes (DB + event broker) | Either operation can fail independently → inconsistency | Outbox pattern: write both in one DB transaction, relay to broker separately |
| Clock-based expiry for correctness | Clock skew between nodes violates assumptions | Use logical clocks or fencing mechanisms |
| Average-based alerting | Hides tail latency that most users experience | Alert on p99/p99.9 sustained degradation |

## Reference Files

Read **only the relevant** reference file for the area you're working on — don't load all of them. Each is self-contained.

- **`references/concurrency.md`** — Race conditions, optimistic/pessimistic locking, fencing tokens, idempotency patterns, message ordering (FIFO, causal, total)
- **`references/resilience.md`** — Retries, circuit breakers, rate limiting, bulkheads, backpressure, queue-based load leveling, dependency management
- **`references/consistency.md`** — Saga pattern, outbox pattern, CDC, caching strategies, partitioning/sharding, distributed consensus (Raft, CRDTs, quorums)
- **`references/blast-radius.md`** — Cell-based architecture, shuffle sharding, static stability, data plane/control plane separation, constant work pattern, multi-tenant fairness
- **`references/operations.md`** — Deployment safety (canary, blue-green, rollback), observability (tracing, structured logging), SLOs, tail latency, error budgets

## Language-Specific Notes

The patterns in this skill are language-agnostic. When implementing:

- **TypeScript/Node.js**: Single-threaded event loop means no data races within a process, but you still have distributed concurrency across instances. Use `AbortController` for timeout propagation. Durations in milliseconds.
- **Java**: Rich concurrency primitives (`ConcurrentHashMap`, `CompletableFuture`, `Semaphore`). Beware GC pauses invalidating lease assumptions — this is the core of Kleppmann's Redlock critique. Libraries: Resilience4j (circuit breakers), Caffeine (caching).
- **Rust**: Ownership model prevents many concurrency bugs at compile time. Use `tokio` for async. `Arc<Mutex<T>>` for shared state. The type system enforces many patterns that are runtime-only in other languages.
