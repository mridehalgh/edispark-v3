variable "alarm_actions" {
  type    = list(string)
  default = []
  validation {
    condition     = var.config.environment != "production" || length(var.alarm_actions) > 0
    error_message = "Production requires operational alert destinations."
  }
}

resource "aws_cloudwatch_log_group" "waf" {
  provider          = aws.global
  name              = "aws-waf-logs-${var.config.name}"
  retention_in_days = 30
}

resource "aws_wafv2_web_acl_logging_configuration" "edge" {
  provider                = aws.global
  resource_arn            = aws_wafv2_web_acl.edge.arn
  log_destination_configs = [aws_cloudwatch_log_group.waf.arn]
  redacted_fields {
    single_header {
      name = "authorization"
    }
  }
  redacted_fields {
    single_header {
      name = "cookie"
    }
  }
  redacted_fields {
    single_header {
      name = "x-csrf-token"
    }
  }
  redacted_fields {
    query_string {
    }
  }
  logging_filter {
    default_behavior = "DROP"
    filter {
      behavior    = "KEEP"
      requirement = "MEETS_ANY"
      condition {
        action_condition {
          action = "BLOCK"
        }
      }
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "frontend_errors" {
  provider            = aws.global
  alarm_name          = "${var.config.name}-frontend-errors"
  comparison_operator = "GreaterThanThreshold"
  threshold           = 1
  evaluation_periods  = 2
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_actions
  metric_query {
    id          = "rate"
    expression  = "IF(requests>=100,errors,0)"
    label       = "Frontend 5xx percentage with minimum request volume"
    return_data = true
  }
  metric_query {
    id = "requests"
    metric {
      metric_name = "Requests"
      namespace   = "AWS/CloudFront"
      period      = 60
      stat        = "Sum"
      dimensions = {
        DistributionId = aws_cloudfront_distribution.application["web"].id, Region = "Global"
      }
    }
  }
  metric_query {
    id = "errors"
    metric {
      metric_name = "5xxErrorRate"
      namespace   = "AWS/CloudFront"
      period      = 60
      stat        = "Average"
      dimensions = {
        DistributionId = aws_cloudfront_distribution.application["web"].id, Region = "Global"
      }
    }
  }
}

output "deployment_alarms" {
  value = {
    "us-east-1" = [aws_cloudwatch_metric_alarm.frontend_errors.alarm_name]
  }
}
