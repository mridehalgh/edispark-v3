data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  tags = merge(var.tags, {
    Service   = "mft"
    ManagedBy = "terraform"
  })
}

resource "aws_kms_key" "hierarchy_root" {
  description             = "MFT hierarchical keyring root for this deployment cell"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  tags                    = local.tags
}

resource "aws_kms_alias" "hierarchy_root" {
  name          = "alias/${var.name}-hierarchy-root"
  target_key_id = aws_kms_key.hierarchy_root.key_id
}

resource "aws_kms_key" "as2_signing" {
  description              = "MFT AS2 CMS and MDN signing key"
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "RSA_3072"
  deletion_window_in_days  = 30
  tags                     = local.tags
}

resource "aws_kms_alias" "as2_signing" {
  name          = "alias/${var.name}-as2-signing"
  target_key_id = aws_kms_key.as2_signing.key_id
}

resource "aws_kms_key" "as2_decryption" {
  description              = "MFT AS2 RSA-OAEP content-key decryption key"
  key_usage                = "ENCRYPT_DECRYPT"
  customer_master_key_spec = "RSA_3072"
  deletion_window_in_days  = 30
  tags                     = local.tags
}

resource "aws_kms_alias" "as2_decryption" {
  name          = "alias/${var.name}-as2-decryption"
  target_key_id = aws_kms_key.as2_decryption.key_id
}

resource "aws_dynamodb_table" "metadata" {
  name         = "${var.name}-metadata"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"

  attribute {
    name = "pk"
    type = "S"
  }
  attribute {
    name = "sk"
    type = "S"
  }
  attribute {
    name = "workPk"
    type = "S"
  }
  attribute {
    name = "workSk"
    type = "S"
  }

  global_secondary_index {
    name            = "work-index"
    hash_key        = "workPk"
    range_key       = "workSk"
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = true
  }
  server_side_encryption {
    enabled = true
  }
  deletion_protection_enabled = true
  tags                        = local.tags
}

resource "aws_dynamodb_table" "key_store" {
  name         = "${var.name}-hierarchical-key-store"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "branch-key-id"
  range_key    = "type"

  attribute {
    name = "branch-key-id"
    type = "S"
  }
  attribute {
    name = "type"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }
  server_side_encryption {
    enabled = true
  }
  deletion_protection_enabled = true
  tags                        = local.tags
}

resource "aws_s3_bucket" "payloads" {
  bucket              = var.payload_bucket_name
  object_lock_enabled = true
  tags                = local.tags
}

resource "aws_s3_bucket_public_access_block" "payloads" {
  bucket                  = aws_s3_bucket.payloads.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "payloads" {
  bucket = aws_s3_bucket.payloads.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "payloads" {
  bucket = aws_s3_bucket.payloads.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "payloads" {
  depends_on = [aws_s3_bucket_versioning.payloads]
  bucket     = aws_s3_bucket.payloads.id

  rule {
    id     = "retention"
    status = "Enabled"
    filter {}
    expiration {
      days = var.retention_days
    }
    noncurrent_version_expiration {
      noncurrent_days = var.retention_days
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

resource "aws_iam_policy" "data_plane" {
  name = "${var.name}-data-plane"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "Metadata"
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:DeleteItem", "dynamodb:Query", "dynamodb:TransactWriteItems", "dynamodb:ConditionCheckItem"]
        Resource = [aws_dynamodb_table.metadata.arn, "${aws_dynamodb_table.metadata.arn}/index/work-index"]
      },
      {
        Sid      = "KeyStoreRead"
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem"]
        Resource = aws_dynamodb_table.key_store.arn
      },
      {
        Sid      = "EncryptedObjects"
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = "${aws_s3_bucket.payloads.arn}/*"
      },
      {
        Sid      = "HierarchyDecrypt"
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = aws_kms_key.hierarchy_root.arn
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_policy" "as2_signing" {
  name = "${var.name}-as2-signing"
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Action = ["kms:GetPublicKey", "kms:Sign"], Resource = aws_kms_key.as2_signing.arn
  }] })
  tags = local.tags
}

resource "aws_iam_policy" "as2_decryption" {
  name = "${var.name}-as2-decryption"
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Action = ["kms:GetPublicKey", "kms:Decrypt"], Resource = aws_kms_key.as2_decryption.arn
  }] })
  tags = local.tags
}

resource "aws_cloudwatch_metric_alarm" "kms_throttles" {
  alarm_name          = "${var.name}-kms-throttles"
  namespace           = "AWS/KMS"
  metric_name         = "ThrottleCount"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = var.alarm_actions
  dimensions          = { KeyId = aws_kms_key.hierarchy_root.key_id }
  treat_missing_data  = "notBreaching"
  tags                = local.tags
}

resource "aws_cloudwatch_metric_alarm" "worker_errors" {
  alarm_name          = "${var.name}-worker-system-errors"
  namespace           = "AWS/DynamoDB"
  metric_name         = "SystemErrors"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = var.alarm_actions
  dimensions          = { TableName = aws_dynamodb_table.metadata.name }
  treat_missing_data  = "notBreaching"
  tags                = local.tags
}
