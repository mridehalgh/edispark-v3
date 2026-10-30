# EdiSpark CRN

A CRN is EdiSpark's stable, tenant-scoped identifier for a resource. It can be used in APIs,
events, audit records, and access-control policies without exposing storage details.

## Format

```
crn:edispark:<service>:<region>:<tenant-id>:<resource-type>/<resource-id>
```

`<resource-type>/` is optional; a global resource uses an empty `<region>`. Segments accept only
letters, numbers, `_`, `-`, `.`, and `/` (within the resource segment). Tenant IDs are KSUIDs.

Example:

```
crn:edispark:documents:eu-west-2:2OnHVzeAJKwm0ThFLcy1HfLHGPm:document-set/2OnHmVjaQa5eGWfT0HROcJkJmQ2
```

The default partition is always `edispark`; other partitions are rejected to keep identifiers
portable across EdiSpark services.
