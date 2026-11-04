# Design

## Document ownership

A draft is a `Document` with `lifecycleStatus=DRAFT`. Each save adds an immutable `DocumentVersion`; finalization validates the current version against its tenant-provisioned OASIS UBL 2.1 schema. Final or cancelled documents cannot be sent as drafts. Typed relations express the business chain without coupling the Documents aggregate to a retailer-only workflow.

The six authoring definitions identify the OASIS main-document namespace, parties, references, line container, and relation semantics. The form writes canonical UBL JSON. Sending resolves an active partner capability and produces the configured EDIFACT or TRADACOMS derivative; authored forms never write EDI directly.

## Workflow policy

The first deployable workflow seam lives on a versioned inbound `ExchangeCapability`:

- `MANUAL`: create operational work only.
- `AUTO_DRAFT`: prefill a related response draft.
- `APPROVAL_REQUIRED`: prefill and require one authorised member or four-eyes approval.
- `AUTO_SEND`: execute only after agreement qualification and document validation.

Existing capabilities default to Manual/None. Response automation is invalid on outbound capabilities. This policy is deliberately document-type-neutral and can later compile into the planned canonical workflow graph/runtime without changing partner contracts.

## Security and tenancy

The BFF derives `createdBy` from the authenticated session, resolves schemas through the tenant-scoped Documents API, validates identifiers, and never exposes service tokens or URLs to browser code. Partner activation and existing role gates continue to protect configuration changes.

## Compatibility

DynamoDB records without lifecycle or typed relations load as Final with a `RELATED_TO` relation when the legacy field exists. Old capability records and constructors default to Manual workflow and strict outbound-loss handling.
