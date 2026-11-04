# UBL authoring and response workflows

## Why

Operations users need to create the six supported order-to-cash documents, keep incomplete work safely, relate responses to their source documents, and decide per partner whether inbound documents create manual work, a prepared response, an approval request, or a qualified automatic send.

## What changes

- Treat Draft, Final, and Cancelled as lifecycle states of ordinary immutable-version Documents; do not add a draft API or draft aggregate.
- Add typed document-chain relations and OASIS UBL 2.1 schema-gated finalization.
- Add progressive business forms for Order, Order Response, Despatch Advice, Invoice, Remittance Advice, and Receipt Advice.
- Add partner response automation and approval policy to versioned exchange capabilities.
- Keep OASIS UBL 2.1 canonical; EDIFACT and TRADACOMS remain partner-specific exchange derivatives with mapping evidence.

## Impact

Documents domain/API/persistence, Partnerships capability contracts, generated SDKs, local demo coverage, and the Documents/Partners web experience change. Existing documents reconstitute as Final and existing partner routes remain Manual.
