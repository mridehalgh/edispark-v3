---
title: Principle of Least Astonishment — Methods Must Do Only What Their Name Promises
paths:
  - "**/*.java"
impact: HIGH
---

# Principle of Least Astonishment — Methods Must Do Only What Their Name Promises

A method must do exactly what its name and signature suggest — nothing more, nothing less. Hidden side effects violate caller expectations, create invisible coupling, and make code unpredictable. When a method named `getUser` also emits analytics events, or a method named `validate` throws instead of returning a boolean, callers cannot reason about behavior from the interface alone. This forces every developer to read the implementation before using the method, defeating the purpose of abstraction.

Keep methods honest: if a name implies a pure query, do not mutate state. If a name implies validation, return a result rather than throwing. Make all side effects explicit at the call site so the reader's mental model matches the actual execution. When additional work is needed (logging, analytics, notifications), perform it in the calling code or use clearly named wrapper methods that advertise the combined behavior.

## Incorrect

The method name `getUser` promises a data retrieval operation, but it secretly logs an analytics event and updates a last-accessed timestamp — side effects the caller never asked for and cannot see from the signature.

```java
// UserService.java — hidden side effects inside a getter
public User getUser(String userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));

    // Hidden side effect: analytics tracking
    analyticsService.track("user_viewed", Map.of(
            "userId", user.getId(),
            "timestamp", Instant.now().toString()
    ));

    // Hidden side effect: mutates database state
    userRepository.updateLastAccessed(userId, Instant.now());

    return user;
}
```

## Correct

The getter does only what its name says — retrieves a user. Side effects are performed explicitly at the call site, where the caller can see and control them.

```java
// UserService.java — getter does only what the name promises
public User getUser(String userId) {
    return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
}

// call site — side effects are explicit and visible
User user = userService.getUser(userId);
analyticsService.track("user_viewed", Map.of("userId", user.getId()));
userRepository.updateLastAccessed(userId, Instant.now());
```

## Reference

- [Principle of Least Astonishment (Wikipedia)](https://en.wikipedia.org/wiki/Principle_of_least_astonishment)
- [Clean Code by Robert C. Martin](https://www.oreilly.com/library/view/clean-code-a/9780136083238/) — Chapter 3: Functions should do one thing