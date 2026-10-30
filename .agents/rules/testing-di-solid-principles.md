# Testing, Dependency Injection & SOLID Principles 🧪⚡🏗️

## Testing Philosophy 🎯

### Test-First Mindset
- **Tests define behavior** - Write tests that specify what the system should do
- **Tests enable refactoring** - Good tests allow fearless code changes
- **Tests are documentation** - Tests explain how components should be used
- **Tests drive design** - Hard-to-test code indicates design problems

### Testing Hierarchy
```
Unit Tests (Fast, Isolated)
├── Business Logic Testing
├── Component Behavior Testing
└── Edge Case Validation

Integration Tests (Realistic, Focused)
├── Module Integration
├── AWS Service Integration
└── Database Integration

End-to-End Tests (Complete, Expensive)
├── Full Workflow Testing
├── Multi-Tenant Scenarios
└── Performance Validation
```

## Unit Testing Standards 🔬

### Test Structure Pattern
```java
// ✅ Good: Clear test structure with descriptive naming
class EdifactMessageParserTest {
  
  @Mock private SchemaValidator schemaValidator;
  @Mock private MessageTransformer transformer;
  @InjectMocks private EdifactMessageParser parser;
  
  @Test
  void shouldParseValidOrdersMessageSuccessfully() {
    // Given - Arrange test data and mocks
    String validMessage = "UNH+1+ORDERS:D:03B:UN:EAN008'";
    when(schemaValidator.validate(any())).thenReturn(ValidationResult.valid());
    
    // When - Execute the behavior under test
    ParsedMessage result = parser.parse(validMessage);
    
    // Then - Assert expected outcomes
    assertThat(result.getMessageType()).isEqualTo("ORDERS");
    verify(schemaValidator).validate(any());
  }
}
```

### Test Behavior, Not Implementation
```java
// ❌ Bad: Testing implementation details
@Test
void shouldCallValidatorAndTransformerInOrder() {
  parser.parse("message");
  
  InOrder inOrder = inOrder(schemaValidator, transformer);
  inOrder.verify(schemaValidator).validate(any());
  inOrder.verify(transformer).transform(any());
}

// ✅ Good: Testing observable behavior
@Test
void shouldReturnValidParsedMessageForValidInput() {
  String validMessage = "UNH+1+ORDERS:D:03B:UN:EAN008'";
  
  ParsedMessage result = parser.parse(validMessage);
  
  assertThat(result.isValid()).isTrue();
  assertThat(result.getMessageType()).isEqualTo("ORDERS");
}
```

### Test Data Management
```java
// ✅ Good: Centralized test data with meaningful names
public class EdiTestData {
  
  public static String validOrdersMessage() {
    return """
      UNH+1+ORDERS:D:03B:UN:EAN008'
      BGM+220+[order_number]+9'
      NAD+BY+[buyer_id]++[company_name]+[address]'
      UNT+4+1'
      """;
  }
  
  public static ParsedMessage expectedOrdersResult() {
    return ParsedMessage.builder()
      .messageType("ORDERS")
      .version("D.03B")
      .valid(true)
      .build();
  }
}
```

### Parameterized Testing
```java
@ParameterizedTest
@ValueSource(strings = {
  "UNH+1+ORDERS:D:03B:UN:EAN008'",
  "UNH+1+INVOIC:D:03B:UN:EAN008'",
  "UNH+1+DESADV:D:03B:UN:EAN008'"
})
void shouldParseValidMessageTypes(String message) {
  ParsedMessage result = parser.parse(message);
  
  assertThat(result.isValid()).isTrue();
  assertThat(result.getMessageType()).isIn("ORDERS", "INVOIC", "DESADV");
}
```

## Dependency Injection Best Practices 💉

### Constructor Injection Pattern
```java
// ✅ Good: Constructor injection with immutable dependencies
@Component
@RequiredArgsConstructor
public class MessageProcessingService {
  
  private final MessageParser parser;
  private final ValidationService validator;
  private final StorageService storage;
  private final EventPublisher eventPublisher;
  
  public ProcessingResult process(String message) {
    // Implementation uses injected dependencies
  }
}
```

### Interface-Based Dependencies
```java
// ✅ Good: Depend on interfaces, not concrete classes
public interface MessageParser {
  ParsedMessage parse(String message);
  boolean supports(String messageType);
}

@Component
public class EdifactMessageParser implements MessageParser {
  // Implementation
}

@Component
@RequiredArgsConstructor
public class MessageProcessingService {
  private final MessageParser parser; // Interface dependency
}
```

