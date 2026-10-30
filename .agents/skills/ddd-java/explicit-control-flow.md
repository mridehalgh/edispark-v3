---
title: Explicit Control Flow and Policy-Mechanism Separation
paths:
  - "**/*.java"
impact: HIGH
---

# Explicit Control Flow and Policy-Mechanism Separation

Error conditions, branching, and control flow decisions must be visible at the call site — never hidden inside helper methods that look like simple validators or utilities. This is an application of the policy-mechanism separation principle: a "mechanism" is a pure method that computes a result and returns it; a "policy" is what the caller decides to do with that result — throw, log, branch, or ignore.

When policy is hidden inside mechanism (e.g., a `validate` method that throws instead of returning a boolean), the call site becomes deceptive. The reader sees what looks like a passive check but is actually a control flow branch that can halt execution. Keeping mechanisms pure and policies explicit at the call site makes code predictable and composable: the same mechanism can serve different policies without modification.

Apply this separation consistently:

- **Mechanism** = `isValid(result)` returns a boolean. **Policy** = the caller decides to throw.
- **Mechanism** = `applyNewFeature(baseData)` returns new data. **Policy** = the caller decides whether to call it based on a feature flag.
- **Mechanism** = `formatResult(result)` returns a string. **Policy** = the caller decides to log it.

## Incorrect

`validateResult` hides a throw inside what reads like a passive validation check. The call site shows no branching, no `if`, no `throw` — the reader assumes execution continues normally after the call. The control flow decision (throw on invalid) is buried inside the mechanism.

```java
private void validateResult(Result result) {
    if (!result.isSuccess()) {
        throw new ProcessingException(result.getError());
    }
    if (result.getValue() < 0) {
        throw new IllegalArgumentException("Negative value");
    }
}

// call site — looks harmless, hides two possible throws
Result result = performProcess(param);
validateResult(result);
```

Similarly, hiding a feature-flag policy inside the mechanism couples the feature decision to the transformation:

```java
private Data applyNewFeature(Data data) {
    if (!featureFlags.isEnabled("new-feature")) {
        return data;  // policy hidden inside mechanism
    }
    return transform(data);
}

// call site — reader cannot tell a feature flag is being checked
Data output = applyNewFeature(baseData);
```

## Correct

The mechanism (`isValid`) is a pure method that returns a value. The policy (what to do when invalid) is explicit at the call site. Every branch point is visible to the reader.

```java
private boolean isValid(Result result) {
    return result.isSuccess() && result.getValue() >= 0;
}

// call site — control flow is visible
Result result = performProcess(param);
if (!isValid(result)) {
    throw new ProcessingException(result.toString());
}
```

The feature-flag policy is at the call site, and the mechanism is a pure transformation:

```java
private Data applyNewFeature(Data data) {
    return transform(data);  // pure mechanism — always transforms
}

// call site — policy is explicit
Data output = featureEnabled ? applyNewFeature(baseData) : baseData;
```

Logging follows the same pattern — the mechanism formats, the caller decides to log:

```java
String summary = formatResult(result);  // mechanism: returns string
logger.info(summary);                   // policy: caller decides to log
```

## Reference

- [Policy-Mechanism Separation (Wikipedia)](https://en.wikipedia.org/wiki/Separation_of_mechanism_and_policy)
