# Resilience and Retry Patterns 🔄

## Core Philosophy: Minimal Retries

**The optimum number of retries is less than 1.** Retries should be used sparingly and only for truly transient failures. Most failures are permanent and retrying wastes resources while delaying error feedback.

**Default approach: Fail fast and fix the root cause instead of masking problems with retries.**

## When NOT to Retry ❌

- **Validation errors**: Invalid input data won't become valid on retry
- **Authentication/authorization failures**: Credentials won't magically fix themselves
- **Business logic violations**: Rule violations are permanent
- **Malformed data**: Parsing errors indicate permanent data issues
- **Resource not found**: Missing files/records won't appear on retry
- **Client errors (4xx)**: These indicate permanent problems

## When TO Retry ✅

Only retry for **truly transient** failures:
- **Network timeouts**: Temporary connectivity issues
- **Service unavailable (503)**: Temporary overload
- **Rate limiting (429)**: Temporary throttling
- **Database connection timeouts**: Temporary connection pool exhaustion
- **Temporary resource locks**: Brief contention scenarios

## Resilience4j Implementation

### Dependency Configuration
```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
  <version>2.1.0</version>
</dependency>
```

### Circuit Breaker Pattern (Preferred)
```java
@Component
public class ExternalServiceClient {
  
  private final CircuitBreaker circuitBreaker;
  
  public ExternalServiceClient() {
    this.circuitBreaker = CircuitBreaker.ofDefaults("externalService");
  }
  
  public String callExternalService(String data) {
    Supplier<String> decoratedSupplier = CircuitBreaker
      .decorateSupplier(circuitBreaker, () -> {
        // Only the actual service call - no retry logic here
        return httpClient.post(data);
      });
    
    return decoratedSupplier.get();
  }
}
```

### Minimal Retry Configuration (Use Sparingly)
```java
@Configuration
public class ResilienceConfiguration {
  
  @Bean
  public Retry minimalRetry() {
    return Retry.custom("minimal")
      .maxAttempts(2)  // MAXIMUM: Only 1 retry (2 total attempts)
      .waitDuration(Duration.ofMillis(100))  // Short delay - fail fast
      .retryOnException(throwable -> 
        // ONLY retry truly transient failures - be very selective
        throwable instanceof ConnectTimeoutException ||
        throwable instanceof SocketTimeoutException ||
        (throwable instanceof HttpStatusException && 
         ((HttpStatusException) throwable).getStatusCode() == 503)
      )
      .build();
  }
  
  // Prefer Circuit Breaker over Retry for most scenarios
  @Bean
  public CircuitBreaker defaultCircuitBreaker() {
    return CircuitBreaker.custom("default")
      .failureRateThreshold(50)
      .waitDurationInOpenState(Duration.ofSeconds(10))
      .slidingWindowSize(10)
      .minimumNumberOfCalls(5)
      .build();
  }
}
```

### Stacked Decorators (Circuit Breaker + Minimal Retry)
```java
@Component
public class ResilientServiceClient {
  
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;
  
  public ResilientServiceClient(CircuitBreaker circuitBreaker, Retry retry) {
    this.circuitBreaker = circuitBreaker;
    this.retry = retry;
  }
  
  public ProcessingResult processWithResilience(String data) {
    // Stack decorators: Circuit Breaker -> Retry -> Actual call
    Supplier<ProcessingResult> decoratedSupplier = Decorators
      .ofSupplier(() -> doActualProcessing(data))
      .withCircuitBreaker(circuitBreaker)
      .withRetry(retry)  // Retry is inner decorator
      .decorate();
    
    try {
      return decoratedSupplier.get();
    } catch (Exception e) {
      log.error("Processing failed after resilience patterns: {}", e.getMessage());
      return ProcessingResult.failure(e.getMessage());
    }
  }
  
  private ProcessingResult doActualProcessing(String data) {
    // Actual business logic - no retry logic here
    return externalService.process(data);
  }
}
```

## Anti-Patterns to Avoid ❌

