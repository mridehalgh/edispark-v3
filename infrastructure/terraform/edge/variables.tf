variable "config" {
  type = object({
    account_id   = string, environment = string, name = string, vpc_id = string,
    alb_arn      = string, alb_dns = string, alb_security_group_id = string,
    function_arn = string, endpoint_arn = string, web_domain = string
  })
}

variable "domain_name" {
  type = string
}

variable "hosted_zone_id" {
  type = string
}

variable "assets_bucket" {
  type = string
}
