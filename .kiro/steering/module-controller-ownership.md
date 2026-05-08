# Module Controller Ownership Rule 🎯

## Controller Ownership
- **Modules own their controllers** - Each module defines its own REST endpoints
- **Central app provides infrastructure** - Security, monitoring, configuration only
- **No controller logic in central app** - Central app should not contain business domain controllers
- **Auto-discovery pattern** - Use `@ComponentScan` to discover module controllers

## Implementation Pattern
```java
// ✅ Good: Module owns its controller
@RestController
@RequestMapping("/api/partners")
public class PartnerController {
  // Partner domain logic here
}

// ✅ Good: Central app discovers controllers
@SpringBootApplication
@ComponentScan(basePackages = "io.edispark")
public class CentralWebApplication {
  // Infrastructure only
}

// ❌ Bad: Central app with business controllers
@RestController
public class CentralController {
  // Don't put module logic here
}
```

## Benefits
- **Encapsulation**: Domain logic stays with business logic
- **Autonomy**: Modules evolve independently
- **Testability**: Modules can be tested in isolation
- **Reusability**: Modules work in other applications