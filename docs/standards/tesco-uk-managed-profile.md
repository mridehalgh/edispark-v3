# Tesco UK managed depot pilot

| Workflow | Wire identity | Local evidence |
|---|---|---|
| Purchase order | Inbound `ORDERS:D:96A:UN:EAN008` | `LM00000000042` with two order lines, depot GLN and delivery date and time |
| Commercial invoice | Outbound `INVOIC:D:96A:UN:EAN008` | `42901` with two lines in GBP, standard and zero-rated VAT, order and depot references, and reconciled totals |
| Despatch advice | Outbound `DESADV:D:96A:UN:DESAD3` | `42900` with two identified pallets and three case allocations for two GTINs |

The repository implementation uses the UK EANCOM D96A Orders supplier booklet stored as version 1.4, the UK INVOIC example in *Eancom Invoice Flat File*, and the June 2013 *ASN Supplier How To Document* plus the DESAD3 example. The retained order booklet has a version 1.3 title page. Review the exact guide versions with Tesco before retailer qualification.

The order mapping reads the buyer and seller, depot, contract reference when supplied, delivery schedule, item identity and ordered quantity. The invoice profile covers standard GBP commercial invoices with VAT breakdowns and reconciled line and invoice totals. The ASN profile covers the UK direct-to-depot case workflow and requires a booked delivery date, depot GLN, unique SSCCs in pallet `TraceID`, GTINs, and complete pallet allocation by order line. It uses the UBL item best-before date and the `Tesco packaging` additional item property with value `BOX` or `TRAY`.

Tesco Ireland, direct-to-store, consolidator, catch-weight, foreign currency and separate TAXCON messaging are outside the pilot. Use retailer-supplied identities, credentials and test endpoints for live qualification. The catalogue verification label records local simulator evidence.
