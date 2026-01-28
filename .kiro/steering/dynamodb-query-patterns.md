---
inclusion: always
---

# DynamoDB Query Patterns

## SCAN Operations - Prohibited

**NEVER use DynamoDB SCAN operations in production code.** SCAN reads every item in a table or index, consuming massive read capacity and causing performance degradation as data grows.

### Why SCAN is Prohibited

- **Performance**: SCAN examines every item regardless of how many you need
- **Cost**: Consumes read capacity units proportional to table size, not result size
- **Scalability**: Performance degrades linearly as table grows
- **Unpredictable**: Response times vary wildly based on table size

### Correct Alternatives

| Use Case | Solution |
|----------|----------|
| Get all items of a type | Use Query with a GSI where PK = entity type |
| List items with filters | Design GSI to support the access pattern with Query |
| Search across attributes | Use Query with composite sort keys or multiple GSIs |
| Pagination | Use Query with LastEvaluatedKey for continuation tokens |

### Query Requirements

Every access pattern MUST be supported by either:
1. **Base table Query**: Using the primary key
2. **GSI Query**: Using a global secondary index designed for that pattern

If you cannot Query for an access pattern, the data model needs a GSI.

## Pagination

### Rules

1. **Do not** return `LastEvaluatedKey` (or any part of it) directly to clients
2. **Do not** ask clients to send `ExclusiveStartKey`, partition/sort keys, timestamps, or other DynamoDB key attributes

### Implementation

Encode DynamoDB's `LastEvaluatedKey` into an opaque token (e.g. Base64) before returning to clients. Decode the token back when receiving it. Clients only ever see and send a single opaque `nextToken` string.

## Exception: Development and Testing

SCAN may be used ONLY in:
- Local development utilities
- Test fixtures and setup code
- Administrative scripts run manually

Mark these clearly with comments explaining why SCAN is acceptable in that context.
