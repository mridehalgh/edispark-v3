resource "aws_s3_bucket" "assets" {
  bucket = "${local.name}-${var.account_id}-assets"
}

resource "aws_kms_key" "assets" {
  description             = "Public hashed frontend asset encryption; edge state owns its key policy"
  enable_key_rotation     = true
  deletion_window_in_days = 30
}

resource "aws_s3_bucket_public_access_block" "assets" {
  bucket                  = aws_s3_bucket.assets.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "assets" {
  bucket = aws_s3_bucket.assets.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "assets" {
  bucket = aws_s3_bucket.assets.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.assets.arn
    }
  }
}
// No expiration: retain hashed assets used by every retained rollback revision.
resource "aws_acm_certificate" "backend" {
  domain_name       = "api.${var.domain_name}"
  validation_method = "DNS"
  lifecycle { create_before_destroy = true }
}

resource "aws_route53_record" "backend_certificate" {
  for_each = toset(["api.${var.domain_name}"])
  zone_id  = var.hosted_zone_id
  name     = one(aws_acm_certificate.backend.domain_validation_options).resource_record_name
  type     = one(aws_acm_certificate.backend.domain_validation_options).resource_record_type
  ttl      = 300
  records  = [one(aws_acm_certificate.backend.domain_validation_options).resource_record_value]
}

resource "aws_acm_certificate_validation" "backend" {
  certificate_arn         = aws_acm_certificate.backend.arn
  validation_record_fqdns = [for record in aws_route53_record.backend_certificate : record.fqdn]
}

output "backend_certificate_arn" { value = aws_acm_certificate_validation.backend.certificate_arn }

output "assets_bucket" { value = aws_s3_bucket.assets.id }

output "assets_key_arn" { value = aws_kms_key.assets.arn }
