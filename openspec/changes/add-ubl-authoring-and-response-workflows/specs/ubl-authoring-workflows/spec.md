# UBL authoring and response workflow delta

## ADDED Requirements

### Requirement: Drafts remain ordinary versioned documents

The system SHALL represent authored work with the normal `Document` aggregate and API, SHALL expose Draft, Final, and Cancelled lifecycle states, and SHALL validate the current version against its tenant-scoped schema before finalization.

#### Scenario: User saves an incomplete document

- **WHEN** an authenticated user submits one of the supported authoring forms
- **THEN** the system creates a normal versioned document in Draft state using the authenticated user as its creator

#### Scenario: User finalizes valid authored content

- **WHEN** a user finalizes a Draft whose current version satisfies its OASIS UBL schema
- **THEN** the system marks that document Final and makes it eligible for the existing outbound exchange flow

#### Scenario: User finalizes invalid authored content

- **WHEN** a user finalizes a Draft whose current version does not satisfy its schema
- **THEN** the system returns validation errors and leaves the document in Draft state

### Requirement: OASIS UBL 2.1 is the canonical authoring model

The system SHALL create canonical OASIS UBL 2.1 JSON for Order, Order Response, Despatch Advice, Invoice, Remittance Advice, and Receipt Advice. EDIFACT and TRADACOMS payloads SHALL remain partner-specific derivatives rather than form-owned source formats.

#### Scenario: User authors any supported business document

- **WHEN** a user selects one of the six supported document types and completes its required business fields
- **THEN** the form builds a document with the matching OASIS main-document namespace, parties, references, lines, quantities, and monetary values

#### Scenario: User sends an authored document to an EDI partner

- **WHEN** a Final authored document is submitted through an eligible active partner capability
- **THEN** the existing exchange flow resolves the partner mapping and produces its configured EDI derivative without changing the canonical source document

### Requirement: Business document chains use typed relations

The system SHALL persist typed document relations independently of retailer-specific workflow code and SHALL preserve legacy untyped relations as `RELATED_TO`.

#### Scenario: User creates a response from an order

- **WHEN** a user chooses Create response on an Order
- **THEN** the system creates an Order Response Draft in the same document set with a `RESPONDS_TO` relation to that Order

#### Scenario: A legacy relation is loaded

- **WHEN** the system reconstitutes a stored document containing only the former related-document identifier
- **THEN** it exposes that relation as `RELATED_TO` without losing the target identifier

### Requirement: Partner capabilities declare safe response automation policy

The system SHALL allow versioned inbound exchange capabilities to declare Manual, Auto Draft, Approval Required, or Auto Send behavior together with an optional response document type and approval policy. Existing capabilities SHALL default to Manual with no approval policy.

#### Scenario: Analyst configures a reviewed response

- **WHEN** an analyst configures an inbound Order capability as Approval Required
- **THEN** the capability requires a non-empty approval policy and can identify Order Response as the response document type

#### Scenario: Analyst attempts outbound response automation

- **WHEN** an analyst assigns a non-Manual response automation mode to an outbound capability
- **THEN** the system rejects the invalid capability configuration

### Requirement: Linked documents avoid unnecessary re-keying

The system SHALL prefill a business-relevant next document from its directly selected source and typed-relation ancestors, SHALL prefer the nearest stage value, and SHALL preserve the correct OASIS order, despatch, invoice, and line references.

#### Scenario: User creates a fulfilment document from an existing chain

- **WHEN** a user creates a response, despatch, receipt, or invoice from a supported earlier document
- **THEN** the form copies the relevant parties, identifiers, references, items, quantities, units, and available prices while showing their source documents

#### Scenario: A nearer document omits an earlier commercial value

- **WHEN** the directly selected document contains the current quantity but omits an item identifier or unit price available on an ancestor
- **THEN** smart copy keeps the current quantity and fills the missing identifier or price from that ancestor

#### Scenario: User creates a remittance from an invoice

- **WHEN** a user creates a Remittance Advice from an Invoice
- **THEN** the form creates an invoice-referenced payment allocation using the payable amount rather than inventing item quantities

#### Scenario: Copied commercial values have not been reviewed

- **WHEN** copied quantities, prices, or amounts are present and the user has not confirmed their review
- **THEN** the form refuses to save the Draft and keeps every copied value editable

### Requirement: Each supported document has a specialist business form

The system SHALL expose document-specific authoring sections for all six supported OASIS UBL 2.1 document types while preserving one canonical UBL document model.

#### Scenario: Supplier prepares a multi-pallet ASN

- **WHEN** one order line is divided across several logistics units
- **THEN** the Despatch Advice records separate handling units, SSCCs and line quantities whose allocations can be reconciled with the delivered quantity

#### Scenario: Receiver records discrepancies

- **WHEN** received goods are short, rejected or damaged
- **THEN** the Receipt Advice records the affected quantity, reason and item traceability without replacing the related Despatch Advice

### Requirement: Partner field requirements are immutable and reproducible

The system SHALL manage reusable tenant-scoped partner document profile revisions and SHALL allow an exchange capability to pin a published profile matching its partner, direction and document type.

#### Scenario: Analyst publishes retailer requirements

- **WHEN** an analyst publishes a draft profile containing required fields, defaults and allowed codes
- **THEN** that revision becomes immutable and can be selected by compatible agreement capabilities

#### Scenario: Incomplete partner document is saved

- **WHEN** an authored Draft does not yet satisfy its pinned profile
- **THEN** saving remains available with readiness warnings while finalization and sending remain blocked

### Requirement: Logistics labels derive from canonical handling units

The system SHALL validate supplied SSCCs and render GS1 logistics labels as PDF and ZPL derivatives of the source document version.

#### Scenario: User generates pallet labels

- **WHEN** a Despatch Advice contains handling units with valid unique SSCCs
- **THEN** the system produces downloadable A5 PDF and printer-ready ZPL labels linked to that document version

