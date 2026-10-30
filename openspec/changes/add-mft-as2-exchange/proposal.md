# MFT AS2 exchange

## Why

EdiSpark needs a tenant-isolated transport boundary that can receive and send EDI payloads over AS2 while retaining the payload, MDN, certificate, and retry evidence required by operations teams.

## What changes

- Add a dedicated MFT bounded context and Spring Boot API.
- Add AS2 partner, certificate, transfer, payload, and MDN workflows.
- Add an authenticated control-plane UI and generated TypeScript client.
- Add a loopback-only AS2 partner simulator for local interoperability and failure testing.

## Boundaries

SFTP hosting, SFTP/S3 delivery routes, automatic Documents-domain ingestion, cloud infrastructure, and marketing pages are not part of this change.

