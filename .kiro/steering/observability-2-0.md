# Observability 2.0 Standards 📊

## Core Philosophy
- **Observability is not optional** - Every component must be observable by design
- **Telemetry first** - Metrics, traces, and logs are first-class citizens in code
- **Context propagation** - Distributed tracing across all service boundaries
- **Business observability** - Technical metrics AND business KPIs
- **Instrumentation enables learning** - Systems explain themselves through their data
- **Log first, aggregate later** - Detailed logs enable both troubleshooting and metrics

## Why Instrumentation Matters 🎯

### Amazon's Operational Culture
At Amazon, instrumentation is the core of operational culture. Great instrumentation helps us see the experience we're giving our customers. This focus spans the entire company - increased latency results in poor shopping experiences and lower conversion rates.

### Focus on High Percentile Latency
We don't just consider average latency. We focus on latency outliers like 99.9th and 99.99th percentile because:
- One slow request out of 1,000 or 10,000 is still a poor experience
- Reducing high percentile latency often improves median latency
- High latency in one service has multiplier effects across other services
- Service-oriented architecture means deep call chains amplify latency issues

### Multiple Perspectives Required
Service owners must measure operational performance from multiple places to get end-to-end visibility:
- Each component emits metrics about its own behavior
- Components measure how they perceive other components
- Combined metrics enable quick problem diagnosis and root cause analysis

## The Three Pillars + Context

### Metrics 📈
```java
@Component
public class EdiProcessingMetrics {
  
  private final MeterRegistry meterRegistry;
  
  public void recordMessageProcessed(String messageType, String partnerId, 
                                   Duration processingTime, boolean success) {
    
    Timer.builder("edi.processing.duration")
      .tag("message.type", messageType)
      .tag("partner.id", partnerId)
      .tag("success", String.valueOf(success))
      .register(meterRegistry)
      .record(processingTime);
    
    Counter.builder("edi.messages.processed")
      .tag("message.type", messageType)
      .tag("partner.id", partnerId)
      .tag("status", success ? "success" : "failure")
      .register(meterRegistry)
      .increment();
  }
}
```

### Distributed Tracing 🔗
```java
@Component
public class FileParser {
  
  @NewSpan("edi.file.parse")
  public ParsedMessage parse(@SpanTag("file.key") String fileKey) {
    
    Span.current().setAttributes(
      Attributes.of(
        AttributeKey.stringKey("tenant.id"), TenantContext.getCurrentTenant().asString(),
        AttributeKey.stringKey("file.size"), String.valueOf(fileSize),
        AttributeKey.stringKey("file.type"), detectedType
      )
    );
    
    return doParse(fileKey);
  }
}
```

### Structured Logging 📝
```java
@Slf4j
public class MessageProcessor {
  
  public void processMessage(String messageId, String partnerId) {
    log.info("Processing EDI message",
      kv("message.id", messageId),
      kv("partner.id", partnerId),
      kv("tenant.id", TenantContext.getCurrentTenant()),
      kv("processing.start", Instant.now())
    );
  }
}
```

### Canonical Log Lines 2.0 🎯

**The single best method for production insight** - One unified log line per request with all key vitals.

#### Why Canonical Log Lines Matter

Logging is one of the oldest and most ubiquitous patterns in computing. Key to gaining insight into problems ranging from basic failures in test environments to the most tangled problems in production, it's common practice across all software stacks and all types of infrastructure, and has been for decades.

Although logs are powerful and flexible, their sheer volume often makes it impractical to extract insight from them in an expedient way. Relevant information is spread across many individual log lines, and even with the most powerful log processing systems, searching for the right details can be slow and requires intricate query syntax.

Canonical log lines solve this by **colocating everything important in single information-dense lines**, making queries and aggregations faster to write and faster to run.

#### The Problem with Traditional Logging

A typical request might generate multiple log lines:
```
[2024-01-15 14:30:32.990] Request started http_method=POST http_path=/api/orders request_id=req_123
[2024-01-15 14:30:32.991] User authenticated auth_type=jwt user_id=usr_456 tenant_id=tenant_789
[2024-01-15 14:30:32.992] Rate limiting ran rate_allowed=true rate_quota=1000 rate_remaining=999
[2024-01-15 14:30:32.998] Order created order_id=ord_123 partner_id=TESCO001 total_value=1250.50
[2024-01-15 14:30:32.999] Request finished duration=0.009 db_queries=5 http_status=201
```

