# Implementation

- [x] Replace production NoOp validation; package offline OASIS UBL schemas and test nested constraints.
- [x] Add structured route readiness and enforce schema, lifecycle, partner and mapping checks before generated EDI or transport.
- [x] Implement repeated mappings and coverage-aware publication without replacing historical revisions.
- [x] Validate the supported encoder's required fields and controls; reject unsupported fields and silent data loss.
- [x] Export contracts, regenerate SDKs and add authenticated BFF readiness.
- [x] Add readiness/correction UI, mapping recovery, recheck and activity evidence.
- [x] Add local scenarios and regression tests; verify the reported source and neighbouring workflows.
- [x] Authorize business operations for members while retaining admin-only configuration; test the actual security filter chain.

Historical mapping revisions remain retained and pinned until an administrator reviews their field coverage and publishes a replacement. The runtime does not invent commercial omission decisions or claim full partner implementation-guide certification.
