output "application_environment" {
  value = {
    AWS_REGION                        = data.aws_region.current.region
    INTEGRATION_METADATA_TABLE        = aws_dynamodb_table.metadata.name
    INTEGRATION_EVENT_BUS             = aws_cloudwatch_event_bus.integration.name
    INTEGRATION_INBOUND_MFT_QUEUE_URL = aws_sqs_queue.inbound_mft_payloads.url
  }
}

output "agreement_snapshot_queue_url" {
  value = aws_sqs_queue.agreement_snapshots.url
}

output "application_policy_arn" {
  value = aws_iam_policy.application.arn
}

output "mft_event_publisher_policy_arn" {
  value = aws_iam_policy.mft_event_publisher.arn
}
