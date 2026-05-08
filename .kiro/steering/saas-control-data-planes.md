# SaaS Control and Data Planes Architecture 🏗️

## Overview
SaaS architectures are fundamentally divided into two distinct planes that serve different purposes and have different operational characteristics. Understanding this separation is critical for building scalable, manageable multi-tenant systems.

## Control Plane vs Application Plane 🎯

### Control Plane (Management Layer)
The control plane contains all functionality for **managing the multi-tenant environment itself**:
- **Tenant onboarding and provisioning**
- **Authentication and authorization management**
- **Billing and subscription management**
- **Operational monitoring and analytics**
- **Administrative interfaces and APIs**

**Key Characteristic**: Control plane services are **NOT multi-tenant** - they are global services that manage all tenants.

### Application Plane (Business Logic Layer)
The application plane contains the **actual business functionality** that tenants consume:
- **Multi-tenant business applications**
- **Domain-specific services and APIs**
- **Tenant-isolated data processing**
- **Business logic and workflows**

**Key Characteristic**: Application plane services **ARE multi-tenant** - they implement tenant isolation and context.

## EdiSpark Architecture Mapping 📋

### Control Plane Components

#### Core Services (Global, Non-Tenant)
```java
// Tenant Management Service
@RestController
@RequestMapping("/admin/tenants")
public class TenantManagementController {
  
  @PostMapping
  public ResponseEntity<TenantInfo> createTenant(@RequestBody CreateTenantRequest request) {
    // Global service - manages ALL tenants
    TenantInfo tenant = tenantService.createTenant(request);
    
    // Provision tenant-specific resources
    provisioningService.provisionTenantResources(tenant.getId());
    
    return ResponseEntity.ok(tenant);
  }
  
  @GetMapping
  public ResponseEntity<List<TenantInfo>> getAllTenants() {
    // Admin view of ALL tenants across the platform
    return ResponseEntity.ok(tenantService.getAllTenants());
  }
}
```

#### Authentication Service (Global)
```java
@Component
public class SaasAuthenticationService {
  
  public AuthenticationResult authenticate(String token) {
    // Global authentication - determines which tenant user belongs to
    JwtClaims claims = jwtService.validateToken(token);
    
    return AuthenticationResult.builder()
      .userId(claims.getUserId())
      .tenantId(claims.getTenantId())  // Critical: identifies tenant context
      .roles(claims.getRoles())
      .build();
  }
}
```

#### Billing Service (Global)
```java
@Component
public class BillingService {
  
  public void recordUsage(String tenantId, UsageMetrics metrics) {
    // Global service tracking usage across ALL tenants
    BillingRecord record = BillingRecord.builder()
      .tenantId(tenantId)
      .messagesProcessed(metrics.getMessageCount())
      .storageUsed(metrics.getStorageBytes())
      .timestamp(Instant.now())
      .build();
    
    billingRepository.save(record);
  }
}
```

#### Admin Application
```java
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('SAAS_ADMIN')")
public class SaasAdminController {
  
  @GetMapping("/dashboard")
  public ResponseEntity<AdminDashboard> getDashboard() {
    // Cross-tenant analytics and monitoring
    AdminDashboard dashboard = AdminDashboard.builder()
      .totalTenants(tenantService.getTenantCount())
      .totalMessages(metricsService.getTotalMessagesProcessed())
      .systemHealth(healthService.getOverallHealth())
      .build();
    
    return ResponseEntity.ok(dashboard);
  }
}
```

### Application Plane Components

#### Multi-Tenant EDI Processing
```java
@Component
@RequiredArgsConstructor
public class EdiProcessingService {
  
  private final TenantEncryptionService encryptionService;
  private final TenantAwareStorageService storageService;
  
  public ProcessingResult processMessage(String message) {
    // TENANT-AWARE: Uses current tenant context
    String tenantId = TenantContext.getCurrentTenant().asString();
    
    // Tenant-specific processing
    ParsedMessage parsed = parseWithTenantSchema(message, tenantId);
    
    // Tenant-specific encryption
    byte[] encrypted = encryptionService.encryptForCurrentTenant(parsed.toJson());
    
    // Tenant-isolated storage
    String key = storageService.storeForCurrentTenant(encrypted);
    
    return ProcessingResult.success(key);
  }
}
```

#### Tenant-Aware API Controllers
```java
@RestController
@RequestMapping("/api/partners")
public class PartnerController {
  
  @GetMapping
  public ResponseEntity<List<Partner>> getPartners() {
    // TENANT-AWARE: Automatically filters by current tenant
    List<Partner> partners = partnerService.findAllForCurrentTenant();
    return ResponseEntity.ok(partners);
  }
  
  @PostMapping
  public ResponseEntity<Partner> createPartner(@RequestBody CreatePartnerRequest request) {
    // TENANT-AWARE: Partner created within current tenant context
    Partner partner = partnerService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(partner);
  }
}
```

