# Design

Global mappings are static-stability data shipped in the transformations artifact. They are not copied into tenant partitions and do not require a runtime control-plane dependency. The catalogue resolves global IDs before tenant persistence so legacy local fixture rows with the same stable IDs cannot shadow the vended revision.

Each revision records `GLOBAL` scope, the `EdiSpark` vendor, immutable version metadata, and a mapping sample containing parsed source/target JSON and optional raw source/target EDI. Creating a revision from a global entry produces a new tenant-owned draft with `derivedFromMappingId` lineage and carries the sample forward.

Partner qualification and local transformation execution resolve mappings through the application service, which understands both catalogue and tenant repositories. Mutation, archive, and deletion remain tenant-only; global entries return a non-deletable usage explanation.

The bundled examples cover commercially meaningful headers, references, parties, identifiers, addresses, delivery, payment, allowances, tax, totals, and line/item data. JSON Pointer output creation supports arrays so inbound mappings can materialize valid UBL alternate-JSON shapes.
