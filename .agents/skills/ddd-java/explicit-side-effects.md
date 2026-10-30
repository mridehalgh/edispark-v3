---
title: Explicit Side Effects and Call-Site Transparency
paths:
  - "**/*.java"
impact: HIGH
---

# Explicit Side Effects and Call-Site Transparency

A reader must understand what a line of code does without opening the called method. When `processOrder(order)` internally saves to a database, sends an email, and emits an event, the call site is opaque — the reader must jump into the implementation to learn what actually happens. This defeats abstraction because it hides critical information rather than irrelevant detail.

Make side effects visible where they are triggered. Each step in a workflow — persistence, notifications, external calls — should appear as a distinct line at the call site. The orchestrating method becomes a transparent table of contents: a reader sees every effect the system produces without drilling into any implementation.

This rule governs call-site composition, not individual method design. Individual methods should still be focused and well-named (see Principle of Least Astonishment). This rule ensures the orchestration of those methods is itself readable.

## Incorrect

The call site delegates everything to a single opaque method. A reader sees one line and has no idea that it persists data, charges a payment, sends a confirmation email, and publishes a domain event. The only way to learn this is to open `processOrder`.

```java
// OrderController.java — opaque orchestration
public ResponseEntity<OrderResponse> handleCheckout(@RequestBody CheckoutRequest request) {
    Order order = buildOrder(request);

    // What does this do? Saves to DB? Sends email? Charges payment?
    // Reader must open processOrder() to find out.
    orderService.processOrder(order);

    return ResponseEntity.ok(new OrderResponse("ok"));
}

// OrderService.java — all side effects hidden inside
public void processOrder(Order order) {
    orderRepository.save(order);
    paymentGateway.charge(order.getCustomerId(), order.getTotal());
    emailService.sendConfirmation(order.getCustomerId(), order.getId());
    eventBus.publish(new OrderCompletedEvent(order.getId()));
}
```

## Correct

Every side effect is visible at the call site. A reader scanning `handleCheckout` sees exactly what the system does — persist, charge, notify, publish — without opening any implementation. Each called method does one focused thing, and the orchestrator makes the workflow transparent.

```java
// OrderController.java — transparent orchestration
public ResponseEntity<OrderResponse> handleCheckout(@RequestBody CheckoutRequest request) {
    Order order = buildOrder(request);

    // Every effect is visible right here
    orderRepository.save(order);
    paymentGateway.charge(order.getCustomerId(), order.getTotal());
    emailService.sendConfirmation(order.getCustomerId(), order.getId());
    eventBus.publish(new OrderCompletedEvent(order.getId()));

    return ResponseEntity.ok(new OrderResponse("ok"));
}
```
