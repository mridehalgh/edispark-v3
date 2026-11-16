resource "aws_security_group" "alb" {
  name_prefix = "${var.foundation.name}-origin-"
  description = "Private CloudFront VPC origin; edge state owns its sole ingress rule"
  vpc_id      = var.foundation.vpc_id
}

resource "aws_security_group" "tasks" {
  name_prefix = "${var.foundation.name}-tasks-"
  description = "Only ALB initiated application requests are accepted"
  vpc_id      = var.foundation.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "tasks" {
  security_group_id            = aws_security_group.tasks.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

resource "aws_vpc_security_group_egress_rule" "alb" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.tasks.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}
# trivy:ignore:AVD-AWS-0104:exp:2027-02-01 -- Approved no-NAT IPv4 partner HTTPS egress; task ingress is ALB-only.
resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.tasks.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_lb" "api" {
  name                       = substr(var.foundation.name, 0, 32)
  internal                   = true
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = var.foundation.origin_subnets
  drop_invalid_header_fields = true
  enable_deletion_protection = true
}

resource "aws_lb_target_group" "api" {
  name                 = substr("${var.foundation.name}-api", 0, 32)
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = var.foundation.vpc_id
  deregistration_delay = 30
  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "api" {
  load_balancer_arn = aws_lb.api.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.backend_certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}
locals {
  backend_url  = "https://api.${var.domain_name}"
  frontend_url = "https://${var.domain_name}"
  backend_environment = merge(var.foundation.backend_environment, {
    SPRING_PROFILES_ACTIVE                = "production"
    AUTH_ISSUER                           = local.frontend_url
    AUTH_AUDIENCE                         = local.frontend_url
    AUTH_JWKS_URI                         = "${local.frontend_url}/api/auth/jwks"
    MFT_AS2_SIGNING_CERTIFICATE_BASE64    = var.as2_certificates.signing
    MFT_AS2_ENCRYPTION_CERTIFICATE_BASE64 = var.as2_certificates.encryption
    MFT_PUBLIC_AS2_URL                    = "${local.backend_url}/as2"
    MFT_PUBLIC_AS2_MDN_URL                = "${local.backend_url}/as2/mdn"
    MFT_ASYNC_MDN_URL                     = "${local.backend_url}/as2/mdn"
    MFT_LOCAL_DEMO_SEED_ENABLED           = "false"
    INTEGRATION_LOCAL_DEMO_SEED_ENABLED   = "false"
    DYNAMODB_LOCAL_EMBEDDED               = "false"
  })
}

resource "aws_cloudwatch_metric_alarm" "api_errors" {
  alarm_name          = "${var.foundation.name}-api-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  threshold           = 1
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_actions
  metric_query {
    id          = "rate"
    expression  = "IF(requests>=100,100*errors/requests,0)"
    label       = "API 5xx percentage"
    return_data = true
  }
  metric_query {
    id = "errors"
    metric {
      metric_name = "HTTPCode_Target_5XX_Count"
      namespace   = "AWS/ApplicationELB"
      period      = 60
      stat        = "Sum"
      dimensions = {
        LoadBalancer = aws_lb.api.arn_suffix
      }
    }
  }
  metric_query {
    id = "requests"
    metric {
      metric_name = "RequestCount"
      namespace   = "AWS/ApplicationELB"
      period      = 60
      stat        = "Sum"
      dimensions = {
        LoadBalancer = aws_lb.api.arn_suffix
      }
    }
  }
}

resource "aws_ecs_task_definition" "api" {
  skip_destroy = true
  lifecycle { create_before_destroy = true }
  family                   = var.foundation.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = var.foundation.execution_role_arn
  task_role_arn            = var.foundation.task_role_arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }
  container_definitions = jsonencode([{
    name = "api", image = var.backend_image, essential = true, user = "10001:10001",
    portMappings = [{
      containerPort = 8080, protocol = "tcp"
    }],
    environment = [for name, value in local.backend_environment : {
      name = name, value = value
    }],
    readonlyRootFilesystem = true,
    linuxParameters = {
      initProcessEnabled = true
      capabilities       = { drop = ["ALL"] }
    },
    mountPoints = [{
      sourceVolume = "tmp", containerPath = "/tmp", readOnly = false
    }],
    ulimits = [{
      name = "nofile", softLimit = 65536, hardLimit = 65536
    }],
    logConfiguration = {
      logDriver = "awslogs", options = {
        awslogs-group = var.foundation.log_group, awslogs-region = "eu-west-2", awslogs-stream-prefix = "api"
      }
    }
  }])
  volume {
    name = "tmp"
  }
}

