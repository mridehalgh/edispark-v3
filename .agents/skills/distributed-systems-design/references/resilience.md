# Resilience Patterns

## Retry Strategies

### Exponential Backoff with Decorrelated Jitter

Never retry immediately. Never retry without jitter. Amazon's recommended approach — decorrelated jitter:

```typescript
function decorrelatedJitterBackoff(base: number, cap: number, previousSleep: number): number {
  return Math.min(cap, randomBetween(base, previousSleep * 3));
}

async function retryWithBackoff<T>(
  fn: () => Promise<T>,
  opts: { maxRetries: number; baseMs: number; capMs: number; deadlineMs?: number }
): Promise<T> {
  const deadline = opts.deadlineMs ? Date.now() + opts.deadlineMs : undefined;
  let sleep = opts.baseMs;

  for (let attempt = 0; attempt <= opts.maxRetries; attempt++) {
    try {
      return await fn();
    } catch (err) {
      if (attempt === opts.maxRetries) throw err;
      if (!isRetryable(err)) throw err;

      sleep = decorrelatedJitterBackoff(opts.baseMs, opts.capMs, sleep);

      // Don't retry if we'd exceed the deadline
      if (deadline && Date.now() + sleep > deadline) throw err;

      await delay(sleep);
    }
  }
  throw new Error("Unreachable");
}

function isRetryable(err: unknown): boolean {
  // Retry: 429, 503, 5xx, timeouts, connection errors
  // Don't retry: 400, 401, 403, 404, 409 — these won't succeed on retry
  if (err instanceof HttpError) return err.status >= 500 || err.status === 429;
  if (err instanceof TimeoutError || err instanceof ConnectionError) return true;
  return false;
}
```

### Retry Budgets

Instead of per-request retry limits, cap retries as a percentage of total requests:

- Allow retry requests up to 10% of the original request rate.
- If the budget is exhausted, all retries are suppressed until the next window.
- Prevents retry amplification in cascading failures.
- Google SRE uses this approach across their infrastructure.

### Retry Amplification

Service A retries 3x to B, which retries 3x to C. A single failure at C causes up to 27 requests (3^3). Solutions:
1. **Retry only at the outermost layer** — inner services fail fast.
2. **Use retry budgets at each layer** — caps total retry traffic.
3. **Propagate deadlines** — inner services skip retries when the deadline has passed.

### Deadlines vs Timeouts

A **timeout** is the max wait for a single call. A **deadline** is the max time for the entire end-to-end operation.

```typescript
// Deadline propagation
function handleRequest(req: Request): Response {
  const deadline = req.headers["x-deadline"]
    ? new Date(req.headers["x-deadline"])
    : new Date(Date.now() + DEFAULT_DEADLINE_MS);

  const remainingMs = deadline.getTime() - Date.now();
  if (remainingMs <= 0) return { status: 504, body: "Deadline exceeded" };

  // Pass to downstream — it knows the remaining budget
  return callDownstream(url, {
    timeout: Math.min(remainingMs, CALL_TIMEOUT_MS),
    headers: { "x-deadline": deadline.toISOString() },
  });
}
```

---

## Circuit Breakers

### States

```
CLOSED (normal) --[failure threshold exceeded]--> OPEN
OPEN --[timeout expires]--> HALF-OPEN
HALF-OPEN --[probe succeeds]--> CLOSED
HALF-OPEN --[probe fails]--> OPEN
```

### Implementation Guidance

- Use **error rate** (failures / total) within a sliding window, not absolute failure count. Absolute counts trip on low-traffic services.
- Don't count all errors equally. A timeout may be transient; a 500 may indicate a real problem. Weight them.
- **Circuit break per endpoint or per instance**, not globally. One bad downstream instance shouldn't circuit-break all of them.
- Test the open state explicitly. What does your service do when the circuit is open? If you haven't tested this, it will surprise you.

### Fallback Strategies

- Return cached/stale data
- Return a degraded response (recommendations down → show popular items)
- Return an error to the caller (sometimes correct)
- **Never rely on untested fallback code paths.** Amazon Builders' Library: untested fallbacks fail when needed most. Prefer graceful degradation over switching to alternate code that never runs in production.

---

## Rate Limiting

### Algorithm Selection

| Algorithm | Burst Handling | Precision | Memory | Best For |
|---|---|---|---|---|
| Token bucket | Allows bursts up to bucket size | Good | Low (2 values) | Most use cases (AWS, Stripe use this) |
| Leaky bucket | Smooths to constant rate | Good | Low | When downstream needs truly constant rate |
| Fixed window | Boundary problem: 2x burst at window edge | Rough | Low | Simple, low-stakes limiting |
| Sliding window counter | Weighted estimate, good balance | Good | Low | When fixed window's boundary is unacceptable |
| Sliding window log | Exact | High (stores every timestamp) | High | When precision matters and traffic is low |

