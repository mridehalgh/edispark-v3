variable "name" {
  description = "Stable integration control-plane deployment name."
  type        = string
  default     = "edispark-integration"
}

variable "alarm_actions" {
  description = "SNS topic ARNs or other CloudWatch alarm actions."
  type        = list(string)
  default     = []
}

variable "tags" {
  type    = map(string)
  default = {}
}
