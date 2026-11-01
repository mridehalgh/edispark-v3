# EDI Exchange

## Vision

Exchange makes every inbound and outbound business document follow the exact active trading agreement while preserving a complete, tenant-isolated operational history.

## Overview

The bounded context owns exchange identity, stage state, idempotency, retry/review decisions, and cross-context correlation. It orchestrates through anti-corruption ports and never imports Documents, Transformations, or MFT models.

## Narrative

For outbound work, Exchange resolves an active agreement capability, obtains canonical document bytes, requests a published mapping, stores the generated derivative, and asks MFT to transport it. For inbound work, a retained MFT payload is resolved against an immutable agreement snapshot, split and transformed, then recorded in Documents. Each stage is idempotent and observable; invalid business content needs review while transient infrastructure failures retry within a bounded deadline.

Production consumes metadata-only inbound payload events from an encrypted SQS queue with bounded redrive and a dead-letter queue. It retrieves bytes from MFT using a tenant-scoped trusted route, verifies the advertised size and SHA-256 hash, resolves exactly one inbound agreement capability, and records raw EDI plus canonical JSON in Documents. The MFT transfer ID is the idempotency key for both Documents and Exchange evidence.
