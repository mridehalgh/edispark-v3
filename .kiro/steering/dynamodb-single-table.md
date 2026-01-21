# DynamoDB Single Table Design

## Design Philosophy

**One table per bounded context.** Each domain module owns a single DynamoDB table containing all entity types within that context. This aligns with DDD principles—bounded contexts remain autonomous with their own data store, while still benefiting from single table design patterns within each context.

Single table design consolidates multiple entity types into one table, using carefully crafted partition and sort keys to support all access patterns. This approach maximises performance and minimises costs by reducing the number of requests needed.

## Multi-Attribute Composite Keys

GSIs support multi-attribute keys, allowing you to compose partition keys and sort keys from multiple attributes:

- Partition key: up to **4 attributes**
- Sort key: up to **4 attributes**
- Total: up to **8 attributes** per key schema

### Why Multi-Attribute Keys Matter

Multi-attribute keys simplify your data model by eliminating manual concatenation into synthetic keys. Instead of creating composite strings like `TOURNAMENT#WINTER2024#REGION#NA-EAST`, use natural attributes from your domain model directly. DynamoDB handles the composite key logic automatically:

- **Partition keys**: Hashes multiple attributes together for data distribution
- **Sort keys**: Maintains hierarchical sort order across multiple attributes

### Benefits

- **No concatenation** when writing data
- **No parsing** when reading data
- **No backfilling** when adding GSIs to existing tables
- **Type safety** preserved on individual key components
- **Cleaner code** using natural domain attributes

### Data Types in Multi-Attribute Keys

Each attribute can have its own type: String (S), Number (N), or Binary (B).

**Important sorting behaviour:**
- **Number** attributes sort numerically: 5, 50, 500, 1000
- **String** attributes sort lexicographically: "1000", "5", "50", "500"

Use Number types for numeric sorting without zero-padding.

### Query Semantics

When querying a GSI with multi-attribute keys:

1. **Partition key**: Must specify ALL attributes using equality conditions
2. **Sort key**: Query left-to-right in defined order
   - Can query first attribute alone
   - Can query first two attributes together
   - Cannot skip attributes in the middle
3. **Inequality conditions** (`>`, `<`, `BETWEEN`, `begins_with`) must be the **last condition**

### Key Ordering Principle

Order attributes from **most general to most specific**:

- **Partition keys**: Combine attributes always queried together with good distribution
- **Sort keys**: Place frequently queried attributes first to maximise query flexibility

## Example: Tournament Match System

### Access Patterns

| Pattern | Solution |
|---------|----------|
| Look up match by ID | Base table: PK = matchId |
| Matches by tournament/region, filter by round/bracket | GSI: PK = [tournamentId, region], SK = [round, bracket, matchId] |
| Player match history, filter by date/round | GSI: PK = [player1Id], SK = [matchDate, round] |

### Traditional Approach (Avoid)

```javascript
// Manual concatenation required
const item = {
    matchId: 'match-001',
    tournamentId: 'WINTER2024',
    region: 'NA-EAST',
    round: 'SEMIFINALS',
    bracket: 'UPPER',
    // Synthetic keys needed for GSI
    GSI_PK: `TOURNAMENT#${tournamentId}#REGION#${region}`,
    GSI_SK: `${round}#${bracket}#${matchId}`
};
```

### Multi-Attribute Approach (Preferred)

```javascript
// Use existing attributes directly
const item = {
    matchId: 'match-001',
    tournamentId: 'WINTER2024',
    region: 'NA-EAST',
    round: 'SEMIFINALS',
    bracket: 'UPPER',
    player1Id: '101',
    matchDate: '2024-01-18'
    // No synthetic keys - GSI uses existing attributes
};
```

### Schema Design

**Base Table:**
- PK: matchId (1 attribute)

**TournamentRegionIndex (GSI):**
- PK: tournamentId, region (2 attributes)
- SK: round, bracket, matchId (3 attributes)

**PlayerMatchHistoryIndex (GSI):**
- PK: player1Id (1 attribute)
- SK: matchDate, round (2 attributes)

### Example Queries

```
// All WINTER2024 matches in NA-EAST
Query TournamentRegionIndex
  WHERE tournamentId = 'WINTER2024' AND region = 'NA-EAST'

