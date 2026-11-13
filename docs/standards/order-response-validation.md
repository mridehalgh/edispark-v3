# Order Response validation and delivery scope

## Retained incident

The reported document is `d2bee046-e045-31f1-905c-9afca09768da/af18da40-15ce-4a28-8ee9-211229dd2566`.
The first linked correction is `31de9980-c671-4cf0-a806-fc35f74ab799/1c788944-3baf-442e-bf98-2965e00a411d`.
Its canonical representation is `1b379572-0be7-4ea3-b24e-a1a7011edaf6`, hash
`sha-256:83a48e26c2616cc0d2401bfb2e664b1a9162826a27bfa3d077e2b0b242af2243`.
These references identify retained local examples; do not copy their payloads into the public fixture catalogue.

The eligible retained partner is Northstar, EDIFACT D96A. The earlier TRADACOMS attempt selected another partner.
The source had eight lines, but the historical mapping mapped the first line only and omitted populated party,
currency, response and item fields. JSON fixture equality did not validate the resulting EDI contract.
The authoring dropdown also incorrectly labelled AB as acceptance and AP as acceptance with changes.
AB means receipt acknowledgement; AP means acceptance; CA means conditional acceptance. The user selected
acceptance of the order and all eight lines for a new correction. Historical final documents remain immutable.

## Supported core demo contract

`edispark.ordrsp.d96a.core.v1` / `UN-D96A-ORDRSP-CORE/1` is a bounded, explicitly named local core subset.
It is not a Kingfisher or Ocado qualification. The new mapping is
`d9c01ea5-f555-4c6d-9c87-1465ea78ac60`, revision 2 of the retained core mapping family.
Original v1 mapping identifiers are unchanged.

| Source field family | Disposition in the core subset |
| --- | --- |
| UBLVersionID | Retained as target sourceVersion and checked for 2.1; selects the source contract, not a business EDI segment |
| ID, IssueDate | BGM document reference, DTM 137 |
| OrderReference ID | RFF ON |
| DocumentCurrencyCode | CUX 2, currency usage 9 |
| OrderResponseCode | Explicit lookup: AP→BGM 29, AB→12, RE→27, CA→30; checked against line decisions |
| Seller and buyer endpoint values | NAD SU/BY identifiers and UNB sender/receiver |
| Seller and buyer endpoint schemes | Explicit code lookup to NAD agency; unknown schemes fail |
| Seller and buyer trading names | NAD C080 name component |
| Seller and buyer legal registration names | NAD C080 name component, separately retained from trading name |
| Delivery country | NAD DP country |
| Every line ID | LIN 1082, unique numeric identifier |
| Every line decision | LIN action code; no implicit acceptance default in the encoder |
| Every quantity and unit | QTY 21 value and unit |
| Every item name and description | Separate IMD occurrences, each split into the two supported 35-character components |
| Every supplier item reference | PIA SA |
| Optional buyer item reference / standard item ID | PIA IN / LIN EN |
| Optional note | FTX AAI |
| Additional populated fields | Block with a source-coverage finding; require a supported mapping or a reviewed reasoned omission |

Empty responses and more than 10,000 lines are rejected by this bounded contract. The limit is an implementation
limit, not the maximum in the partner guide. A model is validated before encoding; a separate reader validates
the final segment order, qualifiers, dates, field lengths, line count, UNT and UNZ controls.

Publication checks both UBL source validity and the mapped target contract, in addition to source coverage and
fixture equality. Evidence retains rule identity, rule implementation fingerprint, mapping hash and fixture hashes;
it does not retain customer fixture bytes. Route qualification pins this identity. Activation and send reject stale
target evidence. Administrator recovery loads only the tenant-authorized, exact source representation/hash.

## Authoritative partner sources and blockers

