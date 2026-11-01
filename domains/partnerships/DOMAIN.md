# Trading Partner Management

## Vision

Trading Partner Management is the authoritative control plane for who a tenant exchanges business documents with and the exact, qualified rules governing each exchange. It keeps retailer identity and EDI agreements independent from document storage and transport technology.

## Overview

The bounded context owns trading partners, business identities, partnerships, immutable agreement revisions, exchange capabilities, qualification evidence, and activation. It references published mapping revisions and MFT connections by opaque identifiers. It does not store document payloads, execute mappings, or manage AS2 certificates.

## Narrative

An integration analyst creates a retailer, records its qualified business identifiers, and links one tenant business profile to the retailer through a partnership. Each agreement revision declares directional capabilities such as outbound UBL Invoice to EDIFACT INVOIC D96A over a particular AS2 connection. A revision remains a draft until every capability has a published mapping, schema, transport connection, unique routing key, and successful qualification evidence. Activation freezes the revision and publishes a last-known-good snapshot for exchange workers. Editing creates a new revision so historical exchanges remain reproducible.

## Integration points

| Direction | Context | Contract |
|---|---|---|
| References | Transformations | Published mapping and schema IDs |
| References | MFT | Active AS2 connection ID |
| Publishes to | Exchange | Immutable activated agreement snapshot |
