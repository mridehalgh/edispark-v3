---
title: Functional Core, Imperative Shell
paths:
  - "**/*.java"
impact: HIGH
---

# Functional Core, Imperative Shell

Keep business logic in pure methods that take inputs and return outputs with no side effects. Push all side effects -- database calls, HTTP requests, logging, file I/O, and state mutations -- to an outer "imperative shell" that orchestrates the pure core. Pure methods are deterministic: given the same inputs they always produce the same outputs. This makes them trivially testable without mocks, easy to reason about, and safe to compose. When side effects are mixed into calculation logic, tests become slow and brittle (requiring database stubs, mock loggers, HTTP interceptors), bugs hide behind non-deterministic execution, and refactoring becomes dangerous because any change might alter when and how I/O occurs. Separate what to compute from how to execute it.

## Incorrect

Business calculation is tangled with logging, database reads, and persistence. Testing the pricing logic requires mocking the logger, database, and notification service.

```java
public void applySubscriptionRenewal(String customerId, Logger logger,
        CustomerRepository db, Mailer mailer) {
    Customer customer = db.findById(customerId);
    Plan plan = db.findPlanById(customer.getPlanId());

    // Pure calculation mixed with side effects
    double price = plan.getBasePrice();
    if (customer.getLoyaltyYears() >= 3) {
        price = price * 0.85;
        logger.info("Applied 15% loyalty discount for {}", customerId);
    }
    if (customer.getReferralCount() >= 5) {
        price = price - 10;
        logger.info("Applied $10 referral credit for {}", customerId);
    }
    double tax = price * customer.getTaxRate();
    double total = price + tax;

    db.createInvoice(customerId, total, tax);
    mailer.send(customer.getEmail(), "Your renewal total is " + total);
    logger.info("Renewal processed: {}, total: {}", customerId, total);
}
```

## Correct

The pure core calculates the renewal price with no side effects. The imperative shell fetches data, calls the pure method, then performs all I/O. The core is testable with plain assertions and zero mocks.

```java
// Pure core — deterministic, no side effects, trivially testable
public record RenewalInput(double basePrice, int loyaltyYears, int referralCount, double taxRate) {}

public record RenewalResult(double price, double tax, double total, List<String> appliedDiscounts) {}

public static RenewalResult calculateRenewal(RenewalInput input) {
    List<String> discounts = new ArrayList<>();
    double price = input.basePrice();

    if (input.loyaltyYears() >= 3) {
        price = price * 0.85;
        discounts.add("loyalty_15pct");
    }
    if (input.referralCount() >= 5) {
        price = price - 10;
        discounts.add("referral_credit_10");
    }

    double tax = price * input.taxRate();
    return new RenewalResult(price, tax, price + tax, List.copyOf(discounts));
}

// Imperative shell — orchestrates I/O around the pure core
public void processRenewal(String customerId, CustomerRepository db,
        Mailer mailer, Logger logger) {
    Customer customer = db.findById(customerId);
    Plan plan = db.findPlanById(customer.getPlanId());

    RenewalResult result = calculateRenewal(new RenewalInput(
            plan.getBasePrice(),
            customer.getLoyaltyYears(),
            customer.getReferralCount(),
            customer.getTaxRate()
    ));

    db.createInvoice(customerId, result.total(), result.tax());
    mailer.send(customer.getEmail(), "Your renewal total is " + result.total());
    logger.info("Renewal processed customerId={} total={} discounts={}",
            customerId, result.total(), result.appliedDiscounts());
}
```

## Reference

- [Functional Core, Imperative Shell - Gary Bernhardt](https://www.destroyallsoftware.com/screencasts/catalog/functional-core-imperative-shell)
- [Boundaries - Gary Bernhardt (talk)](https://www.destroyallsoftware.com/talks/boundaries)