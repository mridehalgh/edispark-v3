# MFT AWS infrastructure module

This module defines the MFT data plane and an optional application deployment. It never creates DNS records,
public X.509 AS2 certificates, or a CI/CD pipeline, and it performs no deployment unless explicitly applied.

It creates the MFT metadata/lease table, AWS Encryption SDK branch-key store, encrypted-object bucket,
one symmetric hierarchical root key, separate RSA signing and RSA-OAEP decryption keys, attachable
least-privilege IAM policies, and baseline alarms. With `enable_service=true`, it also defines an ECS/Fargate
cluster and service, HTTPS ALB listener, task roles, security groups, CloudWatch logs, and CPU autoscaling.

Do not run `terraform apply` until the target account, region, retention policy, object-lock policy, alarm
destinations, backup recovery procedure, and KMS key administrators have been reviewed. The key-store table
must be retained for as long as any corresponding ciphertext exists.

```bash
terraform init -backend=false
terraform validate
terraform plan -var='payload_bucket_name=[globally-unique-name]'
```

The application workload role should attach the data-plane policy. Signing and decryption policies are
separate intentionally; attach them only to the runtime components that perform those operations.

The default `enable_service=false` validates the infrastructure without creating runtime resources. Enabling
the service requires an immutable image, VPC, at least two public and two service subnets, an ACM certificate,
public AS2 URLs, and both base64-DER public AS2 certificates. Public certificates are not secrets; no private
key or PKCS#12 password is accepted. DNS and deployment promotion remain separate reviewed steps.