### Avoid Field Injection
```java
// ❌ Bad: Field injection makes testing difficult
@Component
public class MessageService {
  @Autowired
  private MessageParser parser;
  
  @Autowired
  private ValidationService validator;
}

// ✅ Good: Constructor injection enables easy testing
@Component
@RequiredArgsConstructor
public class MessageService {
  private final MessageParser parser;
  private final ValidationService validator;
}
```

### Configuration Properties Pattern
```java
// ✅ Good: Type-safe configuration with validation
@ConfigurationProperties(prefix = "edispark.processing")
@Validated
@Data
public class ProcessingConfiguration {
  
  @NotNull
  @Min(1)
  private Integer maxBatchSize = 1000;
  
  @NotNull
  @Pattern(regexp = "^[A-Z0-9_]+$")
  private String defaultPartnerId;
  
  @NotNull
  private Duration processingTimeout = Duration.ofMinutes(5);
}
```

## SOLID Principles Implementation 🏗️

### Single Responsibility Principle (SRP)
```java
// ❌ Bad: Multiple responsibilities in one class
public class MessageProcessor {
  public void processMessage(String message) {
    // Parse message
    // Validate message
    // Store message
    // Send notifications
    // Log processing
  }
}

// ✅ Good: Each class has single responsibility
@Component
@RequiredArgsConstructor
public class MessageProcessingOrchestrator {
  private final MessageParser parser;
  private final MessageValidator validator;
  private final MessageStorage storage;
  private final NotificationService notificationService;
  
  public ProcessingResult process(String message) {
    ParsedMessage parsed = parser.parse(message);
    ValidationResult validation = validator.validate(parsed);
    
    if (validation.isValid()) {
      String messageId = storage.store(parsed);
      notificationService.notifyProcessed(messageId);
      return ProcessingResult.success(messageId);
    }
    
    return ProcessingResult.failure(validation.getErrors());
  }
}
```

### Open/Closed Principle (OCP)
```java
// ✅ Good: Open for extension, closed for modification
public interface MessageParser {
  ParsedMessage parse(String message);
  boolean supports(String messageType);
}

@Component
public class EdifactMessageParser implements MessageParser {
  public boolean supports(String messageType) {
    return messageType.startsWith("EDIFACT");
  }
}

@Component
public class TradacomsMessageParser implements MessageParser {
  public boolean supports(String messageType) {
    return messageType.startsWith("TRADACOMS");
  }
}

// Parser registry automatically discovers new parsers
@Component
@RequiredArgsConstructor
public class MessageParserRegistry {
  private final List<MessageParser> parsers;
  
  public MessageParser getParser(String messageType) {
    return parsers.stream()
      .filter(parser -> parser.supports(messageType))
      .findFirst()
      .orElseThrow(() -> new UnsupportedMessageTypeException(messageType));
  }
}
```

### Liskov Substitution Principle (LSP)
```java
// ✅ Good: Subtypes are substitutable for base types
public abstract class MessageValidator {
  public ValidationResult validate(ParsedMessage message) {
    ValidationResult result = doValidate(message);
    logValidation(message, result);
    return result;
  }
  
  protected abstract ValidationResult doValidate(ParsedMessage message);
  
  private void logValidation(ParsedMessage message, ValidationResult result) {
    // Common logging behavior
  }
}

public class EdifactValidator extends MessageValidator {
  @Override
  protected ValidationResult doValidate(ParsedMessage message) {
    // EDIFACT-specific validation that maintains contract
    return ValidationResult.valid(); // Always returns ValidationResult
  }
}
```

### Interface Segregation Principle (ISP)
```java
// ❌ Bad: Fat interface forces unnecessary dependencies
public interface MessageProcessor {
  void parseMessage(String message);
  void validateMessage(ParsedMessage message);
  void storeMessage(ParsedMessage message);
  void sendNotification(String messageId);
  void generateReport(String messageId);
}

// ✅ Good: Segregated interfaces for specific concerns
public interface MessageParser {
  ParsedMessage parse(String message);
}

public interface MessageValidator {
  ValidationResult validate(ParsedMessage message);
}

public interface MessageStorage {
  String store(ParsedMessage message);
}

public interface NotificationService {
  void notifyProcessed(String messageId);
}
```

