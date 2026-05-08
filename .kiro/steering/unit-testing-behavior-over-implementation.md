# Unit Testing: Behavior Over Implementation 🧪

## Core Philosophy: Test Behavior, Not Implementation

**The fundamental mistake in modern TDD**: Pairing each implementation class with a twin test class creates brittle, implementation-coupled tests that make refactoring painful.

**The correct approach**: Test units of behavior that represent business requirements, not individual classes or methods.

## The Real TDD Cycle 🔄

### RED Phase: Define Required Behavior
Write tests that represent the **behavior needed from the system**, not the structure of classes:

```java
// ✅ Good: Testing behavior - "System should process valid EDI orders"
@Test
void shouldProcessValidEdiOrderAndStoreResult() {
  // Given: A valid EDI order message
  String validOrder = """
    UNH+1+ORDERS:D:03B:UN:EAN008'
    BGM+220+ORDER123+9'
    NAD+BY+BUYER001++[company_name]+[address]'
    UNT+4+1'
    """;
  
  // When: Processing the order
  ProcessingResult result = orderProcessor.process(validOrder);
  
  // Then: Order is successfully processed and stored
  assertThat(result.isSuccess()).isTrue();
  assertThat(result.getOrderNumber()).isEqualTo("ORDER123");
  assertThat(result.getStorageLocation()).isNotNull();
}

// ❌ Bad: Testing implementation - "Parser class should call validator"
@Test
void shouldCallValidatorWhenParsing() {
  parser.parse(message);
  verify(validator).validate(any()); // Testing implementation detail
}
```

### GREEN Phase: Sinful Implementation
Write code with **no thoughts on design, patterns, or structure**. Do it the "naughty way":

```java
// ✅ Good: Sinful GREEN implementation - everything in one method
public ProcessingResult process(String ediMessage) {
  // Parse inline - no separate parser class yet
  String messageType = ediMessage.substring(
    ediMessage.indexOf("UNH+1+") + 6, 
    ediMessage.indexOf(":")
  );
  
  // Validate inline - no separate validator yet
  if (!ediMessage.startsWith("UNH+")) {
    return ProcessingResult.failure("Invalid format");
  }
  
  // Extract order number inline
  String orderNumber = ediMessage.substring(
    ediMessage.indexOf("BGM+220+") + 8,
    ediMessage.indexOf("+", ediMessage.indexOf("BGM+220+") + 8)
  );
  
  // Store inline - no separate storage service yet
  String storageId = "stored-" + System.currentTimeMillis();
  
  return ProcessingResult.success(orderNumber, storageId);
}
```

### REFACTOR Phase: Extract Design
**Only now** add proper design, patterns, and structure:

```java
// ✅ Good: REFACTOR - extract responsibilities while keeping tests green
@Component
@RequiredArgsConstructor
public class OrderProcessingService {
  private final EdiParser parser;
  private final OrderValidator validator;
  private final OrderStorage storage;
  
  public ProcessingResult process(String ediMessage) {
    ParsedOrder order = parser.parse(ediMessage);
    ValidationResult validation = validator.validate(order);
    
    if (!validation.isValid()) {
      return ProcessingResult.failure(validation.getErrors());
    }
    
    String storageId = storage.store(order);
    return ProcessingResult.success(order.getOrderNumber(), storageId);
  }
}
```

**Key Insight**: The same test now covers multiple classes (parser, validator, storage) without modification.

## Proper Test Isolation 🏝️

### What "Isolation" Really Means

**WRONG**: Isolate every class from every other class
**RIGHT**: Isolate tests from crossing ports (network, database, file system, external services)

```java
// ✅ Good: Isolate at ports, not between domain classes
@Test
void shouldProcessOrderAndNotifyPartner() {
  // Mock external service (crosses port)
  when(partnerNotificationService.notify(any())).thenReturn(true);
  
  // Don't mock domain collaborators - let them work together
  ProcessingResult result = orderProcessor.process(validOrderMessage);
  
  assertThat(result.isSuccess()).isTrue();
  verify(partnerNotificationService).notify(any(NotificationRequest.class));
}

// ❌ Bad: Over-isolation - mocking domain collaborators
@Test
void shouldCallParserThenValidatorThenStorage() {
  when(parser.parse(any())).thenReturn(parsedOrder);
  when(validator.validate(any())).thenReturn(ValidationResult.valid());
  when(storage.store(any())).thenReturn("storage-id");
  
  orderProcessor.process(message);
  
  // Brittle - breaks when refactoring internal structure
  InOrder inOrder = inOrder(parser, validator, storage);
  inOrder.verify(parser).parse(message);
  inOrder.verify(validator).validate(parsedOrder);
  inOrder.verify(storage).store(parsedOrder);
}
```