### Don't Implement Custom Retry Logic
```java
// ❌ Bad: Custom retry implementation
public String callService(String data) {
  int attempts = 0;
  while (attempts < 3) {
    try {
      return httpClient.post(data);
    } catch (Exception e) {
      attempts++;
      if (attempts >= 3) throw e;
      Thread.sleep(1000 * attempts); // Don't do this
    }
  }
}

// ✅ Good: Use Resilience4j
public String callService(String data) {
  Supplier<String> decoratedSupplier = Retry
    .decorateSupplier(retry, () -> httpClient.post(data));
  return decoratedSupplier.get();
}
```

### Don't Retry Everything
```java
// ❌ Bad: Retrying validation errors
@Retryable(value = {Exception.class}, maxAttempts = 3)
public void processMessage(String message) {
  if (message == null) {
    throw new IllegalArgumentException("Message cannot be null");
  }
  // This will never succeed on retry!
}

// ✅ Good: Only retry transient failures
@Retryable(value = {ConnectTimeoutException.class}, maxAttempts = 2)
public void processMessage(String message) {
  // Validate first - don't retry validation
  Objects.requireNonNull(message, "Message cannot be null");
  
  // Only the network call gets retried
  externalService.send(message);
}
```

## Configuration Best Practices

### Application Properties
```yaml
resilience4j:
  circuitbreaker:
    instances:
      default:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        sliding-window-size: 10
        minimum-number-of-calls: 5
  
  retry:
    instances:
      default:
        max-attempts: 2  # ABSOLUTE MAXIMUM - prefer 1 (no retry)
        wait-duration: 100ms  # Fail fast
        retry-exceptions:
          # Be extremely selective - only true transient failures
          - java.net.ConnectTimeoutException
          - java.net.SocketTimeoutException
          # DO NOT add generic exceptions like RuntimeException
```

### Monitoring and Metrics
```java
@Component
public class ResilienceMetrics {
  
  private final MeterRegistry meterRegistry;
  
  public ResilienceMetrics(MeterRegistry meterRegistry, 
                          CircuitBreaker circuitBreaker, 
                          Retry retry) {
    this.meterRegistry = meterRegistry;
    
    // Monitor circuit breaker state
    circuitBreaker.getEventPublisher()
      .onStateTransition(event -> 
        meterRegistry.counter("circuit.breaker.state.transition",
          "from", event.getStateTransition().getFromState().name(),
          "to", event.getStateTransition().getToState().name())
        .increment());
    
    // Monitor retry attempts
    retry.getEventPublisher()
      .onRetry(event -> 
        meterRegistry.counter("retry.attempts",
          "name", retry.getName())
        .increment());
  }
}
```

## EDI-Specific Resilience Patterns

### S3 Operations
```java
@Component
public class ResilientS3Service {
  
  // AWS SDKs have retries by default - usually no additional retry needed
  public String uploadFile(String key, byte[] content) {
    // AWS SDK handles retries automatically for transient failures
    PutObjectResponse response = s3Client.putObject(
      PutObjectRequest.builder().bucket(bucket).key(key).build(),
      RequestBody.fromBytes(content)
    );
    return response.eTag();
  }
  
  // Only add Circuit Breaker if you need to protect against cascading failures
  private final CircuitBreaker s3Circuit = CircuitBreaker.ofDefaults("s3");
  
  public String uploadFileWithCircuitBreaker(String key, byte[] content) {
    Supplier<String> decoratedUpload = CircuitBreaker
      .decorateSupplier(s3Circuit, () -> {
        PutObjectResponse response = s3Client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).build(),
          RequestBody.fromBytes(content)
        );
        return response.eTag();
      });
    
    return decoratedUpload.get();
  }
}
```

