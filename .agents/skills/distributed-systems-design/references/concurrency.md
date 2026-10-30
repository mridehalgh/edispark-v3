# Concurrency, Idempotency & Message Ordering

## Race Conditions & Concurrency Control

### Optimistic Locking

Read a record with its version. On write, include the version as a precondition. If the version changed, retry.

```typescript
// TypeScript — optimistic locking with a version column
async function updateWithRetry(id: string, updateFn: (record: Record) => Record, maxRetries = 3) {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    const record = await db.query("SELECT * FROM items WHERE id = $1", [id]);
    const updated = updateFn(record);

    const result = await db.query(
      "UPDATE items SET data = $1, version = version + 1 WHERE id = $2 AND version = $3",
      [updated.data, id, record.version]
    );

    if (result.rowCount > 0) return updated;
    // Version conflict — another writer got there first. Retry with fresh read.
  }
  throw new Error(`Optimistic lock failed after ${maxRetries} retries`);
}
```

**When to use**: Low-contention scenarios where conflicts are rare. Most web applications.
**When NOT to use**: High-contention hot records where retries would be excessive — use pessimistic locking instead.

### Pessimistic Locking

Acquire an exclusive lock before reading. Other writers block.

- **Always use TTL-based leases**, never indefinite locks. A crashed lock holder must not block the system forever.
- **Always acquire locks in consistent order** across all code paths to prevent deadlocks.
- Best for high-contention hot records (e.g., inventory counters, account balances under heavy concurrent updates).

### Compare-and-Swap (CAS)

Atomic `CAS(key, expected_old, new)` — succeeds only if current value equals expected.

- DynamoDB: `ConditionExpression`
- Redis: `WATCH/MULTI/EXEC`
- PostgreSQL: `UPDATE ... WHERE col = old_val`
- Foundation of lock-free algorithms. In databases, this is optimistic locking at the field level.

### Fencing Tokens

When using leader election with leases, a leader may believe it still holds the lease after it expired (GC pause, clock skew, network partition). A **fencing token** is a monotonically increasing number issued with each lease. The storage layer rejects writes with tokens lower than the highest it has seen.

```
Leader A gets lease with token 33
Leader A pauses (GC)
Lease expires, Leader B gets token 34
Leader A resumes, tries to write with token 33
Storage rejects: 33 < 34 (highest seen)
```

**Critical insight (Kleppmann)**: Without fencing tokens, distributed locks are fundamentally unsafe for protecting shared state. This is the core problem with Redlock — it provides no fencing mechanism.

**Safe distributed locks for correctness**: Use ZooKeeper (sequential znodes) or etcd (revision numbers) which provide fencing tokens. Redis locks are fine for efficiency (avoiding duplicate work) but not for correctness (protecting shared state).

### Leader Election

- Use lease-based leadership with a strongly consistent store (ZooKeeper, etcd, DynamoDB conditional writes).
- Leader writes a lease record with TTL, heartbeats to renew.
- Amazon pattern: the leader should do **constant work** regardless of active/standby status. This makes failover invisible — it doesn't change the system's behavior profile.

### The Check-Then-Act Anti-Pattern

Reading a flag then acting on it in a separate step is always a race condition. Between the check and the act, another process can change the flag.

```typescript
// WRONG — race condition between check and act
const available = await db.query("SELECT available FROM inventory WHERE id = $1", [itemId]);
if (available > 0) {
  await db.query("UPDATE inventory SET available = available - 1 WHERE id = $1", [itemId]);
}

// RIGHT — atomic check-and-act
const result = await db.query(
  "UPDATE inventory SET available = available - 1 WHERE id = $1 AND available > 0",
  [itemId]
);
if (result.rowCount === 0) throw new Error("Out of stock");
```

---

## Idempotency

### Delivery Guarantees

- **At-most-once**: Fire and forget. Message may be lost.
- **At-least-once**: Retry until acknowledged. Message may arrive multiple times. This is what most systems provide.
- **Exactly-once**: Impossible in the general case across system boundaries. Achieved in practice by combining at-least-once delivery with idempotent processing.

### Idempotency Key Pattern

1. Client generates a unique key (UUID) per logical operation, sends it with the request.
2. Server checks a dedup table for this key.
3. If found: return the stored result without re-executing.
4. If not found: execute operation, store result keyed by the idempotency key, return result.
5. Dedup entries have a TTL (24-72 hours).

```typescript
async function handlePayment(req: PaymentRequest) {
  const idempotencyKey = req.headers["idempotency-key"];
  if (!idempotencyKey) throw new Error("Idempotency-Key header required");

  // Atomic check — unique constraint prevents race between concurrent duplicates
  const existing = await db.query(
    "SELECT response FROM idempotency_keys WHERE key = $1",
    [idempotencyKey]
  );
  if (existing.rows.length > 0) return existing.rows[0].response;

  // Execute and store atomically
  const response = await db.transaction(async (tx) => {
    const result = await processPayment(tx, req);
    await tx.query(
      "INSERT INTO idempotency_keys (key, response, created_at) VALUES ($1, $2, NOW())",
      [idempotencyKey, result]
    );
    return result;
  });

  return response;
}
```

### Natural Idempotency

Some operations are naturally idempotent without extra machinery:

- `SET balance = 100` — idempotent (same result every time)
- `SET balance = balance + 10` — NOT idempotent (accumulates)
- `INSERT ... ON CONFLICT DO NOTHING` — idempotent
- `DELETE WHERE id = X` — idempotent

Prefer naturally idempotent operations when possible. When not possible, use idempotency keys.

### Pitfalls

- **Side effects outside the transaction**: Sending an email inside the handler means it fires on every retry. Record the intent in the DB; have a separate worker send and mark sent.
- **Not storing the response**: If you only store "seen" but not the result, retries can't return the correct response.
- **Race on the dedup check**: Two concurrent requests with the same key both pass "not found." Fix: use a DB unique constraint as the atomic guard.

---

## Message Ordering

### Ordering Levels

| Level | Guarantee | Cost | Mechanism |
|---|---|---|---|
| Total ordering | All consumers see all messages in same order | Single serialization point, limited throughput | Single-partition Kafka, single-leader DB |
| Per-entity ordering | Messages for the same entity arrive in order | Moderate | FIFO queue with entity ID as group key, Kafka partition key |
| Causal ordering | If A caused B, all see A before B | Vector clocks or Lamport timestamps | Rarely needed in practice |
| No ordering | Best-effort | Maximum throughput | SQS standard, most pub/sub |

### When to Use What

**Most common need: per-entity ordering.** Use a FIFO queue (SQS FIFO) or Kafka with entity ID as the partition key. This gives ordering where it matters without sacrificing throughput.

**Don't need ordering?** Design consumers to be order-independent: idempotent + commutative operations. Use standard queues for maximum throughput.

**Need total ordering across everything?** This is rare and expensive. Requires a single leader or consensus protocol. Question whether you truly need it.

### Pitfalls

- **Assuming ordering from a standard queue**: SQS standard provides best-effort ordering only. Don't rely on it.
- **Head-of-line blocking**: One slow message in a FIFO group blocks all subsequent messages in that group. Use fine-grained group IDs (per entity, not per tenant).
- **Cross-partition ordering**: Kafka guarantees order within a partition, not across. If you need cross-partition ordering, you need a different approach (but question whether you really do).
