# EdiSpark product strategy

> **Resolve EDI failures, onboard trading partners, and build integrations faster.** Start with a failed exchange, a partner guide, or a sample file. AI helps trace the issue or draft the mapping. EdiSpark tests it against the exact document, mapping revision, and partner rules, giving your team the evidence to act.

## Where we start

Operations teams often have to reconstruct a failure from several systems before they know whether to fix a document, change a mapping, or investigate delivery. Lead with exception resolution for teams exchanging EDIFACT D96A and TRADACOMS 9 through EdiSpark’s canonical UBL model. Bring integration analysts and platform developers into the same workflow as the product expands.

Mapping is the common link. The same versioned mapping and validation evidence can explain a rejection, qualify a partner route, and prove that an ERP integration preserves the required fields. AI-assisted mapping is already available in the market. Compete on carrying evidence from the first sample through qualification and live delivery. [AWS B2B Data Interchange](https://docs.aws.amazon.com/b2bi/latest/userguide/generative-ai-assisted-mapping.html)

| Workflow | What the customer gets |
|---|---|
| Resolve exceptions | A trace from business document to mapping, validation, delivery, and acknowledgement, with missing evidence and the next step made explicit |
| Build and fix mappings | Proposed changes in EdiSpark’s mapping format with field-level sources, unresolved assumptions, and tests for repeated lines, code lists, totals, and required segments |
| Onboard partners | A versioned qualification plan covering supported documents, guides, route configuration, mappings, fixtures, and acknowledgement expectations |
| Build integrations | Canonical UBL examples, generated SDK usage, idempotency and correlation guidance, and fixtures for delivery, retries, and acknowledgements |

## Delivery sequence

1. **Investigate and preview.** Give approved assistants evidence tools for exchange investigation and deterministic mapping and outbound previews. Keep production actions in EdiSpark.
2. **Draft and review.** Generate mapping proposals and regression fixtures from partner guides, schemas, and examples. Attach exact revisions, source evidence, and unresolved assumptions to the review.
3. **Qualify and hand off.** Assemble the mapping and validation evidence into repeatable onboarding and integration workflows. Revalidate the approved version at execution time.

Approved mapping versions and deterministic rules govern the transaction path. A transport receipt proves delivery at that layer. Functional acknowledgement and business acceptance require their own evidence. Customer assistants connect through MCP while application integrations continue to use the public APIs and generated SDKs.

## Document operations and managed file transfer

Lead with **find the PO → follow the document chain → explain the next action**. Buyers and suppliers can see order responses and despatches, receiving teams can identify short or rejected quantities, and finance teams can compare invoice amounts with recorded remittance allocations. The managed file transfer path starts from a transfer ID and explains attempts, retries, and what the AS2 receipt establishes.

Start Order Response authoring through the existing smart-copy flow. It carries source references and provenance into the document form and leaves commercial review and save with the author. Expand to reviewed document, mapping, partner, and MFT proposals after durable review and execution controls are in place.

## Pilot scorecard

Baseline median time to verified diagnosis, first passing mapping, partner qualification, and first successful integration exchange. Compare like-for-like workflows before and during the pilot. Review unsupported diagnoses and findings missed by previews alongside speed. Expand access once pilot evidence shows that operators can verify the results and use them to make decisions.
