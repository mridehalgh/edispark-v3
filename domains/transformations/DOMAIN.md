# Transformations

## Vision

Transformations provides auditable, deterministic conversion between canonical UBL and partner-specific EDI representations without allowing arbitrary tenant code to execute.

## Overview

The context owns mapping drafts, immutable published revisions, schema references, synthetic fixtures, execution limits, and test results. A visual editor produces the same constrained mapping graph consumed by the runtime. Documents stores the resulting derivative; Trading Partner Management chooses which published revision applies.

## Narrative

An analyst connects source and target fields, adds controlled conversions and lookup tables, and tests the draft against synthetic samples. Standards-backed business code lists provide safe defaults for units, weights, currencies, countries, payment means, tax categories and response outcomes. Each value declares its version-specific UBL 2.1, EDIFACT D.96A and TRADACOMS 9 representations when one exists. A missing representation is a compatibility failure rather than permission to copy a modern code into an older directory. A partner mapping can translate those canonical codes to an explicit partner alias. An unmapped code fails rather than silently producing invalid EDI. Publishing freezes the graph, including every code lookup and its source and target syntax, and its hash. Exchange workers execute that exact revision under bounded time, graph, and output limits. Historical exchanges therefore remain reproducible even after a later mapping revision is published.

Mappings use a stable family identity and immutable revision identities. A tested draft can publish now or at a scheduled instant. Published revisions may be archived but remain addressable for historical evidence; hard deletion is limited to unused drafts and is guarded by dependency checks.

## Global EdiSpark catalogue

The domain also vends immutable global revision-1 mappings for both directions of UBL 2.1 Order and Invoice with EDIFACT D96A and TRADACOMS 9. These static-stability defaults are packaged with the service rather than copied into tenant partitions, so every tenant can select the same stable mapping IDs without a cross-plane runtime dependency.

Each global revision includes a privacy-safe UBL example, a parsed EDI model, and the corresponding raw EDI interchange. The field graphs cover the commercially meaningful header, reference, party, identifier, delivery, payment, allowance, tax, total, line, item, price, and classification fields supported by the initial runtime. Coded fields use explicit lookup nodes in both directions, even where UBL and EDIFACT share the same UN or ISO code, so a fork can safely substitute partner-specific TRADACOMS aliases. A tenant can use a global revision directly in an agreement or fork it into a tenant-owned draft; the fork retains its sample and records the source global revision as lineage.

## Ubiquitous Language

| Term | Definition |
|---|---|
| Business code list | A standards-backed set of canonical values that can be offered for authoring and mapping. |
| Code lookup | An immutable mapping rule that translates one canonical or partner code into another and rejects unmapped values. |
| Partner alias | A trading-partner-specific representation of a canonical business code, retained inside its mapping revision. |
| Representation | The value used for one code in a specific syntax and release, such as UBL 2.1, EDIFACT D.96A or TRADACOMS 9. |

## Order Response target proof and recovery

The replacement D96A core mapping uses aligned repeated nodes for every line and explicitly maps overall response meaning, line decisions, identifier schemes, names and references. Publication requires canonical source validation, complete source coverage and target-contract validation in addition to expected JSON equality. Evidence retains mapping/rule/fixture hashes without copying customer fixture bytes. Editing or forking clears proof; qualification and activation reject stale proof.

Administrators can test against a tenant-authorized retained source identity. The caller-owned retained-source port rejects a changed representation/hash or mismatched document type; its composition adapter uses the provider application boundary. Global and historical mappings remain immutable. Partner-specific certification is separate from the explicitly named core demonstration contract.
