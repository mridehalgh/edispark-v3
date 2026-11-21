# EdiSpark MCP pilot

Connect an MCP client to EdiSpark to investigate exchange failures, test mapping revisions, and check whether a document is ready to send. The pilot uses the [2026-07-28 MCP specification](https://blog.modelcontextprotocol.io/posts/2026-07-28/) over stateless Streamable HTTP. Spring Boot 4.2 and Spring AI 2.1 configure the server and WebMVC transport. EdiSpark supplies the tool definitions, authorization, and deterministic preview handlers.

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
| Scopes | `edi:evidence` and `edi:preview` |
| Grant | Authorization code with S256 PKCE |

Sign in as the local demo owner and activate **EdiSpark Local** before connecting. Use the OAuth discovery challenge from `/mcp` and approve the client in the consent page. The local public client uses PKCE without a client secret. To use another loopback callback on a fresh local workspace, set `EDISPARK_MCP_LOCAL_REDIRECT_URIS` before starting. Existing registered clients retain their callback configuration. Run `pnpm smoke:mcp` from the repository root against a running MCP-enabled local stack. Run `pnpm smoke:mcp:stack` when the local ports are free to start, verify, and stop the stack in one command. CI runs the same command. The suite checks discovery, token isolation, all five tools, malformed and oversized calls, stale pins, scope denial, and immediate consent revocation. It uses the dedicated local OAuth client `edispark-local-mcp-smoke`.

## Tools

Use `tools/list` to obtain the JSON input and output schemas. Results are structured and include safe rule codes and field paths. Open EdiSpark for payload inspection, configuration changes, or delivery.

| Tool | Scope | Use |
|---|---|---|
| `find_exchanges` | `edi:evidence` | Look up an exact exchange ID or inspect a window of up to 31 days with optional partner, direction, status, and exact source message reference. Continue while `nextCursor` is present, even after an empty page. A repeated reference can identify multiple results. |
| `trace_exchange` | `edi:evidence` | Read persisted status transitions and available document, route, mapping, and transfer references. Transport receipt, functional acknowledgement, and business acceptance are separate fields. A missing fact appears as `NOT_RECORDED` or `UNAVAILABLE`. |
| `get_route_context` | `edi:evidence` | Resolve an agreement and capability into the persisted revision, mapping and document profile pins, message type, EDI release, qualification, and acknowledgement policy. |
| `preview_mapping` | `edi:preview` | Check a mapping revision with synthetic input and expected JSON strings, or run a compatible outbound mapping with an authorized retained representation. Retained-source checks return validation findings without an expected-output comparison. |
| `check_outbound_readiness` | `edi:preview` | Check a pinned document against a selected agreement and capability. State changes require a new preview. Delivery runs its own readiness validation. |

To investigate a batch, find the exchange records and trace each message ordinal. Use the exact route and document pins from the trace to reproduce an outbound readiness problem. The mapping check and readiness can be run repeatedly without creating exchanges, transfers, or document derivatives.

## Pilot operation

MCP is disabled by default. To prepare a staging pilot, apply the additive OAuth schema migration through the existing migration workflow and preregister the client with exact redirect URIs and required PKCE. Register the canonical MCP resource and explicitly link the client to that resource. The Better Auth resource, client, and client-resource models hold these registrations. Keep dynamic registration disabled and leave user consent enabled.

Configure `EDISPARK_MCP_ENABLED=true` and the same HTTPS `EDISPARK_MCP_RESOURCE` on the API and web runtimes. Configure `EDISPARK_MCP_ORGANIZATIONS` on the web runtime with the opted-in organization IDs. Both runtimes need the same dedicated `EDISPARK_MCP_ACCESS_SECRET` through their secret provider. The API derives its default OAuth issuer from `AUTH_ISSUER` with the `/api/auth` suffix. Set `EDISPARK_MCP_ISSUER` only when overriding that default.

Verify discovery and sign-in on staging with the chosen client before enabling customer access. Review `edispark.mcp.tool` timings and outcomes, and correlate denials and tool calls through their audit correlation IDs. Disable the endpoint to roll back the pilot. Removing a tenant opt-in, membership, client link, or consent blocks subsequent access checks. Access tokens expire after five minutes.
