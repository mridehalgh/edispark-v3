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
