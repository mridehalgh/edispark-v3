## Overview

<!-- High-level summary of the implementation approach -->

## Architecture

<!-- System structure, data flow, diagrams (use Mermaid) -->

## Components and Interfaces

<!-- Modules, APIs, contracts between parts -->

## Data Models

<!-- Schemas, types, data structures -->

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

<!-- Derive from acceptance criteria via prework analysis and property reflection -->

### Property 1: <!-- title -->

*For any* <!-- inputs -->, <!-- what should hold -->.

**Validates: Requirements X.Y**

### Property 2: <!-- title -->

*For any* <!-- inputs -->, <!-- what should hold -->.

**Validates: Requirements X.Y**

<!-- Continue with additional properties... -->

## Error Handling

<!-- Failure modes, recovery strategies, error codes -->

## Testing Strategy

**Framework:** <!-- e.g., fast-check (TypeScript), hypothesis (Python) -->

**Test location:** <!-- e.g., co-located *.test.ts, separate test/ directory -->

**Unit tests:** <!-- specific examples, edge cases, error conditions -->

**Property-based tests:**

For each correctness property:

<!-- Property N: <title>
- Generator strategy: how to produce representative random inputs
- Edge cases to include in generators (from prework)
- Tag: Feature: {feature_name}, Property {N}: {property_text}
- Minimum iterations: 100
-->