### Dependency Inversion Principle (DIP)
```java
// ❌ Bad: High-level module depends on low-level module
public class OrderProcessingService {
  private final S3StorageService s3Storage; // Concrete dependency
  private final SqsNotificationService sqsNotification; // Concrete dependency
  
  public void processOrder(Order order) {
    s3Storage.store(order); // Tightly coupled to S3
    sqsNotification.send(order.getId()); // Tightly coupled to SQS
  }
}

// ✅ Good: Both depend on abstractions
public interface StorageService {
  String store(Object data);
}

public interface NotificationService {
  void notify(String messageId);
}

@Component
@RequiredArgsConstructor
public class OrderProcessingService {
  private final StorageService storage; // Abstract dependency
  private final NotificationService notification; // Abstract dependency
  
  public void processOrder(Order order) {
    String orderId = storage.store(order);
    notification.notify(orderId);
  }
}
```

## Testing with Dependency Injection 🧪💉

### Mock Injection Pattern
```java
@ExtendWith(MockitoExtension.class)
class MessageProcessingServiceTest {
  
  @Mock private MessageParser parser;
  @Mock private ValidationService validator;
  @Mock private StorageService storage;
  
  @InjectMocks private MessageProcessingService service;
  
  @Test
  void shouldProcessValidMessageSuccessfully() {
    // Given
    String message = "test-message";
    ParsedMessage parsed = new ParsedMessage("ORDERS");
    
    when(parser.parse(message)).thenReturn(parsed);
    when(validator.validate(parsed)).thenReturn(ValidationResult.valid());
    when(storage.store(parsed)).thenReturn("stored-id");
    
    // When
    ProcessingResult result = service.process(message);
    
    // Then
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getMessageId()).isEqualTo("stored-id");
  }
}
```

### Integration Testing with Test Configuration
```java
@TestConfiguration
public class TestMessageProcessingConfiguration {
  
  @Bean
  @Primary
  public StorageService testStorageService() {
    return new InMemoryStorageService(); // Test double
  }
  
  @Bean
  @Primary
  public NotificationService testNotificationService() {
    return new NoOpNotificationService(); // Test double
  }
}

@SpringBootTest
@Import(TestMessageProcessingConfiguration.class)
class MessageProcessingIntegrationTest {
  
  @Autowired
  private MessageProcessingService service;
  
  @Test
  void shouldProcessMessageEndToEnd() {
    // Integration test with test doubles
  }
}
```

## Design Patterns for Testability 🎨

### Strategy Pattern for Algorithm Selection
```java
public interface CompressionStrategy {
  byte[] compress(byte[] data);
  byte[] decompress(byte[] data);
}

@Component
@RequiredArgsConstructor
public class MessageCompressionService {
  private final Map<String, CompressionStrategy> strategies;
  
  public byte[] compress(byte[] data, String algorithm) {
    CompressionStrategy strategy = strategies.get(algorithm);
    if (strategy == null) {
      throw new UnsupportedCompressionException(algorithm);
    }
    return strategy.compress(data);
  }
}

// Easy to test each strategy independently
class GzipCompressionStrategyTest {
  private final GzipCompressionStrategy strategy = new GzipCompressionStrategy();
  
  @Test
  void shouldCompressAndDecompressData() {
    byte[] original = "test data".getBytes();
    
    byte[] compressed = strategy.compress(original);
    byte[] decompressed = strategy.decompress(compressed);
    
    assertThat(decompressed).isEqualTo(original);
  }
}
```

### Factory Pattern for Complex Object Creation
```java
public interface MessageParserFactory {
  MessageParser createParser(String messageType);
}

@Component
public class MessageParserFactoryImpl implements MessageParserFactory {
  
  @Override
  public MessageParser createParser(String messageType) {
    return switch (messageType) {
      case "EDIFACT" -> new EdifactMessageParser();
      case "TRADACOMS" -> new TradacomsMessageParser();
      default -> throw new UnsupportedMessageTypeException(messageType);
    };
  }
}

// Factory is easily mockable for testing
class MessageServiceTest {
  @Mock private MessageParserFactory parserFactory;
  @Mock private MessageParser parser;
  
  @InjectMocks private MessageService service;
  
  @Test
  void shouldUseCorrectParserForMessageType() {
    when(parserFactory.createParser("EDIFACT")).thenReturn(parser);
    
    service.processMessage("EDIFACT", "message-content");
    
    verify(parserFactory).createParser("EDIFACT");
    verify(parser).parse("message-content");
  }
}
```

## Error Handling and Testing 🚨

