# Generate from a MIG

Organisation owners and admins start in **Document rules → Generate from a MIG**. Select an exact exchange direction, document type, standard and release. Choose a compatible catalogue profile when the partner uses a dedicated encoder. The selected encoder pin carries through preview and the imported draft. Upload the primary guide with its edition, up to three labelled supplements, and optional test files. For an inbound job, the input is raw EDI and the expected file is UBL JSON. An outbound job uses UBL JSON and can compare its result against expected raw EDI.

The workbench quotes the maximum before dispatch. It shows credits estimated, held and settled, plus each candidate’s checks. The two candidates receive the same pinned guide and compatible catalogue evidence. Each candidate can use one bounded round of Spring AI tools to inspect an excerpt, find fields, inspect a bundled example or preview rules. Uploaded test files stay in the deterministic validation path. Compare decisions against the cited excerpts and record a reason for disagreements. Confirm the prevailing citation when editions conflict. Revalidation checks the selected operations against the production mapping runtime, EDI adapter, UBL schema, and supported partner rules. Explicit draft creation retains the evidence pack and leaves the new revision editable.

A structural preview checks the document and mapping pipeline. The MIG is the source for partner requirements. The default output attachment is a reference and can contain a partial example. EdiSpark checks it separately with the UBL 2.1 schema or selected EDI validator and shows any issues as advisory. With no uploaded input, EdiSpark uses a compatible catalogue input where available. If there is no compatible catalogue example, the reference check still runs and the mapping preview needs an uploaded input. Choose an exact comparison when you have both a complete expected output and the input that produced it. That comparison checks semantic JSON equality or EDI segment equality. EDI comparison preserves business values, segment order and trailer counts. It masks the generated interchange timestamps and references. Generated repeat and optional-field cases exercise the runtime separately. The workbench reports unreadable sections, runtime release and field gaps, unsupported predicates, unresolved bindings, and source fields that the graph leaves populated and unused.

Use **Rerun with same files** on an existing job to prepare a new estimate from the retained guide, supplements, fixtures, exchange format, partner and catalogue selection. The estimate uses the current model runner and refreshes pinned evidence. It links back to the original job and starts with a new review. Reserving credits remains a separate action. **Retry** continues an incomplete job with its completed candidate. Reruns share the original upload retention window.

## Compare the guide with the standard

For EDIFACT D96A ORDERS, the estimate compares explicit guide table rows with the pinned UNECE segment table. It shows the guide's own position and citation beside the directory position, group path, usage and maximum repeats. A partner may require a conditional segment or narrow a repeat limit. An expanded limit is flagged as a conflict. Repeated tags with unclear scope are flagged for review. The comparison is evidence for the analyst and both candidates. Mapping and fixture validation still determine draft readiness.

For plain text, use a message-area heading such as `Heading:`, `Detail:` or `Summary:`, then write each declaration as `0040 | DTM | Order issue date | M | 1`. Use a group row such as `0100 | Segment Group 1 | C | 10` before its children. Prefix a row with `STD` only when the guide explicitly identifies the UNECE position, for example `STD 0030 | DTM | M | 1`. A column headed `UNECE Position` or `Directory Position` also declares official positions. The same row format can be read from positioned PDF text, Word tables, Excel cells and recovered OCR tables. Keep the source anchors so each row can be checked against the original.

The directory comparison currently covers segment and group usage and repeat limits. Qualifiers, field types, conditions and requirements in prose remain part of the cited mapping review. The comparison only evaluates extracted declarations. Guide omissions do not establish an exclusion.

## Intake and retention

Guide uploads are bounded to 3 MiB each and fixtures to 1 MiB each. Office extraction reads table rows and cells without executing macros or formulas. Positioned PDF text and page-bounded OCR carry their own anchors. The evidence manifest records guide digests, schema and runtime fingerprints, compatible catalogue revisions and applicable published partner profiles. The structure report records the exact directory table digest. Tenant uploads and artifacts use tenant and resource-bound authenticated encryption. Unimported payloads expire after 30 days and DynamoDB removes expired items asynchronously. Imported evidence and decisions remain associated with the draft identity. Ledger receipts contain invocation metadata and stay available for billing reconciliation.

The archive under `vendor/partners/migs` is an engineering reference. The workflow has no route to that directory. Tests and demo guides use synthetic identifiers and small, representative excerpts.

## Local benchmark

