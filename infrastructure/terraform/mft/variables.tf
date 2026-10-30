variable "name" {
  description = "Stable deployment-cell name; do not include tenant identifiers."
  type        = string
  default     = "edispark-mft"
}

variable "payload_bucket_name" {
  description = "Globally unique S3 bucket name for encrypted MFT objects."
  type        = string
}

variable "retention_days" {
  description = "Days before current encrypted payload versions expire."
  type        = number
  default     = 90
  validation {
    condition     = var.retention_days >= 1
    error_message = "retention_days must be at least one day."
  }
}

variable "alarm_actions" {
  description = "Optional SNS topic ARNs or other CloudWatch alarm actions."
  type        = list(string)
  default     = []
}

variable "tags" {
  type    = map(string)
  default = {}
}

variable "enable_service" {
  description = "Create the ECS/Fargate and ALB deployment resources. Keep false until an image and certificates are approved."
  type        = bool
  default     = false
}

variable "container_image" {
  description = "Immutable MFT API container image reference, preferably an ECR digest."
  type        = string
  default     = ""
}

variable "vpc_id" {
  type    = string
  default = ""
}

variable "public_subnet_ids" {
  type    = list(string)
  default = []
}

variable "service_subnet_ids" {
  type    = list(string)
  default = []
}

variable "assign_public_ip" {
  description = "Use only where private subnets do not yet have NAT/VPC endpoints."
  type        = bool
  default     = false
}

variable "tls_certificate_arn" {
  description = "ACM certificate ARN for the public HTTPS listener."
  type        = string
  default     = ""
}

variable "public_as2_url" {
  type    = string
  default = ""
}

variable "public_as2_mdn_url" {
  type    = string
  default = ""
}

variable "auth_issuer" {
  type    = string
  default = ""
}

variable "auth_jwks_uri" {
  type    = string
  default = ""
}

variable "auth_audience" {
  type    = string
  default = ""
}

variable "as2_signing_certificate_base64" {
  description = "Base64 DER public signing certificate. This is public material, not a private key."
  type        = string
  default     = ""
}

variable "as2_encryption_certificate_base64" {
  description = "Base64 DER public encryption certificate. This is public material, not a private key."
  type        = string
  default     = ""
}

variable "desired_count" {
  type    = number
  default = 2
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "ingress_cidr_blocks" {
  type    = list(string)
  default = ["0.0.0.0/0"]
}
