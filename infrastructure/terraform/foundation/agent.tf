variable "enable_desk_agent" {
  description = "Enable the native Desk agent after validating its model in this environment."
  type        = bool
  default     = false
}

variable "desk_ai_monthly_credits" {
  description = "Default monthly AI allowance per organisation in credits. Zero pauses paid requests."
  type        = number
  default     = 1000
  validation {
    condition     = var.desk_ai_monthly_credits >= 0 && var.desk_ai_monthly_credits <= 200000000 && floor(var.desk_ai_monthly_credits) == var.desk_ai_monthly_credits
    error_message = "Use whole credits between 0 and 200000000."
  }
}

variable "desk_ai_rounds_per_minute" {
  type    = number
  default = 30
  validation {
    condition     = var.desk_ai_rounds_per_minute >= 1 && var.desk_ai_rounds_per_minute <= 10000 && floor(var.desk_ai_rounds_per_minute) == var.desk_ai_rounds_per_minute
    error_message = "Use a whole number of model rounds between 1 and 10000."
  }
}

variable "desk_ai_organisation_credit_allowances" {
  description = "Monthly allowance overrides in credits, keyed by the internal tenant ID."
  type        = map(number)
  default     = {}
  validation {
    condition     = alltrue([for tenant, credits in var.desk_ai_organisation_credit_allowances : can(regex("^[a-zA-Z0-9]{27}$", tenant)) && credits >= 0 && credits <= 200000000 && floor(credits) == credits])
    error_message = "Supply internal tenant IDs and whole credits between 0 and 200000000."
  }
}

resource "aws_dynamodb_table" "desk_usage" {
  name                        = "${local.name}-desk-usage"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "pk"
  range_key                   = "sk"
  deletion_protection_enabled = true
  attribute {
    name = "pk"
    type = "S"
  }
  attribute {
    name = "sk"
    type = "S"
  }
  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }
  point_in_time_recovery { enabled = true }
}

resource "aws_dynamodb_table_item" "desk_allowance" {
  for_each   = var.desk_ai_organisation_credit_allowances
  table_name = aws_dynamodb_table.desk_usage.name
  hash_key   = aws_dynamodb_table.desk_usage.hash_key
  range_key  = aws_dynamodb_table.desk_usage.range_key
  item = jsonencode({
    pk = { S = "POLICY#${each.key}" }, sk = { S = "ALLOWANCE" }, monthlyCredits = { N = tostring(each.value) }
  })
}

module "desk_guardrail" {
  count  = var.enable_desk_agent ? 1 : 0
  source = "../modules/desk-guardrail"
  name   = "${local.name}-desk"
}

resource "aws_bedrockagentcore_memory" "desk" {
  count                 = var.enable_desk_agent ? 1 : 0
  name                  = "edispark_desk_${var.environment}"
  event_expiry_duration = 30
}

resource "aws_iam_role_policy" "desk_agent" {
  count = var.enable_desk_agent ? 1 : 0
  name  = "desk-agent"
  role  = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Action    = ["bedrock:InvokeModelWithResponseStream"]
        Resource  = "arn:aws:bedrock:eu-west-2::foundation-model/amazon.nova-micro-v1:0"
        Condition = { StringEquals = { "bedrock:GuardrailIdentifier" = "${module.desk_guardrail[0].arn}:${module.desk_guardrail[0].version}" } }
      },
      {
        Effect    = "Deny"
        Action    = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
        Resource  = "*"
        Condition = { StringNotEquals = { "bedrock:GuardrailIdentifier" = "${module.desk_guardrail[0].arn}:${module.desk_guardrail[0].version}" } }
      },
      {
        Effect   = "Allow"
        Action   = ["bedrock:ApplyGuardrail"]
        Resource = [module.desk_guardrail[0].arn, "${module.desk_guardrail[0].arn}:${module.desk_guardrail[0].version}"]
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:ConditionCheckItem"]
        Resource = aws_dynamodb_table.desk_usage.arn
      },
      {
        Effect    = "Allow"
        Action    = ["dynamodb:UpdateItem"]
        Resource  = aws_dynamodb_table.desk_usage.arn
        Condition = { "ForAllValues:StringLike" = { "dynamodb:LeadingKeys" = ["USAGE#*"] } }
      },
      {
        Effect   = "Allow"
        Action   = ["bedrock-agentcore:ListSessions", "bedrock-agentcore:ListEvents", "bedrock-agentcore:CreateEvent"]
        Resource = aws_bedrockagentcore_memory.desk[0].arn
      }
    ]
  })
}