// SEMIFINALS in UPPER bracket for WINTER2024/NA-EAST
Query TournamentRegionIndex
  WHERE tournamentId = 'WINTER2024' AND region = 'NA-EAST'
    AND round = 'SEMIFINALS' AND bracket = 'UPPER'

// Player 101's matches in January 2024
Query PlayerMatchHistoryIndex
  WHERE player1Id = '101'
    AND matchDate BETWEEN '2024-01-01' AND '2024-01-31'
```

## Key Design Patterns

### Hierarchical Data

```
PK: [ParentType, ParentId]
SK: [ChildType, ChildId]
```

### Time-Series with Status

```
PK: [EntityType, EntityId]
SK: [Status, Timestamp]
GSI: PK = [Status], SK = [Timestamp, EntityId]
```

### Multi-Tenant

```
PK: [TenantId, EntityType]
SK: [EntityId]
```

## GSI Strategy

- Create GSIs to support access patterns not covered by the base table
- Use sparse indexes (only items with GSI attributes appear in the index)
- Leverage multi-attribute keys to reduce the number of GSIs needed
- Project only required attributes to minimise storage and RCU costs
- Add GSIs to existing tables without backfilling synthetic keys

## Data Modelling Process

1. **Identify entities** - List all entity types to store
2. **Define access patterns** - Document every query the application needs
3. **Design base table keys** - Support the most common patterns
4. **Add GSIs** - Cover remaining patterns with multi-attribute composite keys
5. **Use the MCP server** - The `awslabs.dynamodb-mcp-server` can assist with data modelling

## MCP Server for Design Assistance

Use the DynamoDB MCP server's `dynamodb_data_modeling` tool for:
- Requirements gathering
- Access pattern analysis
- Production-ready schema design
- Hot partition analysis
- Cost optimisation strategies

## Best Practices

### Do

- Start with access patterns, not entity relationships
- Use multi-attribute composite keys for natural hierarchies
- Order key attributes from general to specific
- Use Number types for numeric sorting
- Keep items small (under 400KB, ideally under 10KB)
- Use sparse GSIs for optional attributes
- Document your key schema and access patterns

### Avoid

- Creating one table per entity type (relational thinking)
- Manual string concatenation for composite keys
- Over-indexing with too many GSIs
- Storing large blobs in DynamoDB
- Designing without knowing your access patterns
- Skipping attributes in sort key queries

## Integration with DDD

### One Table Per Bounded Context

Each bounded context owns exactly one DynamoDB table:

```
domains/
├── ordering/          -> ordering-table
├── inventory/         -> inventory-table
└── shipping/          -> shipping-table
```

This ensures:
- **Autonomy** - Each domain controls its own data model and access patterns
- **Independent scaling** - Tables scale based on domain-specific load
- **Clear ownership** - No cross-domain data coupling at the storage layer
- **Simpler migrations** - Schema changes affect only one domain

### Table Naming Convention

```
{service-name}-{bounded-context}-{environment}

Examples:
- myapp-ordering-prod
- myapp-inventory-dev
```

### Domain Mapping

- **Aggregates** map naturally to items with the same partition key
- **Entity IDs** become part of the key structure
- **Domain events** can be stored as items with time-based sort keys
- **Repositories** abstract the single table design from domain code

```java
public interface OrderRepository {
    Optional<Order> findById(OrderId id);
    List<Order> findByCustomer(CustomerId customerId);
    void save(Order order);
}

// Infrastructure implementation handles key construction
class DynamoDbOrderRepository implements OrderRepository {
    // Uses multi-attribute keys: PK=[CustomerId, "ORDER"], SK=[OrderId]
}
```
