# Consistency, Caching & Data Patterns

## Consistency Patterns

### The Dual-Write Problem

Writing to a DB and publishing to a message broker are two separate operations. Either can fail independently. If the DB write succeeds but the publish fails, the system is inconsistent. If you publish first, you may publish an event for a transaction that rolls back.

**Never do this:**
```typescript
// WRONG — dual write. Either operation can fail independently.
await db.query("INSERT INTO orders ...", [order]);
await kafka.publish("order-created", order); // What if this fails? Or the above?
```

**Use the outbox pattern instead** (see below).

### Saga Pattern

A sequence of local transactions across services. Each step has a compensating transaction (undo). If step N fails, compensate steps N-1 through 1.

**Orchestration** (recommended for most cases):
```
Saga Coordinator:
  1. Create Order (order-service)     → compensate: Cancel Order
  2. Reserve Inventory (inventory-svc) → compensate: Release Inventory
  3. Charge Payment (payment-svc)      → compensate: Refund Payment
  4. Ship (shipping-svc)               → compensate: Cancel Shipment

If step 3 fails:
  compensate step 2: Release Inventory
  compensate step 1: Cancel Order
```

**Choreography** (decentralized — each service listens for events and acts):
- Harder to understand and debug
- No single place to see the saga's current state
- Best for simple, 2-3 step flows

**Key requirements:**
- Every step must be idempotent (retried steps must produce the same result)
- Compensating transactions must also be idempotent and retriable
- Compensating transactions can fail too — they need their own retry logic

### Outbox Pattern

Write business data and an event record in a single DB transaction. A separate process reads the outbox and publishes events.

```typescript
// RIGHT — single transaction guarantees both or neither
await db.transaction(async (tx) => {
  await tx.query("INSERT INTO orders (id, data) VALUES ($1, $2)", [order.id, order]);
  await tx.query(
    "INSERT INTO outbox (id, aggregate_type, event_type, payload) VALUES ($1, $2, $3, $4)",
    [uuid(), "order", "order-created", JSON.stringify(order)]
  );
});

// Separate worker — polls outbox and publishes
// This achieves at-least-once event publishing, guaranteed by the DB transaction
```

**Outbox relay options:**
- Polling: simple, slight delay
- Change Data Capture (CDC): captures from DB transaction log, lower latency, more operational complexity

### Change Data Capture (CDC)

Capture changes from the database's transaction log (WAL in PostgreSQL, binlog in MySQL) and publish as events.

- More reliable than application-level event publishing — captures ALL changes, including direct DB modifications
- Tools: Debezium, AWS DMS, PostgreSQL logical replication
- Often used with the outbox pattern: write to an outbox table, use CDC to publish outbox events

### Two-Phase Commit (2PC)

- Phase 1 (prepare): coordinator asks all participants if they can commit.
- Phase 2 (commit/abort): if all agreed, commit; otherwise abort.
- **Drawback**: blocking protocol. If coordinator crashes between phases, participants hold locks indefinitely.
- **When to use**: within a single database cluster. Almost never appropriate across service boundaries.
- **Prefer sagas** for cross-service coordination.

---

## Caching

### Strategy Selection

| Strategy | Consistency | Write Latency | Read Latency | Complexity | Best For |
|---|---|---|---|---|---|
| Cache-aside | Stale until TTL/invalidation | Normal (DB only) | Miss penalty | Low | Most cases |
| Write-through | Always consistent | Higher (cache + DB) | Always fast | Medium | Read-heavy, consistency-critical |
| Write-behind | Eventually consistent | Very fast | Always fast | High | Write-heavy, can tolerate lag |

### Cache-Aside (Lazy Loading)

```typescript
async function getUser(id: string): Promise<User> {
  const cached = await cache.get(`user:${id}`);
  if (cached) return cached;

  const user = await db.query("SELECT * FROM users WHERE id = $1", [id]);
  await cache.set(`user:${id}`, user, { ttlSeconds: 300 });
  return user;
}
```

Pros: Only caches what's requested. Cache failure = higher latency, not an outage.
Cons: Miss penalty. Stale until TTL or explicit invalidation.

### Cache Stampede Prevention

When a popular key expires, many concurrent requests all miss and hit the DB simultaneously.

**Request coalescing** (best first-line defense):
```typescript
const inflightRequests = new Map<string, Promise<unknown>>();

async function getWithCoalescing<T>(key: string, fetchFn: () => Promise<T>): Promise<T> {
  const inflight = inflightRequests.get(key);
  if (inflight) return inflight as Promise<T>;

  const promise = fetchFn().finally(() => inflightRequests.delete(key));
  inflightRequests.set(key, promise);
  return promise;
}
```

