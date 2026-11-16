resource "aws_kms_key" "payload_storage" {
  description             = "S3 server-side encryption in addition to tenant envelope encryption"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  tags                    = local.tags
}
resource "aws_iam_policy" "payload_storage" {
  name = "${var.name}-payload-storage"
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect    = "Allow", Action = ["kms:GenerateDataKey", "kms:Decrypt"], Resource = aws_kms_key.payload_storage.arn,
    Condition = { StringEquals = { "kms:ViaService" = "s3.${data.aws_region.current.region}.amazonaws.com" } }
  }] })
}
