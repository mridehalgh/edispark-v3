---
name: add-ubl-document-view
description: Add or extend EdiSpark business-facing UI views for OASIS UBL documents while preserving the separate technical Files workspace. Use when implementing Documents views, routes, adapters, lists, formatters, schema checks, or tests for UBL Order, Invoice, Credit Note, Despatch Advice/ASN, Receipt Advice, Order Response, or another UBL document type.
---

# Add a UBL Document View

Build business views from the authoritative UBL contract, not from a single sample. Keep raw protocol and storage mechanics in Files while presenting recognizable operational documents in Documents.

## Required reading

1. Read `ui/PRODUCT.md` and `ui/DESIGN.md`.
2. Read [references/repository-map.md](references/repository-map.md).
3. Read [references/ubl-mapping-guide.md](references/ubl-mapping-guide.md).
4. Inspect the target document's authoritative files:
   - `ubl-source/json-schema/maindoc/UBL-<Type>-2.1.json`
   - Referenced definitions in `ubl-source/json-schema/common/`
   - Generated classes under `ubl-model/target/generated-sources/java/` after Maven generation
   - Relevant `ubl-model/src/test/` coverage and `UblJsonMapper`
5. Inspect the current Documents and Files implementations before editing. Preserve unrelated dirty-tree work.

## Non-negotiable boundaries

- Treat `ubl-source` as the formal JSON contract and `ubl-model` as the repository's generated Java interpretation.
- Use samples only as test fixtures and visual acceptance cases.
- Keep `/documents` business-facing and `/file` technical. Always provide traceability from a business document to its File.
- Keep backend domain/API names as Documents unless the backend contract genuinely changes.
- Reuse the generated Documents client through the authenticated BFF. Never call the backend directly from browser code.
- If an endpoint or DTO must change, also use the `api-contract-sdk` skill and update backend, OpenAPI, generated client, BFF, and UI in that order.
- Do not bundle the complete UBL schema graph into browser JavaScript.
- Never silently guess the meaning of an unknown UBL field. Omit it from the business projection and leave it accessible through Files.

## Workflow

### 1. Establish the document contract

- Find the main document definition and list its required fields.
- Trace every displayed aggregate into Common Aggregate Components.
- Trace scalar values and attributes into Common Basic Components, Unqualified Data Types, and CCTS.
- Confirm cardinality: UBL JSON commonly wraps even singleton fields in arrays.
- Confirm the generated model exposes the same property names, types, required annotations, and date/time behavior.
- Decide the focused business projection. Prefer fields an operations user recognizes; preserve uncommon details through Files.

Write a small mapping table while working:

| UI field | UBL path | Cardinality | Required | View-model type |
|---|---|---:|---:|---|
| Reference | `<Root>[0].ID[0]._` | 1 | Yes | `string` |

Do not commit a separate mapping document unless it provides lasting value; encode stable mappings in the adapter and contract check.

### 2. Create a focused adapter

- Add one adapter module per document type, following `ui/src/lib/ubl-order.ts`.
- Accept `unknown`, narrow records and arrays defensively, and return a discriminated success/failure result.
- Validate the document namespace and required root structure.
- Preserve `_` scalar values and meaningful attributes such as `currencyID`, `unitCode`, `schemeID`, and language/list metadata when the UI needs them.
- Convert raw UBL into a small immutable view model. Never pass the raw schema object into React components.
- Treat optional fields as optional and malformed required structures as an unavailable business view, not a render crash.
- Support repeated structures instead of assuming the first sample's counts.

### 3. Resolve a usable representation

- Use document-set summaries for list pages; do not download content for every row.
- On detail, select the requested document by both set ID and document ID.
- Consider only representations that can contain the target UBL format.
- For JSON views, try a current JSON source first, then JSON derivatives newest-first.
- Parse candidates until one validates as the requested UBL document type.
- Abort stale requests and show safe loading, unavailable, malformed, and no-representation states.
- Link unsupported cases to `/file/:setId`.

### 4. Design the business document

- Model the familiar real-world artefact: invoice, credit note, ASN, and so on—not a generic JSON viewer.
- Lead with reference, parties, dates, operational state, and primary amount or quantity.
- Use semantic sections, definition lists, and real tables. On narrow screens, replace wide tables with stacked rows that preserve essential fields.
- Format dates, times, quantities, and currencies with `Intl`; keep source codes visible when no safe business label exists.
- Use the EDI Spark design system: restrained indigo, mint only for meaningful actions/success, compact radii, ruled composition, Source Sans 3, and no decorative cards.
- Meet WCAG 2.2 AA, keyboard operation, visible focus, semantic headings, and colour-plus-text status communication.
- Keep raw payloads, hashes, schema IDs, versions, derivatives, and parsing internals in Files.

### 5. Add contract safeguards

- Extend or create a deterministic script like `ui/scripts/check-ubl-order-contract.mjs`.
- Read schemas directly from `ubl-source`.
- Assert each mapped property still exists with expected required status, type, and cardinality.
- Assert required aggregate and scalar conventions used by the adapter.
- Fail with a field-specific message when the source contract drifts.
- Add the check to the UI test command.

### 6. Test at three levels

- Adapter tests:
  - Minimum schema-valid document using generic placeholders.
  - Official OASIS example from `ubl-source/json`.
  - Application seed only as an integration fixture.
  - Missing required fields, empty required arrays, invalid JSON, wrong namespace, wrong document type, repeated structures, decimals, multiple currencies/units, and omitted optional data.
- UI behavior:
  - List flattening and identifiers.
  - Direct source and derivative resolution.
  - Loading, failure, unsupported, and File fallback states.
  - Responsive tables/cards and keyboard/accessibility behavior.
- Repository verification:
  - `pnpm --filter ui test`
  - `pnpm --filter ui exec tsc --noEmit`
  - `pnpm --filter ui build`
  - `./mvnw -B -pl ubl-model test`
  - `git diff --check`

Treat pre-existing warnings separately from new failures and report them clearly.

## Completion checklist

- The view is derived from schemas and generated models, not a fixture.
- Required fields and repeated structures are handled correctly.
- The list avoids content-fetch waterfalls.
- Business users see a recognizable document.
- Technical users retain complete File traceability.
- Contract drift fails deterministically.
- Official OASIS and application fixtures pass.
- Typecheck, build, UBL model tests, accessibility review, and visual QA pass or have an explicit documented limitation.