To answer "Which partners are being rate limited most?", you need complex queries across multiple lines. **Canonical log lines fix this.**

#### Implementation

```java
@Component
public class CanonicalLogLineService {
  
  public void logRequest(HttpServletRequest request, HttpServletResponse response, 
                        Duration duration, RequestMetrics metrics) {
    
    Map<String, Object> canonicalData = Map.of(
      "message", String.format("%s %s -> %d (%.6fs)", 
        request.getMethod(), request.getRequestURI(), 
        response.getStatus(), duration.toNanos() / 1_000_000_000.0),
      "canonical_log_line", true,
      "http_method", request.getMethod(),
      "http_path", request.getRequestURI(),
      "http_status", response.getStatus(),
      "duration", duration.toNanos() / 1_000_000_000.0,
      "db_duration", metrics.getDbDuration().toNanos() / 1_000_000_000.0,
      "db_num_queries", metrics.getDbQueryCount(),
      "tenant_id", TenantContext.getCurrentTenant().asString(),
      "user_id", getCurrentUserId(),
      "request_id", MDC.get("request.id"),
      "sentry_trace_url", getSentryTraceUrl(),
      "business_metrics", Map.of(
        "orders_processed", metrics.getOrdersProcessed(),
        "total_value", metrics.getTotalOrderValue(),
        "partner_id", metrics.getPartnerId()
      )
    );
    
    log.info("Canonical log line", canonicalData);
  }
}
```

#### Middleware Implementation Pattern

```java
@Component
public class CanonicalLogMiddleware implements Filter {
  
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    
    long startTime = System.nanoTime();
    RequestMetrics metrics = new RequestMetrics();
    
    try {
      // Attach metrics collector to request context
      RequestContextHolder.setMetrics(metrics);
      
      // Process request
      chain.doFilter(request, response);
      
    } finally {
      // ALWAYS emit canonical log line, even on exceptions
      try {
        Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
        canonicalLogService.logRequest(httpRequest, httpResponse, duration, metrics);
      } catch (Exception e) {
        // Never let canonical logging break the request
        log.error("Failed to emit canonical log line", e);
      }
    }
  }
}
```

#### JSON Structure Benefits

- **Clean display**: `message` field shows terse human-readable summary
- **Rich context**: Full structured data available when expanded
- **Nested structures**: Complex data like `business_metrics` properly organized
- **Aggregation friendly**: Easy to query patterns across requests
- **Operational links**: Direct URLs to Sentry traces and errors
- **Hidden by default**: Reduces visual noise while maintaining full data access

#### Query Power Examples

```sql
-- Error rate trends by status
canonical_log_line=true | timechart count by http_status

-- Database performance percentiles
canonical_log_line=true | timechart p50(db_duration) p95(db_duration) p99(db_duration)

-- Top users by request volume
canonical_log_line=true | top by user_id

-- Partner rate limiting analysis
canonical_log_line=true rate_allowed=false | stats count by business_metrics.partner_id

-- Business value by partner over time
canonical_log_line=true | timechart sum(business_metrics.total_value) by business_metrics.partner_id

-- Slow requests for specific endpoint
canonical_log_line=true http_path="/api/orders" duration>1.0 | stats count p95(duration)
```

#### Data Warehousing Integration

```java
@Component
public class CanonicalLogWarehouse {
  
  @EventListener
  public void handleCanonicalLogEvent(CanonicalLogEvent event) {
    // Emit to Kafka for long-term analytics
    kafkaTemplate.send("canonical-logs", event.toProtobuf());
  }
}

// Consumer stores to S3 -> Redshift/BigQuery for analytics
// Enables queries like: "Go version adoption over time"
// SELECT DATE_TRUNC('week', created) AS week, 
//        language_version, COUNT(DISTINCT user_id)
// FROM canonical_logs 
// WHERE created > CURRENT_DATE - interval '2 months'
```

#### Production Hardening

- **Always emit**: Use `finally` blocks and exception handling
- **Never fail requests**: Wrap logging in try/catch
- **Stable schema**: Use protocol buffers for data warehouse integration
- **Async emission**: Don't block request processing
- **Field stability**: Keep field names consistent for team muscle memory

#### Business Intelligence Integration

Canonical log lines become the **primary data source** for:
- Developer dashboards showing API usage patterns
- Partner performance analytics
- Business metrics and KPI tracking
- Product feature adoption measurement
- Long-term trend analysis

