---
title: Method and Class Size Limits
impact: MEDIUM
paths:
  - "**/*.java"
---

# Method and Class Size Limits

- Decompose methods longer than 80 lines into smaller, focused methods of 50 lines or fewer. When a method grows beyond 80 lines, it is almost certainly doing more than one thing and should be split.
- Keep classes under 200 lines of code. Large methods accumulate multiple responsibilities, making them harder to test, review, and reuse.
- Extract cohesive blocks of logic into named methods that each serve a single purpose. If extracted methods are only used within the same context, keep them in the same class. However, when a class exceeds 200 lines even after decomposition, split related methods into separate classes grouped by responsibility.

## Incorrect

A single method handles validation, transformation, persistence, and notification. At over 80 lines it is difficult to test individual behaviors or reuse any part of the logic.

```java
public User processUserRegistration(Object input) {
    // Validate input (lines 1-20)
    if (input == null) throw new IllegalArgumentException("Invalid input");
    var request = (RegistrationRequest) input;
    if (request.getEmail() == null || request.getEmail().isBlank()) throw new IllegalArgumentException("Email required");
    if (request.getName() == null || request.getName().isBlank()) throw new IllegalArgumentException("Name required");
    if (request.getPassword() == null || request.getPassword().isBlank()) throw new IllegalArgumentException("Password required");
    if (request.getPassword().length() < 8) throw new IllegalArgumentException("Password too short");
    if (!request.getPassword().matches(".*[A-Z].*")) throw new IllegalArgumentException("Password needs uppercase");
    if (!request.getPassword().matches(".*[0-9].*")) throw new IllegalArgumentException("Password needs digit");
    if (!request.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Invalid email format");

    // Normalize data (lines 21-35)
    String normalizedEmail = request.getEmail().toLowerCase().trim();
    String normalizedName = request.getName().trim().replaceAll("\\s+", " ");
    String hashedPassword = passwordEncoder.encode(request.getPassword());
    String assignedRole = "admin".equals(request.getRole()) ? "user" : Objects.requireNonNullElse(request.getRole(), "user");
    Instant createdAt = Instant.now();

    // Check duplicates and persist (lines 36-55)
    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
        throw new IllegalStateException("Email already registered");
    }
    User user = new User(normalizedEmail, normalizedName, hashedPassword, assignedRole, createdAt);
    userRepository.save(user);

    // Send notifications (lines 56-80+)
    String welcomeHtml = "<h1>Welcome " + normalizedName + "</h1><p>Your account is ready.</p>";
    emailService.send(normalizedEmail, "Welcome!", welcomeHtml);
    analyticsService.track("user_registered", Map.of("userId", user.getId(), "role", assignedRole));
    auditLog.record("registration", Map.of("userId", user.getId(), "email", normalizedEmail));

    return user;
}
```

## Correct

Each responsibility is extracted into a focused method under 50 lines. Methods that are only used together stay in the same class.

```java
private RegistrationRequest validateRegistrationInput(Object input) {
    if (input == null) throw new IllegalArgumentException("Invalid input");
    var request = (RegistrationRequest) input;
    if (request.getEmail() == null || request.getEmail().isBlank()) throw new IllegalArgumentException("Email required");
    if (request.getName() == null || request.getName().isBlank()) throw new IllegalArgumentException("Name required");
    if (request.getPassword() == null || request.getPassword().isBlank()) throw new IllegalArgumentException("Password required");
    if (request.getPassword().length() < 8) throw new IllegalArgumentException("Password too short");
    if (!request.getPassword().matches(".*[A-Z].*")) throw new IllegalArgumentException("Password needs uppercase");
    if (!request.getPassword().matches(".*[0-9].*")) throw new IllegalArgumentException("Password needs digit");
    if (!request.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Invalid email format");
    return request;
}

private NormalizedUser normalizeAndHash(RegistrationRequest input) {
    return new NormalizedUser(
            input.getEmail().toLowerCase().trim(),
            input.getName().trim().replaceAll("\\s+", " "),
            passwordEncoder.encode(input.getPassword()),
            "admin".equals(input.getRole()) ? "user" : Objects.requireNonNullElse(input.getRole(), "user")
    );
}

private User persistUser(NormalizedUser data) {
    if (userRepository.findByEmail(data.email()).isPresent()) {
        throw new IllegalStateException("Email already registered");
    }
    User user = new User(data.email(), data.name(), data.password(), data.role(), Instant.now());
    return userRepository.save(user);
}

private void notifyRegistration(User user) {
    emailService.send(user.getEmail(), "Welcome!", "<h1>Welcome " + user.getName() + "</h1>");
    analyticsService.track("user_registered", Map.of("userId", user.getId(), "role", user.getRole()));
    auditLog.record("registration", Map.of("userId", user.getId(), "email", user.getEmail()));
}

public User processUserRegistration(Object input) {
    RegistrationRequest validated = validateRegistrationInput(input);
    NormalizedUser normalized = normalizeAndHash(validated);
    User user = persistUser(normalized);
    notifyRegistration(user);
    return user;
}
```