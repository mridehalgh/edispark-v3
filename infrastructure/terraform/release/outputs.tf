output "edge_config" {
  value = {
    account_id            = var.foundation.account_id
    environment           = var.foundation.environment
    name                  = var.foundation.name
    vpc_id                = var.foundation.vpc_id
    alb_arn               = aws_lb.api.arn
    alb_dns               = aws_lb.api.dns_name
    alb_security_group_id = aws_security_group.alb.id
    function_arn          = var.foundation.function_arn
    endpoint_arn          = aws_lambdaweb_function_endpoint.web.arn
    web_domain            = aws_lambdaweb_function_endpoint.web.regional_domain_names["eu-west-2"]
  }
}

output "deployment" {
  description = "Non-secret release inputs retained for promotion and rollback."
  value = {
    foundation              = var.foundation
    domain_name             = var.domain_name
    backend_certificate_arn = var.backend_certificate_arn
    as2_certificates        = var.as2_certificates
    backend_image           = var.backend_image
    revisions               = var.revisions
    traffic                 = var.traffic
    alarm_actions           = var.alarm_actions
  }
}
