# EdiSpark integration control plane

This stack provisions the tenant-partitioned DynamoDB metadata table and the EventBridge-to-SQS agreement snapshot channel used by the Trading Partnerships, Transformations, and Exchange bounded contexts.

The SQS target has a five-attempt dead-letter policy. Consumers must remain idempotent by agreement ID and revision; event delivery is at least once.

Run `terraform fmt -recursive infrastructure/terraform/integration` and review the plan before applying. The metadata table uses point-in-time recovery, encryption, and deletion protection.