#### Provisioning Service (Application Plane)
```java
@Component
public class TenantProvisioningService {
  
  public void provisionTenantResources(String tenantId) {
    // Provision tenant-specific application resources
    
    // Create tenant-specific S3 directories
    s3Service.createTenantDirectories(tenantId);
    
    // Generate tenant-specific encryption keys
    encryptionService.generateTenantKeys(tenantId);
    
    // Initialize tenant-specific database schemas
    databaseService.initializeTenantSchema(tenantId);
    
    // Configure tenant-specific processing rules
    workflowService.initializeTenantWorkflows(tenantId);
  }
}
```

## Architectural Boundaries 🚧

### Control Plane Characteristics
- **Global scope**: Operates across ALL tenants
- **Administrative focus**: Management, monitoring, billing
- **Single instance**: One deployment serves entire platform
- **No tenant isolation**: Services are not multi-tenant internally
- **Cross-tenant visibility**: Can see and manage all tenants

### Application Plane Characteristics
- **Tenant scope**: Operates within tenant context
- **Business focus**: Domain functionality and workflows
- **Tenant isolation**: Complete data and processing separation
- **Context-aware**: Always knows current tenant
- **Tenant-limited visibility**: Can only see current tenant's data

## Implementation Patterns 🔧

### Control Plane Service Pattern
```java
// Control plane services are GLOBAL
@Service
public class GlobalTenantService {
  
  // No tenant context needed - operates globally
  public List<TenantMetrics> getAllTenantMetrics() {
    return tenantRepository.findAll().stream()
      .map(this::calculateMetrics)
      .collect(Collectors.toList());
  }
  
  public void suspendTenant(String tenantId, String reason) {
    // Global operation affecting specific tenant
    Tenant tenant = tenantRepository.findById(tenantId);
    tenant.suspend(reason);
    tenantRepository.save(tenant);
    
    // Notify application plane to stop processing
    applicationPlaneNotifier.notifyTenantSuspended(tenantId);
  }
}
```

### Application Plane Service Pattern
```java
// Application plane services are TENANT-AWARE
@Service
public class TenantAwareMessageService {
  
  public List<Message> getMessages() {
    // Automatically scoped to current tenant
    String tenantId = TenantContext.getCurrentTenant().asString();
    return messageRepository.findByTenantId(tenantId);
  }
  
  public Message createMessage(CreateMessageRequest request) {
    // Created within current tenant context
    Message message = Message.builder()
      .tenantId(TenantContext.getCurrentTenant().asString())
      .content(request.getContent())
      .build();
    
    return messageRepository.save(message);
  }
}
```

## Cross-Plane Communication 🔄

### Control Plane → Application Plane
```java
@Component
public class TenantLifecycleManager {
  
  @EventListener
  public void handleTenantCreated(TenantCreatedEvent event) {
    // Control plane event triggers application plane provisioning
    applicationPlaneProvisioner.provisionTenant(event.getTenantId());
  }
  
  @EventListener
  public void handleTenantSuspended(TenantSuspendedEvent event) {
    // Control plane decision affects application plane behavior
    applicationPlaneManager.suspendTenantProcessing(event.getTenantId());
  }
}
```

### Application Plane → Control Plane
```java
@Component
public class UsageReportingService {
  
  @Scheduled(fixedRate = 300000) // Every 5 minutes
  public void reportUsageMetrics() {
    String tenantId = TenantContext.getCurrentTenant().asString();
    
    UsageMetrics metrics = UsageMetrics.builder()
      .tenantId(tenantId)
      .messagesProcessed(getProcessedMessageCount())
      .storageUsed(getStorageUsage())
      .build();
    
    // Report to control plane for billing
    controlPlaneBillingService.recordUsage(metrics);
  }
}
```

## Infrastructure Considerations ☁️

### Control Plane Infrastructure
```java
// CDK Stack for Control Plane
public class ControlPlaneStack extends Stack {
  
  public ControlPlaneStack(Construct scope, String id, StackProps props) {
    super(scope, id, props);
    
    // Global services - single deployment
    Function tenantManagementFunction = Function.Builder.create(this, "TenantManagement")
      .functionName("saas-tenant-management")
      .handler("io.edispark.control.TenantManagementHandler")
      .build();
    
    // Global database for tenant metadata
    Table tenantTable = Table.Builder.create(this, "TenantTable")
      .tableName("saas-tenants")
      .partitionKey(Attribute.builder().name("tenantId").type(AttributeType.STRING).build())
      .build();
    
    // Global admin API
    RestApi adminApi = RestApi.Builder.create(this, "AdminApi")
      .restApiName("saas-admin-api")
      .build();
  }
}
```

### Application Plane Infrastructure
```java
// CDK Stack for Application Plane
public class ApplicationPlaneStack extends Stack {
  
  public ApplicationPlaneStack(Construct scope, String id, StackProps props) {
    super(scope, id, props);
    
    // Tenant-aware processing functions
    Function ediProcessorFunction = Function.Builder.create(this, "EdiProcessor")
      .functionName("edi-processor")
      .handler("io.edispark.parser.EdiProcessorHandler")
      .environment(Map.of(
        "TENANT_ENCRYPTION_TABLE", tenantEncryptionTable.getTableName(),
        "TENANT_STORAGE_BUCKET", tenantStorageBucket.getBucketName()
      ))
      .build();
    
    // Tenant-partitioned storage
    Bucket tenantStorageBucket = Bucket.Builder.create(this, "TenantStorage")
      .bucketName("edispark-tenant-data")
      .build();
  }
}
```

