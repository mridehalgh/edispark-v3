# MFT AS2 requirements

## 1. Tenant isolation

1.1 Authenticated control-plane requests SHALL derive the tenant only from the validated `tenant_id` token claim.

1.2 Public AS2 requests SHALL resolve a tenant only from an exact, case-sensitive registered local AS2 identifier.

1.3 A tenant SHALL NOT read or mutate another tenant's profiles, partners, certificates, transfers, payloads, or evidence.

## 2. Partner and certificate configuration

2.1 An administrator SHALL configure local AS2 identities and remote partner profiles.

2.2 The system SHALL validate certificate encoding, validity dates, key usage, fingerprints, and private-key matching before activation.

2.3 Production private key material SHALL be non-exportable in AWS KMS and SHALL NOT be accepted, returned, persisted, or written to logs by EdiSpark APIs.

2.4 Local PKCS#12 identities SHALL be available only in local simulation and contract-export profiles.

2.5 Tenant payload and evidence encryption SHALL use one logical branch key per standard tenant beneath a bounded-cell KMS Hierarchical keyring root, with a dedicated physical KMS key available only through explicit policy.

2.6 Standard-tier AS2 identities SHALL share one RSA signing key and one RSA decryption key within a bounded deployment cell; signing and decryption SHALL use separate workload roles.

2.7 Production partner activation SHALL require RSA-OAEP key transport and separate signing and encryption certificates. RSA PKCS#1 v1.5 decryption SHALL NOT have a production fallback.

2.8 The system SHALL NOT represent cell-shared asymmetric keys as per-tenant cryptographic isolation because KMS asymmetric operations do not support encryption context.

2.9 An organisation administrator SHALL be able to copy the AS2 identifier, public receiving URLs, supported algorithms, certificate fingerprint, and expiry for a partner connection and download the associated public X.509 certificate. This operation SHALL be tenant-scoped and SHALL NOT return private keys, PKCS#12 material, passwords, or managed-key references.

## 3. Transfers

3.1 An authenticated caller SHALL submit an outbound payload with a partner and idempotency key.

3.2 An inbound AS2 payload SHALL be authenticated, retained immutably, correlated to its partner, and acknowledged with an MDN.

3.3 Duplicate inbound messages SHALL return their prior outcome without storing or processing the payload twice.

3.4 Automatic retries SHALL apply only to transient transport failures and SHALL use bounded exponential backoff with jitter.

3.5 A verified negative MDN or cryptographic failure SHALL require review and SHALL NOT retry automatically.

3.6 Manual resend SHALL create a new transfer linked to the original.

3.7 An outbound transfer submitted by the Exchange domain SHALL retain immutable provenance for the exchange, agreement and revision, capability, source document, and mapping revision.

## 4. Evidence and operations

4.1 Each transfer SHALL expose direction, status, partner, filename, size, hash, AS2 Message-ID, attempts, MDN result, certificate fingerprints, algorithms, and timestamps.

4.2 Payload and evidence downloads SHALL require tenant authorization.

4.3 Transfer lists SHALL be filterable and paginated without database scans or exposing persistence keys.

4.4 Certificate expiry SHALL surface warnings at 30, 14, and 7 days.

4.5 Evidence SHALL record the cell, certificate fingerprint, immutable key version, algorithm, and KMS request ID without recording plaintext or private material.

## 5. Local simulator

5.1 The local simulator SHALL operate as a second AS2 peer with generated non-production certificates.

5.2 It SHALL support folder-driven inbound files, received-file inspection, synchronous and asynchronous MDNs, duplicates, negative MDNs, transient HTTP failures, and invalid-certificate scenarios.

5.3 Simulator endpoints and UI SHALL be unavailable outside the local profile.

5.4 The complete local runner SHALL start the simulator, and an authenticated development-only tools dock SHALL trigger deterministic inbound and outbound exchanges on demand without exposing backend credentials or service URLs to browser JavaScript.

