variable "foundation" {
  type = object({
    name               = string, account_id = string, environment = string, vpc_id = string,
    task_subnets       = list(string), origin_subnets = list(string), task_role_arn = string,
    execution_role_arn = string, web_role_arn = string, function_name = string, function_arn = string,
    cluster_arn        = string, ecr_repository = string, log_group = string, dsql_host = string,
    web_secret_arn     = string, jwt_key_arn = string, backend_environment = map(string)
  })
}

variable "domain_name" {
  type = string
}

variable "backend_certificate_arn" {
  type = string
}

variable "as2_certificates" {
  type = object({
    signing = string, encryption = string
  })
}

variable "backend_image" {
  type = string
  validation {
    condition     = can(regex("^[0-9]+[.]dkr[.]ecr[.]eu-west-2[.]amazonaws[.]com/.+@sha256:[0-9a-f]{64}$", var.backend_image))
    error_message = "The API image must be an immutable London ECR digest."
  }
}

variable "revisions" {
  description = "Retain active and rollback releases; removing a release requires review."
  type = map(object({
    bucket = string, key = string, version_id = string
  }))
  validation {
    condition     = alltrue([for release_id, revision in var.revisions : can(regex("^[0-9a-f]{40}$", release_id)) && revision.version_id != ""])
    error_message = "Revisions require a source commit and immutable S3 object version."
  }
}

variable "traffic" {
  type = map(number)
  validation {
    condition     = length(var.traffic) >= 1 && length(var.traffic) <= 2 && sum(values(var.traffic)) == 100 && alltrue([for weight in values(var.traffic) : weight >= 1 && weight <= 100 && floor(weight) == weight])
    error_message = "Route to one or two retained revisions with integer weights totaling 100."
  }
}

variable "alarm_actions" {
  type    = list(string)
  default = []
  validation {
    condition     = var.foundation.environment != "production" || length(var.alarm_actions) > 0
    error_message = "Production requires operational alert destinations."
  }
}
