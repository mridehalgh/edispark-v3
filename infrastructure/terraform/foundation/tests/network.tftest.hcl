mock_provider "aws" {}
mock_provider "aws" { alias = "ireland" }
run "uk_foundation_has_no_nat_and_preserves_data" {
  command = plan
  variables {
    account_id     = "000000000002"
    environment    = "staging"
    domain_name    = "staging.example.invalid"
    hosted_zone_id = "ZPLACEHOLDER"
  }
  assert {
    condition     = length(aws_subnet.tasks) == 2 && length(aws_subnet.origins) == 2
    error_message = "Tasks and private origins must span two availability zones."
  }
  assert {
    condition     = aws_route.internet.destination_cidr_block == "0.0.0.0/0" && aws_vpc.application.enable_dns_hostnames
    error_message = "Public-IP task egress needs an internet gateway route and VPC DNS."
  }
  assert {
    condition     = aws_dsql_cluster.auth.deletion_protection_enabled && aws_dynamodb_table.documents.deletion_protection_enabled
    error_message = "Authentication and document data must be protected from accidental deletion."
  }
  assert {
    condition     = aws_s3_bucket_versioning.documents.versioning_configuration[0].status == "Enabled"
    error_message = "Document versions must be recoverable."
  }
  assert {
    condition     = contains([for index in aws_dynamodb_table.documents.global_secondary_index : index.name], "GSI3-TenantSchemasIndex")
    error_message = "Schema listing requires a tenant-keyed index."
  }
  assert {
    condition     = !contains(local.documents_metadata_actions, "dynamodb:Scan")
    error_message = "The application must not scan tenant document metadata. Backfills use separate administrative authority."
  }
  assert {
    condition     = output.release_config.backend_environment.DOCUMENTS_KEY_STORE_TABLE == module.mft.application_environment.MFT_KEY_STORE_TABLE
    error_message = "Documents must use the provisioned cell key store rather than an unconfigured encryption fallback."
  }
}

run "enabled_desk_has_versioned_protection_and_shared_limits" {
  command = plan
  variables {
    account_id                             = "000000000002"
    environment                            = "staging"
    domain_name                            = "staging.example.invalid"
    hosted_zone_id                         = "ZPLACEHOLDER"
    enable_desk_agent                      = true
    desk_ai_monthly_credits                = 1200
    desk_ai_organisation_credit_allowances = { "2OnHVzeAJKwm0ThFLcy1HfLHGPm" = 0 }
    mig_london_pilot_verified              = true
    mig_london_rates = {
      sonnet_input = 3, sonnet_output = 15, nova_input = 1, nova_output = 2
    }
  }
  assert {
    condition     = length(module.desk_guardrail) == 1 && length(aws_iam_role_policy.desk_agent) == 1 && contains(keys(output.release_config.backend_environment), "EDISPARK_AGENT_VERSION")
    error_message = "Enabled releases require a guardrail version and task policy."
  }
  assert {
    condition     = output.release_config.backend_environment.EDISPARK_AGENT_MONTHLYCREDITS == "1200" && aws_dynamodb_table.desk_usage.deletion_protection_enabled && length(aws_dynamodb_table_item.desk_allowance) == 1
    error_message = "Release configuration must preserve the shared monthly allowance and individual overrides."
  }
  assert {
    condition     = length(aws_iam_role_policy.mig_pilot) == 1 && output.release_config.backend_environment.EDISPARK_MIG_LONDON_PILOT_VERIFIED == "true"
    error_message = "A verified London deployment must configure MIG inference without a tenant list."
  }
}
