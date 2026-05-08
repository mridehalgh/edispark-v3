# Multi-Tenancy Architecture Standards 🏢

## Overview
EdiSpark uses a comprehensive multi-tenant architecture with tenant-specific encryption, data isolation, and context propagation using the Cottn framework and Java 21 ScopedValues.

## Core Multi-Tenancy Principles 🎯

### Tenant Isolation Levels
- **Data Isolation**: Complete separation of tenant data using encryption and partitioning
- **Processing Isolation**: Tenant context propagated through all processing layers
- **Storage Isolation**: S3 bucket partitioning with tenant-specific encryption keys
- **Security Isolation**: Per-tenant encryption keys managed via AWS KMS and DynamoDB

### Tenant Context Management
```java
// Existing Cottn framework pattern
@Component
public class TenantAwareService {
  
  public void processWithTenantContext(String s3Key) {
    TenantContextHolder tenantContext = extractTenantFromS3Key(s3Key);
    
    // Execute with tenant context using ScopedValue
    TenantContext.runWhere(tenantContext, () -> {
      // All operations within this block have tenant context
      processFile(s3Key);
    });
  }
  
  private TenantContextHolder extractTenantFromS3Key(String s3Key) {
    // Extract from S3 key: tenantId={tenant-id}/yyyy/mm/dd/file.edifact
    String[] keyParts = StringUtils.split(s3Key, "/");
    String[] tenantVariable = StringUtils.split(keyParts[0], "=");
    String tenantIdString = tenantVariable[1];
    return TenantContextHolder.of(TenantId.fromString(tenantIdString));
  }
}
```

## S3 Storage Multi-Tenancy 📦

### Bucket Structure and Partitioning
```
Inbound Bucket: dev-cell0-eu-west-inbound-edifact-files
├── tenantId={tenant-id-1}/
│   ├── 2024/01/15/{uuid}.edifact
│   └── 2024/01/16/{uuid}.edifact
├── tenantId={tenant-id-2}/
│   ├── 2024/01/15/{uuid}.edifact
│   └── 2024/01/16/{uuid}.edifact

Parsed Bucket: {S3_PARSED_FILES}
├── tenantId={tenant-id-1}/
│   ├── 2024/01/15/{uuid}.edifact.parsed.gz
│   └── 2024/01/16/{uuid}.edifact.parsed.gz
├── tenantId={tenant-id-2}/
│   ├── 2024/01/15/{uuid}.edifact.parsed.gz
│   └── 2024/01/16/{uuid}.edifact.parsed.gz
```

### S3 Event Processing Pattern
```java
@AsyncCottnFunction(name = "EdifactReadFile")
public void processS3Event(S3Event s3Events, Context context) {
  s3Events.getRecords().forEach(this::processEventRecord);
}

private void processEventRecord(S3EventNotificationRecord record) {
  String key = record.getS3().getObject().getUrlDecodedKey();
  TenantContextHolder tenantContext = getTenantId(key);
  
  // Process with tenant context
  TenantContext.runWhere(tenantContext, () -> {
    fileParser.parse(key);
  });
}
```

### S3 Access Patterns
```java
@Component
public class TenantAwareS3Service {
  
  private final S3Client s3Client;
  
  public void uploadFile(String fileName, byte[] content) {
    String tenantId = TenantContext.getCurrentTenant().asString();
    String s3Key = buildTenantS3Key(tenantId, fileName);
    
    s3Client.putObject(PutObjectRequest.builder()
      .bucket(bucketName)
      .key(s3Key)
      .tagging(Tagging.builder()
        .tagSet(Tag.builder()
          .key("TenantId")
          .value(tenantId)
          .build())
        .build())
      .build(), 
      RequestBody.fromBytes(content));
  }
  
  private String buildTenantS3Key(String tenantId, String fileName) {
    LocalDate now = LocalDate.now();
    return String.format("tenantId=%s/%d/%02d/%02d/%s",
      tenantId, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), fileName);
  }
}
```

## Encryption and Security 🔐