### Port Boundaries in EDI Processing

```java
// Ports (mock these in tests)
public interface ExternalPartnerService {
  void notifyOrderReceived(OrderNotification notification);
}

public interface DatabaseRepository {
  void saveOrder(ProcessedOrder order);
}

public interface S3StorageService {
  String uploadFile(String key, byte[] content);
}

// Domain services (don't mock these - let them collaborate)
public interface OrderParser {
  ParsedOrder parse(String ediMessage);
}

public interface OrderValidator {
  ValidationResult validate(ParsedOrder order);
}

public interface OrderTransformer {
  ProcessedOrder transform(ParsedOrder order);
}
```

## Test Structure Patterns 📋

### Behavior-Driven Test Organization

```java
// ✅ Good: Tests organized by business behavior
class OrderProcessingBehaviorTest {
  
  @Test
  void shouldAcceptValidOrdersFromTrustedPartners() {
    // Test covers: parsing, validation, transformation, storage
  }
  
  @Test
  void shouldRejectOrdersWithInvalidFormat() {
    // Test covers: parsing, error handling, logging
  }
  
  @Test
  void shouldHandlePartnerNotificationFailuresGracefully() {
    // Test covers: processing, notification, retry logic
  }
}

// ❌ Bad: Tests organized by implementation classes
class OrderParserTest { /* ... */ }
class OrderValidatorTest { /* ... */ }
class OrderStorageTest { /* ... */ }
class OrderTransformerTest { /* ... */ }
```

### Test Naming That Describes Behavior

```java
// ✅ Good: Names describe business behavior
@Test
void shouldProcessEDIFACTOrdersFromRetailPartners() { }

@Test
void shouldRejectOrdersExceedingCreditLimit() { }

@Test
void shouldConvertTRADACOMSOrdersToInternalFormat() { }

// ❌ Bad: Names describe implementation details
@Test
void shouldCallValidateMethodOnValidator() { }

@Test
void shouldReturnTrueWhenParsingSucceeds() { }

@Test
void shouldInvokeStorageServiceExactlyOnce() { }
```

## Refactoring Safety 🛡️

### Tests That Enable Fearless Refactoring

```java
// This test remains unchanged through major refactoring
@Test
void shouldProcessBatchOfOrdersAndGenerateReport() {
  // Given: A batch of EDI orders
  List<String> orderBatch = List.of(
    createValidOrder("ORDER001"),
    createValidOrder("ORDER002"),
    createValidOrder("ORDER003")
  );
  
  // When: Processing the batch
  BatchProcessingResult result = batchProcessor.process(orderBatch);
  
  // Then: All orders processed and report generated
  assertThat(result.getSuccessCount()).isEqualTo(3);
  assertThat(result.getFailureCount()).isEqualTo(0);
  assertThat(result.getReportLocation()).isNotNull();
}
```

**Refactoring scenarios this test survives:**
- Extracting separate classes for parsing, validation, storage
- Introducing design patterns (Strategy, Factory, Chain of Responsibility)
- Changing internal data structures
- Optimizing algorithms
- Adding caching layers

### Anti-Pattern: Implementation-Coupled Tests

```java
// ❌ Bad: This test breaks with every refactoring
@Test
void shouldFollowExactProcessingSequence() {
  // Brittle - assumes specific class structure
  when(orderParser.parse(message)).thenReturn(parsedOrder);
  when(orderValidator.validate(parsedOrder)).thenReturn(validResult);
  when(orderTransformer.transform(parsedOrder)).thenReturn(transformedOrder);
  when(orderStorage.store(transformedOrder)).thenReturn("id");
  
  orderProcessor.process(message);
  
  // Breaks when introducing new classes or changing sequence
  InOrder sequence = inOrder(orderParser, orderValidator, orderTransformer, orderStorage);
  sequence.verify(orderParser).parse(message);
  sequence.verify(orderValidator).validate(parsedOrder);
  sequence.verify(orderTransformer).transform(parsedOrder);
  sequence.verify(orderStorage).store(transformedOrder);
}
```

## Discovery Testing vs. Production Testing 🔍

### Discovery Tests (Don't Check In)