**Remember**: Out of all observability tools, canonical log lines have proven most useful for daily operational visibility and incident response. They're used **every single day** by engineers for production insight.

### Contextual Information 🎯
- **Tenant Context**: Always include tenant ID in telemetry via ScopedValue
- **Business Context**: Partner ID, message type, order numbers
- **Technical Context**: Service version, environment, region
- **Correlation IDs**: Trace requests across service boundaries using ScopedValue
- **ScopedValue Propagation**: Use Java 21 ScopedValues instead of ThreadLocal for context

### ScopedValue Context Management 🔄

#### Why ScopedValues Over ThreadLocal
- **Automatic cleanup**: No memory leaks from forgotten ThreadLocal.remove()
- **Immutable sharing**: Safe context propagation across async boundaries
- **Virtual thread friendly**: Designed for modern Java concurrency
- **Structured concurrency**: Natural integration with scoped operations
- **Better performance**: Reduced memory overhead and GC pressure

#### ScopedValue Implementation Pattern
```java
@Component
public class ObservabilityContext {
  
  private static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();
  private static final ScopedValue<RequestMetrics> REQUEST_METRICS = ScopedValue.newInstance();
  
  public static String getCorrelationId() {
    return CORRELATION_ID.orElse("unknown");
  }
  
  public static RequestMetrics getRequestMetrics() {
    return REQUEST_METRICS.orElse(RequestMetrics.empty());
  }
  
  public static void runWithContext(String correlationId, RequestMetrics metrics, Runnable task) {
    ScopedValue.runWhere(CORRELATION_ID, correlationId)
      .runWhere(REQUEST_METRICS, metrics, task);
  }
  
  public static <T> T callWithContext(String correlationId, RequestMetrics metrics, Supplier<T> task) {
    return ScopedValue.callWhere(CORRELATION_ID, correlationId)
      .callWhere(REQUEST_METRICS, metrics, task);
  }
}
```

#### Integration with Existing TenantContext
```java
// Leverage existing ScopedValue-based TenantContext
public class CanonicalLogEntry {
  
  public void emit() {
    Map<String, Object> logData = Map.of(
      "correlation.id", ObservabilityContext.getCorrelationId(),
      "tenant.id", TenantContext.getCurrentTenant().asString(), // Uses ScopedValue
      "timestamp", Instant.now(),
      "metrics", ObservabilityContext.getRequestMetrics()
    );
    
    logger.info("Canonical log line", logData);
  }
}
```

#### Async Propagation with ScopedValues
```java
@Component
public class AsyncProcessor {
  
  @Async
  public CompletableFuture<Void> processAsync(String data) {
    // ScopedValues automatically propagate to async operations
    String correlationId = ObservabilityContext.getCorrelationId();
    String tenantId = TenantContext.getCurrentTenant().asString();
    
    return CompletableFuture.runAsync(() -> {
      // Context is available in async thread
      log.info("Processing async", 
        kv("correlation.id", correlationId),
        kv("tenant.id", tenantId)
      );
    });
  }
}
```

## OpenTelemetry with AWS CloudWatch 📊

### OTEL Configuration
```java
@Configuration
public class ObservabilityConfiguration {
  
  @Bean
  public OpenTelemetry openTelemetry() {
    return OpenTelemetrySdk.builder()
      .setTracerProvider(
        SdkTracerProvider.builder()
          .addSpanProcessor(BatchSpanProcessor.builder(
            OtlpGrpcSpanExporter.builder()
              .setEndpoint("https://otlp.us-east-1.amazonaws.com/v1/traces")
              .addHeader("Authorization", "AWS4-HMAC-SHA256 ...")
              .build())
            .build())
          .setResource(Resource.getDefault()
            .merge(Resource.builder()
              .put(ResourceAttributes.SERVICE_NAME, "edifact-parser")
              .put(ResourceAttributes.SERVICE_VERSION, "1.0.0")
              .build()))
          .build())
      .setMeterProvider(
        SdkMeterProvider.builder()
          .registerMetricReader(
            PeriodicMetricReader.builder(
              OtlpGrpcMetricExporter.builder()
                .setEndpoint("https://otlp.us-east-1.amazonaws.com/v1/metrics")
                .build())
              .setInterval(Duration.ofSeconds(30))
              .build())
          .build())
      .buildAndRegisterGlobal();
  }
}
```

