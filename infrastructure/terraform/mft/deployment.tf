locals {
  service_environment = {
    SPRING_PROFILES_ACTIVE                = "production"
    AWS_REGION                            = data.aws_region.current.region
    MFT_METADATA_TABLE                    = aws_dynamodb_table.metadata.name
    MFT_PAYLOAD_BUCKET                    = aws_s3_bucket.payloads.id
    MFT_KEY_STORE_TABLE                   = aws_dynamodb_table.key_store.name
    MFT_KEY_STORE_LOGICAL_NAME            = aws_dynamodb_table.key_store.name
    MFT_ROOT_KMS_KEY_ARN                  = aws_kms_key.hierarchy_root.arn
    MFT_AS2_SIGNING_KEY_ARN               = aws_kms_key.as2_signing.arn
    MFT_AS2_DECRYPTION_KEY_ARN            = aws_kms_key.as2_decryption.arn
    MFT_AS2_SIGNING_CERTIFICATE_BASE64    = var.as2_signing_certificate_base64
    MFT_AS2_ENCRYPTION_CERTIFICATE_BASE64 = var.as2_encryption_certificate_base64
    MFT_PUBLIC_AS2_URL                    = var.public_as2_url
    MFT_PUBLIC_AS2_MDN_URL                = var.public_as2_mdn_url
    MFT_SUPPORTED_ENCRYPTION_ALGORITHMS   = "AES256-CBC"
    MFT_SUPPORTED_SIGNATURE_ALGORITHMS    = "SHA-256"
    MFT_KEY_ENCRYPTION_ALGORITHM          = "RSA-OAEP-SHA256"
    AUTH_ISSUER                           = var.auth_issuer
    AUTH_JWKS_URI                         = var.auth_jwks_uri
    AUTH_AUDIENCE                         = var.auth_audience
  }
}

resource "aws_cloudwatch_log_group" "service" {
  count             = var.enable_service ? 1 : 0
  name              = "/edispark/${var.name}"
  retention_in_days = var.log_retention_days
  tags              = local.tags
}

resource "aws_ecs_cluster" "this" {
  count = var.enable_service ? 1 : 0
  name  = var.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  tags = local.tags
}

resource "aws_iam_role" "task_execution" {
  count = var.enable_service ? 1 : 0
  name  = "${var.name}-task-execution"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole"
  }] })
  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  count      = var.enable_service ? 1 : 0
  role       = aws_iam_role.task_execution[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "task" {
  count = var.enable_service ? 1 : 0
  name  = "${var.name}-task"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{
    Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole"
  }] })
  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "task_data" {
  count      = var.enable_service ? 1 : 0
  role       = aws_iam_role.task[0].name
  policy_arn = aws_iam_policy.data_plane.arn
}

resource "aws_iam_role_policy_attachment" "task_signing" {
  count      = var.enable_service ? 1 : 0
  role       = aws_iam_role.task[0].name
  policy_arn = aws_iam_policy.as2_signing.arn
}

resource "aws_iam_role_policy_attachment" "task_decryption" {
  count      = var.enable_service ? 1 : 0
  role       = aws_iam_role.task[0].name
  policy_arn = aws_iam_policy.as2_decryption.arn
}

resource "aws_security_group" "load_balancer" {
  count       = var.enable_service ? 1 : 0
  name_prefix = "${var.name}-alb-"
  description = "Public HTTPS for AS2 and authenticated MFT APIs"
  vpc_id      = var.vpc_id

  dynamic "ingress" {
    for_each = var.ingress_cidr_blocks
    content {
      protocol    = "tcp"
      from_port   = 443
      to_port     = 443
      cidr_blocks = [ingress.value]
    }
  }
  egress {
    protocol    = "tcp"
    from_port   = 8080
    to_port     = 8080
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = local.tags
}

resource "aws_security_group" "service" {
  count       = var.enable_service ? 1 : 0
  name_prefix = "${var.name}-service-"
  description = "MFT tasks accept traffic only from the ALB"
  vpc_id      = var.vpc_id
  ingress {
    protocol        = "tcp"
    from_port       = 8080
    to_port         = 8080
    security_groups = [aws_security_group.load_balancer[0].id]
  }
  egress {
    protocol    = "-1"
    from_port   = 0
    to_port     = 0
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = local.tags
}

resource "aws_lb" "this" {
  count              = var.enable_service ? 1 : 0
  name               = substr(var.name, 0, 32)
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.load_balancer[0].id]
  subnets            = var.public_subnet_ids
  tags               = local.tags
}

resource "aws_lb_target_group" "service" {
  count       = var.enable_service ? 1 : 0
  name        = substr("${var.name}-api", 0, 32)
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id
  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  deregistration_delay = 30
  tags                 = local.tags
}

resource "aws_lb_listener" "https" {
  count             = var.enable_service ? 1 : 0
  load_balancer_arn = aws_lb.this[0].arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.tls_certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service[0].arn
  }
  tags = local.tags
}

resource "aws_ecs_task_definition" "service" {
  count                    = var.enable_service ? 1 : 0
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.task_execution[0].arn
  task_role_arn            = aws_iam_role.task[0].arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }
  container_definitions = jsonencode([{
    name                   = "edispark-api"
    image                  = var.container_image
    essential              = true
    portMappings           = [{ containerPort = 8080, protocol = "tcp" }]
    environment            = [for name, value in local.service_environment : { name = name, value = value }]
    readonlyRootFilesystem = true
    linuxParameters        = { initProcessEnabled = true }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service[0].name
        awslogs-region        = data.aws_region.current.region
        awslogs-stream-prefix = "mft"
      }
    }
    healthCheck = {
      command     = ["CMD-SHELL", "wget -q -O - http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])
  tags = local.tags

  lifecycle {
    precondition {
      condition = !var.enable_service || (
        var.container_image != "" && var.vpc_id != "" && length(var.public_subnet_ids) >= 2
        && length(var.service_subnet_ids) >= 2 && var.tls_certificate_arn != ""
        && var.public_as2_url != "" && var.public_as2_mdn_url != ""
        && var.as2_signing_certificate_base64 != "" && var.as2_encryption_certificate_base64 != ""
      )
      error_message = "Service deployment requires an immutable image, VPC/subnets, ACM certificate, public URLs, and both public AS2 certificates."
    }
  }
}

resource "aws_ecs_service" "this" {
  count                              = var.enable_service ? 1 : 0
  name                               = var.name
  cluster                            = aws_ecs_cluster.this[0].id
  task_definition                    = aws_ecs_task_definition.service[0].arn
  desired_count                      = var.desired_count
  launch_type                        = "FARGATE"
  health_check_grace_period_seconds  = 60
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  enable_execute_command             = false
  network_configuration {
    subnets          = var.service_subnet_ids
    security_groups  = [aws_security_group.service[0].id]
    assign_public_ip = var.assign_public_ip
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.service[0].arn
    container_name   = "edispark-api"
    container_port   = 8080
  }
  depends_on = [aws_lb_listener.https]
  tags       = local.tags
}

resource "aws_appautoscaling_target" "service" {
  count              = var.enable_service ? 1 : 0
  max_capacity       = max(var.desired_count, 6)
  min_capacity       = var.desired_count
  resource_id        = "service/${aws_ecs_cluster.this[0].name}/${aws_ecs_service.this[0].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  count              = var.enable_service ? 1 : 0
  name               = "${var.name}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.service[0].resource_id
  scalable_dimension = aws_appautoscaling_target.service[0].scalable_dimension
  service_namespace  = aws_appautoscaling_target.service[0].service_namespace
  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 60
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}
