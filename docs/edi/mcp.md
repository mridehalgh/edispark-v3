# EdiSpark MCP pilot

Connect an MCP client to EdiSpark to find business documents, trace their workflow, reconcile retained records, and investigate AS2 transfers. The pilot uses the [2026-07-28 MCP specification](https://blog.modelcontextprotocol.io/posts/2026-07-28/) over stateless Streamable HTTP. Spring Boot 4.2 and Spring AI 2.1 configure the server and WebMVC transport. EdiSpark supplies the tool definitions, authorization, and deterministic preview handlers.

Open **AI** in the signed-in workspace for the endpoint, client-specific instructions, example prompts, and tool argument reference. The page is available to organisation admins and owners.

## Local setup

Start the normal demo workspace with MCP enabled.

```sh
EDISPARK_MCP_ENABLED=true pnpm dev
```

| Client setting | Local value |
|---|---|
| MCP server | `http://localhost:8080/mcp` |
| OAuth client ID | `edispark-local-mcp` |
| Redirect URI | `http://127.0.0.1:9876/callback` |
| Resource indicator | The exact MCP server URL |
| Scopes | `edi:evidence` for document evidence, `edi:preview` for previews and the smart-copy handoff, `mft:evidence` for AS2 evidence, and optional `edi:commercial` for reconciliation values |
| Grant | Authorization code with S256 PKCE |

Sign in as the local demo owner and activate **EdiSpark Local** before connecting. Use the OAuth discovery challenge from `/mcp` and approve the client in the consent page. The local public client uses PKCE without a client secret. To use another loopback callback on a fresh local workspace, set `EDISPARK_MCP_LOCAL_REDIRECT_URIS` before starting. Existing registered clients retain their callback configuration. Run `pnpm smoke:mcp` from the repository root against a running MCP-enabled local stack. Run `pnpm smoke:mcp:stack` when the local ports are free to start, verify, and stop the stack in one command. CI runs the same command. The suite checks discovery, token isolation, the documented tool catalogue and Order Response smart-copy parity, malformed and oversized calls, stale pins, scope denial, and immediate consent revocation. It uses the dedicated local OAuth client `edispark-local-mcp-smoke`.

## Tools

Use `tools/list` to obtain the JSON input and output schemas. Results are structured and include safe rule codes and field paths. Open EdiSpark for payload inspection, configuration changes, or delivery.

| Tool | Scope | Use |
|---|---|---|
| `find_exchanges` | `edi:evidence` | Look up an exact exchange ID, use the document set and document IDs, or inspect a window of up to 31 days with optional partner, direction, status, and exact source message reference. Continue while `nextCursor` is present, even after an empty page. A repeated reference can identify multiple results. |
| `trace_exchange` | `edi:evidence` | Read persisted status transitions and available document, route, mapping, and transfer references. Transport receipt, functional acknowledgement, and business acceptance are separate fields. A missing fact appears as `NOT_RECORDED` or `UNAVAILABLE`. |
| `get_route_context` | `edi:evidence` | Resolve an agreement and capability into the persisted revision, mapping and document profile pins, message type, EDI release, qualification, and acknowledgement policy. |
| `preview_mapping` | `edi:preview` | Check a mapping revision with synthetic input and expected JSON strings, or run a compatible outbound mapping with an authorized retained representation. Retained-source checks return validation findings without an expected-output comparison. |
| `check_outbound_readiness` | `edi:preview` | Check a pinned document against a selected agreement and capability. State changes require a new preview. Delivery runs its own readiness validation. |
| `find_documents`, `get_document_context`, `get_document_workflow` | `edi:evidence` | Find an exact business reference, open its retained representation, and follow recorded document relationships in both directions. |
| `list_eligible_routes` | `edi:evidence` | Find qualified routes and revision pins to use with the readiness preview. |
| `prepare_related_document_draft`, `prepare_document_correction` | `edi:preview` | Open the existing **Create related → Order Response** smart-copy flow or the correction form with the selected source pinned. The author reviews copied values and saves in Documents. |
| `compare_order_to_response`, `reconcile_despatch_and_receipt`, `reconcile_invoice_and_remittance` | `edi:evidence` | Compare exact retained UBL pins. Return finding codes and line positions. Add `edi:commercial` for commercial values. |
| `find_transfers`, `trace_transfer`, `find_mdn_deliveries` | `mft:evidence` | Find AS2 transfers and inspect retry, receipt, and attempt evidence, including asynchronous MDN delivery for inbound files. |
| `find_connections`, `find_certificates` | `mft:evidence` | Inspect AS2 connection revisions, receipt and security configuration, and certificate fingerprints and validity. |
| `find_document_exceptions`, `trace_interchange` | `edi:evidence` | Find document-linked review, failure, or receipt wait states and trace the messages linked to a transfer. |
| `get_mapping_revision`, `get_document_profile`, `list_business_codes` | `edi:evidence` | Inspect mapping compatibility, partner field requirements, and the standard business code catalogue. |
| `validate_document_preview`, `compare_mapping_revisions`, `preview_route_qualification`, `preview_inbound_interchange` | `edi:preview` | Validate canonical UBL, compare mapping nodes, evaluate persisted route qualification, and inspect synthetic inbound messages without changing domain state. |
| `get_connection_setup`, `get_certificate_usage`, `compare_connection_revisions` | `mft:evidence` | Inspect the AS2 onboarding settings, certificate usage, and changes between revisions. |
| `check_transfer_readiness`, `preview_certificate_rollover` | `mft:evidence` | Evaluate persisted connection configuration and proposed certificate pairs without sending a transfer. |

Document-reference and reverse-lineage searches currently read bounded tenant pages. Keep following `nextCursor` until it is absent, including after empty pages. The server matches references in the retained canonical UBL representation and returns metadata with authenticated workspace paths. Reconciliation requires explicit document and line references and reports gaps where matching evidence is incomplete.

To investigate a batch, find the exchange records and trace each message ordinal. Use the exact route and document pins from the trace to reproduce an outbound readiness problem. The mapping check and readiness can be run repeatedly without creating exchanges, transfers, or document derivatives.

## Pilot operation

MCP is disabled by default. To prepare a staging pilot, apply the additive OAuth schema migration through the existing migration workflow and preregister the client with exact redirect URIs and required PKCE. Register the canonical MCP resource and explicitly link the client to that resource. The Better Auth resource, client, and client-resource models hold these registrations. Keep dynamic registration disabled and leave user consent enabled.

Configure `EDISPARK_MCP_ENABLED=true` and the same HTTPS `EDISPARK_MCP_RESOURCE` on the API and web runtimes. Configure `EDISPARK_MCP_ORGANIZATIONS` on the web runtime with the opted-in organization IDs. Both runtimes need the same dedicated `EDISPARK_MCP_ACCESS_SECRET` through their secret provider. The API derives its default OAuth issuer from `AUTH_ISSUER` with the `/api/auth` suffix. Set `EDISPARK_MCP_ISSUER` only when overriding that default.

Verify discovery and sign-in on staging with the chosen client before enabling customer access. Review `edispark.mcp.tool` timings and outcomes, and correlate denials and tool calls through their audit correlation IDs. Disable the endpoint to roll back the pilot. Removing a tenant opt-in, membership, client link, or consent blocks subsequent access checks. Access tokens expire after five minutes.