### CloudWatch Integration
```java
@Component
public class CloudWatchMetrics {
  
  private final CloudWatchAsyncClient cloudWatch;
  private final Meter meter;
  
  public CloudWatchMetrics(OpenTelemetry openTelemetry) {
    this.cloudWatch = CloudWatchAsyncClient.create();
    this.meter = openTelemetry.getMeter("edifact-processing");
  }
  
  public void recordProcessingMetrics(String messageType, Duration duration, boolean success) {
    // OTEL metrics (auto-exported to CloudWatch)
    meter.counterBuilder("edi.messages.processed")
      .build()
      .add(1, 
        Attributes.of(
          AttributeKey.stringKey("message.type"), messageType,
          AttributeKey.stringKey("status"), success ? "success" : "failure"
        ));
    
    meter.histogramBuilder("edi.processing.duration")
      .setUnit("ms")
      .build()
      .record(duration.toMillis(),
        Attributes.of(
          AttributeKey.stringKey("message.type"), messageType
        ));
  }
}
```

### Lambda OTEL Configuration
```java
// Lambda environment variables
Map.of(
  "AWS_LAMBDA_EXEC_WRAPPER", "/opt/otel-instrument",
  "OTEL_LAMBDA_DISABLE_AWS_CONTEXT_PROPAGATION", "true",
  "OTEL_PROPAGATORS", "tracecontext,baggage,xray",
  "OTEL_TRACES_EXPORTER", "otlp",
  "OTEL_METRICS_EXPORTER", "otlp",
  "OTEL_EXPORTER_OTLP_ENDPOINT", "https://otlp.us-east-1.amazonaws.com",
  "OTEL_RESOURCE_ATTRIBUTES", "service.name=edifact-parser,service.version=1.0.0"
)
```

## Implementation Requirements

### Every Service Must Have
- **Health Checks**: Liveness and readiness probes
- **OTEL Integration**: OpenTelemetry SDK with CloudWatch exporters
- **Trace Propagation**: X-Ray and OTEL trace context headers
- **Structured Logs**: JSON format with correlation IDs sent to CloudWatch Logs

### Every API Endpoint Must Have
- **Request/Response Logging**: With sanitized payloads
- **Duration Metrics**: P50, P95, P99 latencies
- **Error Rate Tracking**: 4xx and 5xx responses
- **Business Metrics**: Domain-specific KPIs

### Every Database Operation Must Have
- **Query Performance Metrics**: Execution time and row counts
- **Connection Pool Monitoring**: Active/idle connections
- **Error Tracking**: Failed queries and timeouts
- **Business Impact**: Records processed, data volume

## Observability Patterns

### Circuit Breaker Pattern
```java
@Component
public class ExternalServiceClient {
  
  @CircuitBreaker(name = "erp-service")
  @TimedAspect(name = "erp.api.call")
  public ApiResponse callErpSystem(OrderData order) {
    
    return Span.current().wrap(() -> {
      try {
        ApiResponse response = erpClient.sendOrder(order);
        
        Metrics.counter("erp.api.success",
          "partner.id", order.getPartnerId(),
          "order.type", order.getType()
        ).increment();
        
        return response;
        
      } catch (Exception e) {
        Metrics.counter("erp.api.error",
          "partner.id", order.getPartnerId(),
          "error.type", e.getClass().getSimpleName()
        ).increment();
        
        throw e;
      }
    });
  }
}
```

### Async Processing Observability
```java
@Component
public class AsyncMessageProcessor {
  
  @EventListener
  @TraceAsync
  public void handleEdiMessage(EdiMessageEvent event) {
    
    try (MDCCloseable mdc = MDC.putCloseable("correlation.id", event.getCorrelationId())) {
      
      Span span = tracer.nextSpan()
        .name("edi.message.async.process")
        .tag("message.type", event.getMessageType())
        .tag("tenant.id", event.getTenantId())
        .start();
      
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        processMessage(event);
        
        Metrics.counter("edi.async.processed",
          "message.type", event.getMessageType(),
          "status", "success"
        ).increment();
        
      } catch (Exception e) {
        span.tag("error", e.getMessage());
        
        Metrics.counter("edi.async.processed",
          "message.type", event.getMessageType(),
          "status", "error"
        ).increment();
        
        throw e;
      } finally {
        span.end();
      }
    }
  }
}
```

## Business Observability