### Multi-Tenant Encryption Configuration
```java
@Factory
@RequiredArgsConstructor
public class EncryptionConfig {
  
  private final DynamoDbClient dynamoDbClient;
  private final KmsClient kmsClient;
  
  // Environment variables for encryption configuration
  private static final String ENC_KEY_STORE_TABLE_NAME = System.getenv("ENC_KEY_STORE_TABLE_NAME");
  private static final String ENC_LOGICAL_KEY_STORE_TABLE_NAME = System.getenv("ENC_LOGICAL_KEY_STORE_TABLE_NAME");
  private static final String ENC_KMS_KEY_ARN = System.getenv("ENC_KMS_KEY_ARN");
  
  @Singleton
  MultiTenantEncryptionLib multiTenantEncryptionLib() {
    return new MultiTenantEncryptionLib(KeyStoreProvider.KeyStoreProviderConfig.builder()
      .dynamoDbClient(dynamoDbClient)
      .kmsClient(kmsClient)
      .keyStoreTableName(ENC_KEY_STORE_TABLE_NAME)
      .logicalKeyStoreName(ENC_LOGICAL_KEY_STORE_TABLE_NAME)
      .kmsKeyArn(ENC_KMS_KEY_ARN)
      .build());
  }
}
```

### Tenant-Specific Encryption Usage
```java
@Component
@RequiredArgsConstructor
public class TenantEncryptionService {
  
  private final MultiTenantEncryptionLib encryptionLib;
  
  public byte[] encryptForCurrentTenant(byte[] data) {
    String tenantId = TenantContext.getCurrentTenant().asString();
    return encryptionLib.encrypt(data, tenantId);
  }
  
  public byte[] decryptForCurrentTenant(byte[] encryptedData) {
    String tenantId = TenantContext.getCurrentTenant().asString();
    return encryptionLib.decrypt(encryptedData, tenantId);
  }
}
```

### Key Management Strategy
- **Per-Tenant Keys**: Each tenant has unique encryption keys
- **KMS Integration**: AWS KMS manages master keys
- **DynamoDB Storage**: Encrypted tenant keys stored in DynamoDB
- **Automatic Rotation**: Keys rotated based on policy
- **Access Control**: IAM policies restrict key access by tenant

## Database Multi-Tenancy 🗄️

### Tenant Data Isolation Patterns
```java
@Entity
@Table(name = "edi_processing_audit")
public class EdiProcessingAudit {
  
  @Id
  private String messageId;
  
  @Column(name = "tenant_id", nullable = false)
  private String tenantId;  // Always include tenant ID
  
  @Column(name = "partner_id")
  private String partnerId;
  
  @Column(name = "message_type")
  private String messageType;
  
  // Tenant context automatically populated
  @PrePersist
  public void setTenantContext() {
    this.tenantId = TenantContext.getCurrentTenant().asString();
  }
}
```

### Repository Pattern with Tenant Filtering
```java
@Repository
public interface EdiAuditRepository extends JpaRepository<EdiProcessingAudit, String> {
  
  // Automatically filter by current tenant
  @Query("SELECT e FROM EdiProcessingAudit e WHERE e.tenantId = :#{T(io.cottn.core.tenancy.TenantContext).getCurrentTenant().asString()}")
  List<EdiProcessingAudit> findAllForCurrentTenant();
  
  @Query("SELECT e FROM EdiProcessingAudit e WHERE e.tenantId = :#{T(io.cottn.core.tenancy.TenantContext).getCurrentTenant().asString()} AND e.partnerId = :partnerId")
  List<EdiProcessingAudit> findByPartnerIdForCurrentTenant(@Param("partnerId") String partnerId);
}
```

## API Multi-Tenancy 🌐

