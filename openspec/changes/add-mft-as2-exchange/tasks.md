# MFT AS2 implementation tasks

## 1. Domain and storage

- [x] 1.1 Create the MFT module, domain model, ports, and DOMAIN.md
- [x] 1.2 Implement tenant-scoped metadata and local encrypted payload/secret adapters
- [x] 1.3 Implement partner, certificate, transfer, idempotency, and evidence services
- [x] 1.4 Add production DynamoDB metadata persistence and distributed worker leases
- [ ] 1.5 Add S3 payload/evidence storage with tenant-bound SSE-KMS policy and retention
- [x] 1.6 Add atomic global ownership for inbound AS2 identity pairs
- [x] 1.8 Retain agreement, document, mapping, and exchange provenance on orchestrated transfers
- [ ] 1.7 Add the central tenant-key registry, exact alias authorization, required encryption context, and tenant offboarding lifecycle

## 2. AS2 service

- [x] 2.1 Add the MFT Spring Boot application, security, health, and configuration
- [x] 2.2 Implement inbound AS2 and MDN endpoints
- [x] 2.3 Implement outbound preparation, delivery worker, timeouts, and retries
- [x] 2.4 Implement certificate validation and secure/legacy policy gates
- [x] 2.5 Implement the production AWS managed-crypto adapter and remove private-key upload from the production API
- [x] 2.6 Add purpose-separated certificate rollover with a bounded verification grace window
- [x] 2.7 Add cell-shared signing/decryption key configuration, separated IAM policies, and RSA-OAEP activation gate
- [x] 2.8 Add a fail-closed partner lifecycle with production activation and suspension
- [x] 2.9 Separate partner signing and encryption certificates and validate their purpose at activation
- [x] 2.10 Bound raw AS2 request bodies and outbound payloads before allocation or storage
- [x] 2.11 Reject Message-ID replay conflicts while returning the original result for exact retries
- [x] 2.12 Persist asynchronous MDNs to a leased retry outbox before acknowledging inbound delivery
- [x] 2.13 Add global and per-partnership AS2 admission controls
- [ ] 2.14 Wire certificate-expiry and managed-key lifecycle alarms to the deployed operations stack

## 3. API contract and UI

- [x] 3.1 Export the MFT OpenAPI contract and generate `@edispark/mft-client`
- [x] 3.2 Add authenticated Next.js BFF routes
- [x] 3.3 Add overview, transfers, partner, and certificate operations UI
- [x] 3.4 Add the KMS hierarchy, tenant boundary, and blast-radius records under `docs/mft`, with end-user compatibility guidance in the MFT setup guide
- [x] 3.5 Add a tenant-scoped retailer onboarding pack with copyable AS2 details and public-certificate download

## 4. Simulator

- [x] 4.1 Add generated local identities and loopback-only partner peer
- [ ] 4.2 Add inbox/outbox workflow and selectable failure scenarios
- [x] 4.3 Integrate simulator startup into local tooling

## 5. Correctness and verification

- [x] 5.1 Implement property: Outbound idempotence
- [x] 5.2 Implement property: Inbound deduplication
- [x] 5.3 Implement property: Tenant isolation
- [x] 5.4 Implement property: Transfer state safety
- [x] 5.5 Implement property: Payload integrity
- [ ] 5.6 Add protocol, security, API, and frontend scenario tests
- [x] 5.7 Run module tests, contract export/generation, typecheck, build, and diff checks
- [x] 5.8 Document key custody, tenant isolation, sensitive data, compatibility, recovery, and production readiness under `docs/mft/`
- [x] 5.9 Verify inactive partners cannot send or receive and cross-tenant certificate IDs cannot activate a route
- [x] 5.10 Verify malformed CMS, oversized bodies, replay conflicts, certificate rollover, and durable MDN retries