### Key Performance Indicators
```java
@Component
public class BusinessMetrics {
  
  public void recordOrderProcessing(ProcessedOrder order) {
    
    // Business value metrics
    Gauge.builder("business.order.value")
      .tag("partner.id", order.getPartnerId())
      .tag("currency", order.getCurrency())
      .register(meterRegistry, order.getTotalAmount().doubleValue());
    
    // Processing efficiency
    Timer.builder("business.order.processing.time")
      .tag("partner.id", order.getPartnerId())
      .tag("order.complexity", calculateComplexity(order))
      .register(meterRegistry)
      .record(order.getProcessingDuration());
    
    // SLA compliance
    boolean withinSla = order.getProcessingDuration().compareTo(getSlaThreshold(order.getPartnerId())) <= 0;
    
    Counter.builder("business.sla.compliance")
      .tag("partner.id", order.getPartnerId())
      .tag("within.sla", String.valueOf(withinSla))
      .register(meterRegistry)
      .increment();
  }
}
```

### Error Classification
```java
public enum BusinessErrorType {
  INVALID_PRODUCT_CODE("business.error.invalid_product"),
  INSUFFICIENT_INVENTORY("business.error.insufficient_inventory"),
  CREDIT_LIMIT_EXCEEDED("business.error.credit_limit"),
  DUPLICATE_ORDER("business.error.duplicate_order");
  
  private final String metricName;
  
  public void recordError(String partnerId, String orderNumber) {
    Metrics.counter(this.metricName,
      "partner.id", partnerId,
      "order.number", orderNumber
    ).increment();
  }
}
```

## Alerting and SLOs

### Service Level Objectives
```yaml
# SLO Configuration
slos:
  edi_processing_availability:
    target: 99.9%
    window: 30d
    metric: up{job="edi-processor"}
    
  edi_processing_latency:
    target: 95%
    window: 24h
    metric: histogram_quantile(0.95, edi_processing_duration_seconds)
    threshold: 5s
    
  business_order_success_rate:
    target: 99.5%
    window: 24h
    metric: rate(edi_orders_processed_total{status="success"})
```

### Alert Rules
```yaml
# Alert Configuration
alerts:
  - name: EdiProcessingHighErrorRate
    condition: rate(edi_messages_processed_total{status="error"}[5m]) > 0.05
    severity: warning
    annotations:
      summary: "High EDI processing error rate"
      
  - name: EdiProcessingDown
    condition: up{job="edi-processor"} == 0
    severity: critical
    annotations:
      summary: "EDI processing service is down"
      
  - name: BusinessSlaViolation
    condition: rate(business_sla_compliance_total{within_sla="false"}[15m]) > 0.1
    severity: warning
    annotations:
      summary: "Business SLA violations detected"
```

## Development Guidelines

