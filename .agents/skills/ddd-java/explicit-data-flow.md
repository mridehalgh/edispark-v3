---
title: Explicit Data Flow
paths:
  - "**/*.java"
impact: HIGH
---

# Explicit Data Flow

If a method produces a result, return it. Never rely on mutation of an input parameter to communicate output. Data should flow explicitly through return values so the reader can trace where each value comes from. When a method mutates its input, the data flow is hidden -- you cannot tell from the call site what changed or whether other references were affected. With explicit returns and `final` variables, the call site becomes self-documenting: assignments show data origin, and immutability guarantees no downstream code silently altered what you are reading. Prefer pure expressions that produce new values over procedures that modify existing ones.

## Incorrect

Mutation of the input hides where data ends up. The call site looks like a standalone command, but the caller depends on the side effect for its result.

```java
Map<String, Object> result = new HashMap<>();
if (featureEnabled) {
    applyNewFeature(result);  // mutates result in-place
}
```

Using reassignment is still unclear because the reader cannot tell whether `applyNewFeature` also mutates the original in addition to returning a value.

```java
Map<String, Object> result = new HashMap<>();
if (featureEnabled) {
    result = applyNewFeature(result);  // unclear whether applyNewFeature also mutates
}
```

## Correct

A pure expression with `final` makes data flow visible in one line. The reader sees exactly what `result` is bound to, with no hidden mutation.

```java
final Map<String, Object> result = featureEnabled ? applyNewFeature(baseData) : Map.of();
```

## Reference

- [Referential Transparency](https://en.wikipedia.org/wiki/Referential_transparency)
- [Effective Java by Joshua Bloch](https://www.oreilly.com/library/view/effective-java/9780134686097/) — Item 17: Minimize mutability
