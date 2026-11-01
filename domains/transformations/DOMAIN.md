# Transformations

## Vision

Transformations provides auditable, deterministic conversion between canonical UBL and partner-specific EDI representations without allowing arbitrary tenant code to execute.

## Overview

The context owns mapping drafts, immutable published revisions, schema references, synthetic fixtures, execution limits, and test results. A visual editor produces the same constrained mapping graph consumed by the runtime. Documents stores the resulting derivative; Trading Partner Management chooses which published revision applies.

## Narrative

An analyst connects source and target fields, adds controlled conversions and lookup tables, and tests the draft against synthetic samples. Publishing freezes the graph and its hash. Exchange workers execute that exact revision under bounded time, graph, and output limits. Historical exchanges therefore remain reproducible even after a later mapping revision is published.

Mappings use a stable family identity and immutable revision identities. A tested draft can publish now or at a scheduled instant. Published revisions may be archived but remain addressable for historical evidence; hard deletion is limited to unused drafts and is guarded by dependency checks.

## Global EdiSpark catalogue

The domain also vends immutable global revision-1 mappings for both directions of UBL 2.1 Order and Invoice with EDIFACT D96A and TRADACOMS 9. These static-stability defaults are packaged with the service rather than copied into tenant partitions, so every tenant can select the same stable mapping IDs without a cross-plane runtime dependency.

Each global revision includes a privacy-safe UBL example, a parsed EDI model, and the corresponding raw EDI interchange. The field graphs cover the commercially meaningful header, reference, party, identifier, delivery, payment, allowance, tax, total, line, item, price, and classification fields supported by the initial runtime. A tenant can use a global revision directly in an agreement or fork it into a tenant-owned draft; the fork retains its sample and records the source global revision as lineage.
