# AS2 partner simulator

This local-only peer uses the same `phase2` protocol library as the MFT service. It receives signed,
encrypted, compressed AS2 messages at `http://localhost:8082/as2`, returns a signed synchronous MDN,
and can send files back to `http://localhost:8081/as2`.

1. Run `pnpm mft:lab:certs` once. The command creates disposable certificates under
   `.local/as2-simulator` and prints the random PKCS#12 password.
2. Upload `edispark.p12` as a **Local identity** and `simulator.cer` as a **Partner public** certificate.
3. Create a partner with local AS2 ID `EDISPARK`, remote AS2 ID `SIMULATOR`, and endpoint
   `http://localhost:8082/as2`. Local development must set `MFT_ALLOW_PRIVATE_NETWORKS=true`.
4. Run the MFT API and `pnpm dev:as2-simulator`.

Send a file into EdiSpark:

```sh
curl -F file=@sample.edi http://localhost:8082/api/simulator/send
```

Inspect files seen by the peer:

```sh
curl http://localhost:8082/api/simulator/messages
```

Certificates and private keys are test-only, disposable, and ignored with the rest of `.local`. This simulator intentionally uses local PKCS#12 files; it is not the production key-custody model. See the [MFT documentation](../../docs/mft/README.md) for the KMS-only production design, RSA-OAEP compatibility gate, and remaining sensitive-data inventory.
