# Spock Testing Standards 🧪

## Default Testing Framework

### Spock and Groovy by Default
- **Primary testing framework**: Use Spock Framework with Groovy for all tests
- **JUnit only when necessary**: Use JUnit 5 only for performance benchmarks or when Spock limitations exist
- **Groovy test sources**: Place tests in `src/test/groovy` directory
- **Maven configuration**: Include Groovy compiler and Spock dependencies

## Why Spock Framework

### Advantages Over JUnit
- **Readable specifications**: Natural language test descriptions
- **Built-in mocking**: No need for separate mocking frameworks
- **Data-driven testing**: Powerful `where:` blocks for parameterized tests
- **Better assertions**: Rich comparison and error messages
- **Behavior-driven**: Given-When-Then structure enforces clarity

### Spock Best Practices

#### Test Structure
```groovy
class MessageProcessingServiceSpec extends Specification {
  
  MessageParser parser = Mock()
  ValidationService validator = Mock()
  StorageService storage = Mock()
  
  MessageProcessingService service = new MessageProcessingService(parser, validator, storage)
  
  def "should process valid EDI message successfully"() {
    given: "a valid EDI message"
    def message = "UNH+1+ORDERS:D:03B:UN:EAN008'"
    def parsedMessage = new ParsedMessage(messageType: "ORDERS")
    
    and: "mocked dependencies return expected results"
    parser.parse(message) >> parsedMessage
    validator.validate(parsedMessage) >> ValidationResult.valid()
    storage.store(parsedMessage) >> "stored-id-123"
    
    when: "processing the message"
    def result = service.process(message)
    
    then: "the message is processed successfully"
    result.success
    result.messageId == "stored-id-123"
    
    and: "all dependencies are called correctly"
    1 * parser.parse(message)
    1 * validator.validate(parsedMessage)
    1 * storage.store(parsedMessage)
  }
}
```

#### Data-Driven Testing
```groovy
def "should validate different message types"() {
  given: "a message validator"
  def validator = new MessageValidator()
  
  expect: "validation results match expected outcomes"
  validator.validate(message) == expectedResult
  
  where: "testing various message formats"
  message                           || expectedResult
  "UNH+1+ORDERS:D:03B:UN:EAN008'"  || true
  "UNH+1+INVOIC:D:03B:UN:EAN008'"  || true
  "INVALID_FORMAT"                  || false
  ""                                || false
  null                              || false
}
```

#### Exception Testing
```groovy
def "should throw exception for invalid message format"() {
  given: "an invalid EDI message"
  def invalidMessage = "INVALID_FORMAT"
  
  when: "parsing the message"
  parser.parse(invalidMessage)
  
  then: "parsing exception is thrown"
  def exception = thrown(EdifactParsingException)
  exception.message.contains("Invalid format")
}
```

#### Mock Interactions
```groovy
def "should retry on transient failures"() {
  given: "a service that may fail transiently"
  def externalService = Mock(ExternalService)
  def retryableService = new RetryableService(externalService)
  
  when: "calling the service"
  def result = retryableService.processWithRetry("test-data")
  
  then: "service is called multiple times on failure"
  3 * externalService.process("test-data") >>> [
    { throw new TransientException("Temporary failure") },
    { throw new TransientException("Still failing") },
    { return "success" }
  ]
  
  and: "final result is successful"
  result == "success"
}
```

## Maven Configuration

### Required Dependencies
```xml
<dependencies>
  <!-- Spock Framework -->
  <dependency>
    <groupId>org.spockframework</groupId>
    <artifactId>spock-core</artifactId>
    <version>2.3-groovy-4.0</version>
    <scope>test</scope>
  </dependency>
  
  <!-- Spock Spring Integration -->
  <dependency>
    <groupId>org.spockframework</groupId>
    <artifactId>spock-spring</artifactId>
    <version>2.3-groovy-4.0</version>
    <scope>test</scope>
  </dependency>
  
  <!-- Groovy -->
  <dependency>
    <groupId>org.apache.groovy</groupId>
    <artifactId>groovy</artifactId>
    <version>4.0.15</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

### Groovy Compiler Plugin
```xml
<plugin>
  <groupId>org.codehaus.gmavenplus</groupId>
  <artifactId>gmavenplus-plugin</artifactId>
  <version>3.0.2</version>
  <executions>
    <execution>
      <goals>
        <goal>compileTests</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### Surefire Configuration
```xml
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <includes>
      <include>**/*Spec</include>
      <include>**/*Test</include>
    </includes>
  </configuration>
</plugin>
```

## Spring Boot Integration

