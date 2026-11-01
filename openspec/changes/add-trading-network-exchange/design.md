# Trading network and document exchange design

## Architecture

Trading Partnerships owns business identities and immutable agreement revisions. Transformations owns mapping graphs, fixtures, hashes, and publication. Exchange owns orchestration evidence and references these domains only through immutable IDs. The Integration API is their composition root and calls Documents and MFT through explicit ACL ports; neither downstream domain imports the new business models.

The browser calls authenticated Next.js BFF routes generated from the Integration OpenAPI contract. Bearer credentials stay server-side and the tenant is derived only from the validated token claim at every service.

## Qualification and routing

An agreement revision is eligible only when every route is unique, its document type matches ORDERS/INVOIC, its standard profile is EDIFACT D96A or TRADACOMS 9, its published mapping has the exact source and target schema IDs, and its AS2 connection is active for the same tenant. Activation publishes an immutable snapshot. Active revisions are never edited; a new draft revision is created instead.

## Mapping runtime

The runtime executes a bounded declarative graph over JSON pointers. It allows copy, constant, concatenate, coalesce, uppercase, and lowercase operations, caps graphs at 2,000 nodes, and caps input/output at 25 MiB. Publication requires at least one passing fixture and records a SHA-256 graph hash. Arbitrary scripts, network calls, and filesystem access are unavailable.

The bounded mapped model is rendered as EDIFACT or TRADACOMS by a standard-aware writer. This keeps visual field mapping separate from interchange syntax and prevents user-provided delimiters from escaping segments.

## Exchange consistency

Outbound submission atomically claims `(tenant, idempotencyKey)`, resolves one active agreement revision, pins the canonical Documents version and hash, executes the published mapping, retains a variant-keyed EDI derivative, and submits MFT with agreement/document/mapping provenance. Dependency failures become explicit review or failed stages; content is never logged.

Production metadata uses one tenant-partitioned DynamoDB table with transactional idempotency markers. Agreement activation events are published to a dedicated EventBridge bus and delivered at least once to an encrypted SQS queue with a five-attempt DLQ; consumers deduplicate by agreement ID and revision.

## Observability and security

The Integration API emits one structured completion event per request with correlation ID, tenant ID, deployment metadata, status, and duration. Tokens and payloads are excluded. Downstream calls propagate bearer and correlation context. Inputs use validated UUIDs, bounded strings, constrained JSON pointers, bounded files, and separator escaping.

## Local workspace

The complete runner starts Documents, MFT, Integration, the AS2 simulator, and web in dependency order. MFT writes the reconciled demo connection ID to a local-only handoff file; Integration uses it to seed one partner, one active agreement, and eight published mappings for inbound and outbound Order/Invoice × EDIFACT/TRADACOMS. A local polling adapter reconciles retained inbound transfers with the same idempotent processor used by production SQS delivery.