| Case | Fixture or check | Expected result |
| --- | --- | --- |
| Tesco-style EDIFACT purchase order | Synthetic EAN008 input and held-out UBL in `MigPreviewTest`, plus the outbound D96A guide and fixtures under `mig-demo` | The inbound and outbound checks preserve line identities, quantities, units and GTINs. The reviewed graph matches the held-out output. |
| Sainsbury’s legacy TRADACOMS invoice | Synthetic HSSF invoice worksheet in `MigIntakeTest` | Invoice table row and cell coordinates survive extraction. |
| B&Q TRADACOMS acknowledgement | Synthetic acknowledgement table in `MigIntakeTest` and the existing B&Q writer and mapping tests | Word table citations survive. The deployed B&Q ACK v1.4 encoder and validator own outbound acceptance. |
| D96A ORDERS directory differences | Synthetic guide table with locally numbered DTM, NAD and QTY rows, plus `MigStructureComparisonTest` | The estimate shows narrowed limits beside their UNECE occurrences and flags expanded limits and unresolved scope. |
| Scanned table | Blank PDF and deterministic OCR response in `MigIntakeTest` | A page is a blocking gap until OCR supplies a cited row. |
| Conflicting guide editions | Conflicting BGM excerpts in `MigReviewTest` | Review requires an explicit prevailing citation. |
| Aldi D01B | Unsupported exact coordinates in `MigReviewTest` | Requirements stay cited and the unsupported release blocks draft readiness. |

## Local Bedrock

The default local runner uses fixture declarations for deterministic development and CI. To process a real guide locally, configure `migBedrock` in the ignored `.local/settings.json` with an active AWS SSO profile, a published London guardrail and the verified rates for direct London inference. The local launcher activates the `mig-bedrock` Spring profile when those settings are present. Local files and job records stay in the local workspace. The local runner sends Bedrock and Textract requests through the London endpoints. The European Sonnet 5.5 profile can route inference across its listed European regions. Local Nova uses a single call with the pinned evidence to avoid throttling during a tool follow-up.

The settings fields are `awsProfile`, `guardrailId`, `guardrailVersion`, `sonnetInputMicros`, `sonnetOutputMicros`, `novaInputMicros` and `novaOutputMicros`. Rates are whole US dollar millionths per million tokens. A fresh estimate pins the runner and model rates. Start a new job when switching from the fixture runner to Bedrock. Set `SPRING_PROFILES_ACTIVE=local` for a fixture-only run with the settings still in place.

Set `TYPESAFE_API_KEY` in the environment when starting the local workbench to compare Claude with Jev. Jev receives selected cited guide excerpts and a bounded list of mapping operations from the pinned catalogue. It selects from those operations with typed decisions. Low-confidence, unsupported and out-of-batch requirements appear as gaps. Claude and Jev run independently and use the same compiler, fixture preview and review. The cards report compiled rules, cited requirements and settled credits per candidate. Jev's model defaults to `jev-1.14.0`. It is billed for confirmed input tokens.

Claude Sonnet 4.6 remains the default direct London model. Set `EDISPARK_MIG_CLAUDE_MODEL=eu.anthropic.claude-sonnet-5-5` for the European Sonnet 5.5 profile in the local runner. It is invoked through the London endpoint at $2.20 input and $11 output per million tokens. For direct London Opus 5.5, use `EDISPARK_MIG_CLAUDE_MODEL=anthropic.claude-opus-5-5` after the AWS account has model access. Start a fresh estimate, or rerun from an existing job, when changing the candidate models.

## London deployment

Production requires direct London access to Claude Sonnet 4.6 and Amazon Nova Lite, an enabled published guardrail, disabled Bedrock invocation payload logging, and London Textract. Verify these in the deployment account using the workload identity. Run both direct model IDs against the synthetic benchmark before setting `mig_london_pilot_verified`. Set the verified per-model rates in `mig_london_rates`. Once London inference is configured and verified, every organisation owner and admin can use the workbench. Guardrail processing and OCR are service overhead, while organisation credits cover confirmed model token usage.

The worker records each invocation before dispatch. Its response, usage and release of the unused hold are checkpointed with a conditional DynamoDB transaction. A known failure leaves just that branch eligible for retry. An invocation with unconfirmed usage remains held for reconciliation. Platform operators may submit confirmed token counts and an external receipt reference through `POST /api/platform/mig/{jobId}/invocations/{invocationId}/reconcile` while the job checkpoint is retained. Submit counts for the unresolved model call. Known turn receipts are included automatically. An explicitly confirmed nonbillable unresolved call uses zero counts. A repeated receipt is idempotent.

MCP uses the separately consented `edi:mig:read` scope. Grant checks are repeated on each request. The scope adds job status, bounded excerpts, discrepancies and recorded preview results. Upload, reserve, retry and draft creation use the authenticated workbench.

For the pilot, compare time to reviewed draft and analyst edits with comparable manually authored mappings. The service records job outcomes, disagreements, rule gaps, validation outcomes, retries and settled model usage through the `edispark.mig` metrics.
