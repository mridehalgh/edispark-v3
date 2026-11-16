resource "aws_cloudfront_vpc_origin" "api" {
  vpc_origin_endpoint_config {
    name                   = "${var.config.name}-api"
    arn                    = var.config.alb_arn
    http_port              = 80
    https_port             = 443
    origin_protocol_policy = "https-only"
    origin_ssl_protocols {
      items    = ["TLSv1.2"]
      quantity = 1
    }
  }
}
data "aws_security_group" "cloudfront" {
  filter {
    name   = "vpc-id"
    values = [var.config.vpc_id]
  }
  filter {
    name   = "group-name"
    values = ["CloudFront-VPCOrigins-Service-SG*"]
  }
  depends_on = [aws_cloudfront_vpc_origin.api]
}

resource "aws_vpc_security_group_ingress_rule" "cloudfront" {
  security_group_id            = var.config.alb_security_group_id
  referenced_security_group_id = data.aws_security_group.cloudfront.id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}

resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "${var.config.name}-web"
  origin_access_control_origin_type = "lambda"
  signing_behavior                  = "always-amz-auth"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_origin_access_control" "assets" {
  name                              = "${var.config.name}-assets"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_acm_certificate" "edge" {
  provider                  = aws.global
  domain_name               = var.domain_name
  subject_alternative_names = ["api.${var.domain_name}"]
  validation_method         = "DNS"
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "validation" {
  for_each = toset([var.domain_name, "api.${var.domain_name}"])
  zone_id  = var.hosted_zone_id
  name     = one([for option in aws_acm_certificate.edge.domain_validation_options : option if option.domain_name == each.key]).resource_record_name
  type     = one([for option in aws_acm_certificate.edge.domain_validation_options : option if option.domain_name == each.key]).resource_record_type
  ttl      = 300
  records  = [one([for option in aws_acm_certificate.edge.domain_validation_options : option if option.domain_name == each.key]).resource_record_value]
}

resource "aws_acm_certificate_validation" "edge" {
  provider                = aws.global
  certificate_arn         = aws_acm_certificate.edge.arn
  validation_record_fqdns = [for record in aws_route53_record.validation : record.fqdn]
}

resource "aws_wafv2_web_acl" "edge" {
  provider = aws.global
  name     = var.config.name
  scope    = "CLOUDFRONT"
  default_action {
    allow {
    }
  }
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.config.name}-waf"
    sampled_requests_enabled   = false
  }
  dynamic "rule" {
    for_each = {
      "AWSManagedRulesCommonRuleSet" = 10, "AWSManagedRulesKnownBadInputsRuleSet" = 20, "AWSManagedRulesAmazonIpReputationList" = 30
    }
    content {
      name     = rule.key
      priority = rule.value
      override_action {
        none {
        }
      }
      statement {
        managed_rule_group_statement {
          name        = rule.key
          vendor_name = "AWS"
          dynamic "rule_action_override" {
            for_each = rule.key == "AWSManagedRulesCommonRuleSet" ? [1] : []
            content {
              name = "SizeRestrictions_BODY"
              action_to_use {
                count {}
              }
            }
          }
          // EDI is signed binary MIME and can exceed managed body inspection limits.
          // Keep IP/rate controls on AS2; application admission/crypto validates its body.
          dynamic "scope_down_statement" {
            for_each = rule.key == "AWSManagedRulesAmazonIpReputationList" ? [] : [1]
            content {
              not_statement {
                statement {
                  byte_match_statement {
                    positional_constraint = "STARTS_WITH"
                    search_string         = "/as2"
                    field_to_match {
                      uri_path {
                      }
                    }
                    text_transformation {
                      priority = 0
                      type     = "NONE"
                    }
                  }
                }
              }
            }
          }
        }
      }
      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = rule.key
        sampled_requests_enabled   = false
      }
    }
  }
  dynamic "rule" {
    for_each = {
      auth = {
        prefix = "/api/auth/", limit = 300, priority = 40
        }, api = {
        prefix = "/api/", limit = 3000, priority = 50
        }, as2 = {
        prefix = "/as2", limit = 3000, priority = 60
      }
    }
    content {
      name     = "${rule.key}-rate"
      priority = rule.value.priority
      action {
        block {
        }
      }
      statement {
        rate_based_statement {
          limit                 = rule.value.limit
          aggregate_key_type    = "IP"
          evaluation_window_sec = 300
          scope_down_statement {
            byte_match_statement {
              positional_constraint = "STARTS_WITH"
              search_string         = rule.value.prefix
              field_to_match {
                uri_path {
                }
              }
              text_transformation {
                priority = 0
                type     = "NONE"
              }
            }
          }
        }
      }
      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "${rule.key}-rate"
        sampled_requests_enabled   = false
      }
    }
  }
}
data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}
data "aws_cloudfront_cache_policy" "assets" {
  name = "Managed-CachingOptimized"
}
data "aws_cloudfront_origin_request_policy" "web" {
  name = "Managed-AllViewerExceptHostHeader"
}
data "aws_cloudfront_origin_request_policy" "api" {
  name = "Managed-AllViewer"
}

