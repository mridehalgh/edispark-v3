# Trading network implementation tasks

## 1. Bounded contexts

- [x] 1.1 Add Trading Partnerships domain, tenant repository, agreement revisions, and strict qualification
- [x] 1.2 Add Transformations domain, bounded mapping runtime, fixtures, hashes, and immutable revisions
- [x] 1.3 Add Exchange domain, idempotent orchestration, lifecycle evidence, and ACL ports
- [x] 1.4 Add bounded EDIFACT parser/writer primitives and standard-aware outbound envelope rendering

## 2. Domain integration

- [x] 2.1 Add variant-aware external derivatives to Documents
- [x] 2.2 Add immutable orchestration provenance to MFT transfers
- [x] 2.3 Compose Documents, Mapping, Partnership, and MFT ACL adapters in Integration API
- [x] 2.4 Add DynamoDB production repositories with transactional idempotency claims
- [x] 2.5 Add EventBridge-to-SQS agreement snapshot infrastructure with DLQ
- [x] 2.6 Consume inbound MFT payload events and route them into canonical Documents

## 3. API, UI, and local workspace

- [x] 3.1 Export Integration OpenAPI and generate `@edispark/integration-client`
- [x] 3.2 Add authenticated BFF routes and Trading Network UI
- [x] 3.3 Add eligible-route send controls to Order and Invoice views
- [x] 3.4 Start Integration API in the complete local runner and reconcile connected demo data

## 4. Verification

- [x] 4.1 Test qualification, tenant identity uniqueness, mapping publication, and immutability
- [x] 4.2 Test exchange idempotency, derivative retention, transport queuing, and provenance
- [x] 4.3 Test EDIFACT parsing and EDIFACT/TRADACOMS envelope generation
- [x] 4.4 Run the complete Maven, web, contract, and local end-to-end verification matrix
