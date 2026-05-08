---
inclusion: fileMatch
fileMatchPattern: '*Test.java|*Spec.groovy|*IT.java'
---

# Testing Strategy and Standards 🧪

## Test Architecture Overview 🏗️

### Test Pyramid Structure
```
    /\     E2E Tests (Few)
   /  \    - Full workflow testing
  /____\   - AWS integration testing
 /      \  
/________\  Integration Tests (Some)
           - Module integration
           - AWS service mocking
           
Unit Tests (Many)
- Business logic testing
- Individual component testing
```

### Test Categories
- **Unit Tests**: Individual class/method testing with mocks
- **Integration Tests**: Module-to-module interaction testing
- **Contract Tests**: API contract validation
- **End-to-End Tests**: Full workflow validation
- **Performance Tests**: Load and stress testing

## Unit Testing Standards 🎯

### JUnit 5 Configuration
```java
@ExtendWith(MockitoExtension.class)
class EdifactMessageParserTest {
  
  @Mock
  private SchemaValidator schemaValidator;
  
  @Mock
  private MessageTransformer transformer;
  
  @InjectMocks
  private EdifactMessageParser parser;
  
  @Test
  @DisplayName("Should parse valid ORDERS message successfully")
  void shouldParseValidOrdersMessage() {
    // Given
    String validOrdersMessage = """
      UNH+1+ORDERS:D:03B:UN:EAN008'
      BGM+220+ORDER123+9'
      DTM+137:20240115:102'
      UNT+4+1'
      """;
    
    when(schemaValidator.validate(any())).thenReturn(ValidationResult.valid());
    
    // When
    ParsedMessage result = parser.parse(validOrdersMessage);
    
    // Then
    assertThat(result).isNotNull();
    assertThat(result.getMessageType()).isEqualTo("ORDERS");
    assertThat(result.getVersion()).isEqualTo("D.03B");
    verify(schemaValidator).validate(any());
  }
}
```

### Spock Framework for Groovy Tests
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
  
  def "should handle parsing errors gracefully"() {
    given: "an invalid EDI message"
    def invalidMessage = "INVALID_FORMAT"
    
    and: "parser throws exception"
    parser.parse(invalidMessage) >> { throw new EdifactParsingException("Invalid format") }
    
    when: "processing the message"
    def result = service.process(invalidMessage)
    
    then: "error is handled gracefully"
    !result.success
    result.errorMessage.contains("Invalid format")
    
    and: "storage is not called"
    0 * storage.store(_)
  }
}
```

### Test Data Management
```java
@TestConfiguration
public class TestDataConfiguration {
  
  @Bean
  @Primary
  public TestDataProvider testDataProvider() {
    return new TestDataProvider();
  }
}

public class TestDataProvider {
  
  public String getValidOrdersMessage() {
    return """
      UNH+1+ORDERS:D:03B:UN:EAN008'
      BGM+220+[order_number]+9'
      NAD+BY+[buyer_id]++[company_name]+[address]'
      DTM+137:[date]:102'
      UNT+5+1'
      """;
  }
  
  public String getValidInvoiceMessage() {
    return """
      UNH+1+INVOIC:D:03B:UN:EAN008'
      BGM+380+[invoice_number]+9'
      DTM+137:[date]:102'
      UNT+4+1'
      """;
  }
}
```

## Integration Testing 🔗

### Spring Boot Test Configuration
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
  "aws.s3.bucket=test-edifact-bucket",
  "aws.region=us-east-1",
  "spring.profiles.active=test"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MessageProcessingIntegrationTest {
  
  @Autowired
  private MessageProcessingService service;
  
  @MockBean
  private S3Client s3Client;
  
  @MockBean
  private SqsClient sqsClient;
  
  @Test
  void shouldProcessMessageEndToEnd() {
    // Given
    String testMessage = testDataProvider.getValidOrdersMessage();
    
    when(s3Client.getObject(any(GetObjectRequest.class)))
      .thenReturn(ResponseInputStream.nullInputStream());
    
    // When
    ProcessingResult result = service.processFromS3("test-key");
    
    // Then
    assertThat(result.isSuccess()).isTrue();
    verify(s3Client).getObject(any(GetObjectRequest.class));
    verify(sqsClient).sendMessage(any(SendMessageRequest.class));
  }
}
```

