data "aws_region" "current" {}

locals {
  tags = merge(var.tags, {
    Service   = "integration"
    ManagedBy = "terraform"
  })
}

resource "aws_dynamodb_table" "metadata" {
  name                        = "${var.name}-metadata"
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

  point_in_time_recovery { enabled = true }
  server_side_encryption { enabled = true }
  tags = local.tags
}

resource "aws_cloudwatch_event_bus" "integration" {
  name = "${var.name}-events"
  tags = local.tags
}

resource "aws_sqs_queue" "dead_letter" {
  name                      = "${var.name}-dead-letter"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
  tags                      = local.tags
}

resource "aws_sqs_queue" "agreement_snapshots" {
  name                       = "${var.name}-agreement-snapshots"
  visibility_timeout_seconds = 60
  message_retention_seconds  = 1209600
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dead_letter.arn
    maxReceiveCount     = 5
  })
  tags = local.tags
}

resource "aws_sqs_queue" "inbound_mft_payloads" {
  name                       = "${var.name}-inbound-mft-payloads"
  visibility_timeout_seconds = 60
  message_retention_seconds  = 1209600
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dead_letter.arn
    maxReceiveCount     = 5
  })
  tags = local.tags
}

resource "aws_cloudwatch_event_rule" "agreement_activated" {
  name           = "${var.name}-agreement-activated"
  event_bus_name = aws_cloudwatch_event_bus.integration.name
  event_pattern = jsonencode({
    source      = ["com.edispark.partnerships"]
    detail-type = ["TradingAgreementActivated"]
  })
  tags = local.tags
}

resource "aws_cloudwatch_event_target" "agreement_snapshots" {
  event_bus_name = aws_cloudwatch_event_bus.integration.name
  rule           = aws_cloudwatch_event_rule.agreement_activated.name
  arn            = aws_sqs_queue.agreement_snapshots.arn
}

resource "aws_sqs_queue_policy" "agreement_snapshots" {
  queue_url = aws_sqs_queue.agreement_snapshots.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.agreement_snapshots.arn
      Condition = { ArnEquals = { "aws:SourceArn" = aws_cloudwatch_event_rule.agreement_activated.arn } }
    }]
  })
}

resource "aws_cloudwatch_event_rule" "inbound_mft_payload_received" {
  name           = "${var.name}-inbound-mft-payload-received"
  event_bus_name = aws_cloudwatch_event_bus.integration.name
  event_pattern = jsonencode({
    source      = ["com.edispark.mft"]
    detail-type = ["MftInboundPayloadReceived"]
  })
  tags = local.tags
}

resource "aws_cloudwatch_event_target" "inbound_mft_payloads" {
  event_bus_name = aws_cloudwatch_event_bus.integration.name
  rule           = aws_cloudwatch_event_rule.inbound_mft_payload_received.name
  arn            = aws_sqs_queue.inbound_mft_payloads.arn
}

resource "aws_sqs_queue_policy" "inbound_mft_payloads" {
  queue_url = aws_sqs_queue.inbound_mft_payloads.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.inbound_mft_payloads.arn
      Condition = { ArnEquals = { "aws:SourceArn" = aws_cloudwatch_event_rule.inbound_mft_payload_received.arn } }
    }]
  })
}

resource "aws_iam_policy" "application" {
  name = "${var.name}-application"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "TenantMetadata"
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:Query", "dynamodb:TransactWriteItems"]
        Resource = aws_dynamodb_table.metadata.arn
      },
      {
        Sid      = "PublishDomainEvents"
        Effect   = "Allow"
        Action   = ["events:PutEvents"]
        Resource = aws_cloudwatch_event_bus.integration.arn
      },
      {
        Sid      = "ConsumeInboundMftPayloads"
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
        Resource = aws_sqs_queue.inbound_mft_payloads.arn
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_policy" "mft_event_publisher" {
  name = "${var.name}-mft-event-publisher"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "PublishInboundPayloadEvents"
      Effect   = "Allow"
      Action   = ["events:PutEvents"]
      Resource = aws_cloudwatch_event_bus.integration.arn
    }]
  })
  tags = local.tags
}

resource "aws_cloudwatch_metric_alarm" "dead_letters" {
  alarm_name          = "${var.name}-dead-letters"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = var.alarm_actions
  dimensions          = { QueueName = aws_sqs_queue.dead_letter.name }
  treat_missing_data  = "notBreaching"
  tags                = local.tags
}
