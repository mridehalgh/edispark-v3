# Web product architecture

The web application presents business tasks rather than bounded contexts. Domain ownership remains unchanged behind generated clients and authenticated BFF routes.

## Product surfaces

| Surface | Audience | Domain sources |
|---|---|---|
| Documents | Organization members, admins, owners | Documents; Exchange status |
| Connections | Organization admins and owners | Partnerships; MFT; Exchange |
| Mappings | Organization admins and owners | Transformations |
| Workspace | Organization admins and owners | Better Auth organizations |
| Platform | EdiSpark platform administrators | Reserved global boundary; no tenant capabilities |

`member` is the operations role. `admin` and `owner` can change tenant configuration. `platform_admin` is a separate global claim and never grants tenant access.

## Route ownership

- `/documents` is the authenticated landing page and primary workflow.
- `/settings/connections` composes business partners, agreements, transport, and evidence.
- `/settings/mappings` owns transformation configuration.
- `/settings/workspace` owns organization membership and sign-in policy.
- `/file/*` is technical evidence and requires organization-administrator access.
- `/platform/*` is isolated from tenant settings and requires `platform_admin`.

Legacy MFT, Trading Network, Mappings, Organization, and invoice-detail URLs redirect to these routes so bookmarks remain valid.

## Implementation rules

- Keep service contracts generated and domain-specific; compose screen models in the BFF or pure web adapters.
- Enforce roles in Spring Security and the BFF. Navigation visibility is not authorization.
- Do not expose raw EDI, hashes, protocol identifiers, or mapping internals in the primary Documents flow.
- Prefer one title, one primary action, and progressive disclosure over helper copy or summary cards.
