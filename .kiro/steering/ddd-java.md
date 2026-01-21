# Domain-Driven Design (DDD) in Java

## Strategic Design

### Bounded Contexts

- Identify distinct business domains with clear boundaries
- Each bounded context has its own ubiquitous language
- Use context mapping to define relationships between contexts
- Prefer separate modules or packages per bounded context

### Ubiquitous Language

- Use domain terminology consistently in code, tests, and documentation
- Class and method names should reflect business concepts
- Avoid technical jargon in domain layer

## Tactical Patterns

### Entities

- Objects with unique identity that persists over time
- Identity matters more than attributes
- Use meaningful business identifiers when possible

```java
public class Order {
    private final OrderId id;
    private OrderStatus status;
    private List<OrderLine> lines;
    
    public void confirm() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Only draft orders can be confirmed");
        }
        this.status = OrderStatus.CONFIRMED;
    }
}
```

### Value Objects

- Immutable objects defined by their attributes
- No identity - equality based on all attributes
- Use Java records for simple value objects

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }
    
    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
        return new Money(amount.add(other.amount), currency);
    }
}
```

### Aggregates

- Cluster of entities and value objects with a single root entity
- All external access goes through the aggregate root
- Enforce invariants within aggregate boundaries
- Keep aggregates small - prefer references by ID to other aggregates

```java
public class Order { // Aggregate Root
    private final OrderId id;
    private final CustomerId customerId; // Reference by ID, not entity
    private List<OrderLine> lines; // Owned by this aggregate
    
    public void addLine(Product product, int quantity) {
        // Invariant: max 10 lines per order
        if (lines.size() >= 10) {
            throw new OrderLimitExceededException();
        }
        lines.add(new OrderLine(product.id(), quantity, product.price()));
    }
}
```

### Domain Events

- Capture something significant that happened in the domain
- Use past tense naming (OrderPlaced, PaymentReceived)
- Immutable records with timestamp

```java
public record OrderPlaced(
    OrderId orderId,
    CustomerId customerId,
    Money totalAmount,
    Instant occurredAt
) implements DomainEvent {}
```

### Repositories

- Abstraction for aggregate persistence
- One repository per aggregate root
- Interface in domain layer, implementation in infrastructure

```java
public interface OrderRepository {
    Optional<Order> findById(OrderId id);
    void save(Order order);
    void delete(Order order);
}
```

### Domain Services

- Stateless operations that don't belong to a single entity
- Use when logic spans multiple aggregates
- Keep thin - prefer putting logic in entities/value objects

```java
public class PricingService {
    public Money calculateTotal(Order order, DiscountPolicy policy) {
        Money subtotal = order.calculateSubtotal();
        return policy.apply(subtotal);
    }
}
```

### Factories

- Encapsulate complex object creation
- Use when construction requires significant logic
- Can be static methods, separate classes, or builder patterns

```java
public class OrderFactory {
    public Order createFromCart(Cart cart, CustomerId customerId) {
        var order = new Order(OrderId.generate(), customerId);
        cart.items().forEach(item -> 
            order.addLine(item.product(), item.quantity())
        );
        return order;
    }
}
```

## Multi-Module Maven Structure

### Module Types

1. **Domain Modules** - One per bounded context, contains domain logic
2. **Support Libraries** - Shared utilities, common abstractions, cross-cutting concerns
3. **Application Module** - Orchestrates domains, exposes APIs, wires everything together

### Project Layout

```
my-project/
├── pom.xml                          # Parent POM
├── support/
│   ├── common/                      # Shared value objects, interfaces
│   │   └── pom.xml
│   ├── domain-core/                 # Base classes for DDD (Entity, AggregateRoot, etc.)
│   │   └── pom.xml
│   ├── testing/                     # Test utilities, fixtures, custom assertions
│   │   └── pom.xml
│   └── messaging/                   # Event publishing abstractions
│       └── pom.xml
├── domains/
│   ├── ordering/                    # Ordering bounded context
│   │   └── pom.xml
│   ├── inventory/                   # Inventory bounded context
│   │   └── pom.xml
│   └── shipping/                    # Shipping bounded context
│       └── pom.xml
└── application/
    └── pom.xml                      # Spring Boot app, REST controllers, config
```

### Module Dependencies

```
support/common        <- No dependencies (pure Java)
support/domain-core   <- depends on common
support/testing       <- depends on domain-core, JUnit, AssertJ

domains/ordering      <- depends on domain-core, common
domains/inventory     <- depends on domain-core, common
domains/shipping      <- depends on domain-core, common

