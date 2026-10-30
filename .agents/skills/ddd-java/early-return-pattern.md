---
title: Use Early Returns to Reduce Nesting
impact: MEDIUM
paths:
  - "**/*.java"
---

# Use Early Returns to Reduce Nesting

Always use early returns to handle error conditions and edge cases at the top of methods instead of wrapping logic in nested conditionals. Deeply nested code (more than 3 levels) increases cognitive load, obscures the happy path, and makes methods harder to read, review, and maintain. When guard clauses are placed first, the main logic stays at the top indentation level and reads linearly from top to bottom.

## Incorrect

Validation checks are nested inside each other, pushing the core business logic deep into indentation. The happy path is buried at the innermost level, and error handling is scattered across multiple `else` branches at the bottom.

```java
public User validateUser(String userId, String role) {
    if (userId != null && !userId.isBlank()) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            if (!user.isDeleted()) {
                if (user.getRole().equals(role)) {
                    if (user.isEmailVerified()) {
                        // happy path buried 5 levels deep
                        return user;
                    } else {
                        throw new ValidationException("Email not verified");
                    }
                } else {
                    throw new ValidationException("Insufficient role");
                }
            } else {
                throw new ValidationException("User is deleted");
            }
        } else {
            throw new NotFoundException("User not found");
        }
    } else {
        throw new IllegalArgumentException("User ID is required");
    }
}
```

## Correct

Guard clauses handle each error condition with an early return at the top level. The happy path flows naturally at the end of the method with zero unnecessary nesting.

```java
public User validateUser(String userId, String role) {
    if (userId == null || userId.isBlank()) {
        throw new IllegalArgumentException("User ID is required");
    }

    User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));

    if (user.isDeleted()) {
        throw new ValidationException("User is deleted");
    }
    if (!user.getRole().equals(role)) {
        throw new ValidationException("Insufficient role");
    }
    if (!user.isEmailVerified()) {
        throw new ValidationException("Email not verified");
    }

    return user;
}
```

## Reference

- [Flattening Arrow Code](https://blog.codinghorror.com/flattening-arrow-code/)
