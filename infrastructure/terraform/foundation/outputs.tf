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
      DOCUMENTS_TABLE_NAME = aws_dynamodb_table.documents.name
      DOCUMENTS_S3_ENABLED = "true"
      DOCUMENTS_S3_BUCKET  = aws_s3_bucket.documents.id
    })
  }
}
