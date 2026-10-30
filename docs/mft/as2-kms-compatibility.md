# AS2 compatibility with AWS KMS

## Supported production profile

An EdiSpark KMS-native partnership requires:

| Capability | Required profile |
|---|---|
| Signing | RSA KMS `SIGN_VERIFY` key; SHA-256 or stronger |
| Encryption key transport | RSA-OAEP using SHA-256 preferred; SHA-1 only by explicit temporary policy |
| Content encryption | AES-128/256 where supported by both peers |
| Local certificates | Separate signing and encryption X.509 certificates |
| MDN | Signed MDN with SHA-256 MIC |
| TLS | HTTPS with modern TLS policy; independent from AS2 message certificates |

## Hard limitation

AWS KMS RSA decryption supports `RSAES_OAEP_SHA_1` and `RSAES_OAEP_SHA_256`. It does not support RSA PKCS#1 v1.5 decryption.

Consequences:

1. A partner that encrypts the CMS/AS2 content-encryption key with RSA PKCS#1 v1.5 is incompatible with the KMS-native EdiSpark endpoint.
2. There is no production software-key fallback. The partnership cannot be activated until the partner enables RSA-OAEP.
3. The partner capability questionnaire and pre-production exchange must prove OAEP interoperability.
4. Negative protocol responses must avoid leaking whether a particular key, tenant, or certificate exists.

## Two certificates are mandatory

An AWS KMS RSA key is created for signing/verification **or** encryption/decryption, not both. A KMS-native EdiSpark identity therefore has:

- one signing certificate whose public key belongs to a `SIGN_VERIFY` KMS key;
- one encryption certificate whose public key belongs to an `ENCRYPT_DECRYPT` KMS key.

Both public certificates are sent to the trading partner. EdiSpark stores the public certificate chains, fingerprints, purposes, activation windows, and KMS key references. The private keys never leave KMS.

## Partner onboarding gate

Before activation, record and test:

- exact, case-sensitive local and partner AS2 IDs;
- RSA-OAEP algorithm and digest;
- separate certificate support;
- signature and MIC digest;
- content-encryption algorithm;
- synchronous/asynchronous signed MDN behavior;
- maximum message size and compression order;
- TLS policy, endpoint, source IP requirements, and timeout;
- certificate rollover and emergency contact process.

The activation test sends a signed/encrypted file in both directions, validates the signed MDN/MIC, replays the Message-ID to confirm idempotency, and performs a dual-certificate rollover rehearsal.

## KMS-native AS2 protocol release gate

The local `phase2` adapter consumes a Java PKCS#12 keystore. Production needs a KMS-aware CMS adapter that:

- hashes large signing inputs locally and calls `kms:Sign` with `MessageType=DIGEST`;
- emits CMS RSA-OAEP parameters understood by the partner;
- sends the wrapped content-encryption key to `kms:Decrypt` with the exact key and algorithm;
- never materializes a private key or stores a PKCS#12 password;
- records request ID, algorithm, certificate fingerprint, key version, latency, and outcome without logging plaintext.

The managed DynamoDB/S3/hierarchical-keyring data plane is implemented, and the production API rejects private-key uploads. The Phase2 servlet receiver is restricted to local and contract-export profiles so it cannot accidentally provide a software-key production fallback. The KMS-native CMS signer/decrypter still requires partner interoperability evidence before the public AS2 and asynchronous-MDN endpoints can be enabled or called production-ready. Do not represent the service as KMS-native AS2 production-ready until those protocol tests pass against deployed AWS KMS keys.
