---
title: Separate Domain Logic from Infrastructure
paths:
  - "**/*.java"
impact: HIGH
---

# Separate Domain Logic from Infrastructure

Keep business logic in pure domain and use case layers, free of framework or infrastructure dependencies. When domain logic is coupled to controllers, ORMs, or HTTP libraries, it becomes untestable in isolation, impossible to reuse across delivery mechanisms, and fragile to infrastructure changes. Define domain entities that model business rules with no imports from framework or database packages. Implement use cases as classes that depend on abstract repository interfaces, not concrete database clients. Let the infrastructure layer implement those interfaces and inject them at composition time. This dependency inversion ensures the domain drives the architecture rather than the framework dictating how business rules are organized.

## Critical Clean Architecture & DDD Principles

- Separate domain entities from infrastructure concerns
- Keep business logic independent of frameworks
- Define use cases clearly and keep them isolated
- Avoid code duplication through creation of reusable functions and modules

## Incorrect

Business logic is embedded directly in the Spring controller, coupled to the web framework and JPA repository. Testing requires spinning up the full application context and database.

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @PostMapping
    public ResponseEntity<OrderEntity> createOrder(@RequestBody CreateOrderRequest request) {
        // Business rule mixed into the controller
        double total = request.getItems().stream()
                .mapToDouble(i -> i.getPrice() * i.getQty())
                .sum();
        double discount = total > 100 ? total * 0.1 : 0;

        OrderEntity order = new OrderEntity();
        order.setCustomerId(request.getCustomerId());
        order.setTotal(total - discount);
        order.setItems(request.getItems());
        orderJpaRepository.save(order);

        return ResponseEntity.ok(order);
    }
}
```

Poor Architectural Choices:
- Mixing business logic with UI components
- Database queries directly in controllers
- Lack of clear separation of concerns

## Correct

Domain logic lives in a framework-free use case that depends on an abstract repository. The controller is a thin adapter that delegates to the use case.

```java
// domain/Order.java — pure business logic, no framework imports
public class Order {

    public static double calculateTotal(List<OrderItem> items) {
        double subtotal = items.stream()
                .mapToDouble(i -> i.getPrice() * i.getQty())
                .sum();
        double discount = subtotal > 100 ? subtotal * 0.1 : 0;
        return subtotal - discount;
    }
}

// application/CreateOrderUseCase.java — use case depends on abstraction
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;

    public CreateOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderEntity execute(String customerId, List<OrderItem> items) {
        double total = Order.calculateTotal(items);
        return orderRepository.save(new OrderEntity(customerId, total, items));
    }
}

// infrastructure/OrderController.java — thin adapter
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
    }

    @PostMapping
    public ResponseEntity<OrderEntity> createOrder(@RequestBody CreateOrderRequest request) {
        OrderEntity order = createOrderUseCase.execute(request.getCustomerId(), request.getItems());
        return ResponseEntity.ok(order);
    }
}
```

## Reference

- [Clean Architecture by Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Domain-Driven Design by Eric Evans](https://www.domainlanguage.com/ddd/)
