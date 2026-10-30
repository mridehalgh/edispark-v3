---
title: Enforce Separation of Concerns Between Layers
paths:
  - "**/*.java"
impact: HIGH
---

# Enforce Separation of Concerns Between Layers

Do NOT mix business logic with UI components or place database queries directly in controllers. Each architectural layer must have a single responsibility: controllers handle HTTP concerns, services encapsulate business logic, and repositories manage data access. Violating these boundaries creates tightly coupled code that is difficult to test, refactor, and reason about. When business rules live inside controllers, they cannot be reused across different entry points (API, CLI, events) and changes to infrastructure leak into domain logic. Maintain clear boundaries between contexts by delegating work through well-defined interfaces rather than inlining cross-cutting concerns.

## Critical principles

- Do NOT mix business logic with UI components
- Keep database queries out of controllers
- Maintain clear boundaries between contexts
- Ensure proper separation of responsibilities

## Incorrect

The controller mixes HTTP handling, business logic, and database queries in a single method, making it impossible to reuse or test the business rules independently.

```java
// OrderController.java — everything in one place
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        // Database query directly in controller
        var customer = jdbcTemplate.queryForMap(
                "SELECT * FROM customers WHERE id = ?", request.getCustomerId());
        if (customer == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Customer not found"));
        }

        // Business logic mixed into controller
        double total = 0;
        for (var item : request.getItems()) {
            var product = jdbcTemplate.queryForMap(
                    "SELECT * FROM products WHERE id = ?", item.getProductId());
            total += (double) product.get("price") * item.getQuantity();
        }
        if (total > 10000) {
            total = total * 0.9; // 10% discount for large orders
        }

        // More database queries inline
        jdbcTemplate.update(
                "INSERT INTO orders (customer_id, total) VALUES (?, ?)",
                request.getCustomerId(), total);

        return ResponseEntity.status(201).body(Map.of("total", total));
    }
}
```

## Correct

The controller delegates to a service for business logic and a repository for data access. Each layer has a single responsibility and can be tested and reused independently.

```java
// OrderController.java — handles HTTP only
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request.getCustomerId(), request.getItems());
        return ResponseEntity.status(201).body(order);
    }
}

// OrderService.java — business logic only
public class OrderService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderService(CustomerRepository customerRepository,
            ProductRepository productRepository, OrderRepository orderRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    public Order createOrder(String customerId, List<OrderItem> items) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));

        double total = calculateTotal(items);
        return orderRepository.save(new Order(customerId, total));
    }

    private double calculateTotal(List<OrderItem> items) {
        double total = 0;
        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new NotFoundException("Product not found"));
            total += product.getPrice() * item.getQuantity();
        }
        return total > 10000 ? total * 0.9 : total;
    }
}

// OrderRepository.java — data access only
@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Order save(Order order) {
        jdbcTemplate.update(
                "INSERT INTO orders (customer_id, total) VALUES (?, ?)",
                order.getCustomerId(), order.getTotal());
        return order;
    }
}
```

## Reference

- [Clean Architecture by Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)