### Tenant Context Extraction
```java
@Component
public class TenantContextFilter implements Filter {
  
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    
    // Extract tenant from header, JWT, or subdomain
    String tenantId = extractTenantId(httpRequest);
    
    if (tenantId != null) {
      TenantContextHolder tenantContext = TenantContextHolder.of(TenantId.fromString(tenantId));
      
      // Execute request with tenant context
      TenantContext.runWhere(tenantContext, () -> {
        try {
          chain.doFilter(request, response);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    } else {
      // Handle missing tenant context
      ((HttpServletResponse) response).setStatus(HttpStatus.BAD_REQUEST.value());
    }
  }
  
  private String extractTenantId(HttpServletRequest request) {
    // Priority order: Header > JWT > Subdomain
    String tenantId = request.getHeader("X-Tenant-ID");
    
    if (tenantId == null) {
      tenantId = extractFromJWT(request);
    }
    
    if (tenantId == null) {
      tenantId = extractFromSubdomain(request);
    }
    
    return tenantId;
  }
}
```

### Controller Tenant Awareness
```java
@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class PartnerController {
  
  private final PartnerService partnerService;
  
  @GetMapping
  public ResponseEntity<List<Partner>> getPartners() {
    // Service automatically filters by current tenant context
    List<Partner> partners = partnerService.findAllForCurrentTenant();
    return ResponseEntity.ok(partners);
  }
  
  @PostMapping
  public ResponseEntity<Partner> createPartner(@RequestBody CreatePartnerRequest request) {
    // Tenant context automatically applied during creation
    Partner partner = partnerService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(partner);
  }
}
```

## Observability Multi-Tenancy 📊

### Tenant-Aware Logging
```java
@Component
public class TenantAwareLogger {
  
  public void logWithTenantContext(String message, Object... args) {
    String tenantId = TenantContext.getCurrentTenant().asString();
    
    log.info("{} - {}", 
      kv("tenant.id", tenantId),
      kv("message", message),
      args
    );
  }
}
```

### Metrics with Tenant Dimensions
```java
@Component
public class TenantMetricsService {
  
  private final MeterRegistry meterRegistry;
  
  public void recordProcessingMetrics(String messageType, Duration duration, boolean success) {
    String tenantId = TenantContext.getCurrentTenant().asString();
    
    Timer.builder("edi.processing.duration")
      .tag("tenant.id", tenantId)
      .tag("message.type", messageType)
      .tag("success", String.valueOf(success))
      .register(meterRegistry)
      .record(duration);
    
    Counter.builder("edi.messages.processed")
      .tag("tenant.id", tenantId)
      .tag("message.type", messageType)
      .tag("status", success ? "success" : "failure")
      .register(meterRegistry)
      .increment();
  }
}
```

### Canonical Log Lines with Tenant Context
```java
public void logCanonicalEntry(HttpServletRequest request, HttpServletResponse response, Duration duration) {
  String tenantId = TenantContext.getCurrentTenant().asString();
  
  Map<String, Object> canonicalData = Map.of(
    "canonical_log_line", true,
    "tenant.id", tenantId,
    "http_method", request.getMethod(),
    "http_path", request.getRequestURI(),
    "http_status", response.getStatus(),
    "duration", duration.toNanos() / 1_000_000_000.0,
    "correlation.id", MDC.get("correlation.id")
  );
  
  log.info("Canonical log line", canonicalData);
}
```

## Testing Multi-Tenancy 🧪

### Tenant Context Test Setup
```groovy
class TenantAwareServiceSpec extends Specification {
  
  def "should process file with correct tenant context"() {
    given: "a tenant context and S3 key"
    def tenantId = "test-tenant-123"
    def s3Key = "tenantId=${tenantId}/2024/01/15/test-file.edifact"
    def tenantContext = TenantContextHolder.of(TenantId.fromString(tenantId))
    
    when: "processing with tenant context"
    def result = TenantContext.callWhere(tenantContext, () -> {
      return service.processFile(s3Key)
    })
    
    then: "processing succeeds with correct tenant context"
    result.success
    result.tenantId == tenantId
  }
}
```

