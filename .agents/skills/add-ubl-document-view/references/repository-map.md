# Repository map

## Product boundary

- `ui/PRODUCT.md`: Documents-versus-Files rationale and product principles.
- `ui/DESIGN.md`: EDI Spark product tokens, density, accessibility, and visual rules.
- `/documents`: business-facing projections for operational users.
- `/file`: technical file sets, immutable versions, derivatives, hashes, parse metadata, and raw content.

## Current Order implementation

- `ui/src/app/documents/list.tsx`: business Order list built from document-set summaries.
- `ui/src/app/documents/order.tsx`: Order representation resolution and business view.
- `ui/src/lib/ubl-order.ts`: schema-derived defensive adapter and view model.
- `ui/src/lib/ubl-order.test.ts`: official OASIS, seed, minimum-valid, and failure tests.
- `ui/scripts/check-ubl-order-contract.mjs`: deterministic source-schema drift check.
- `ui/src/app/files/`: technical Files UI and raw representation access.
- `ui/src/lib/documents-api.ts`: browser-to-same-origin BFF functions.
- `ui/src/lib/documents-server.ts`: authenticated generated-client factory.
- `packages/documents-client`: generated backend client; do not duplicate its DTOs.

## UBL sources of truth

- `ubl-source/json-schema/maindoc/`: document-root JSON schemas.
- `ubl-source/json-schema/common/UBL-CommonAggregateComponents-2.1.json`: shared business aggregates.
- `ubl-source/json-schema/common/UBL-CommonBasicComponents-2.1.json`: named basic components.
- `ubl-source/json-schema/common/BDNDR-UnqualifiedDataTypes-1.1.json`: amount/date/time primitive rules.
- `ubl-source/json-schema/common/BDNDR-CCTS_CCT_SchemaModule-1.1.json`: identifier/code/text/quantity attributes.
- `ubl-source/json/`: official OASIS examples; fixtures, not specifications.
- `ubl-model/pom.xml`: generates Java classes from `ubl-source`.
- `ubl-model/src/main/java/com/example/ubl/util/UblJsonMapper.java`: JSON/date/time serialization behavior.
- `ubl-model/src/test/java/oasis/names/specification/ubl/`: generated-model document tests.

## Backend data path

1. Document-set summary supplies set ID, document ID, type, count, and metadata.
2. Detail BFF retrieves full document metadata and representations.
3. Source content or derivative content is fetched through authenticated same-origin routes.
4. The type adapter converts raw UBL JSON to a business view model.
5. The business component renders the view model and links back to the technical File.

Keep this path unless the backend contract lacks data necessary for the requested capability.