resource "aws_ecs_service" "api" {
  name                               = var.foundation.name
  cluster                            = var.foundation.cluster_arn
  task_definition                    = aws_ecs_task_definition.api.arn
  desired_count                      = 2
  launch_type                        = "FARGATE"
  enable_execute_command             = false
  health_check_grace_period_seconds  = 90
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  wait_for_steady_state              = true
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  alarms {
    alarm_names = [aws_cloudwatch_metric_alarm.api_errors.alarm_name]
    enable      = true
    rollback    = true
  }
  network_configuration {
    subnets          = var.foundation.task_subnets
    security_groups  = [aws_security_group.tasks.id]
    assign_public_ip = true
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }
  depends_on = [aws_lb_listener.api]
  timeouts {
    create = "20m"
    update = "20m"
  }
}

resource "aws_lambdaweb_function_revision" "web" {
  provider      = aws.ireland
  for_each      = var.revisions
  function_name = var.foundation.function_name
  description   = each.key
  build_config {
    runtime_config {
      runtime = "nodejs24.x"
    }
    code_config {
      s3_object {
        bucket     = each.value.bucket
        key        = each.value.key
        version_id = each.value.version_id
      }
    }
  }
  service_config {
    execution_role_arn              = var.foundation.web_role_arn
    timeout_seconds                 = 30
    max_concurrency_per_environment = 32
    environment_variables = {
      AWS_LAMBDA_NODEJS_ENTRYPOINT = "index.mjs"
      BETTER_AUTH_JWT_KMS_REGION   = "eu-west-2"
      NODE_ENV                     = "production"
      BETTER_AUTH_URL              = local.frontend_url
      BETTER_AUTH_DSQL_HOST        = var.foundation.dsql_host
      BETTER_AUTH_DSQL_USER        = "edispark_auth"
      BETTER_AUTH_JWT_KMS_KEY_ID   = var.foundation.jwt_key_arn
      BETTER_AUTH_JWT_KID          = "jwt-v1"
      BETTER_AUTH_JWT_JWKS_URL     = "${local.frontend_url}/api/auth/jwks"
      WEB_SECRET_ARN               = var.foundation.web_secret_arn
      DOCUMENTS_API_URL            = local.backend_url
      MFT_API_URL                  = local.backend_url
      INTEGRATION_API_URL          = local.backend_url
      RELEASE_ID                   = each.key
      NEXT_TELEMETRY_DISABLED      = "1"
    }
    telemetry_config {
      logging_config {
        log_group             = "/aws/lambda/web/${var.foundation.name}"
        application_log_level = "WARN"
        system_log_level      = "WARN"
      }
    }
  }
}

resource "aws_lambdaweb_function_endpoint" "web" {
  provider             = aws.ireland
  function_name        = var.foundation.function_name
  endpoint_name        = "live"
  endpoint_type        = "PerRegion"
  regions              = ["eu-west-2"]
  auth_type            = "IamAuth"
  auto_deployment_mode = "Disabled"
  dynamic "revision_weight" {
    for_each = var.traffic
    content {
      revision_id = aws_lambdaweb_function_revision.web[revision_weight.key].revision_id
      weight      = revision_weight.value
    }
  }
}