### Token Bucket Implementation

```typescript
interface TokenBucket {
  tokens: number;
  lastRefillTime: number;
  maxTokens: number;
  refillRatePerSecond: number;
}

function tryConsume(bucket: TokenBucket): boolean {
  const now = Date.now();
  const elapsed = (now - bucket.lastRefillTime) / 1000;
  bucket.tokens = Math.min(bucket.maxTokens, bucket.tokens + elapsed * bucket.refillRatePerSecond);
  bucket.lastRefillTime = now;

  if (bucket.tokens >= 1) {
    bucket.tokens -= 1;
    return true;
  }
  return false; // Rate limited
}
```

### Distributed Rate Limiting

Three approaches, in order of preference:

1. **Local rate limiting per instance**: Divide global limit by instance count. Imprecise if traffic is uneven, but has no external dependency. Preferred by Amazon — rate limiting shouldn't itself be a critical dependency.
2. **Centralized counter in Redis**: Simple, precise, but adds latency and a SPOF.
3. **Local buckets with periodic sync**: Each instance gets a local allowance, periodically syncs with central store. Best balance for high-traffic services.

### Don't Forget

- Rate limit internal service-to-service calls too. A bug in service A can overwhelm service B.
- Rate limiting alone isn't enough. You still need load shedding, circuit breakers, and queue depth limits.
- Rate limit by authenticated identity when possible, not by IP (many clients share IPs behind NAT).

---

## Bulkhead Pattern

Isolate dependencies into separate resource pools. A slow dependency exhausts only its pool, not all shared resources.

```java
// Java — Resilience4j bulkhead per dependency
Bulkhead paymentBulkhead = Bulkhead.of("payment", BulkheadConfig.custom()
    .maxConcurrentCalls(10)      // payment service gets 10 threads max
    .maxWaitDuration(Duration.ofMillis(100))
    .build());

Bulkhead inventoryBulkhead = Bulkhead.of("inventory", BulkheadConfig.custom()
    .maxConcurrentCalls(20)      // inventory service gets 20 threads max
    .maxWaitDuration(Duration.ofMillis(100))
    .build());

// If payment is slow, it exhausts only its 10 threads — inventory is unaffected
```

In Node.js, use semaphores (concurrency limiters) since there are no thread pools:

```typescript
class Semaphore {
  private current = 0;
  private queue: Array<() => void> = [];

  constructor(private readonly max: number) {}

  async acquire(): Promise<void> {
    if (this.current < this.max) {
      this.current++;
      return;
    }
    return new Promise((resolve) => this.queue.push(() => { this.current++; resolve(); }));
  }

  release(): void {
    this.current--;
    const next = this.queue.shift();
    if (next) next();
  }
}
```

---

## Queue-Based Load Leveling

### Backpressure

When a consumer is overwhelmed, signal the producer to slow down:
- Bounded queues that reject when full
- HTTP 429/503 responses
- gRPC flow control

**Unbounded queues are dangerous.** They grow until memory is exhausted, then fail catastrophically. Always bound queue depth.

### Dead Letter Queues

Every queue should have a DLQ. Messages that fail processing after N attempts go to the DLQ.
- Monitor DLQ depth — it's a leading indicator of problems.
- Design for reprocessing: DLQ messages should be replayable after the issue is fixed.

### Poison Pills

A message that always crashes the consumer. Without handling, it blocks the queue forever.
- Track per-message failure count. After N failures, move to DLQ.
- Validate/deserialize messages before processing. Fail fast on invalid messages.

### Message Deadlines

Messages should have a maximum age. Discard stale messages rather than processing them:
- During a backlog, processing old messages wastes resources (Amazon: "Avoiding insurmountable queue backlogs").
- Shed old work, process only recent messages.

---

## Dependency Management

### Timeouts on Every Call

Every outbound call (HTTP, DB, cache, queue) must have an explicit timeout.
- Choose based on dependency's p99 latency plus margin. If p99 is 50ms, 200ms is reasonable.
- Framework defaults are almost always too long (30s, 60s, infinity). Override them.
- Monitor timeout rates — a spike is an early warning.

### Graceful Degradation

Pre-classify dependencies:
- **Critical**: Service cannot function without it (primary database, auth service).
- **Non-critical**: Service works at reduced quality without it (recommendations, analytics, preferences).

Pre-decide and document what happens when each dependency is unavailable. Don't figure it out during the outage.

### Hidden Dependencies

Audit all outbound connections. Libraries you import may make HTTP calls you don't know about. Your service doesn't call X directly, but dependency B does — B's failure due to X becomes your failure.

Wrap dependencies in an adapter/interface. This makes it possible to substitute, mock, or degrade.