### Exception Testing Patterns
```java
@Test
void shouldThrowExceptionForInvalidMessage() {
  String invalidMessage = "INVALID_FORMAT";
  
  assertThatThrownBy(() -> parser.parse(invalidMessage))
    .isInstanceOf(EdifactParsingException.class)
    .hasMessageContaining("Invalid format")
    .satisfies(ex -> {
      EdifactParsingException parseEx = (EdifactParsingException) ex;
      assertThat(parseEx.getLineNumber()).isEqualTo(1);
      assertThat(parseEx.getSegmentId()).isEqualTo("UNH");
    });
}
```

### Resilience Testing
```java
@Test
void shouldRetryOnTransientFailures() {
  // Given
  when(externalService.process(any()))
    .thenThrow(new TransientException("Temporary failure"))
    .thenThrow(new TransientException("Still failing"))
    .thenReturn("success");
  
  // When
  String result = resilientService.processWithRetry("test-data");
  
  // Then
  assertThat(result).isEqualTo("success");
  verify(externalService, times(3)).process("test-data");
}
```

## Performance Testing Considerations ⚡

### Memory Usage Testing
```java
@Test
void shouldProcessLargeMessageWithinMemoryLimits() {
  // Given
  String largeMessage = generateLargeMessage(1_000_000); // 1MB
  long initialMemory = getUsedMemory();
  
  // When
  ParsedMessage result = parser.parse(largeMessage);
  
  // Then
  long memoryUsed = getUsedMemory() - initialMemory;
  assertThat(memoryUsed).isLessThan(10_000_000); // < 10MB
  assertThat(result).isNotNull();
}
```

### Concurrent Processing Testing
```java
@Test
void shouldHandleConcurrentProcessingCorrectly() throws InterruptedException {
  int threadCount = 10;
  int messagesPerThread = 100;
  CountDownLatch latch = new CountDownLatch(threadCount);
  AtomicInteger successCount = new AtomicInteger();
  
  for (int i = 0; i < threadCount; i++) {
    new Thread(() -> {
      try {
        for (int j = 0; j < messagesPerThread; j++) {
          ProcessingResult result = service.process("test-message-" + j);
          if (result.isSuccess()) {
            successCount.incrementAndGet();
          }
        }
      } finally {
        latch.countDown();
      }
    }).start();
  }
  
  latch.await(30, TimeUnit.SECONDS);
  assertThat(successCount.get()).isEqualTo(threadCount * messagesPerThread);
}
```

## Quality Gates and Metrics 📊

### Test Coverage Requirements
- **Unit Tests**: Minimum 90% line coverage for business logic
- **Integration Tests**: All public APIs covered
- **End-to-End Tests**: Critical user journeys covered
- **Performance Tests**: All performance-critical paths tested

### Code Quality Metrics
```java
// Cyclomatic complexity should be low (< 10)
// Method length should be short (< 20 lines)
// Class coupling should be minimal (< 7 dependencies)
// Test-to-code ratio should be high (> 1:1)
```

### Continuous Quality Monitoring
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <configuration>
    <rules>
      <rule>
        <element>CLASS</element>
        <limits>
          <limit>
            <counter>LINE</counter>
            <value>COVEREDRATIO</value>
            <minimum>0.90</minimum>
          </limit>
        </limits>
      </rule>
    </rules>
  </configuration>
</plugin>
```

## Success Criteria ✅

### Testing Excellence Indicators
- [ ] All components have comprehensive unit tests
- [ ] Integration tests cover module boundaries
- [ ] Test names clearly describe behavior being tested
- [ ] Tests are fast, reliable, and independent
- [ ] Test data is realistic and well-organized
- [ ] Error scenarios are thoroughly tested

### Dependency Injection Maturity
- [ ] Constructor injection used consistently
- [ ] Dependencies are interfaces, not concrete classes
- [ ] Configuration is externalized and type-safe
- [ ] Components are easily testable in isolation
- [ ] No circular dependencies exist

### SOLID Principles Adherence
- [ ] Each class has a single, well-defined responsibility
- [ ] Components are open for extension, closed for modification
- [ ] Subtypes are fully substitutable for their base types
- [ ] Interfaces are focused and client-specific
- [ ] High-level modules depend on abstractions

**Remember**: Good testing, proper dependency injection, and SOLID principles work together to create maintainable, reliable, and extensible software. They're not separate concerns but complementary practices that reinforce each other! 🧪⚡🏗️