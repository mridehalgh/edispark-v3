# Qualified Order Response delivery

## ADDED Requirements

### Requirement: Complete source and target accountability

The system SHALL account for every populated source field and every required target field using the exact mapping revision and target contract. Mapped values SHALL be represented in final EDI with their intended business meaning. Intentional omissions SHALL have a narrow reviewed rationale.

#### Scenario: Rich eight-line response

- **GIVEN** a valid corrected Order Response containing eight lines and additional party/status fields
- **WHEN** readiness is evaluated
- **THEN** every populated occurrence is mapped, justified or reported as a blocker, and no required target field is missing.

### Requirement: Authoritative target validation

The system SHALL validate final Order Response EDI against identified standard/release and partner-rule versions before transport. Unavailable definitions, unsupported required semantics and invalid target values SHALL block delivery.

#### Scenario: Invalid mapped status

- **GIVEN** a mapping that emits a status or function incompatible with the source response and target profile
- **WHEN** readiness or publication qualification runs
- **THEN** it fails with a located, actionable finding and transport is not submitted.

### Requirement: Immutable qualified route revisions

Replacement mappings and agreements SHALL retain distinct immutable revision identities and qualification evidence for their exact dependencies. Historical sources, failed exchanges and published definitions SHALL remain unchanged.

#### Scenario: Dependency changes after qualification

- **GIVEN** an agreement qualified against particular mapping/profile/rule versions
- **WHEN** its effective dependencies change before sending
- **THEN** the stale qualification cannot authorize delivery and readiness requires the current qualified configuration.

### Requirement: Demonstrated delivery and traceability

The local verified journey SHALL retain the complete generated payload, simulator receipt evidence, configured MDN verification result and links to source/correction/exchange/transfer. Activity SHALL represent recorded events in numbered chronological order.

#### Scenario: Complete local delivery

- **GIVEN** the corrected eight-line document and a passing qualified route
- **WHEN** it is sent to the local AS2 simulator
- **THEN** all eight lines' required values and response semantics are preserved, payload hashes agree and the configured transport receipt determines the delivery state.

### Requirement: Authorized and idempotent recovery

Members SHALL be able to correct, validate and submit documents. Configuration publication and activation SHALL require administrative permission. Replaying one attempt SHALL NOT create an additional transfer, and retained source input SHALL remain tenant-scoped.

#### Scenario: Mapping repair from a finding

- **GIVEN** a retained readiness finding for a specific document representation
- **WHEN** an authorized administrator opens a draft mapping revision from it
- **THEN** testing uses that exact retained input and publication requires its complete passing evidence; a member cannot publish the revision.
