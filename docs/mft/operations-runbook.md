# MFT operations runbook

## Provision a tenant

1. Create the tenant record and immutable cell assignment.
2. Default `data_key_mode` to `HIERARCHICAL`; do not create a tenant KMS key.
3. Idempotently create one branch key in the assigned cell key store and conditionally persist its opaque branch ID in the trusted tenant crypto registry.
4. Write and decrypt a canary using the expected branch plus required tenant/service/resource encryption context.
5. If an approved request explicitly selects `DEDICATED_KMS`, provision the dedicated key and policy instead, record the override and cost owner, and run the same context-bound canary.

Branch creation is a privileged control-plane operation. Data-plane roles must not receive `CreateKey`, `VersionKey`, key-store writes, or arbitrary branch selection.

## Provision an AS2 partnership

1. Reserve the exact `(AS2-To, AS2-From)` pair with a conditional write.
2. Complete the KMS-native capability questionnaire, including RSA-OAEP and separate certificates.
3. Select the active signing and decryption certificate versions for the assigned cell.
4. Store the partner public signing/encryption certificates and verify fingerprints out of band.
5. Run bidirectional signed/encrypted exchange, signed MDN validation, duplicate replay, timeout, and rollover tests.
6. Publish the last-known-good partnership snapshot to the cell before enabling traffic.

## Normal monitoring

Alert on:

- KMS `AccessDenied`, throttling, disabled/pending-deletion keys, p99 latency, and invalid ciphertext;
- hierarchical cache hit/miss/eviction rates, key-store read failures, branch lookup mismatch, unexpected root/branch combinations, and DynamoDB recovery status;
- certificate expiry thresholds and activation-window gaps;
- signed MDN/MIC mismatch, negative MDN, replay anomalies, and retry exhaustion;
- per-tenant queue depth, concurrency rejection, and oldest-message age;
- control-plane snapshot age and failed conditional writes;
- unexpected tenant/cell/key-version combinations in audit events.

Never log EDI payloads, plaintext data keys, bearer tokens, passwords, private material, or partner certificate-upload bodies.

## Rotate AS2 keys

1. Create a new cell signing key and cell decryption key; do not repurpose either usage.
2. Create separate public X.509 certificates from their public keys.
3. Register immutable certificate versions and distribute both public certificates to partners.
4. Verify partner installation before changing outbound activation dates.
5. Keep old decryption material enabled for the agreed grace/replay window.
6. Monitor old/new fingerprints independently and remove the old versions only after all partnerships close their grace periods.

## KMS incident

- Stop accepting new work when the bounded queue or deadline budget is exhausted.
- Retry only throttles, timeouts, and documented transient KMS failures with decorrelated jitter.
- Do not retry authorization, disabled-key, incorrect-key, invalid-ciphertext, or unsupported-algorithm failures.
- Do not switch to local keys or another cell's key.
- Preserve ciphertext, queued transfer, Message-ID, and evidence; route the transfer to review when retry policy expires.
- Treat a shared Hierarchical root, key-store, or cell AS2 key failure as a cell incident and communicate the affected tenant set from the registry.

## Rotate tenant data material

- For `HIERARCHICAL`, call the key-store `VersionKey` operation with the trusted branch ID. The branch ID remains stable; new writes use the active version and old versions remain for reads.
- For `DEDICATED_KMS`, follow the dedicated-key rotation/migration policy and retain all key references required by existing ciphertext.
- In both modes, write and decrypt a canary, preserve immutable version evidence, and never edit key-store records directly.

## Offboard a tenant

1. Disable new control-plane mutations and AS2 submissions.
2. Drain or explicitly cancel queued transfers and retain evidence according to policy.
3. Deactivate partnerships and release AS2 identity pairs only after the quarantine period.
4. For a hierarchical tenant, retire its branch in the registry but retain branch versions for the ciphertext retention window; never delete the shared root or key-store table during tenant offboarding.
5. For a dedicated-key tenant, schedule key deletion only after the retention and recovery window and explicit approval.
6. Do not delete shared cell AS2 keys during tenant offboarding.
7. Retain public certificates/fingerprints with immutable evidence as required for verification.

## Recovery exercise

At least quarterly, restore the hierarchical DynamoDB key store plus one synthetic tenant's registry, metadata, encrypted payload, public certificate history, and key references into an isolated environment. Prove that active and previous branch versions decrypt the intended ciphertext, exact context is required, another branch cannot decrypt it, MDN evidence still verifies, and private AS2 keys remain non-exportable.
