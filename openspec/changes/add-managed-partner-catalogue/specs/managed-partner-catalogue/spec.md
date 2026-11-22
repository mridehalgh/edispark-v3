## ADDED Requirements

### Requirement: System-managed published catalogue

The system SHALL restrict creating, editing, publishing and retiring managed templates to system administrators. Platform administrators SHALL be able to manage the catalogue without selecting an organisation.

#### Scenario: Organisation owner attempts to publish a template

- **WHEN** an organisation owner without the system administrator role submits a publish request
- **THEN** the platform API refuses the request

### Requirement: Tenant-owned setup from published revisions

The system SHALL allow an organisation administrator to apply a published template revision using tenant-owned AS2 configuration. The system SHALL create the partner, connection and agreement as drafts and retain the applied revision. A repeated request with the same input SHALL resolve to the same drafts.

#### Scenario: Two organisations apply the same publication

- **WHEN** administrators in different organisations use the same publication and supply their own AS2 settings
- **THEN** each organisation receives its own isolated setup and the template revision remains reusable

### Requirement: Managed retailer qualification

A managed live agreement SHALL pass existing partner and transport qualification and retain retailer acceptance for its exact agreement and AS2 configuration before activation. A local simulator fixture MAY activate within the local stack.

#### Scenario: AS2 settings change after retailer acceptance

- **WHEN** the organisation attempts activation after a material connection change
- **THEN** the system requires retailer acceptance for the revised configuration