Use low-level, class-specific tests for **discovery** when working with legacy code:

```java
// Discovery test - helps understand existing code, don't commit
@Test
void discoverHowLegacyParserWorks() {
  LegacyEdiParser parser = new LegacyEdiParser();
  
  // Explore behavior to understand before refactoring
  ParseResult result = parser.parseSegment("UNH+1+ORDERS");
  
  // Document findings, then delete this test
  assertThat(result.getSegmentType()).isEqualTo("UNH");
}
```

### Production Tests (Check In)

Write behavior-focused tests that survive refactoring:

```java
// Production test - documents requirements, commit this
@Test
void shouldExtractOrderHeaderFromEDIFACTMessage() {
  String ediMessage = "UNH+1+ORDERS:D:03B:UN:EAN008'BGM+220+ORDER123+9'";
  
  OrderHeader header = orderProcessor.extractHeader(ediMessage);
  
  assertThat(header.getMessageType()).isEqualTo("ORDERS");
  assertThat(header.getVersion()).isEqualTo("D.03B");
  assertThat(header.getOrderNumber()).isEqualTo("ORDER123");
}
```

## Testing Strategies by Domain 🎯

### EDI Message Processing Tests

```java
class EdiMessageProcessingBehaviorTest {
  
  @Test
  void shouldProcessValidEDIFACTOrdersEndToEnd() {
    // Behavior: System accepts valid EDIFACT orders
    String edifactOrder = createValidEdifactOrder();
    
    ProcessingResult result = messageProcessor.process(edifactOrder);
    
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getProcessedMessage().getStandard()).isEqualTo("EDIFACT");
  }
  
  @Test
  void shouldProcessValidTRADACOMSOrdersEndToEnd() {
    // Behavior: System accepts valid TRADACOMS orders
    String tradacomsOrder = createValidTradacomsOrder();
    
    ProcessingResult result = messageProcessor.process(tradacomsOrder);
    
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getProcessedMessage().getStandard()).isEqualTo("TRADACOMS");
  }
  
  @Test
  void shouldRejectMalformedMessagesWithClearErrors() {
    // Behavior: System provides clear feedback for invalid messages
    String malformedMessage = "INVALID_EDI_FORMAT";
    
    ProcessingResult result = messageProcessor.process(malformedMessage);
    
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorMessage()).contains("Invalid message format");
    assertThat(result.getErrorCode()).isEqualTo("PARSE_ERROR");
  }
}
```

### Multi-Tenant Processing Tests

```java
class MultiTenantProcessingBehaviorTest {
  
  @Test
  void shouldIsolateTenantDataDuringProcessing() {
    // Behavior: Each tenant's data remains isolated
    String tenant1Message = createOrderForTenant("tenant-1");
    String tenant2Message = createOrderForTenant("tenant-2");
    
    // Process for tenant 1
    TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString("tenant-1")), () -> {
      ProcessingResult result1 = messageProcessor.process(tenant1Message);
      assertThat(result1.isSuccess()).isTrue();
    });
    
    // Process for tenant 2
    TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString("tenant-2")), () -> {
      ProcessingResult result2 = messageProcessor.process(tenant2Message);
      assertThat(result2.isSuccess()).isTrue();
    });
    
    // Verify isolation - tenant 1 can't see tenant 2's data
    TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString("tenant-1")), () -> {
      List<ProcessedOrder> orders = orderRepository.findAllForCurrentTenant();
      assertThat(orders).hasSize(1);
      assertThat(orders.get(0).getTenantId()).isEqualTo("tenant-1");
    });
  }
}
```

## Mock Usage Guidelines 🎭

### When TO Mock

```java
// ✅ Mock external services (cross ports)
@MockBean
private PartnerNotificationService partnerService; // External HTTP service

@MockBean  
private S3Client s3Client; // AWS service

@MockBean
private DatabaseRepository repository; // Database access

// ✅ Mock for error simulation
@Test
void shouldHandlePartnerServiceFailuresGracefully() {
  when(partnerService.notify(any()))
    .thenThrow(new ServiceUnavailableException("Partner system down"));
  
  ProcessingResult result = orderProcessor.process(validOrder);
  
  assertThat(result.isSuccess()).isTrue(); // Processing continues
  assertThat(result.getWarnings()).contains("Partner notification failed");
}
```

### When NOT to Mock

