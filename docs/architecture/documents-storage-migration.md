# Documents encrypted storage cutover

The runtime requires tenant-scoped encrypted content. It never falls back to shared plaintext files. Local startup copies retained metadata-owned blobs into the configured stable local tenant's encrypted namespace before seeding. It preserves original files and developer records. No local reset is required. The disposable local root is stored with owner-only permissions beside encrypted content; losing it makes that local encrypted data unreadable.

Production uses S3 and the cell's shared Hierarchical key store/root with a tenant branch and authenticated Documents service/resource context. MFT retains its existing service context and ciphertext format. Foundation Terraform adds `GSI3-TenantSchemasIndex`, removes runtime Documents Scan permission, and exports explicit `DOCUMENTS_*` key-store configuration. Do not switch runtime reads until the index and content migration have been verified.

## Administrative rehearsal and execution

Use a separate migration identity with read/scan/update permission on Documents metadata plus required S3, tenant registry, Hierarchical key-store, and KMS permissions. Runtime application credentials deliberately lack metadata Scan. Freeze writes during the final inventory/copy/reconciliation window, or repeat with a final frozen inventory. Obtain the complete tenant inventory from the control plane; a partial list cannot establish global coverage.

1. Snapshot metadata and retain the original content directory. Provision the index and key/backend configuration first; wait for the index to be ACTIVE.
2. Run the schema-index command in dry-run mode, inspect its ownership checks, then run with `--apply`. This mode only backfills metadata and exits. Re-run until changes are zero and verify index visibility/completeness against the administrative schema inventory before continuing. GSI reads are eventually consistent.
3. Run content dry-run for every retained tenant. Inventory includes all document versions, derivatives, and schema definitions. Rehearse with synthetic two-tenant data first. Resolve every `MISSING` or `CORRUPT` receipt before cutover.
4. Run content copy with `--apply`. Copies verify the original hash, encrypt separately for each owner, read back, and verify the destination hash. Re-running emits `VERIFIED` for completed copies. Identical bytes owned by several tenants receive independent copies. `unreferenced-in-supplied-inventory` is informational and never authorizes deletion.
5. Repeat the complete inventory until every reference is `VERIFIED`; reconcile counts with retained metadata. Exercise authorized downloads and foreign-tenant denial in preproduction, then switch the application during the controlled cutover window.
6. Rehearse rollback using the retained snapshot, original files, and matching prior application version/configuration. Retention and deletion require a separate operational decision. This tool never deletes originals or restores a runtime plaintext reader.

Build the unified executable jar. Invoke its standalone entry point so Spring workers and seeders do not start:

```sh
java -Dloader.main=com.edispark.documents.infrastructure.storage.DocumentsMigrationMain \
  -cp apps/edispark-api/target/edispark-api-1.0.0-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --schema-index /path/to/legacy-content TENANT_ID
```

Append `--apply` after reviewing the dry-run. For content migration, omit `--schema-index` and supply all retained tenant IDs after the directory. IDs must be valid internal tenant IDs. Configuration requires `AWS_REGION`, `DOCUMENTS_TABLE_NAME`, `DOCUMENTS_S3_BUCKET`, `DOCUMENTS_TENANT_KEY_REGISTRY_TABLE`, `DOCUMENTS_KEY_STORE_TABLE`, `DOCUMENTS_KEY_STORE_LOGICAL_NAME`, and `DOCUMENTS_ROOT_KMS_KEY_ARN`; schema-only mode requires only region/table. Supply credentials through the standard AWS provider chain, never arguments or documentation. Receipts contain tenant ID, content hash and status, never payload/key material. Restrict access to these receipts as operational metadata.

Synthetic tests exercise dry-run, copying, re-running, corrupt/missing data, independent tenant deletion and wrong-context decryption. Live IAM, internal-token custody, producer grants, production migration coverage and rollback have not been exercised by those tests and remain release gates.
