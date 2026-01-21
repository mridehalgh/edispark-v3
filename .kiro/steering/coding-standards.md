# Coding Standards & Development Practices

## Code Design Principles

- Write small, focused functions (4-5 lines where appropriate)
- Follow SOLID principles for maintainable code
- Design for testability with clear separation of concerns

## Lombok Usage

Use Lombok to eliminate boilerplate code. Never write getters, setters, toString, equals, hashCode, or constructors manually.

### Required Annotations

| Annotation | Use Case |
|------------|----------|
| `@Getter` | All classes needing read access to fields |
| `@Setter` | Mutable classes (avoid in domain entities) |
| `@ToString` | All classes for debugging |
| `@EqualsAndHashCode` | Value objects and entities (use `onlyExplicitlyIncluded` for entities) |
| `@RequiredArgsConstructor` | Classes with final fields |
| `@AllArgsConstructor` | When all-args constructor is needed |
| `@NoArgsConstructor` | JPA entities (use `access = AccessLevel.PROTECTED`) |
| `@Builder` | Complex object construction |
| `@Value` | Immutable value objects (combines @Getter, @AllArgsConstructor, @ToString, @EqualsAndHashCode, makes fields final and class final) |
| `@Data` | Mutable DTOs only (avoid in domain layer) |

### Domain Entity Pattern

```java
@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // For JPA
public class Order {
    @EqualsAndHashCode.Include
    private OrderId id;
    
    private OrderStatus status;
    // Behavior methods, not setters
}
```

### Value Object Pattern

Prefer Java records for simple value objects. Use `@Value` for complex immutable objects:

```java
@Value
public class Money {
    BigDecimal amount;
    Currency currency;
}
```

### Rules

- NEVER write getters, setters, toString, equals, or hashCode manually
- NEVER use `@Data` in domain entities (it generates setters)
- ALWAYS use `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` for entities with identity
- Prefer records over Lombok for simple immutable value objects
- Use `@Builder` for objects with many optional fields

## Testing Philosophy

- A unit test targets a unit of behavior, not a class
- Write tests for all new behaviors before implementing those behaviors (TDD)
- ALWAYS when creating tasks define the tests first before implementing the feature / behaviour
- ALWAYS MAKE SURE ALL BEHAVIOUR IS TESTED (ESPECIALLY IN AGGREGATE ROOTS)

### When to Write Unit Tests

Unit tests are required when code:
- Crosses a port boundary (database, external API, file system, message queue)
- Contains core business logic or domain rules
- Performs data transformation or validation
- Makes decisions based on input conditions

### When Unit Tests May Not Be Needed

- Simple pass-through code with no logic
- Pure configuration or wiring code
- Code that only delegates to already-tested components

### Test Boundaries

- Mock external dependencies at port boundaries (repositories, HTTP clients, etc.)
- Test behavior through public interfaces, not implementation details
- Each test should verify one specific behavior or outcome

## Code Quality Checks

- When changing a function, check cyclomatic complexity and report if it exceeds 5
- High complexity indicates the function should be refactored into smaller pieces

## Git Workflow

- Never commit or push without explicit permission from the user