```java
// ❌ Don't mock domain collaborators
// Let these work together naturally:
private OrderParser parser;           // Domain service
private OrderValidator validator;     // Domain service  
private OrderTransformer transformer; // Domain service
private BusinessRuleEngine ruleEngine; // Domain service

// ✅ Let domain objects collaborate in tests
@Test
void shouldApplyBusinessRulesWhenProcessingOrders() {
  // No mocking of domain services - let them work together
  ProcessingResult result = orderProcessor.process(orderWithSpecialRules);
  
  assertThat(result.getAppliedRules()).contains("BULK_DISCOUNT", "PRIORITY_SHIPPING");
}
```

## Test Data Management 📊

### Behavior-Focused Test Data

```java
public class EdiTestDataBuilder {
  
  // Build data that represents business scenarios
  public static String validRetailOrder() {
    return """
      UNH+1+ORDERS:D:03B:UN:EAN008'
      BGM+220+RETAIL001+9'
      NAD+BY+RETAILER123++[retailer_name]+[address]'
      LIN+1++PRODUCT001:IN'
      QTY+21:100:PCE'
      UNT+6+1'
      """;
  }
  
  public static String orderExceedingCreditLimit() {
    return """
      UNH+1+ORDERS:D:03B:UN:EAN008'
      BGM+220+LARGE001+9'
      NAD+BY+RETAILER456++[retailer_name]+[address]'
      MOA+86:1000000.00:GBP'
      UNT+5+1'
      """;
  }
  
  public static String orderFromNewPartner() {
    return """
      UNH+1+ORDERS:D:03B:UN:EAN008'
      BGM+220+NEW001+9'
      NAD+BY+NEWPARTNER++[new_partner_name]+[address]'
      UNT+4+1'
      """;
  }
}
```

## Quality Metrics 📈

### Healthy Test Suite Indicators

- **Test names describe business behavior**, not implementation details
- **Tests survive major refactoring** without modification
- **One test class covers multiple implementation classes** naturally
- **Mocks are used only at port boundaries** (external services)
- **Test failures indicate broken business requirements**, not implementation changes
- **Refactoring is fearless** because tests provide safety net

### Unhealthy Test Suite Warning Signs

- **One test class per implementation class** (over-isolation)
- **Tests break when refactoring internal structure** (implementation coupling)
- **Heavy mocking of domain collaborators** (preventing natural collaboration)
- **Test names mention class/method names** instead of business behavior
- **Developers avoid refactoring** because tests are too brittle
- **Tests focus on "how" instead of "what"** the system should do

## Migration Strategy 🔄

### Refactoring Existing Over-Isolated Tests

1. **Identify behavior clusters**: Group related test classes by business behavior
2. **Combine into behavior tests**: Merge tests that represent same business requirement
3. **Remove excessive mocking**: Let domain objects collaborate naturally
4. **Rename for clarity**: Use business-focused names instead of technical ones
5. **Verify refactoring safety**: Ensure tests still provide protection during changes

### Example Migration

```java
// Before: Over-isolated tests
class OrderParserTest { /* 15 tests */ }
class OrderValidatorTest { /* 12 tests */ }
class OrderTransformerTest { /* 8 tests */ }
class OrderStorageTest { /* 6 tests */ }

// After: Behavior-focused tests  
class OrderProcessingBehaviorTest {
  @Test void shouldProcessValidRetailOrders() { /* covers parsing, validation, transformation, storage */ }
  @Test void shouldRejectInvalidOrderFormats() { /* covers parsing errors, validation failures */ }
  @Test void shouldHandlePartnerSpecificRequirements() { /* covers transformation rules, storage options */ }
  // ... 8 behavior tests total instead of 41 implementation tests
}
```

## Success Criteria ✅

### Implementation Checklist
- [ ] Tests describe business behavior, not implementation details
- [ ] Test names explain "what" the system should do, not "how"
- [ ] Mocks used only at port boundaries (external services)
- [ ] Domain objects collaborate naturally in tests
- [ ] Tests survive major refactoring without changes
- [ ] TDD follows RED (behavior) → GREEN (sinful) → REFACTOR (design) cycle

### Quality Validation
- [ ] Refactoring feels safe and doesn't break tests
- [ ] Test failures indicate broken business requirements
- [ ] New developers can understand system behavior from reading tests
- [ ] Test maintenance overhead is minimal
- [ ] Implementation can evolve without test changes

**Remember**: The goal is tests that document business requirements and enable fearless refactoring. Test the behavior your users care about, not the internal structure of your code! 🧪