The user selected **B&Q only**. Screwfix is explicitly outside this change.
The public trading-partner page lists B&Q retail messages in TRADACOMS, including its
[ACK v1.4 guide](https://vendor.kingfisher.com/media/1099/acknowledgment-technical-spec-v14.pdf)
for dropship/direct-to-site flows. Retrieved 2026-11-09, SHA-256
`9e05a559a7400f8181f9be788f2adcbdd2826c138710219a4b484e2c4a86fb4b`.
The guide is titled TRADACOMS v9 but identifies ACKHDR/ACKMNT message versions as 4; a generic ACKHDR:9 encoder
is not a substitute. The implemented separate contract is `edispark.bq.tradacoms.ack.v1_4`,
rule set `BQ-TRADACOMS-ACK-1.4/FULL-ACCEPTANCE/1`, immutable mapping
`d9c01ea5-f555-4c6d-9c87-1465ea78ac61`. The first subset accepts the order and every line.
The guide's trailer table says ACKLTR whereas its complete-file example says ACKTLR.
The implementation follows the complete-file example (ACKTLR:4); confirm this in partner qualification before production.

No Screwfix profile is implemented or requested. Castorama France requirements are not B&Q requirements.

The user supplied the [Kingfisher EASIER programme](https://vendor.kingfisher.com/vendor-readiness/electronic-trading/easier-implementation/).
Its [trading-partner downloads](https://vendor.kingfisher.com/vendor-readiness/electronic-trading/trading-partners)
link the Castorama France DSV [KITS ORDRSP D96A v1.1 guide](https://vendor.kingfisher.com/media/1255/kits-edifact-ordrsp-technical-specification-v-11.pdf).
Retrieved 2026-11-09, PDF SHA-256 `474007bdaa79fd09e942c5039c74544327f971af7cb4e5e3028e571789763212`.
The public guide was read for implementation facts; it is not redistributed here and public availability does not
grant a redistribution licence.

The separate Castorama France profile requires full acceptance, EAN item IDs, buyer/supplier/delivery-party GLNs, a purchase-order reference,
the customer sales-order reference from that purchase order, currency, and per-line promised delivery date, net
price and purchase-order line reference. Use the detailed field tables: the sales-order qualifier is UC, not VN.
The guide's examples disagree with its tables on syntax identifier and EANCOM version. Production qualification
must resolve those differences with the partner; do not silently treat the generic core encoder as this profile.

The retained eight-line response lacks several required Kingfisher fields. They must come from the retained order
or an explicit user decision. Do not invent prices, dates, GLNs, sales-order references, or acceptance decisions.
The core demonstration route must remain separately named. No production Kingfisher route is enabled by this change.

The user-supplied `Ocado-EDI-Trading-Relationship-Document (1).pdf` has SHA-256
`314d81ab2452453123729f8cfbdfd98f7e7ec45d8a853f295c12dd45f1f6090e`.
It defines TRADACOMS v9 purchase orders and invoices; it supplies no Order Response/ACK specification.
Ocado Order Response remains unsupported; the B&Q definition is not an Ocado definition.
Historical illustrative ACK fixtures may remain for provenance; they are not evidence of a qualified send route.

The base directory references are [UNECE D96A ORDRSP](https://service.unece.org/trade/untdid/d96a/trmd/ordrsp_s.htm)
and its [annex](https://service.unece.org/trade/untdid/d96a/trmd/ordrsp_a.htm).
Direct retrieval was blocked by the upstream service; indexed normative tables were consulted alongside the supplied
partner PDF. No Cloudflare response or unverified third-party schema is used as a standard definition.

## B&Q profile and controls

The editor exposes vendor number (SellerSupplierParty CustomerAssignedAccountID), purchase-order version
(OrderReference DocumentReference VersionID, with the matching order ID), sales-order/store reference
(DeliveryLocation ID), promised delivery date and every item's StandardItemIdentification. Missing facts,
invalid ANA/EAN check digits, changed/rejected decisions, fractional traded quantities and unexpected source fields block sending.
Supplier/vendor/product identifiers in the local fixtures are synthetic, never production defaults.

| Source | B&Q ACK disposition |
| --- | --- |
| Supplier ANA, trading name, vendor number, legal name | STX and SDT |
| Buyer ANA and entity name | STX and CDT; checked against the guide |
| Original order reference and version | AOR and DNA 194; version reference must identify that order |
| Delivery reference, optional name and address | CLO |
| Promised delivery date | DNA 186 and every AQD |
| Every EAN, quantity, unit and optional name/description | ALD, matching AQD quantity |
| Every original line ID | DNB 043 |
| AP and Accepted line decisions | Validated full-acceptance subset, TYP 3120 |
| Source ACK ID, issue date | Explicit reasoned omissions retained in source/audit; FIL is actual creation date |
| Currency, country, supplier/customer SKUs, header note | Explicit reasoned omissions for fields with no permitted slot in this unpriced subset; retained source and mapping report preserve provenance |
| Additional populated fields | Block source coverage; no broad parent omissions |

Readiness and mapping qualification independently validate preview bytes without changing counters.
Only dispatch atomically reserves sequential STX transmission and FIL generation numbers with an
exchange-bound reservation in tenant-scoped DynamoDB. Replay and restart reuse it.
Counters are per AS2 connection; the 9,999 file-generation boundary requires an agreed rollover instead of silent wrapping.
The independent reader checks segment order, qualifiers, dates, quantities, uniqueness and message/file counts.
The supported two-digit-year window is 2000–2099.
The separately qualified **B&Q ACK v1.4 · local simulator** agreement is not a production connection or partner certification.

## Check coverage

`OrderResponseContractTest` checks response meaning, escaping, field/control corruption, and 1/8/12/10,000 lines.
`ClasspathGlobalMappingCatalogTest` checks the replacement sample and repeats beyond eight while retaining v1.
`MappingServiceTest` covers target-rule publication gating and retained evidence.
`JsonSchemaDocumentValidatorTest` covers response consistency during document validation.
`PartnershipServiceTest` covers stale target qualification. Local route reconciliation, source-bound recovery,
member/admin authorization and authenticated simulator delivery have separate checks recorded with the OpenSpec tasks.
