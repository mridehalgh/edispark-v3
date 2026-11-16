variable "account_id" {
  type = string
}

variable "domain_name" { type = string }

variable "hosted_zone_id" { type = string }

variable "environment" {
  type = string
  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "Use staging or production in separate AWS accounts."
  }
}

variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
}

variable "alarm_actions" {
  type    = list(string)
  default = []
  validation {
    condition     = var.environment != "production" || length(var.alarm_actions) > 0
    error_message = "Production requires operational alert destinations."
  }
}
