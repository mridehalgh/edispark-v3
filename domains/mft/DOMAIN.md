# Managed File Transfer

## Vision

Managed File Transfer gives every AS2 exchange a verifiable, tenant-isolated chain of evidence. It protects protocol security and delivery truth from being diluted into document parsing or UI concerns.

## Overview

The domain owns local AS2 identities, trading-partner transport profiles, certificate metadata, transfers, attempts, MDNs, payload references, and resend lineage. It does not parse EDI business documents or deliver files through SFTP or object-storage routes.

## Narrative

An integration analyst registers the workspace AS2 identity and a partner's endpoint and certificate policy. The organisation administrator can then share the tenant-scoped AS2 identifier, public receiving URLs, supported algorithms, certificate fingerprint, and public certificate with that partner. Private key material is never part of the onboarding pack. When an operations user submits a file, the service records one idempotent transfer, stores immutable bytes, prepares an AS2 message, and waits for verifiable receipt evidence. Incoming messages are matched by the exact AS2 identities, authenticated, stored once, and acknowledged. Transient network failures may retry with the same message identity; protocol or trust failures require human review. A manual resend is a new, linked exchange so the original evidence never changes.

## Boundaries

The authenticated control plane obtains the workspace from the validated JWT `tenant_id`. The public AS2 data plane has no user token and resolves one workspace from the exact, case-sensitive `(AS2-To, AS2-From)` pair. That pair is globally unique. After resolution, every partner, certificate, transfer, payload, and evidence operation is scoped to the resolved tenant.

The domain stores certificate lifecycle metadata and references to key custody. Production private-key operations belong behind a managed crypto port; raw private keys are not domain data. Partner and EdiSpark public X.509 certificate chains remain stored because they are required for verification, encryption, expiry monitoring, and rollover.

## Production posture

The current local profile uses in-memory metadata and encrypted files for disposable test identities and payloads. These adapters are unavailable outside `local` and `openapi-export`, causing production startup to fail until managed persistence and KMS adapters are supplied. The production key hierarchy, tenant isolation, compatibility profile, operations, and sensitive-data inventory are documented in [`docs/mft`](../../docs/mft/README.md).
