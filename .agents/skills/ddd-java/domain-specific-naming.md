---
title: Use Domain-Specific Names Instead of Generic Class Names
paths:
  - "**/*.java"
impact: HIGH
---

# Use Domain-Specific Names Instead of Generic Class Names

Avoid generic class names like `Utils`, `Helpers`, `Common`, and `Shared`. These names attract unrelated methods, creating grab-bag classes with no cohesion. Use domain-specific names that reflect the bounded context and the class's single responsibility -- names like `OrderCalculator`, `UserAuthenticator`, or `InvoiceGenerator` make purpose immediately clear and enforce cohesion by design.

Generic names signal missing domain analysis. When a developer reaches for `Utils.java`, it usually means the method belongs in a domain class that has not been identified yet. Naming classes after their domain concept prevents them from becoming dumping grounds and keeps each class focused on a single, clear purpose.

## Critical principles

- Follow domain-driven design and ubiquitous language
- **AVOID** generic names: `Utils`, `Helpers`, `Common`, `Shared`
- **USE** domain-specific names: `OrderCalculator`, `UserAuthenticator`, `InvoiceGenerator`
- Follow bounded context naming patterns
- Each class should have a single, clear purpose

## Incorrect

Generic class names attract unrelated methods, making the class a dumping ground with no cohesion or clear ownership.

```java
// Utils.java — grab-bag of unrelated methods
public class Utils {

    public static double calculateOrderTotal(List<OrderItem> items) {
        return items.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
    }

    public static String formatUserDisplayName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    public static String generateInvoiceNumber() {
        return "INV-" + System.currentTimeMillis();
    }
}
```

Generic Naming Anti-Patterns:
- `Utils.java` with 50 unrelated methods
- `Helpers.java` as a dumping ground
- `CommonUtils.java` with unclear purpose

## Correct

Each method lives in a class named after its bounded context, enforcing single responsibility and making purpose self-documenting.

```java
// OrderCalculator.java — all order pricing logic
public class OrderCalculator {

    public static double calculateOrderTotal(List<OrderItem> items) {
        return items.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
    }
}

// UserDisplay.java — user presentation formatting
public class UserDisplay {

    public static String formatUserDisplayName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}

// InvoiceGenerator.java — invoice creation logic
public class InvoiceGenerator {

    public static String generateInvoiceNumber() {
        return "INV-" + System.currentTimeMillis();
    }
}
```

## Reference

- [Domain-Driven Design: Tackling Complexity in the Heart of Software](https://www.domainlanguage.com/ddd/) — Eric Evans
