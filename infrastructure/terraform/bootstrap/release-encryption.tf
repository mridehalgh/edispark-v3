resource "aws_kms_key" "releases" {
  provider                = aws.ireland
  description             = "Versioned immutable release artifact encryption"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  lifecycle { prevent_destroy = true }
}

output "release_encryption_key_arn" { value = aws_kms_key.releases.arn }
