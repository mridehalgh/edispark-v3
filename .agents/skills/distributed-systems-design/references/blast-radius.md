# Blast Radius Containment

## Data Plane vs Control Plane Separation

### Why Separate Them

The **data plane** handles steady-state work (serving requests, processing data). The **control plane** handles coordination (deploying configs, scaling, routing changes).

- **Different availability requirements**: Data plane needs five 9s. Control plane can tolerate four 9s.
- **Blast radius**: A control plane bug (bad config push, failed deployment) must not take down the data plane.
- **Static stability**: The data plane continues working with its last known good config if the control plane is unavailable.

### Static Stability

A system is statically stable if it operates correctly without external input or control plane interaction.

**Design principle: the system works with what it already has.**

```
GOOD: Push-based config with local cache
  - Control plane pushes updates → data plane caches locally
  - Control plane goes down → data plane serves from cache indefinitely
  - Stale config triggers alerts, not failures

BAD: Pull-based config on every request
  - Service queries config service per request
  - Config service goes down → service can't route → outage
```

**Rule: A data plane should never fail because the control plane is unavailable.**

Implementation:
- Data plane loads config from local cache on startup, not from control plane
- Control plane pushes updates asynchronously
- Monitor data-plane-to-control-plane lag — stale configs trigger alerts, not failures
- Control plane validates changes before pushing (dry run, canary)

---

## Cell-Based Architecture

Deploy the system as N independent copies (cells). Each cell handles a subset of customers. Cells share nothing — independent databases, queues, compute.

```
                    ┌─────────────┐
                    │   Router    │  ← Thin, extremely simple and reliable
                    │ (by tenant) │
                    └──────┬──────┘
                 ┌─────────┼─────────┐
                 ▼         ▼         ▼
            ┌────────┐ ┌────────┐ ┌────────┐
            │ Cell A │ │ Cell B │ │ Cell C │
            │ DB+App │ │ DB+App │ │ DB+App │
            └────────┘ └────────┘ └────────┘
```

### Properties

- A failure in Cell A affects only Cell A's tenants (~1/N of total)
- No cross-cell dependencies — a cell failure can't cascade
- Evacuation: reassign a failing cell's tenants to healthy cells (requires headroom)

### Sizing

Cells should be **small enough that losing one is tolerable**, not so small that operational overhead is excessive. If you have 10 cells and lose one, 10% of tenants are affected. If you have 100 cells and lose one, 1%.

### When to Use

- Strong tenant isolation requirements
- Regulatory requirements for data residency
- Service needs to scale beyond what a single deployment can handle
- Blast radius must be provably bounded

---

## Shuffle Sharding

Instead of assigning each tenant to a single shard, assign each tenant to a random subset of K shards out of N total.

### Why It Works

The probability that two tenants share ALL their shards drops combinatorially:

| Shards per tenant (K) | Total shards (N) | Combinations C(N,K) | Overlap probability |
|---|---|---|---|
| 2 | 8 | 28 | 3.6% |
| 3 | 8 | 56 | 1.8% |
| 2 | 16 | 120 | 0.8% |
| 3 | 16 | 560 | 0.18% |

A noisy tenant can only affect others who share ALL of the same shards — which is rare.

### Implementation

```typescript
// Assign each tenant to K random shards (deterministic by tenant ID)
function getShards(tenantId: string, totalShards: number, shardsPerTenant: number): number[] {
  // Use a seeded PRNG based on tenant ID for deterministic assignment
  const seed = hash(tenantId);
  const rng = createSeededRng(seed);

  const shards: Set<number> = new Set();
  while (shards.size < shardsPerTenant) {
    shards.add(rng.nextInt(totalShards));
  }
  return Array.from(shards);
}

// Route request to one of the tenant's shards (with failover)
function routeRequest(tenantId: string, request: Request): Response {
  const shards = getShards(tenantId, 8, 2);
  for (const shard of shards) {
    if (isHealthy(shard)) return sendToShard(shard, request);
  }
  throw new Error("All assigned shards unhealthy");
}
```

Used by: AWS Route 53, AWS Lambda, internal Amazon services.

---

## Multi-Tenant Fairness

### The Noisy Neighbor Problem

One tenant's workload degrades service for others. Fairness must be enforced at **every bottleneck**, not just the API gateway.

### Defense in Depth

```
Layer 1: API Gateway     → Per-tenant rate limits
Layer 2: Application     → Per-tenant concurrency limits (semaphore)
Layer 3: Queue           → Per-tenant queue depth limits
Layer 4: Database        → Per-tenant connection pool limits
Layer 5: Infrastructure  → Shuffle sharding or cell isolation
```

Rate limiting at only one layer creates a false sense of security. A tenant can bypass the API rate limit by consuming all DB connections through legitimate-but-expensive queries.

### Weighted Fair Queuing

Each tenant gets a virtual queue. The scheduler serves queues in proportion to their weight:

```typescript
// Simplified weighted fair scheduler
interface TenantWork {
  tenantId: string;
  weight: number;      // Higher = more capacity allocation
  virtualFinishTime: number;
}

function scheduleNext(tenants: TenantWork[]): TenantWork {
  // Serve the tenant with the lowest virtual finish time
  // This naturally gives more capacity to higher-weighted tenants
  tenants.sort((a, b) => a.virtualFinishTime - b.virtualFinishTime);
  const next = tenants[0];
  next.virtualFinishTime += 1 / next.weight; // Higher weight = smaller increment = more frequent service
  return next;
}
```

### Admission Control

Reject or defer work early when the system approaches capacity:
- Cheaper to reject at the door than to accept and fail expensively deep in the stack
- Per-tenant concurrency limits: cap in-flight requests per tenant
- Return 429 with `Retry-After` header — give tenants actionable information

---

## Constant Work Pattern

Design systems to do the same amount of work regardless of current state. This eliminates bimodal behavior.

### The Problem with Event-Driven Scaling

Event-driven systems are idle when nothing happens and spike when events arrive. This creates:
- Resource usage that's hard to predict
- Thundering herd when many events arrive simultaneously
- Failure modes that only appear under load (untested in normal operation)

### The Constant Work Alternative

```
EVENT-DRIVEN (bimodal):
  0 events  → 0 work
  10 events → 10 units of work
  10000 events → 10000 units of work (overload!)

CONSTANT WORK (uniform):
  Always polls at fixed rate → always does N units of work
  0 events  → N polls, 0 results processed
  10 events → N polls, 10 results processed
  10000 events → N polls, processes what it can (graceful backlog)
```

**Key insight**: The failure mode of constant work is a growing backlog (graceful degradation), not catastrophic overload.

### Examples
- **Health checking**: Continuously check at a fixed rate. Don't burst-check when something fails.
- **Config propagation**: Poll for config changes at a fixed rate. Don't event-drive config pushes that can stampede.
- **Queue processing**: Process at a steady rate. Backlog grows during spikes but the system never overloads.

---

## The Thundering Herd

Many clients simultaneously perform the same action — cache expiry, service restart, config change, cron job.

### Scenarios and Solutions

| Scenario | Solution |
|---|---|
| Cache stampede | Request coalescing — one fetch, others wait |
| Service restart reconnect | Staggered reconnection with random delay |
| Retry storm | Decorrelated jitter on backoff |
| Config change reload | Staggered push or poll with jitter |
| Cron jobs at same time | Random offset: `random(0-59) * * * *` not `0 * * * *` |
| Leader failover | Constant work — standby already doing the same work |

**Universal fix: add jitter.** Any time N clients will do the same thing at the same time, add randomness to spread the load.
