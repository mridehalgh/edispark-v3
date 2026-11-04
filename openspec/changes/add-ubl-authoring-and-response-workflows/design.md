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

## Specialist forms and partner profiles

The six document forms share canonical UBL builders and accessible field primitives but expose document-specific business sections. Despatch and Receipt Advice model shipment handling units explicitly, including SSCC, package data, traceability, and quantities from one order line allocated across several pallets. Incomplete values remain valid Draft work; OASIS and published partner requirements gate finalization and exchange.

Partner document profiles are reusable, tenant-scoped revision families owned by Partnerships. Draft revisions can be edited, published revisions are immutable, and an exchange capability may pin a published revision matching its partner, direction, and document type. Profiles use stable semantic field keys rather than array indexes and support required, optional, hidden, default, fixed, code-list, range, and pattern rules without executable scripts.

GS1 logistics labels are document derivatives generated from UBL `TransportHandlingUnit` values. EdiSpark validates supplied 18-digit SSCCs and renders A5 PDF and printer-ready ZPL; SSCC allocation remains with the tenant's ERP or WMS.
