# Operational Excellence

## Deployment Safety

### Strategy Selection

| Strategy | Rollback Speed | Complexity | Blast Radius | Best For |
|---|---|---|---|---|
| Blue-green | Instant (switch) | Medium | 100% or 0% | Stateless services, quick rollback needed |
| Canary | Fast (stop rollout) | Medium | 1-5% initially | Most services — the default choice |
| Feature flags | Instant (flip flag) | Low | Configurable | Decoupling deploy from release |
| Cell-based | Fast (evacuate cell) | High | 1/N cells | Maximum isolation |

### Canary Deployment (Default Choice)

1. Deploy to 1 instance (one-box). It receives real production traffic.
2. **Bake**: wait and observe for hours, not minutes. Compare metrics to baseline fleet.
3. If healthy, deploy to next wave (10%, 25%, 50%, 100%).
4. Bake between each wave.
5. **Automated rollback**: deployment pipeline monitors alarms. If any alarm fires, rollback is automatic.

Amazon's pattern: the pipeline is the release captain. Humans approve, automation executes.

### Rollback Safety

Every deployment must be rollback-safe. The previous version must be able to read data written by the new version.

**Two-phase deployment for breaking changes:**
1. Deploy code that handles both old and new formats (backward-compatible reader)
2. Deploy code that writes the new format
3. Rollback of phase 2 is safe because phase 1 handles both formats

**Test rollback explicitly.** An untested rollback is not a rollback plan.

### Feature Flags

Decouple deployment from release:
- Deploy with feature disabled
- Enable gradually (by user segment, percentage, region)
- Instant rollback: flip the flag

**Pitfall: flag debt.** Remove flags after full rollout. Stale flags become confusing dead code. Track flag age and set expiration dates.

---

## Observability

### The Three Pillars

| Pillar | What It Tells You | Tool |
|---|---|---|
| **Logs** | What happened at a specific point | Structured JSON logs |
| **Metrics** | Aggregated system behavior over time | Prometheus, CloudWatch |
| **Traces** | The journey of a single request across services | OpenTelemetry, X-Ray |

### Distributed Tracing

Assign a trace ID at the entry point. Propagate through all downstream calls. Each service creates a span.

```typescript
// Use W3C Trace Context headers
// traceparent: 00-<trace-id>-<span-id>-<flags>
// OpenTelemetry handles this automatically — instrument at the framework level

import { trace } from "@opentelemetry/api";

async function handleOrder(req: Request) {
  const span = trace.getActiveSpan();
  span?.setAttribute("order.id", req.body.orderId);
  span?.setAttribute("tenant.id", req.body.tenantId);

  // Downstream calls automatically propagate the trace context
  const inventory = await inventoryService.reserve(req.body.items);
  const payment = await paymentService.charge(req.body.total);

  // The trace shows: handleOrder → reserve → charge, with timing for each
}
```

### Structured Logging

Log as JSON with consistent fields. Log at **service boundaries** (entry, exit, error), not every line.

```typescript
// Consistent log structure
logger.info({
  event: "order_created",
  traceId: context.traceId,
  tenantId: order.tenantId,
  orderId: order.id,
  itemCount: order.items.length,
  durationMs: Date.now() - startTime,
});

// NEVER log:
// - PII (emails, names, addresses)
// - Secrets (API keys, tokens, passwords)
// - Full request/response bodies in production (too verbose, may contain PII)
```

### Health Checks

**Shallow**: Process is alive and responding to HTTP. Use for load balancer health.

**Deep**: Process can reach its critical dependencies. Use for operational dashboards.

**Critical rule: Health checks must be local-only.** If service A's health check calls service B, which calls service C, a failure in C makes all services "unhealthy" — cascading failure through health checks.

```typescript
// GOOD — local health check
app.get("/health", (req, res) => {
  const dbPool = getPoolStats();
  const memUsage = process.memoryUsage();

  res.json({
    status: dbPool.idle > 0 ? "healthy" : "degraded",
    db: { idle: dbPool.idle, active: dbPool.active, waiting: dbPool.waiting },
    memory: { heapUsedMb: Math.round(memUsage.heapUsed / 1024 / 1024) },
    uptime: process.uptime(),
  });
});

// BAD — calls downstream services in health check
app.get("/health", async (req, res) => {
  await fetch("http://payment-service/health"); // cascade risk!
  await fetch("http://inventory-service/health"); // cascade risk!
  res.json({ status: "healthy" });
});
```

---

## Tail Latency & SLOs

### Why Tail Latency Matters at Scale

If a page makes 100 backend calls, each with 1% chance of being slow (p99 latency):
- Probability at least one call is slow: `1 - 0.99^100 = 63%`
- **Most users experience the tail**, not the median

This is Jeff Dean's insight: individual service p99 becomes the user-facing p50.

### Reducing Tail Latency

**Hedged requests**: Send the same request to multiple replicas, use the first response. Reduces tail latency at the cost of increased load. Use sparingly.

**Tied requests**: Like hedged, but the second request includes a reference to the first. The server that starts first cancels the other — reduces wasted work.

**Canary requests**: Send one request first. If it returns within p95, done. If slow, send a backup after a delay.

### SLO Design

```
Define SLOs in percentiles, not averages:
  p50 latency  < 50ms   (median experience)
  p99 latency  < 200ms  (worst 1% of requests)
  p99.9 latency < 1s    (worst 0.1%)
  Availability  > 99.9% (error budget: ~43 min/month)
```

**Error budgets**: If SLO is 99.9% availability, you have 0.1% error budget per month. Spend it on deployments and experiments. When the budget is exhausted, freeze changes and focus on reliability.

**Measure from the client's perspective**, not the server's. Include network latency, load balancer latency, DNS resolution.

**Alert on SLO burn rate**: If you're consuming error budget 10x faster than sustainable, alert immediately. Don't wait for the monthly budget to be exhausted.

### Percentile-Based Alerting

```
BAD:  Alert when average latency > 100ms
      (Hides tail — p99 could be 10s and average stays fine)

GOOD: Alert when p99 latency > 200ms sustained for 5 minutes
      (Catches tail degradation before it affects most users)
```

---

## Distributed Locking Pitfalls

### The Redlock Problem (Kleppmann's Analysis)

Redis's Redlock algorithm has fundamental issues for correctness:

1. **Timing assumption**: Assumes bounded clock drift and process pauses. GC pauses can violate this.
2. **No fencing tokens**: A client paused by GC can resume and write after its lock expired, while another client holds the lock.

```
Client A: acquires lock (Redlock)
Client A: GC pause for 30 seconds
           Lock expires after 10s
           Client B acquires lock
           Client B writes to storage
Client A: resumes, thinks it still holds lock
Client A: writes to storage ← DATA CORRUPTION
```

### When Each Lock Type Is Safe

| Lock Purpose | Redis SET NX | Redlock | ZooKeeper/etcd |
|---|---|---|---|
| Efficiency (avoid duplicate work) | Fine | Fine | Overkill |
| Correctness (protect shared state) | Unsafe | Unsafe | Use this + fencing tokens |

**For correctness**: Use ZooKeeper sequential znodes or etcd revision numbers as fencing tokens. The storage layer rejects writes with stale tokens.

**For efficiency**: Simple `SET resource_name unique_value NX EX ttl` in Redis is sufficient. Use a unique value to ensure only the holder can release.

Always set aggressive TTLs. Prefer short TTLs with renewal over long TTLs. Always handle the case where the lock is lost.
