output "release_config" {
  value = {
    name               = local.name
    account_id         = var.account_id
    environment        = var.environment
    vpc_id             = aws_vpc.application.id
    task_subnets       = aws_subnet.tasks[*].id
    origin_subnets     = aws_subnet.origins[*].id
    task_role_arn      = aws_iam_role.task.arn
    execution_role_arn = aws_iam_role.execution.arn
    web_role_arn       = aws_iam_role.web.arn
    function_name      = aws_lambdaweb_function.web.function_name
    function_arn       = aws_lambdaweb_function.web.arn
    cluster_arn        = aws_ecs_cluster.api.arn
    ecr_repository     = aws_ecr_repository.api.repository_url
    log_group          = aws_cloudwatch_log_group.api.name
    dsql_host          = "${aws_dsql_cluster.auth.identifier}.dsql.eu-west-2.on.aws"
    web_secret_arn     = aws_secretsmanager_secret.web.arn
    jwt_key_arn        = aws_kms_key.jwt.arn
    backend_environment = merge(module.mft.application_environment, module.integration.application_environment, {
      DOCUMENTS_TABLE_NAME                = aws_dynamodb_table.documents.name
      DOCUMENTS_S3_ENABLED                = "true"
      DOCUMENTS_S3_BUCKET                 = aws_s3_bucket.documents.id
      DOCUMENTS_TENANT_KEY_REGISTRY_TABLE = module.mft.application_environment.MFT_METADATA_TABLE
      DOCUMENTS_KEY_STORE_TABLE           = module.mft.application_environment.MFT_KEY_STORE_TABLE
      DOCUMENTS_KEY_STORE_LOGICAL_NAME    = module.mft.application_environment.MFT_KEY_STORE_LOGICAL_NAME
      DOCUMENTS_ROOT_KMS_KEY_ARN          = module.mft.application_environment.MFT_ROOT_KMS_KEY_ARN
      }, var.enable_desk_agent ? {
      EDISPARK_AGENT_ENABLED         = "true"
      EDISPARK_AGENT_GUARDRAIL       = module.desk_guardrail[0].arn
      EDISPARK_AGENT_VERSION         = module.desk_guardrail[0].version
      EDISPARK_AGENT_USAGETABLE      = aws_dynamodb_table.desk_usage.name
      EDISPARK_AGENT_MONTHLYCREDITS  = tostring(var.desk_ai_monthly_credits)
      EDISPARK_AGENT_ROUNDSPERMINUTE = tostring(var.desk_ai_rounds_per_minute)
      AGENTCORE_MEMORY_MEMORYID      = aws_bedrockagentcore_memory.desk[0].id
      } : {}, local.mig_london_ready ? {
      EDISPARK_MIG_LONDON_PILOT_VERIFIED = "true"
      EDISPARK_MIG_SONNET_INPUT_MICROS   = tostring(var.mig_london_rates.sonnet_input)
      EDISPARK_MIG_SONNET_OUTPUT_MICROS  = tostring(var.mig_london_rates.sonnet_output)
      EDISPARK_MIG_NOVA_INPUT_MICROS     = tostring(var.mig_london_rates.nova_input)
      EDISPARK_MIG_NOVA_OUTPUT_MICROS    = tostring(var.mig_london_rates.nova_output)
    } : {})
  }
}
