# Secure UK delivery

Implement the approved Terraform-only AWS deployment: London application serving and data, Ireland Web Function management, Aurora DSQL authentication, CloudFront/WAF, and no NAT gateways or instances. Preserve the local SQLite demo and modular Spring deployment.

CI must parallelize independent checks, pin dependencies and tools, and reject untrusted releases. Staging and production application promotion is automatic after verification; sensitive infrastructure changes require review.
