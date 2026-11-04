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

