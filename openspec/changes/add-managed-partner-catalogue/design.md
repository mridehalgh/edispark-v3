## Ownership and persistence

The unified Spring application owns catalogue and setup orchestration. Catalogue revisions occupy a global key in Integration metadata. Each tenant retains a setup reservation and owns the resulting partnership and MFT records. A conditional reservation links the request and agreement family. Stable resource identifiers and conditional creation allow the same request to resume after an interrupted write.

The signed system role authorizes the platform API and has no implicit tenant authority. Tenant setup uses the signed active-organisation tenant and role. Shared templates contain AS2 policy and workflow pins. Tenant setup collects endpoints, identities and certificate references and validates them through MFT.

## Lifecycle

A draft can be edited and published with optimistic concurrency. Editing a published revision creates a successor draft. Retirement removes the current publication from future setup. Existing setups retain the full template revision snapshot. An organisation records retailer acceptance against a hash of the agreement capabilities, relevant business identities and AS2 configuration. Edits to those inputs require fresh evidence and requalification before activation.

## Pilot coverage

Tesco UK uses inbound EAN008 ORDERS, outbound EAN008 INVOIC and outbound DESAD3 depot ASN. Validation covers the supported UK depot case and pallet profile. The partner material remains in its source location. The example payloads use synthetic identifiers. B&Q uses the existing local full-acceptance ACK v1.4 mapping and evidence.
