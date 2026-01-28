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

## Pagination Pattern

### DynamoDB Pagination Tokens

DynamoDB returns `LastEvaluatedKey` as a continuation token. This token:
- Is a map containing the key attributes of the last evaluated item
- Must be passed as `ExclusiveStartKey` to continue pagination
- Should be encoded before exposing to API clients

### API Abstraction

**NEVER expose raw DynamoDB tokens to API clients.** Instead:

1. **Encode tokens**: Use Base64 or similar encoding to create opaque strings
2. **Abstract the format**: Clients should treat tokens as opaque strings
3. **Handle errors gracefully**: Invalid tokens should return clear error messages

### Implementation Pattern

```java
// Repository layer - works with DynamoDB tokens
public interface DocumentSetRepository {
    PaginatedResult<DocumentSet> findAll(int limit, Map<String, AttributeValue> exclusiveStartKey);
}

// Application layer - abstracts pagination
public record PaginatedResult<T>(
    List<T> items,
    Optional<String> nextToken  // Encoded, opaque to clients
) {}

// Encoding/decoding utility
public class PaginationTokenCodec {
    public static String encode(Map<String, AttributeValue> lastEvaluatedKey) {
        // Base64 encode the key map
    }
    
    public static Map<String, AttributeValue> decode(String token) {
        // Base64 decode and validate
    }
}
```

### API Response Structure

```json
{
  "items": [...],
  "nextToken": "eyJQSyI6eyJTIjoiRE9DU0VUI..."  // Opaque encoded token
}
```

Clients pass `nextToken` back as a query parameter for the next page. They never need to understand its internal structure.

## Design Checklist

Before implementing any list/search endpoint:

- [ ] Identified the Query access pattern (base table or GSI)
- [ ] Confirmed NO SCAN operations are used
- [ ] Designed pagination token encoding/decoding
- [ ] Hidden DynamoDB implementation details from API layer
- [ ] Documented the access pattern in code comments

## Exception: Development and Testing

SCAN may be used ONLY in:
- Local development utilities
- Test fixtures and setup code
- Administrative scripts run manually

Mark these clearly with comments explaining why SCAN is acceptable in that context.