application           <- depends on all domains, Spring Boot
```

### Parent POM Structure

```xml
<modules>
    <module>support/common</module>
    <module>support/domain-core</module>
    <module>support/testing</module>
    <module>support/messaging</module>
    <module>domains/ordering</module>
    <module>domains/inventory</module>
    <module>domains/shipping</module>
    <module>application</module>
</modules>
```

### Domain Module Internal Structure

Each domain module is self-contained and owns everything related to its bounded context, including controllers, DTOs, and infrastructure:

```
domains/ordering/
├── pom.xml
└── src/main/java/com/example/ordering/
    ├── domain/
    │   ├── model/
    │   │   ├── Order.java           # Aggregate root
    │   │   ├── OrderId.java         # Value object
    │   │   ├── OrderLine.java       # Entity within aggregate
    │   │   └── OrderStatus.java     # Enum
    │   ├── event/
    │   │   └── OrderPlaced.java     # Domain event
    │   ├── repository/
    │   │   └── OrderRepository.java # Repository interface (port)
    │   └── service/
    │       └── PricingService.java  # Domain service
    ├── application/
    │   ├── command/
    │   │   └── PlaceOrderCommand.java
    │   ├── handler/
    │   │   └── PlaceOrderHandler.java
    │   └── port/
    │       └── OrderQueryPort.java  # Query interface
    ├── api/
    │   ├── rest/
    │   │   ├── OrderController.java # REST controller for this domain
    │   │   └── OrderExceptionHandler.java
    │   └── dto/
    │       ├── CreateOrderRequest.java
    │       ├── OrderResponse.java
    │       └── OrderMapper.java     # Maps between DTOs and domain
    └── infrastructure/
        ├── persistence/
        │   └── JpaOrderRepository.java
        ├── messaging/
        │   └── OrderEventPublisher.java
        └── config/
            └── OrderingModuleConfig.java  # Spring config for this module
```

### Domain Module Ownership

Each domain module owns:
- Its REST controllers and API endpoints
- Request/response DTOs
- Exception handlers specific to the domain
- JPA entities and repositories
- Event publishers/subscribers
- Module-specific Spring configuration

### Application Module Role

The `application` module is a thin orchestration layer that:
- Contains the Spring Boot main class
- Imports all domain module configurations via component scanning
- Provides global configuration (security, CORS, etc.)
- Handles cross-cutting concerns (global exception handling, logging config)
- Wires up shared infrastructure (database connections, message brokers)

```
application/
├── pom.xml
└── src/main/java/com/example/
    ├── Application.java              # @SpringBootApplication
    ├── config/
    │   ├── SecurityConfig.java       # Global security
    │   ├── WebConfig.java            # CORS, converters
    │   └── AsyncConfig.java          # Thread pools
    └── infrastructure/
        └── GlobalExceptionHandler.java  # Fallback error handling
```

The application module does NOT contain business logic or domain-specific controllers.

### Support Library Guidelines

**common** - Zero dependencies, pure Java:
- Shared value objects (Money, Email, PhoneNumber)
- Common interfaces and marker types
- Utility classes

**domain-core** - DDD building blocks:
- `AggregateRoot<ID>` base class
- `DomainEvent` interface
- `Repository<T, ID>` interface
- `DomainException` base class

**testing** - Test scope only:
- Custom AssertJ assertions
- Test fixtures and builders
- Fake implementations for ports

**messaging** - Event infrastructure abstractions:
- `EventPublisher` interface
- `EventSubscriber` interface

### Dependency Rules

- Domain modules MUST NOT depend on other domain modules directly
- Cross-context communication via events or application layer orchestration
- Support libraries MUST NOT depend on domain modules
- Only `application` module has Spring Boot dependencies
- Domain and support modules remain framework-agnostic

## Best Practices

### Domain Layer Rules

- No framework dependencies in domain layer
- Domain objects are always in a valid state
- Use private setters or no setters - modify through behavior methods
- Throw domain-specific exceptions

### Anti-Corruption Layer

- Translate between bounded contexts
- Protect your domain from external models
- Use adapters and translators at context boundaries

### Testing Domain Logic

- Unit test aggregates and value objects in isolation
- Test behavior, not getters/setters
- Use domain language in test names

```java
@Test
void confirmedOrderCannotBeConfirmedAgain() {
    var order = OrderFixture.confirmedOrder();
    
    assertThatThrownBy(order::confirm)
        .isInstanceOf(IllegalStateException.class);
}
```