**Probabilistic early expiry (XFetch)**: Each request has a small chance of refreshing the cache before TTL. Spreads refreshes over time instead of all at once.

**Background refresh**: A background process refreshes hot keys before they expire. The cache never actually goes empty.

**Stale-while-revalidate**: Serve stale data immediately, refresh in the background.

### Pitfalls

- **Caching errors**: DB timeout returns null, which gets cached. All requests get null for the TTL. Fix: don't cache error responses, or use very short TTL for negative results.
- **Cold cache on deploy**: Empty cache = all requests hit DB. Pre-warm caches before shifting traffic.
- **Read-your-writes inconsistency**: After a write, the cache has stale data. Invalidate the cache entry on write for read-your-writes consistency.

---

## Partitioning & Sharding

### Choosing a Partition Key

The partition key determines data distribution AND access patterns. Get this wrong and you get hot partitions or cross-partition queries.

**Good partition keys:**
- `tenant_id` — when tenants have roughly equal traffic
- `entity_id` (user_id, order_id) — spreads load across entities

**Bad partition keys:**
- `date` — most traffic is "today," creating a hot partition
- `status` — only a few values, extreme skew
- `region` — uneven population distribution

### Consistent Hashing

Traditional `hash(key) % N` requires rehashing everything when N changes. Consistent hashing maps keys and nodes to a ring — adding/removing a node moves only K/N keys.

**Virtual nodes**: Each physical node has multiple positions on the ring. Provides better distribution and smoother rebalancing. Without virtual nodes, a single node removal can overload its neighbor.

### Hot Partition Mitigation

A key with disproportionate traffic creates a hot spot.

**Write-sharding hot keys**: Add a random suffix to spread writes, fan out reads:
```typescript
// Write: spread across 8 sub-partitions
const shard = Math.floor(Math.random() * 8);
await db.increment(`counter:${key}:${shard}`, 1);

// Read: fan out and sum
const shards = await Promise.all(
  Array.from({ length: 8 }, (_, i) => db.get(`counter:${key}:${i}`))
);
const total = shards.reduce((sum, v) => sum + (v ?? 0), 0);
```

### Cross-Partition Operations

Cross-partition transactions are expensive. Design the data model so operations stay within a single partition. If you must go cross-partition, use sagas — not distributed transactions.

Monitor per-partition metrics. Skew is a leading indicator of future scaling problems.

---

## Distributed Consensus

### When You Actually Need It

Most applications don't need consensus directly. You need it when:
- **Leader election**: exactly one process must hold leadership
- **Distributed configuration**: all nodes must agree on the current config
- **Coordination**: e.g., shard assignment, partition ownership

Use an existing system (ZooKeeper, etcd, Consul) rather than implementing consensus yourself.

### Quorum Reads/Writes

With N replicas, write to W, read from R, where W + R > N guarantees overlap:

| Config | Tradeoff |
|---|---|
| N=3, W=2, R=2 | Balanced — tolerates 1 failure for both reads and writes |
| N=3, W=3, R=1 | Fast reads, slow writes — read from any replica |
| N=3, W=1, R=3 | Fast writes, slow reads — write to any replica |

**Sloppy quorums** (Dynamo): During partitions, writes go to any reachable node. Increases availability but can temporarily violate consistency. Use hinted handoff to repair later.

### CRDTs

Data structures that merge without coordination. Guaranteed to converge.

- **G-Counter**: Grow-only counter (per-node counters, sum to read)
- **PN-Counter**: Supports increment and decrement
- **OR-Set**: Add/remove elements with observed-remove semantics
- **LWW-Register**: Last writer wins by timestamp

Use when: concurrent writes without coordination, and the CRDT's merge semantics fit your domain.
Limitation: not all data models can be expressed as CRDTs. Sets can grow large (tombstones).

### CAP Theorem — Practical Implications

During a network partition, choose between Consistency (return error) or Availability (return potentially stale data). Most systems make this choice per-operation, not globally.

**PACELC** (Abadi): Even without a Partition, there's a tradeoff between Latency and Consistency. Strong consistency requires coordination, which adds latency.

The practical question: **When a partition occurs, do you return an error (CP) or potentially stale data (AP)?** Most user-facing reads can be AP. Writes that must not be lost should be CP.