### EventBridge Publishing
```java
@Component
public class ResilientEventPublisher {
  
  // AWS SDKs have retries by default - focus on Circuit Breaker for protection
  private final CircuitBreaker eventBridgeCircuit = CircuitBreaker
    .ofDefaults("eventbridge");
  
  public void publishEvent(CloudEvent event) {
    Runnable decoratedPublish = CircuitBreaker
      .decorateRunnable(eventBridgeCircuit, () -> {
        // AWS SDK handles retries automatically
        eventBridgeClient.putEvents(
          PutEventsRequest.builder()
            .entries(PutEventsRequestEntry.builder()
              .source(event.getSource())
              .detailType(event.getType())
              .detail(event.getData().toString())
              .build())
            .build()
        );
      });
    
    decoratedPublish.run();
  }
}
```

## Testing Resilience Patterns

### Circuit Breaker Testing
```java
@Test
void shouldOpenCircuitAfterFailures() {
  // Given
  CircuitBreaker circuitBreaker = CircuitBreaker.custom("test")
    .failureRateThreshold(50)
    .minimumNumberOfCalls(2)
    .build();
  
  Supplier<String> decoratedSupplier = CircuitBreaker
    .decorateSupplier(circuitBreaker, () -> {
      throw new RuntimeException("Service failure");
    });
  
  // When - trigger failures
  assertThatThrownBy(decoratedSupplier::get).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(decoratedSupplier::get).isInstanceOf(RuntimeException.class);
  
  // Then - circuit should be open
  assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
  
  // Further calls should fail fast
  assertThatThrownBy(decoratedSupplier::get)
    .isInstanceOf(CallNotPermittedException.class);
}
```

### Retry Testing
```java
@Test
void shouldRetryOnlyTransientFailures() {
  // Given
  AtomicInteger attempts = new AtomicInteger(0);
  Retry retry = Retry.custom("test")
    .maxAttempts(2)
    .retryOnException(throwable -> throwable instanceof ConnectTimeoutException)
    .build();
  
  Supplier<String> decoratedSupplier = Retry
    .decorateSupplier(retry, () -> {
      attempts.incrementAndGet();
      throw new ConnectTimeoutException("Timeout");
    });
  
  // When
  assertThatThrownBy(decoratedSupplier::get)
    .isInstanceOf(ConnectTimeoutException.class);
  
  // Then
  assertThat(attempts.get()).isEqualTo(2); // Original + 1 retry
}
```

## Success Criteria ✅

### Implementation Checklist
- [ ] Use Resilience4j instead of custom retry logic
- [ ] Limit retries to 1-2 attempts maximum
- [ ] Only retry truly transient failures
- [ ] Prefer Circuit Breaker over Retry for most scenarios
- [ ] Stack decorators appropriately (Circuit Breaker outer, Retry inner)
- [ ] Monitor resilience patterns with metrics
- [ ] Test failure scenarios and recovery

### Operational Excellence
- [ ] Circuit breakers prevent cascade failures
- [ ] Minimal retries reduce resource waste
- [ ] Fast failure feedback for permanent errors
- [ ] Comprehensive monitoring of resilience patterns
- [ ] Clear alerting on circuit breaker state changes

**Remember**: The optimum number of retries is less than 1. The best retry is no retry. Fix the root cause instead of masking it with retries. When you absolutely must retry (rare cases), do it minimally with Resilience4j patterns and prefer Circuit Breaker over Retry! 🔄

## Retry Decision Tree 🌳

1. **Is this a permanent failure?** → NO RETRY (fix the root cause)
2. **Is this a validation/business logic error?** → NO RETRY (fix the input/logic)
3. **Is this an AWS SDK call?** → NO ADDITIONAL RETRY (AWS SDKs have built-in retries)
4. **Is this a network timeout to external service?** → Maybe 1 retry with Circuit Breaker
5. **Is this a 5xx server error?** → Circuit Breaker only, no retry
6. **Is this a 4xx client error?** → NO RETRY (fix the request)
7. **When in doubt** → NO RETRY (investigate and fix root cause)

**Default stance: Fail fast, alert immediately, fix the underlying issue.**

**Note: AWS SDKs (S3, DynamoDB, EventBridge, etc.) have automatic retries with exponential backoff built-in. Don't add additional retries on top of AWS SDK calls.**