### Code Review Checklist
- [ ] All new endpoints have metrics and tracing
- [ ] Error cases are properly instrumented
- [ ] Business metrics are captured where applicable
- [ ] Logs include correlation IDs and context
- [ ] **Canonical log lines implemented** for all request endpoints
- [ ] **ScopedValues used instead of ThreadLocal** for context management
- [ ] **TenantContext integration** leverages existing ScopedValue implementation
- [ ] Health checks cover all dependencies
- [ ] Sentry trace URLs included in error scenarios
- [ ] Canonical log schema remains stable (don't break team muscle memory)
- [ ] Canonical logging never fails requests (proper exception handling)
- [ ] Context propagation works correctly across async boundaries

### Testing Observability
```java
@Test
void shouldRecordMetricsOnSuccessfulProcessing() {
  // Given
  String messageId = "test-message-123";
  
  // When
  processor.processMessage(messageId);
  
  // Then
  assertThat(meterRegistry.counter("edi.messages.processed", 
    "status", "success").count()).isEqualTo(1);
    
  assertThat(meterRegistry.timer("edi.processing.duration").count()).isEqualTo(1);
}

@Test
void shouldPropagateTraceContext() {
  // Given
  Span parentSpan = tracer.nextSpan().name("test-parent").start();
  
  // When
  try (Tracer.SpanInScope ws = tracer.withSpanInScope(parentSpan)) {
    processor.processMessage("test-message");
  }
  
  // Then
  assertThat(finishedSpans()).hasSize(2);
  assertThat(finishedSpans().get(1).getParentId()).isEqualTo(parentSpan.context().spanId());
}
```

## Mandatory Telemetry

### Every Lambda Function
- **OTEL Layer**: AWS Distro for OpenTelemetry Lambda layer
- **Cold start metrics**: Duration and frequency via OTEL
- **Memory utilization**: Peak and average usage
- **Error rates**: By error type and tenant
- **Business outcomes**: Messages processed, orders created
- **X-Ray Integration**: Automatic trace correlation with CloudWatch

### Every API Gateway
- **Request rates**: By endpoint and tenant
- **Response times**: P50, P95, P99 latencies
- **Error rates**: 4xx and 5xx by endpoint
- **Payload sizes**: Request and response sizes

### Every Database
- **Connection metrics**: Pool utilization and wait times
- **Query performance**: Slow queries and execution plans
- **Data volume**: Records processed and storage growth
- **Replication lag**: For read replicas

## Observability as Code

### Infrastructure Monitoring
```typescript
// CDK Construct for automatic observability
export class ObservableLambdaConstruct extends Construct {
  
  constructor(scope: Construct, id: string, props: ObservableLambdaProps) {
    super(scope, id);
    
    const lambda = new Function(this, 'Function', {
      ...props,
      layers: [
        LayerVersion.fromLayerVersionArn(this, 'OtelLayer',
          'arn:aws:lambda:us-east-1:901920570463:layer:aws-otel-java-wrapper-amd64-ver-1-32-0:1'
        )
      ],
      environment: {
        ...props.environment,
        'AWS_LAMBDA_EXEC_WRAPPER': '/opt/otel-instrument',
        'OTEL_PROPAGATORS': 'tracecontext,baggage,xray',
        'OTEL_TRACES_EXPORTER': 'otlp',
        'OTEL_METRICS_EXPORTER': 'otlp',
        'OTEL_EXPORTER_OTLP_ENDPOINT': 'https://otlp.us-east-1.amazonaws.com',
        'OTEL_RESOURCE_ATTRIBUTES': `service.name=${props.serviceName},service.version=${props.version}`
      },
      tracing: Tracing.ACTIVE
    });
    
    // CloudWatch alarms for OTEL metrics
    new Alarm(this, 'ErrorRate', {
      metric: new Metric({
        namespace: 'AWS/Lambda',
        metricName: 'Errors',
        dimensionsMap: { FunctionName: lambda.functionName },
        statistic: 'Sum'
      }),
      threshold: 5,
      evaluationPeriods: 2
    });
    
    // Custom OTEL metrics alarm
    new Alarm(this, 'ProcessingDuration', {
      metric: new Metric({
        namespace: 'EdiSpark/Processing',
        metricName: 'edi.processing.duration',
        statistic: 'Average'
      }),
      threshold: 5000, // 5 seconds
      evaluationPeriods: 3
    });
  }
}
```

## Success Metrics

You're implementing O11y 2.0 effectively when:
- **Mean Time to Detection (MTTD)** < 5 minutes
- **Mean Time to Resolution (MTTR)** < 30 minutes
- **Service Level Objectives** are consistently met
- **Business metrics** drive technical decisions
- **Observability debt** is treated like technical debt
- **On-call engineers** can diagnose issues from telemetry alone

## Amazon's Instrumentation Principles 🏗️

### What to Measure
To operate services with high availability and latency standards, measure from multiple perspectives:

```java
// Example: Product lookup with comprehensive instrumentation
public GetProductInfoResponse getProductInfo(GetProductInfoRequest request) {
  
  // Record request metadata early (before validation)
  String correlationId = MDC.get("correlation.id");
  String productId = request.getProductId();
  String callerId = request.getCallerId();
  
  log.info("Product lookup started", 
    kv("product.id", productId),
    kv("caller.id", callerId),
    kv("correlation.id", correlationId)
  );
  
  Timer.Sample sample = Timer.start(meterRegistry);
  
  try {
    // Local cache check with metrics
    ProductInfo info = localCache.get(productId);
    Metrics.counter("cache.local.access",
      "result", info != null ? "hit" : "miss"
    ).increment();
    
    if (info == null) {
      // Remote cache with detailed timing
      Timer.Sample remoteSample = Timer.start(meterRegistry);
      info = remoteCache.get(productId);
      remoteSample.stop(Timer.builder("cache.remote.duration")
        .tag("result", info != null ? "hit" : "miss")
        .register(meterRegistry));
      
      if (info != null) {
        localCache.put(info);
        Metrics.gauge("cache.local.size", localCache.size());
      }
    }
    
    if (info == null) {
      // Database query with comprehensive metrics
      Timer.Sample dbSample = Timer.start(meterRegistry);
      try {
        info = db.query(productId);
        dbSample.stop(Timer.builder("database.query.duration")
          .tag("status", "success")
          .tag("table", "products")
          .register(meterRegistry));
        
        // Cache population timing
        localCache.put(info);
        remoteCache.put(info);
        
      } catch (DatabaseException e) {
        dbSample.stop(Timer.builder("database.query.duration")
          .tag("status", "error")
          .tag("error.type", e.getClass().getSimpleName())
          .register(meterRegistry));
        
        Metrics.counter("database.errors",
          "error.type", e.getClass().getSimpleName(),
          "table", "products"
        ).increment();
        
        throw e;
      }
    }
    
    // Record success metrics
    sample.stop(Timer.builder("product.lookup.duration")
      .tag("status", "success")
      .tag("data.source", determineDataSource(info))
      .register(meterRegistry));
    
    Metrics.gauge("response.size.bytes", calculateSize(info));
    
    return new GetProductInfoResponse(info);
    
  } catch (Exception e) {
    // Record failure metrics
    sample.stop(Timer.builder("product.lookup.duration")
      .tag("status", "error")
      .tag("error.type", e.getClass().getSimpleName())
      .register(meterRegistry));
    
    log.error("Product lookup failed",
      kv("product.id", productId),
      kv("error.type", e.getClass().getSimpleName()),
      kv("correlation.id", correlationId),
      e
    );
    
    throw e;
  }
}
```

### Request Log Best Practices

#### How We Log
- **One log entry per unit of work**: Single entry per request/message with all timing and counter data
- **No more than one entry per request**: Avoid multiple entries that complicate analysis
- **Break long-running tasks**: Emit periodic entries for multi-minute workflows
- **Record details before validation**: Log request info early, before rejection by validation/auth
- **Plan for increased verbosity**: Configuration knobs for temporary detailed logging
- **Keep metric names short**: Minimize logging overhead with concise but descriptive names
- **Handle max throughput**: Ensure log volumes can handle peak load without disk filling
- **Synchronize clocks**: Use NTP/Chrony for accurate distributed system timestamps
- **Emit zero counts**: Use 1/0 pattern for availability percentages

#### What We Log
- **All dependency metrics**: Availability and latency of every external call
- **Per-call breakdowns**: Separate metrics per resource, status code, retry attempt
- **Queue depths**: Current depth when accessing queues, plus time-in-queue measurements
- **Error categorization**: Separate counters for each error reason and category
- **Request metadata**: Enough context for customer support and troubleshooting
- **Trace ID propagation**: Consistent trace IDs across all service boundaries
- **Latency by status**: Separate timers for success/error to avoid metric pollution

### High Throughput Optimizations
For services handling high request volumes:

```java
// Sampling strategy
@Component
public class SamplingLogger {
  private final AtomicLong requestCount = new AtomicLong();
  private final int samplingRate;
  
  public void logIfSampled(Supplier<LogEntry> entrySupplier) {
    long count = requestCount.incrementAndGet();
    if (count % samplingRate == 0) {
      LogEntry entry = entrySupplier.get();
      entry.setSamplingRate(samplingRate);
      entry.setSkippedCount(samplingRate - 1);
      logger.info(entry);
    }
  }
}

// Async logging to separate thread
@Component
public class AsyncMetricsLogger {
  private final DisruptorQueue<LogEntry> logQueue;
  
  public void logAsync(LogEntry entry) {
    if (!logQueue.offer(entry)) {
      // Handle backpressure - maybe sample more aggressively
      Metrics.counter("logging.dropped").increment();
    }
  }
}
```

### Application Log Best Practices
- **Keep logs spam-free**: Disable INFO/DEBUG in production, use structured request logs instead
- **Include request IDs**: Enable jumping between request logs and application logs
- **Rate-limit error spam**: Prevent log flooding during error conditions
- **Use format strings**: Avoid string concatenation overhead for disabled log levels
- **Log service request IDs**: Include downstream service request IDs for follow-up

**Remember**: Observability is not just monitoring - it's about understanding system behavior through the data it produces. At Amazon, we "log first, aggregate later" - detailed logs enable both real-time troubleshooting and historical metrics analysis. Build systems that explain themselves! 📊