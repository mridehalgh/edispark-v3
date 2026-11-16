# Design

Use separate foundation, application release, and edge Terraform states per environment. Foundation creates the UK network/data/runtime identities. Application release owns immutable Web Function revisions and ECS definitions. Edge owns CloudFront, WAF, and the function resource policy. Supply explicit outputs between states, never mutable cross-state lookups during deployment.

CloudFront reaches an internal ALB through a VPC origin. Fargate tasks use public IPv4 addresses for outbound IPv4 partner connectivity, with ingress limited to the ALB security group. No NAT gateway or instance is provisioned. DSQL stores authentication data in a dedicated schema using IAM/TLS; DynamoDB and S3 retain domain persistence.

Web Functions use Ireland as the home region, a PerRegion London endpoint, IamAuth, and CloudFront OAC. Frontend logs are centralized in Ireland. CloudFront certificates/WAF use us-east-1. These exceptions are documented and do not permit customer document bodies in logs.

Application promotion verifies immutable artifacts and a KMS-signed manifest, performs forward-compatible migrations, deploys staging, runs synthetic checks, then promotes the same bytes to production. Unknown infrastructure changes fail closed for review. No automatic reversal of schema migrations or replay of partner delivery.