### AWS Service Testing
```java
@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {
  
  @Mock
  private S3Client s3Client;
  
  @InjectMocks
  private S3StorageService storageService;
  
  @Test
  void shouldStoreCompressedMessage() {
    // Given
    String message = "Large EDI message content...";
    String bucketName = "test-bucket";
    String key = "messages/test-message.gz";
    
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
      .thenReturn(PutObjectResponse.builder().eTag("test-etag").build());
    
    // When
    String result = storageService.storeCompressed(message, bucketName, key);
    
    // Then
    assertThat(result).isEqualTo("test-etag");
    
    ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
    
    PutObjectRequest request = requestCaptor.getValue();
    assertThat(request.bucket()).isEqualTo(bucketName);
    assertThat(request.key()).isEqualTo(key);
    assertThat(request.contentEncoding()).isEqualTo("gzip");
  }
}
```

## Performance Testing ⚡

### Lambda Performance Tests
```java
@Test
void shouldProcessLargeMessageWithinTimeLimit() {
  // Given
  String largeMessage = generateLargeEdiMessage(50_000); // 50KB message
  long startTime = System.currentTimeMillis();
  
  // When
  ParsedMessage result = parser.parse(largeMessage);
  long duration = System.currentTimeMillis() - startTime;
  
  // Then
  assertThat(result).isNotNull();
  assertThat(duration).isLessThan(250); // Based on parser/README.md analysis
}

@Test
void shouldHandleCompressionPerformance() {
  // Given
  String message = generateEdiMessage(57_700); // 57.7KB uncompressed
  
  // When
  long startTime = System.currentTimeMillis();
  byte[] compressed = compressionService.compress(message);
  long compressionTime = System.currentTimeMillis() - startTime;
  
  // Then
  assertThat(compressed.length).isLessThan(message.length() * 0.1); // 90%+ compression
  assertThat(compressionTime).isLessThan(588); // Based on performance analysis
}
```

### Load Testing Configuration
```java
@Test
@Timeout(value = 30, unit = TimeUnit.SECONDS)
void shouldHandleConcurrentMessageProcessing() throws InterruptedException {
  // Given
  int numberOfThreads = 10;
  int messagesPerThread = 100;
  CountDownLatch latch = new CountDownLatch(numberOfThreads);
  List<CompletableFuture<Void>> futures = new ArrayList<>();
  
  // When
  for (int i = 0; i < numberOfThreads; i++) {
    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
      try {
        for (int j = 0; j < messagesPerThread; j++) {
          String message = testDataProvider.getValidOrdersMessage();
          ProcessingResult result = service.process(message);
          assertThat(result.isSuccess()).isTrue();
        }
      } finally {
        latch.countDown();
      }
    });
    futures.add(future);
  }
  
  // Then
  latch.await(25, TimeUnit.SECONDS);
  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
}
```

## Test Configuration and Setup 🔧

### Maven Surefire Configuration
```xml
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.0.0-M5</version>
  <configuration>
    <useModulePath>false</useModulePath>
    <useFile>false</useFile>
    <includes>
      <include>**/*Test</include>
      <include>**/*Spec</include>
      <include>**/*IT</include>
    </includes>
    <argLine>--enable-preview</argLine>
    <systemPropertyVariables>
      <aws.region>us-east-1</aws.region>
      <aws.accessKeyId>test</aws.accessKeyId>
      <aws.secretAccessKey>test</aws.secretAccessKey>
    </systemPropertyVariables>
  </configuration>
</plugin>
```

### Test Profiles
```yaml
# application-test.yml
spring:
  profiles:
    active: test
  
aws:
  s3:
    bucket: test-edifact-bucket
  sqs:
    queue-url: https://sqs.us-east-1.amazonaws.com/123456789/test-queue
  
logging:
  level:
    io.edispark: DEBUG
    org.springframework: WARN
```

### TestContainers for Integration Tests
```java
@Testcontainers
class DatabaseIntegrationTest {
  
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
    .withDatabaseName("edispark_test")
    .withUsername("[test_user]")
    .withPassword("[test_password]");
  
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }
  
  @Test
  void shouldPersistParsedMessage() {
    // Test implementation
  }
}
```

## Test Quality and Coverage 📊

### Coverage Requirements
- Minimum 80% line coverage for all modules
- 90% coverage for critical business logic
- 100% coverage for security-related code
- Exclude generated code and configuration classes

### Quality Gates
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.8</version>
  <executions>
    <execution>
      <id>check</id>
      <goals>
        <goal>check</goal>
      </goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <counter>LINE</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.80</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Test Naming Conventions
- Unit tests: `ClassNameTest.java`
- Integration tests: `ClassNameIT.java`
- Spock specifications: `ClassNameSpec.groovy`
- Test methods: `should[ExpectedBehavior]When[StateUnderTest]()`

#[[file:pom.xml]]