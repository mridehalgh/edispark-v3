variable "name" { type = string }

resource "aws_bedrock_guardrail" "desk" {
  name                      = var.name
  blocked_input_messaging   = "This question was blocked by the workspace AI policy. Rephrase it and try again."
  blocked_outputs_messaging = "The workspace AI policy blocked the response. Open the record to continue."
  content_policy_config {
    dynamic "filters_config" {
      for_each = toset(["HATE", "INSULTS", "SEXUAL", "VIOLENCE", "MISCONDUCT", "PROMPT_ATTACK"])
      content {
        type            = filters_config.value
        input_strength  = "HIGH"
        output_strength = filters_config.value == "PROMPT_ATTACK" ? "NONE" : "HIGH"
      }
    }
  }
  sensitive_information_policy_config {
    dynamic "pii_entities_config" {
      for_each = toset(["CREDIT_DEBIT_CARD_NUMBER", "CREDIT_DEBIT_CARD_CVV", "PASSWORD", "AWS_SECRET_KEY"])
      content {
        type   = pii_entities_config.value
        action = "BLOCK"
      }
    }
  }
}

resource "aws_bedrock_guardrail_version" "desk" {
  guardrail_arn = aws_bedrock_guardrail.desk.guardrail_arn
  lifecycle {
    create_before_destroy = true
    replace_triggered_by  = [aws_bedrock_guardrail.desk]
  }
}

output "arn" { value = aws_bedrock_guardrail.desk.guardrail_arn }
output "version" { value = aws_bedrock_guardrail_version.desk.version }
