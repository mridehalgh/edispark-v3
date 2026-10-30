output "application_environment" {
  description = "Non-secret production settings for the MFT application."
  value = {
    AWS_REGION                 = data.aws_region.current.region
    MFT_METADATA_TABLE         = aws_dynamodb_table.metadata.name
    MFT_PAYLOAD_BUCKET         = aws_s3_bucket.payloads.id
    MFT_KEY_STORE_TABLE        = aws_dynamodb_table.key_store.name
    MFT_KEY_STORE_LOGICAL_NAME = aws_dynamodb_table.key_store.name
    MFT_ROOT_KMS_KEY_ARN       = aws_kms_key.hierarchy_root.arn
    MFT_AS2_SIGNING_KEY_ARN    = aws_kms_key.as2_signing.arn
    MFT_AS2_DECRYPTION_KEY_ARN = aws_kms_key.as2_decryption.arn
  }
}

output "service_endpoint" {
  description = "ALB DNS name when enable_service is true. DNS records are intentionally managed outside this module."
  value       = var.enable_service ? aws_lb.this[0].dns_name : null
}

output "attachable_policy_arns" {
  value = {
    data_plane     = aws_iam_policy.data_plane.arn
    as2_signing    = aws_iam_policy.as2_signing.arn
    as2_decryption = aws_iam_policy.as2_decryption.arn
  }
}