resource "aws_cloudfront_response_headers_policy" "security" {
  name = "${var.config.name}-security"
  security_headers_config {
    content_type_options {
      override = true
    }
    frame_options {
      frame_option = "DENY"
      override     = true
    }
    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      override                   = true
    }
  }
}

resource "aws_cloudfront_distribution" "application" {
  for_each        = toset(["web", "api"])
  enabled         = true
  is_ipv6_enabled = true
  aliases         = [each.key == "web" ? var.domain_name : "api.${var.domain_name}"]
  web_acl_id      = aws_wafv2_web_acl.edge.arn
  http_version    = "http2and3"
  origin {
    origin_id                = each.key
    domain_name              = each.key == "web" ? var.config.web_domain : var.config.alb_dns
    origin_access_control_id = each.key == "web" ? aws_cloudfront_origin_access_control.web.id : null
    dynamic "custom_origin_config" {
      for_each = each.key == "web" ? [1] : []
      content {
        http_port              = 80
        https_port             = 443
        origin_protocol_policy = "https-only"
        origin_ssl_protocols   = ["TLSv1.2"]
      }
    }
    dynamic "vpc_origin_config" {
      for_each = each.key == "api" ? [1] : []
      content {
        vpc_origin_id = aws_cloudfront_vpc_origin.api.id
      }
    }
  }
  dynamic "origin" {
    for_each = each.key == "web" ? [1] : []
    content {
      origin_id                = "assets"
      domain_name              = "${var.assets_bucket}.s3.eu-west-2.amazonaws.com"
      origin_access_control_id = aws_cloudfront_origin_access_control.assets.id
      s3_origin_config {
        origin_access_identity = ""
      }
    }
  }
  default_cache_behavior {
    target_origin_id           = each.key
    viewer_protocol_policy     = "https-only"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "PATCH", "POST", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id   = each.key == "web" ? data.aws_cloudfront_origin_request_policy.web.id : data.aws_cloudfront_origin_request_policy.api.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    compress                   = false
  }
  dynamic "ordered_cache_behavior" {
    for_each = each.key == "web" ? [1] : []
    content {
      path_pattern               = "/_next/static/*"
      target_origin_id           = "assets"
      viewer_protocol_policy     = "https-only"
      allowed_methods            = ["GET", "HEAD"]
      cached_methods             = ["GET", "HEAD"]
      cache_policy_id            = data.aws_cloudfront_cache_policy.assets.id
      response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
      compress                   = true
    }
  }
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.edge.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

resource "aws_lambdaweb_resource_policy" "cloudfront" {
  provider     = aws.ireland
  resource_arn = var.config.function_arn
  policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect = "Allow", Principal = {
        Service = "cloudfront.amazonaws.com"
      },
      Action = "lambda:InvokeWebFunctionEndpoint", Resource = var.config.endpoint_arn,
      Condition = {
        StringEquals = {
          "aws:SourceArn" = aws_cloudfront_distribution.application["web"].arn
        }
      }
      }, {
      Effect    = "Deny", Principal = "*", Action = "lambda:InvokeWebFunctionEndpoint", Resource = var.config.endpoint_arn,
      Condition = { StringNotEquals = { "aws:SourceArn" = aws_cloudfront_distribution.application["web"].arn } }
    }]
  })
}

resource "aws_s3_bucket_policy" "assets" {
  bucket = var.assets_bucket
  policy = jsonencode({
    Version = "2012-10-17", Statement = [
      {
        Effect = "Allow", Principal = {
          Service = "cloudfront.amazonaws.com"
          }, Action = "s3:GetObject", Resource = "arn:aws:s3:::${var.assets_bucket}/_next/static/*", Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.application["web"].arn
          }
        }
      },
      {
        Effect = "Deny", Principal = "*", Action = "s3:*", Resource = ["arn:aws:s3:::${var.assets_bucket}", "arn:aws:s3:::${var.assets_bucket}/*"], Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })
}

resource "aws_route53_record" "application" {
  for_each = {
    for pair in setproduct(["web", "api"], ["A", "AAAA"]) : "${pair[0]}-${pair[1]}" => pair
  }
  zone_id = var.hosted_zone_id
  name    = each.value[0] == "web" ? var.domain_name : "api.${var.domain_name}"
  type    = each.value[1]
  alias {
    name                   = aws_cloudfront_distribution.application[each.value[0]].domain_name
    zone_id                = aws_cloudfront_distribution.application[each.value[0]].hosted_zone_id
    evaluate_target_health = false
  }
}
