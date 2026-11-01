# Transformations

## Vision

Transformations provides auditable, deterministic conversion between canonical UBL and partner-specific EDI representations without allowing arbitrary tenant code to execute.

## Overview

The context owns mapping drafts, immutable published revisions, schema references, synthetic fixtures, execution limits, and test results. A visual editor produces the same constrained mapping graph consumed by the runtime. Documents stores the resulting derivative; Trading Partner Management chooses which published revision applies.

## Narrative

An analyst connects source and target fields, adds controlled conversions and lookup tables, and tests the draft against synthetic samples. Publishing freezes the graph and its hash. Exchange workers execute that exact revision under bounded time, graph, and output limits. Historical exchanges therefore remain reproducible even after a later mapping revision is published.
