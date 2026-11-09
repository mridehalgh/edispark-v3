# Outbound validation and recovery

Documents owns schema validation. JSON/JSON Schema validation runs offline with the complete vendored OASIS UBL 2.1 graph, resolving recursive definitions lazily. Unsupported formats and external schema references fail closed. The legacy no-op validator remains available to isolated tests, outside production wiring.

Exchange owns route readiness through caller-owned module adapters. A read-only check binds its findings to the current representation ID/hash, agreement revision, capability, mapping revision and optional published profile. Checks cover lifecycle, document type, retained partner/connection, canonical schema, profile fields and mapping/target execution. Every submission repeats the check; an earlier passing report is never an authorization token. Source and active agreement identity are checked again before transport. Failed checks retain a JSON validation report and end in NEEDS_REVIEW without generated EDI or a transfer.

Mapping rules support one aligned repeated source and target collection using `*`, optional copies/code translations and explicit omissions with required reasons. Coverage tracks actual consumed values, so a coalesce fallback is not falsely credited as represented. Publication requires passing output fixtures without uncovered populated fields. Existing published definitions remain immutable; administrators create revisions and test against representative input before updating a route.

The constrained EDI encoder checks supported release/message combinations, required target values and dates, rejects unsupported target fields, and refuses silent character replacement or truncation. TRADACOMS message counts are calculated from the actual escaped segment stream. These are structural checks for this encoder, not certification against every partner implementation guide; unsupported semantics remain blocked for configuration review.

Corrections create a new draft/set with explicit partner metadata and a CORRECTS link. Unexposed fields are merged from retained source content. The known legacy OrderResponse ResponseCode/Description authoring mistake is repaired in the new draft only. No historical source or exchange is rewritten. The authoring form now emits OrderResponseCode and Note.

Members can author, correct, finalize, check readiness and submit business documents. The API permits only the corresponding business endpoints and read-only partner/profile selectors; mapping publication and partner changes remain admin-only. `DocumentWorkflowAuthorizationTest` exercises the real security filter chain, including unauthenticated, missing-tenant and non-member denial.

## Verification

The targeted Java regression suite covers 64 passing tests across schema validation, correction merging, route readiness, repeat-aware mappings, publication coverage, both encoders, local fixture repair and authorization. All seven supported seed document types pass the full offline schema graph. The web suite has 83 passing tests; generated SDK type checks, OpenAPI validation, web type checking and the production build pass.

Authenticated Chrome verification against the reported document confirms schema and retained-partner findings, read-only readiness, and a direct-send stop at VALIDATING without a generated payload or transport. A linked correction copies all eight lines, passes schema finalization and resolves the selected partner; the retained mapping's uncovered fields remain visible and block sending. A related invoice retains its own OUTBOUND partner metadata and a cross-set INVOICES link. Desktop and mobile readiness panels fit their viewport.
