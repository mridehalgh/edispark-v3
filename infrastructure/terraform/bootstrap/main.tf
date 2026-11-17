terraform {
  required_version = "= 1.14.7"
  required_providers {
    aws = { source = "hashicorp/aws", version = "= 6.73.0" }
  }
}

provider "aws" {
  region              = "eu-west-2"
  allowed_account_ids = [var.account_id]
}

provider "aws" {
  alias               = "ireland"
  region              = "eu-west-1"
  allowed_account_ids = [var.account_id]
}

variable "account_id" { type = string }

variable "environment" {
  type = string
  validation {
    condition     = contains(["tooling", "staging", "production"], var.environment)
    error_message = "Each environment belongs in a separate account."
  }
}

variable "repository" {
  type    = string
  default = "mridehalgh/edispark-v3"
}

variable "role_bindings" {
  description = "Exact protected environment, reusable workflow and reviewed least-privilege policy per CI role. No default permission grants."
  type        = map(object({ environment = string, workflow = string, policy = string }))
  validation {
    condition     = alltrue([for name, binding in var.role_bindings : can(jsondecode(binding.policy)) && can(regex("^[a-z-]+[.]yml$", binding.workflow)) && length(binding.environment) > 0])
    error_message = "CI roles require a valid policy and a named reusable workflow."
  }
}
locals { name = "edispark-${var.environment}-${var.account_id}" }

resource "aws_kms_key" "state" {
  description             = "Terraform state and release metadata encryption"
  enable_key_rotation     = true
  deletion_window_in_days = 30
}

resource "aws_s3_bucket" "state" {
  bucket = "${local.name}-state"
  lifecycle { prevent_destroy = true }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.state.arn
    }
  }
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Deny", Principal = "*", Action = "s3:*", Resource = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"], Condition = { Bool = { "aws:SecureTransport" = "false" } }
  }] })
}

resource "aws_s3_bucket" "releases" {
  provider            = aws.ireland
  bucket              = "${local.name}-releases"
  object_lock_enabled = true
  lifecycle { prevent_destroy = true }
}

resource "aws_s3_bucket_public_access_block" "releases" {
  provider                = aws.ireland
  bucket                  = aws_s3_bucket.releases.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "releases" {
  provider = aws.ireland
  bucket   = aws_s3_bucket.releases.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_object_lock_configuration" "releases" {
  provider = aws.ireland
  bucket   = aws_s3_bucket.releases.id
  rule {
    default_retention {
      mode = "COMPLIANCE"
      days = 90
    }
  }
  depends_on = [aws_s3_bucket_versioning.releases]
}

resource "aws_s3_bucket_server_side_encryption_configuration" "releases" {
  provider = aws.ireland
  bucket   = aws_s3_bucket.releases.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.releases.arn
    }
  }
}

resource "aws_s3_bucket_policy" "releases" {
  provider = aws.ireland
  bucket   = aws_s3_bucket.releases.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Deny", Principal = "*", Action = "s3:*", Resource = [aws_s3_bucket.releases.arn, "${aws_s3_bucket.releases.arn}/*"], Condition = { Bool = { "aws:SecureTransport" = "false" } }
  }] })
}

resource "aws_kms_key" "release_signing" {
  count                    = var.environment == "tooling" ? 1 : 0
  description              = "Release signing only; never used for JWT or AS2"
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "RSA_3072"
  deletion_window_in_days  = 30
  lifecycle { prevent_destroy = true }
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

resource "aws_iam_role" "ci" {
  for_each             = var.role_bindings
  name                 = "edispark-${var.environment}-${each.key}"
  max_session_duration = each.key == "deployment" ? 7200 : 3600
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Principal = { Federated = aws_iam_openid_connect_provider.github.arn }, Action = "sts:AssumeRoleWithWebIdentity",
    Condition = { StringEquals = {
      "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com",
      "token.actions.githubusercontent.com:sub" = "repo:${var.repository}:environment:${each.value.environment}:workflow_ref:${var.repository}/.github/workflows/${each.value.workflow}@refs/heads/main:ref:refs/heads/main"
    } }
  }] })
}

resource "aws_iam_role_policy" "ci" {
  for_each = var.role_bindings
  role     = aws_iam_role.ci[each.key].id
  policy   = each.value.policy
}

resource "aws_codeartifact_domain" "dependencies" {
  count          = var.environment == "tooling" ? 1 : 0
  domain         = "edispark"
  encryption_key = aws_kms_key.state.arn
}

resource "aws_codeartifact_repository" "ingress" {
  for_each   = var.environment == "tooling" ? { npm = "public:npmjs", maven = "public:maven-central" } : {}
  domain     = aws_codeartifact_domain.dependencies[0].domain
  repository = "${each.key}-ingress"
  external_connections { external_connection_name = each.value }
}

resource "aws_codeartifact_repository" "approved" {
  for_each   = var.environment == "tooling" ? toset(["npm", "maven"]) : toset([])
  domain     = aws_codeartifact_domain.dependencies[0].domain
  repository = "${each.key}-approved"
  // Deliberately no public upstream: admission copies reviewed versions into this repository.
}

output "state_backend" {
  value = { bucket = aws_s3_bucket.state.id, region = "eu-west-2", encrypt = true, kms_key_id = aws_kms_key.state.arn, use_lockfile = true }
}

output "release_bucket" { value = aws_s3_bucket.releases.id }

output "signing_key_arn" { value = try(aws_kms_key.release_signing[0].arn, null) }

output "ci_role_arns" { value = { for name, role in aws_iam_role.ci : name => role.arn } }
