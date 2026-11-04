# Complete six-document EDI support

## Why

EdiSpark currently presents and exchanges only UBL Order and Invoice, and its global EDI mappings cover a fixed sample rather than the complete source and target contracts. Operations users and API clients need traceable support for the full order-to-cash document set without silent field loss.

## What changes

- Support UBL 2.1 Order, Order Response, Despatch Advice, Invoice, Remittance Advice, and Receipt Advice in Documents.
- Support EDIFACT D96A and TRADACOMS v9 mappings in both directions for all six types.
- Compile field mappings against authoritative schemas and require every field to have an explicit disposition.
- Preserve unmatched inbound EDI data in a namespaced UBL extension and retain a mapping report for every transformation.
- Add partner-level outbound loss policy, defaulting to strict, with machine-readable API warnings.

## Impact

The change affects UBL and EDI standards modules, Transformations, Partnerships, Exchange, Integration API contracts and clients, Documents demo data, and the web Documents experience.

