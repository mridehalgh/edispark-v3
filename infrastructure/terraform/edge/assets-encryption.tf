variable "assets_key_arn" { type = string }

resource "aws_kms_key_policy" "assets" {
  key_id = var.assets_key_arn
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Principal = { AWS = "arn:aws:iam::${var.config.account_id}:root" }, Action = "kms:*", Resource = "*" },
    { Effect = "Allow", Principal = { Service = "cloudfront.amazonaws.com" }, Action = "kms:Decrypt", Resource = "*", Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.application["web"].arn } } }
  ] })
}