### Spock with Spring Boot Test
```groovy
@SpringBootTest
@TestPropertySource(properties = [
  "aws.s3.bucket=test-bucket",
  "aws.region=us-east-1"
])
class MessageProcessingIntegrationSpec extends Specification {
  
  @Autowired
  MessageProcessingService service
  
  @MockBean
  S3Client s3Client
  
  def "should process message end-to-end"() {
    given: "a test message"
    def message = "UNH+1+ORDERS:D:03B:UN:EAN008'"
    
    and: "mocked S3 response"
    s3Client.getObject(_ as GetObjectRequest) >> ResponseInputStream.nullInputStream()
    
    when: "processing the message"
    def result = service.processFromS3("test-key")
    
    then: "processing succeeds"
    result.success
    
    and: "S3 is called"
    1 * s3Client.getObject(_ as GetObjectRequest)
  }
}
```

## Testing Patterns

### TDD with Spock
```groovy
// RED: Write failing test first
def "should calculate processing fee for premium partners"() {
  given: "a premium partner"
  def partner = new Partner(id: "PREMIUM001", tier: PartnerTier.PREMIUM)
  def calculator = new FeeCalculator()
  
  when: "calculating processing fee"
  def fee = calculator.calculateProcessingFee(partner, 1000.00)
  
  then: "premium discount is applied"
  fee == 15.00 // 1.5% instead of standard 2%
}

// GREEN: Implement minimal code to pass
// REFACTOR: Improve while keeping tests green
```

### Error Scenario Testing
```groovy
def "should handle various error conditions gracefully"() {
  given: "a message processor"
  def processor = new MessageProcessor()
  
  when: "processing problematic input"
  def result = processor.process(input)
  
  then: "appropriate error handling occurs"
  result.success == expectedSuccess
  result.errorType == expectedError
  
  where: "testing different error scenarios"
  input           || expectedSuccess | expectedError
  null            || false           | ErrorType.NULL_INPUT
  ""              || false           | ErrorType.EMPTY_INPUT
  "INVALID"       || false           | ErrorType.PARSE_ERROR
  validMessage()  || true            | null
}
```

### Async Testing
```groovy
def "should process messages asynchronously"() {
  given: "an async message processor"
  def processor = new AsyncMessageProcessor()
  def latch = new CountDownLatch(1)
  def result = null
  
  when: "processing message asynchronously"
  processor.processAsync("test-message") { response ->
    result = response
    latch.countDown()
  }
  
  then: "processing completes within timeout"
  latch.await(5, TimeUnit.SECONDS)
  result.success
}
```

## Performance Testing with Spock

### Benchmark-Style Tests
```groovy
def "should process messages within performance requirements"() {
  given: "a message processor and test data"
  def processor = new MessageProcessor()
  def messages = (1..1000).collect { generateTestMessage(it) }
  
  when: "processing messages in batch"
  def startTime = System.currentTimeMillis()
  def results = messages.collect { processor.process(it) }
  def duration = System.currentTimeMillis() - startTime
  
  then: "all messages process successfully"
  results.every { it.success }
  
  and: "processing time is within limits"
  duration < 5000 // 5 seconds for 1000 messages
  
  and: "average processing time per message is acceptable"
  (duration / messages.size()) < 5 // < 5ms per message
}
```

## Code Quality Standards

### Naming Conventions
- **Spec classes**: End with `Spec` (e.g., `MessageProcessorSpec`)
- **Test methods**: Use descriptive sentences with "should"
- **Feature methods**: Describe behavior, not implementation
- **Data variables**: Use meaningful names in `where:` blocks

### Documentation
```groovy
/**
 * Tests for EDI message processing functionality.
 * 
 * Covers parsing, validation, and storage of EDIFACT messages
 * with various partner configurations and error scenarios.
 */
class MessageProcessingServiceSpec extends Specification {
  
  def "should process ORDERS message for retail partner"() {
    // Test implementation
  }
}
```

### Assertion Quality
```groovy
// Good: Specific assertions
then: "order details are correctly parsed"
result.orderNumber == "ORDER123"
result.lineItems.size() == 2
result.totalAmount == 1250.50

// Avoid: Generic assertions
then: "result is valid"
result != null
result.success
```

## Integration with Existing Rules

### TDD Compliance
- Write Spock specifications first (RED phase)
- Implement minimal code to pass (GREEN phase)
- Refactor while maintaining test coverage (REFACTOR phase)

### Observability Testing
```groovy
def "should emit canonical log line on successful processing"() {
  given: "a message processor with observability"
  def processor = new ObservableMessageProcessor()
  def logCaptor = new LogCaptor()
  
  when: "processing a message"
  processor.process("UNH+1+ORDERS:D:03B:UN:EAN008'")
  
  then: "canonical log line is emitted"
  def logEntry = logCaptor.getCanonicalLogEntry()
  logEntry.status == "success"
  logEntry.messageType == "ORDERS"
  logEntry.duration > 0
}
```

## Success Criteria

### Test Quality Metrics
- [ ] All new features tested with Spock specifications
- [ ] > 90% test coverage maintained
- [ ] Data-driven tests for complex scenarios
- [ ] Clear given-when-then structure
- [ ] Meaningful test descriptions
- [ ] Proper mock usage and verification