### Multi-Tenant Integration Tests
```java
@SpringBootTest
class MultiTenantIntegrationTest {
  
  @Test
  void shouldIsolateTenantData() {
    // Given two different tenants
    String tenant1 = "tenant-1";
    String tenant2 = "tenant-2";
    
    // When creating data for each tenant
    TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenant1)), () -> {
      partnerService.create(new CreatePartnerRequest("Partner A"));
    });
    
    TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenant2)), () -> {
      partnerService.create(new CreatePartnerRequest("Partner B"));
    });
    
    // Then each tenant sees only their data
    List<Partner> tenant1Partners = TenantContext.callWhere(
      TenantContextHolder.of(TenantId.fromString(tenant1)), 
      () -> partnerService.findAllForCurrentTenant()
    );
    
    List<Partner> tenant2Partners = TenantContext.callWhere(
      TenantContextHolder.of(TenantId.fromString(tenant2)), 
      () -> partnerService.findAllForCurrentTenant()
    );
    
    assertThat(tenant1Partners).hasSize(1);
    assertThat(tenant2Partners).hasSize(1);
    assertThat(tenant1Partners.get(0).getName()).isEqualTo("Partner A");
    assertThat(tenant2Partners.get(0).getName()).isEqualTo("Partner B");
  }
}
```

## Infrastructure Multi-Tenancy ☁️

### CDK Multi-Tenant Resources
```java
public class MultiTenantEdifactStack extends Stack {
  
  public MultiTenantEdifactStack(Construct scope, String id, StackProps props) {
    super(scope, id, props);
    
    // Tenant-specific S3 buckets with encryption
    Bucket inboundBucket = Bucket.Builder.create(this, "InboundBucket")
      .bucketName(envPrefix + "-inbound-edifact-files")
      .encryption(BucketEncryption.KMS)
      .encryptionKey(createTenantMasterKey())
      .publicReadAccess(false)
      .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
      .build();
    
    // DynamoDB tables for tenant key storage
    Table keyStoreTable = Table.Builder.create(this, "TenantKeyStore")
      .tableName(envPrefix + "-tenant-key-store")
      .partitionKey(Attribute.builder()
        .name("tenantId")
        .type(AttributeType.STRING)
        .build())
      .sortKey(Attribute.builder()
        .name("keyId")
        .type(AttributeType.STRING)
        .build())
      .encryption(TableEncryption.AWS_MANAGED)
      .pointInTimeRecovery(true)
      .build();
  }
  
  private Key createTenantMasterKey() {
    return Key.Builder.create(this, "TenantMasterKey")
      .description("Master key for tenant-specific encryption")
      .keyPolicy(PolicyDocument.Builder.create()
        .statements(List.of(
          PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            .principals(List.of(new ServicePrincipal("lambda.amazonaws.com")))
            .actions(List.of("kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey"))
            .resources(List.of("*"))
            .build()
        ))
        .build())
      .build();
  }
}
```

### Lambda Environment Configuration
```java
Function parserFunction = Function.Builder.create(this, "ParserFunction")
  .runtime(Runtime.JAVA_21)
  .handler("io.edispark.parser.InboundEdifactFileHandler::demo")
  .environment(Map.of(
    "ENC_KEY_STORE_TABLE_NAME", keyStoreTable.getTableName(),
    "ENC_LOGICAL_KEY_STORE_TABLE_NAME", logicalKeyStoreTable.getTableName(),
    "ENC_KMS_KEY_ARN", tenantMasterKey.getKeyArn(),
    "S3_PARSED_FILES", parsedBucket.getBucketName()
  ))
  .build();
```

## Security Considerations 🔒

### Tenant Isolation Validation
- **Data Leakage Prevention**: Ensure no cross-tenant data access
- **Encryption Key Isolation**: Verify tenant-specific key usage
- **Access Control**: Implement proper IAM policies per tenant
- **Audit Logging**: Track all tenant context changes

### Security Testing Requirements
```java
@Test
void shouldPreventCrossTenantDataAccess() {
  // Attempt to access another tenant's data should fail
  String tenant1 = "tenant-1";
  String tenant2 = "tenant-2";
  
  // Create data for tenant 1
  TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenant1)), () -> {
    partnerService.create(new CreatePartnerRequest("Secret Partner"));
  });
  
  // Try to access from tenant 2 context
  List<Partner> tenant2Data = TenantContext.callWhere(
    TenantContextHolder.of(TenantId.fromString(tenant2)), 
    () -> partnerService.findAllForCurrentTenant()
  );
  
  // Should not see tenant 1's data
  assertThat(tenant2Data).isEmpty();
}
```

