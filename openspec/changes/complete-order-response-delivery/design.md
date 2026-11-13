# Design and delivery plan

## Outcome

The corrected version of the reported eight-line Order Response passes document validation and exact-route readiness, preserves every required business value in valid partner EDI, and reaches the local AS2 simulator with verified receipt evidence. Operations users can understand the result through Documents and its chronological activity.

Original reference: set `d2bee046-e045-31f1-905c-9afca09768da`, document `af18da40-15ce-4a28-8ee9-211229dd2566`. The original remains immutable; qualification and delivery use a linked corrected document selected during implementation.

## 1. Establish the exact contract and findings

Capture the tenant-scoped source representation ID/hash, document version, intended partner, direction, message type, standard/release, agreement revision, mapping revision, document profile and connection. Recompute readiness against that snapshot and reconcile every populated leaf in the current findings.

Group repeated occurrences into business field families while retaining their concrete paths and line identities:

| Family | Review required |
|---|---|
| Response semantics | Header response code and each line's status; agreed target meanings and code translations |
| Parties | Endpoint values and schemes, trading/legal names and applicable target qualifiers |
| Lines | All eight IDs, references, quantities, units, product names and item identifiers |
| Commercial/delivery context | Currency and destination data where supported or required by the selected message/profile |
| Representation metadata | UBL version/namespaces and any metadata with no target-document meaning |

For each field record its source pattern, populated occurrences, business meaning, target element/qualifier, transformation, rule source and disposition. Reuse the broader proposal's coverage vocabulary. Any intentional omission requires a narrow, reviewed rationale; business values must not be omitted merely to make a test green. Unsupported required semantics remain blockers. Technical metadata may be classified as not applicable only when the source contract and target standard justify it.

Completion evidence: every finding is accounted for, required target fields are identified, and no decision depends on assuming that similarly named fields or codes are equivalent.

## 2. Implement target support and authoritative validation

Standards-specific models, writers and validators belong in `standards/edifact` and `standards/tradacoms`. Transformations owns source-to-target mapping and coverage. Exchange orchestrates validation before transport through existing in-process adapters. Integration should compose these components rather than add another message-specific segment builder.

Implement the Order Response slice incrementally. Validate the typed target model and final encoded bytes against the exact release and partner profile. Checks must cover message/segment order and cardinality, required and conditional elements, identifier schemes/qualifiers, response/status and unit code lists, numeric values/precision, date formats, character repertoire/escaping, lengths, references, and message/interchange controls. Add profile-specific constraints separately from base-standard rules, with versioned rule provenance.

Audit the current encoder's accepted-field list against actual emission for Order Response. A field accepted into the intermediate JSON must either appear correctly in the final bytes or have an approved disposition. In particular, fixed header function values must not mask different source response meanings, and line status must be represented according to its actual target semantics.

Do not fabricate absent business values, reduce line cardinality, silently truncate/replace characters, or broaden loss policy to work around gaps. An unavailable validator or unsupported required field blocks readiness with an actionable code.

Use independently reviewed expected EDI and negative fixtures derived from authoritative definitions. The current application writer and reader must not be the only witnesses to one another's correctness. Parsing the application's own output is useful regression evidence, but is insufficient conformance proof.

## 3. Publish and qualify replacements

Create new immutable mapping revision IDs in the appropriate families. Include repeated line mappings and all reviewed field decisions. Keep existing revision IDs, hashes, fixtures and historical exchanges intact.

Publication fixtures cover the actual eight-line source, minimal and richer valid variants, response/status variants, optional fields, alternate supported identifiers/units, and repeated-line boundaries. Coverage must include required target fields as well as populated source fields. Unknown additions and unsupported values must fail with precise locations.

Extend route qualification where necessary: the current `QualificationAcl` checks only published schema compatibility and an active connection. A qualified route should also have evidence for its mapping/profile/target-rule versions and representative validation fixtures. Persist exact version/hash references so changed dependencies invalidate qualification or force requalification.

Create and qualify an agreement draft pinned to the replacement mapping and applicable profile. Activate only the intended local demo revision once its evidence passes. Do not retarget unrelated or customer-created agreements. Every send still rechecks the current document and active configuration; qualification is not a substitute for runtime readiness.

## 4. Make recovery practical

Group repeated findings by business field, showing affected line numbers and occurrence counts. Keep technical JSON pointers, qualifiers and rule citations available on demand. Preserve the distinction between document corrections, mapping changes and route/profile configuration.

Provide an authorized path from a readiness finding to a draft mapping revision tested against the exact retained source version. Administrators should not have to reconstruct the source fixture manually. Keep customer input tenant-scoped and out of global samples, logs and warning metadata.

Operations users retain access to correction, schema finalization, readiness and delivery. Mapping/profile publication and agreement changes remain administrative actions. If contracts change, implement backend DTOs first, export OpenAPI, generate and type-check SDKs, then wire authenticated BFFs and UI.

## 5. Prove delivery and retained evidence

Send only to the local AS2 simulator during implementation verification. Verify:

- The exact corrected source and qualified mapping/profile/rule versions are used.
- Simulator payload bytes match the retained generated artifact hash.
- All eight line identities and values, party identities, references and response semantics match the ledger; a count-only assertion is insufficient.
- The configured MDN signature/MIC/disposition checks run through MFT receipt verification, and Exchange reaches the corresponding delivery state.
- A transport receipt is labelled as transport evidence. Business acceptance is claimed only if separately required and recorded by the configured workflow.
- Document lineage connects the original, correction, generated file, exchange, transfer and receipt.
- Activity shows ordered, numbered validation, conversion, submission and receipt events without invented timestamps.

Exercise failed validation, unsupported source additions, wrong/stale route, missing/wrong mapping/profile/rules, negative or invalid receipts, timeout/retry and idempotent replay. Reusing an attempt must not create another transfer; a corrected document/new attempt must retain distinct provenance. One failing exchange must not prevent a neighbouring valid exchange from progressing.

## Local fixtures and rollout

Add stable fixtures for the valid eight-line delivery, a blocked unsupported-field case and a receipt-failure/retry case. Reconcile creation and missing fixtures without overwriting user edits or duplicating business fixtures. Preserve historical failed records and update `docs/local-demo-data.md` with exact owner, keys and expected states.

After this milestone, extend independently sourced rule packs and complete mappings across the other five document types under `add-complete-six-document-edi-support`. Keep support claims specific to the message, release and profile actually validated.

## Completion gates

1. Every recorded source finding has a justified disposition; every required target field has a valid representation.
2. The selected corrected document passes UBL, target-standard and partner-profile validation, with no unresolved blocking findings.
3. Replacement mapping and agreement revisions are published/qualified with immutable provenance and passing fixtures.
4. The simulator receives the complete eight-line document and MFT verifies the configured receipt; retained hashes and business values agree.
5. Negative, freshness, idempotency and authorization checks pass, along with relevant module tests, generated contract/SDK checks, web checks and the authenticated desktop/mobile journey.

Definitions or partner guides that cannot be sourced are explicit dependencies, not implicit waivers. Their route-specific conformance gates remain open until the evidence exists.
