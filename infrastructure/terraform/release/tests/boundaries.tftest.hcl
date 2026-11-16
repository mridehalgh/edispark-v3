mock_provider "aws" {}
mock_provider "aws" { alias = "ireland" }
variables {
  foundation = {
    name           = "edispark-staging", account_id = "000000000002", environment = "staging",
    vpc_id         = "vpc-placeholder", task_subnets = ["subnet-task-a", "subnet-task-b"], origin_subnets = ["subnet-origin-a", "subnet-origin-b"],
    task_role_arn  = "arn:aws:iam::000000000002:role/task", execution_role_arn = "arn:aws:iam::000000000002:role/execution",
    web_role_arn   = "arn:aws:iam::000000000002:role/web", function_name = "edispark-staging",
    function_arn   = "arn:aws:lambda:eu-west-1:000000000002:web-function/edispark-staging",
    cluster_arn    = "arn:aws:ecs:eu-west-2:000000000002:cluster/edispark-staging",
    ecr_repository = "000000000002.dkr.ecr.eu-west-2.amazonaws.com/edispark-staging/api", log_group = "/edispark/staging/api",
    dsql_host      = "placeholder.dsql.eu-west-2.on.aws", web_secret_arn = "arn:aws:secretsmanager:eu-west-2:000000000002:secret:placeholder-AbCdEf",
    jwt_key_arn    = "arn:aws:kms:eu-west-2:000000000002:key/00000000-0000-0000-0000-000000000001", backend_environment = {}
  }
  domain_name             = "staging.example.invalid"
  backend_certificate_arn = "arn:aws:acm:eu-west-2:000000000002:certificate/00000000-0000-0000-0000-000000000001"
  as2_certificates        = { signing = "placeholder", encryption = "placeholder" }
  backend_image           = "000000000002.dkr.ecr.eu-west-2.amazonaws.com/edispark-staging/api@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  revisions               = { "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" = { bucket = "placeholder-releases", key = "releases/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/web.zip", version_id = "placeholder-version" } }
  traffic                 = { "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" = 100 }
}
run "cloudfront_ingress_and_public_ip_egress" {
  command = plan
  assert {
    condition     = aws_lb.api.internal
    error_message = "The API load balancer must never have a public listener."
  }
  assert {
    condition     = aws_ecs_service.api.network_configuration[0].assign_public_ip && !aws_ecs_service.api.enable_execute_command
    error_message = "Tasks require no-NAT egress without exposing management access."
  }
  assert {
    condition     = aws_lambdaweb_function_endpoint.web.auth_type == "IamAuth" && aws_lambdaweb_function_endpoint.web.endpoint_type == "PerRegion" && aws_lambdaweb_function_endpoint.web.auto_deployment_mode == "Disabled"
    error_message = "The frontend must retain IAM protection, explicit regional routing and controlled revision promotion."
  }
  assert {
    condition     = aws_ecs_task_definition.api.skip_destroy && jsondecode(aws_ecs_task_definition.api.container_definitions)[0].readonlyRootFilesystem
    error_message = "Retain rollback definitions and enforce a read-only container root."
  }
}
run "reject_invalid_traffic_weights" {
  command = plan
  variables { traffic = { "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" = 101 } }
  expect_failures = [var.traffic]
}
