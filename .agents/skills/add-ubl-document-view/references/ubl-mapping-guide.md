# UBL mapping guide

## JSON conventions

- The envelope contains namespace keys such as `_D`, `_A`, and `_B`.
- The document root is an array with one item, for example `Invoice[0]`.
- Components are arrays even when their schema cardinality is `0..1` or `1..1`.
- Primitive values live under `_`.
- Primitive attributes sit beside `_`, for example:
  - Amount: `{ "_": 125.50, "currencyID": "GBP" }`
  - Quantity: `{ "_": 12, "unitCode": "EA" }`
  - Identifier: `{ "_": "501...", "schemeID": "GLN" }`
- Do not assume strings for numeric UBL types. Preserve decimals as numbers in the view model.
- Preserve repeated notes, lines, deliveries, taxes, allowances, references, and identifiers where they matter to the business view.

## Document starting points

Always confirm these against the actual maindoc and common schemas.

### Invoice

- Root: `UBL-Invoice-2.1.json`, namespace `...:Invoice-2`.
- Typical primary fields: `ID`, `IssueDate`, `InvoiceTypeCode`, `DocumentCurrencyCode`, `DueDate`.
- Parties: `AccountingSupplierParty`, `AccountingCustomerParty`, optional `PayeeParty`.
- Lines: `InvoiceLine` → quantity, line extension, item, price, allowances, taxes.
- Totals: `TaxTotal`, `LegalMonetaryTotal`.
- Familiar view: invoice header, supplier/customer, payment terms, tax breakdown, invoice lines, amount due.

### Credit Note

- Root: `UBL-CreditNote-2.1.json`, namespace `...:CreditNote-2`.
- Typical primary fields: `ID`, `IssueDate`, `CreditNoteTypeCode`, `DocumentCurrencyCode`.
- Parties: `AccountingSupplierParty`, `AccountingCustomerParty`.
- Lines: `CreditNoteLine` → credited quantity, line extension, item, price, taxes.
- Totals: `TaxTotal`, `LegalMonetaryTotal`.
- Preserve references to the affected invoice/order and make negative/credit semantics explicit without inventing signs.

### Despatch Advice / ASN

- Root: `UBL-DespatchAdvice-2.1.json`, namespace `...:DespatchAdvice-2`.
- Typical primary fields: `ID`, `IssueDate`, `DespatchSupplierParty`, `DeliveryCustomerParty`.
- Shipment: `Shipment`, `Despatch`, `Delivery`, handling units, transport, carrier, tracking identifiers.
- Lines: `DespatchLine` → delivered/outstanding quantities, order-line references, item, shipment data.
- Familiar view: ASN header, ship-from/to, expected delivery, carrier/tracking, handling units, shipped items.
- Use the product term “Advance shipping notice” while retaining “UBL Despatch Advice” as technical metadata.

### Receipt Advice

- Root: `UBL-ReceiptAdvice-2.1.json`, namespace `...:ReceiptAdvice-2`.
- Focus on shipment/order references, receiving parties, receipt date, received/short/rejected quantities, and discrepancy reasons.

### Order Response

- Root: `UBL-OrderResponse-2.1.json` or `UBL-OrderResponseSimple-2.1.json`.
- Focus on order reference, accepted/rejected state, promised delivery, changes, substitutions, and response lines.
- Do not derive acceptance solely from colour; use explicit text and source codes.

## View-model rules

- Name view models after the business artefact: `InvoiceViewModel`, `AsnViewModel`.
- Model money as value plus currency, quantity as value plus unit, and identifiers as value plus scheme where useful.
- Require only fields required by the canonical schema; optional UI sections should disappear cleanly.
- Return precise failure reasons internally, but show safe plain-English errors to users.
- Keep formatting out of the raw adapter when locale affects presentation.