## Performance Considerations ⚡

### Tenant Context Overhead
- **ScopedValue Performance**: Minimal overhead compared to ThreadLocal
- **Encryption Caching**: Cache tenant keys for performance
- **Connection Pooling**: Separate pools per tenant if needed
- **Metrics Cardinality**: Monitor tenant dimension impact

### Optimization Strategies
```java
@Component
public class TenantKeyCache {
  
  private final LoadingCache<String, EncryptionKey> keyCache;
  
  public TenantKeyCache(MultiTenantEncryptionLib encryptionLib) {
    this.keyCache = Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterWrite(Duration.ofHours(1))
      .build(tenantId -> encryptionLib.getKeyForTenant(tenantId));
  }
  
  public EncryptionKey getKeyForCurrentTenant() {
    String tenantId = TenantContext.getCurrentTenant().asString();
    return keyCache.get(tenantId);
  }
}
```

## Migration and Onboarding 🚀

### New Tenant Onboarding Process
1. **Generate Tenant ID**: Create unique tenant identifier
2. **Provision Encryption Keys**: Set up tenant-specific KMS keys
3. **Create S3 Partitions**: Initialize tenant directory structure
4. **Configure Access Policies**: Set up IAM policies for tenant
5. **Initialize Database Records**: Create tenant-specific configuration
6. **Validate Isolation**: Test tenant data isolation

### Tenant Migration Utilities
```java
@Component
public class TenantMigrationService {
  
  public void migrateTenantData(String fromTenant, String toTenant) {
    // Validate migration permissions
    validateMigrationPermissions(fromTenant, toTenant);
    
    // Migrate with proper tenant context
    TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(fromTenant)), () -> {
      List<EdiMessage> messages = messageRepository.findAllForCurrentTenant();
      
      TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(toTenant)), () -> {
        messages.forEach(message -> {
          // Re-encrypt for new tenant
          message.setTenantId(toTenant);
          messageRepository.save(message);
        });
      });
    });
  }
}
```

## Compliance and Governance 📋

### Data Residency Requirements
- **Geographic Isolation**: Tenant data in specific regions
- **Compliance Boundaries**: GDPR, CCPA, industry-specific requirements
- **Audit Trails**: Complete tenant activity logging
- **Data Retention**: Tenant-specific retention policies

### Governance Policies
```java
@Component
public class TenantGovernanceService {
  
  public void enforceDataRetention(String tenantId) {
    TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenantId)), () -> {
      RetentionPolicy policy = getRetentionPolicy(tenantId);
      
      // Delete data older than retention period
      LocalDate cutoffDate = LocalDate.now().minus(policy.getRetentionPeriod());
      messageRepository.deleteOlderThan(cutoffDate);
    });
  }
  
  private RetentionPolicy getRetentionPolicy(String tenantId) {
    // Load tenant-specific retention policy
    return policyRepository.findByTenantId(tenantId)
      .orElse(RetentionPolicy.defaultPolicy());
  }
}
```

## Success Metrics 📊

### Multi-Tenancy KPIs
- **Tenant Isolation**: Zero cross-tenant data leakage incidents
- **Performance Impact**: < 5% overhead from tenant context
- **Security Compliance**: 100% tenant key isolation
- **Onboarding Time**: < 1 hour for new tenant setup
- **Data Encryption**: 100% tenant data encrypted at rest

### Monitoring and Alerting
```java
// Alert on potential tenant isolation violations
@EventListener
public void handleTenantContextMissing(TenantContextMissingEvent event) {
  alertingService.sendCriticalAlert(
    "Tenant context missing",
    Map.of(
      "operation", event.getOperation(),
      "timestamp", event.getTimestamp(),
      "stackTrace", event.getStackTrace()
    )
  );
}
```

**Remember**: Multi-tenancy is not just about data separation - it's about complete isolation across all system layers while maintaining performance and operational efficiency. Every component must be tenant-aware by design! 🏢