## Security Boundaries 🔒

### Control Plane Security
- **Admin-only access**: Restricted to SaaS operators
- **Cross-tenant operations**: Can manage all tenants
- **Global permissions**: Platform-level authorization
- **Audit logging**: All administrative actions logged

### Application Plane Security
- **Tenant-scoped access**: Users see only their tenant's data
- **Tenant isolation**: Complete data separation
- **Context-based permissions**: Authorization within tenant context
- **Tenant audit trails**: Per-tenant activity logging

## Monitoring and Observability 📊

### Control Plane Metrics
```java
@Component
public class ControlPlaneMetrics {
  
  public void recordTenantOperation(String operation, String tenantId, boolean success) {
    Metrics.counter("control.plane.operations",
      "operation", operation,
      "tenant.id", tenantId,
      "success", String.valueOf(success)
    ).increment();
  }
  
  public void recordPlatformHealth(String component, HealthStatus status) {
    Metrics.gauge("control.plane.health",
      "component", component,
      "status", status.name()
    ).set(status.isHealthy() ? 1 : 0);
  }
}
```

### Application Plane Metrics
```java
@Component
public class ApplicationPlaneMetrics {
  
  public void recordTenantProcessing(Duration duration, boolean success) {
    String tenantId = TenantContext.getCurrentTenant().asString();
    
    Metrics.timer("application.plane.processing",
      "tenant.id", tenantId,
      "success", String.valueOf(success)
    ).record(duration);
  }
}
```

## Best Practices 📋

### Control Plane Guidelines
- **Keep it simple**: Focus on management, not business logic
- **Global perspective**: Design for cross-tenant operations
- **Administrative interfaces**: Build for SaaS operators, not end users
- **Tenant lifecycle**: Handle creation, suspension, deletion
- **Billing integration**: Track usage across all tenants

### Application Plane Guidelines
- **Tenant context first**: Every operation must be tenant-aware
- **Complete isolation**: No cross-tenant data leakage
- **Business focus**: Implement domain functionality
- **Performance optimization**: Optimize for tenant-specific workloads
- **Scalability**: Design for independent tenant scaling

### Cross-Plane Guidelines
- **Clear boundaries**: Don't mix control and application concerns
- **Event-driven communication**: Use events for cross-plane coordination
- **Separate deployments**: Deploy planes independently
- **Different scaling**: Control plane scales with tenant count, application plane scales with usage
- **Security isolation**: Different access patterns and permissions

## Common Anti-Patterns ❌

### Mixing Planes
```java
// ❌ Bad: Control plane logic in application service
@Service
public class OrderService {
  
  public Order createOrder(CreateOrderRequest request) {
    Order order = new Order(request);
    
    // ❌ Don't do tenant management in application plane
    if (tenantService.getTenantUsage(order.getTenantId()) > limit) {
      tenantService.suspendTenant(order.getTenantId());
    }
    
    return orderRepository.save(order);
  }
}

// ✅ Good: Separate concerns
@Service
public class OrderService {
  
  public Order createOrder(CreateOrderRequest request) {
    // Application plane focuses on business logic
    Order order = new Order(request);
    
    // Report usage to control plane via event
    eventPublisher.publishEvent(new OrderCreatedEvent(order));
    
    return orderRepository.save(order);
  }
}
```

### Tenant Context in Control Plane
```java
// ❌ Bad: Using tenant context in control plane
@Service
public class GlobalTenantService {
  
  public List<Tenant> getAllTenants() {
    // ❌ Control plane shouldn't use tenant context
    String currentTenant = TenantContext.getCurrentTenant().asString();
    return tenantRepository.findByTenantId(currentTenant);
  }
}

// ✅ Good: Global operations in control plane
@Service
public class GlobalTenantService {
  
  public List<Tenant> getAllTenants() {
    // ✅ Control plane operates globally
    return tenantRepository.findAll();
  }
}
```

## Success Metrics 📈

### Control Plane Success
- **Tenant onboarding time**: < 5 minutes for new tenant setup
- **Administrative efficiency**: Single interface manages all tenants
- **Billing accuracy**: 100% usage tracking and billing
- **Platform uptime**: 99.9% availability for management operations

### Application Plane Success
- **Tenant isolation**: Zero cross-tenant data leakage incidents
- **Performance consistency**: Similar performance across all tenants
- **Scalability**: Linear scaling with tenant growth
- **Business functionality**: Complete feature parity across tenants

**Remember**: The control plane manages the multi-tenant environment, while the application plane implements the multi-tenant business functionality. Keep these concerns separate for maintainable, scalable SaaS architecture! 🏗️