locals {
  documents_metadata_actions = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem", "dynamodb:Query", "dynamodb:TransactWriteItems", "dynamodb:BatchWriteItem", "dynamodb:BatchGetItem", "dynamodb:DescribeTable"]
  name                       = "edispark-${var.environment}"
  azs                        = ["eu-west-2a", "eu-west-2b"]
}

resource "aws_vpc" "application" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags = {
    Name = local.name
  }
}

resource "aws_internet_gateway" "egress" {
  vpc_id = aws_vpc.application.id
}

resource "aws_subnet" "tasks" {
  count                   = 2
  vpc_id                  = aws_vpc.application.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = false
  tags = {
    Name = "${local.name}-tasks-${count.index}"
  }
}

resource "aws_subnet" "origins" {
  count                   = 2
  vpc_id                  = aws_vpc.application.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = false
  tags = {
    Name = "${local.name}-origin-${count.index}"
  }
}

resource "aws_route_table" "tasks" {
  vpc_id = aws_vpc.application.id
}

resource "aws_route" "internet" {
  route_table_id         = aws_route_table.tasks.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.egress.id
}

resource "aws_route_table_association" "tasks" {
  count          = 2
  subnet_id      = aws_subnet.tasks[count.index].id
  route_table_id = aws_route_table.tasks.id
}

resource "aws_route_table" "origins" {
  vpc_id = aws_vpc.application.id
}

resource "aws_route_table_association" "origins" {
  count          = 2
  subnet_id      = aws_subnet.origins[count.index].id
  route_table_id = aws_route_table.origins.id
}

resource "aws_vpc_endpoint" "gateway" {
  for_each          = toset(["s3", "dynamodb"])
  vpc_id            = aws_vpc.application.id
  service_name      = "com.amazonaws.eu-west-2.${each.key}"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.tasks.id]
  policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect   = "Allow", Principal = "*", Action = each.key == "s3" ? ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"] : ["dynamodb:GetItem", "dynamodb:ConditionCheckItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem", "dynamodb:Query", "dynamodb:Scan", "dynamodb:BatchGetItem", "dynamodb:BatchWriteItem", "dynamodb:TransactWriteItems", "dynamodb:DescribeTable"],
      Resource = each.key == "s3" ? ["arn:aws:s3:::${local.name}-*", "arn:aws:s3:::prod-eu-west-2-starport-layer-bucket/*"] : ["arn:aws:dynamodb:eu-west-2:${var.account_id}:table/${local.name}-*"]
    }]
  })
}
module "mft" {
  source              = "../mft"
  name                = "${local.name}-mft"
  payload_bucket_name = "${local.name}-${var.account_id}-payloads"
  alarm_actions       = var.alarm_actions
  enable_service      = false
}
module "integration" {
  source        = "../integration"
  name          = "${local.name}-integration"
  alarm_actions = var.alarm_actions
}

resource "aws_dynamodb_table" "documents" {
  name         = "${local.name}-documents"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"
  dynamic "attribute" {
    for_each = toset(["PK", "SK", "GSI1PK", "GSI1SK", "GSI2PK", "GSI2SK", "GSI3PK", "GSI3SK"])
    content {
      name = attribute.value
      type = "S"
    }
  }
  dynamic "global_secondary_index" {
    for_each = {
      "GSI1-TenantDocumentSetsIndex" = "GSI1", "GSI2-ContentHashIndex" = "GSI2", "GSI3-TenantSchemasIndex" = "GSI3"
    }
    content {
      name            = global_secondary_index.key
      hash_key        = "${global_secondary_index.value}PK"
      range_key       = "${global_secondary_index.value}SK"
      projection_type = "ALL"
    }
  }
  point_in_time_recovery {
    enabled = true
  }
  server_side_encryption {
    enabled = true
  }
  deletion_protection_enabled = true
}

resource "aws_dsql_cluster" "auth" {
  deletion_protection_enabled = true
  tags = {
    Name = "${local.name}-auth"
  }
}

resource "aws_kms_key" "storage" {
  enable_key_rotation     = true
  deletion_window_in_days = 30
  description             = "${local.name} encrypted storage and logs"
  policy = jsonencode({
    Version = "2012-10-17", Statement = [
      {
        Effect = "Allow", Principal = {
          AWS     = "arn:aws:iam::${var.account_id}:root"
        }, Action = "kms:*", Resource = "*"
      },
      {
        Effect = "Allow", Principal = {
          Service = "logs.eu-west-2.amazonaws.com"
          }, Action = ["kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:DescribeKey"], Resource = "*", Condition = {
          ArnLike = {
            "kms:EncryptionContext:aws:logs:arn" = "arn:aws:logs:eu-west-2:${var.account_id}:log-group:/edispark/${var.environment}/*"
          }
        }
      }
    ]
  })
}

resource "aws_kms_key" "jwt" {
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "ECC_NIST_P256"
  deletion_window_in_days  = 30
  description              = "${local.name} JWT signing only"
}

resource "aws_s3_bucket" "documents" {
  bucket = "${local.name}-${var.account_id}-documents"
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.storage.arn
    }
  }
}

resource "aws_s3_bucket_policy" "documents_tls" {
  bucket = aws_s3_bucket.documents.id
  policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect = "Deny", Principal = "*", Action = "s3:*", Resource = [aws_s3_bucket.documents.arn, "${aws_s3_bucket.documents.arn}/*"], Condition = {
        Bool = {
          "aws:SecureTransport" = "false"
        }
      }
    }]
  })
}

resource "aws_secretsmanager_secret" "web" {
  name                    = "${local.name}/web"
  kms_key_id              = aws_kms_key.storage.arn
  recovery_window_in_days = 30
  description             = "Set its value out of band; Terraform never reads the value"
}

resource "aws_ecr_repository" "api" {
  name                 = "${local.name}/api"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.storage.arn
  }
}

resource "aws_ecs_cluster" "api" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_iam_role" "task" {
  name = "${local.name}-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect = "Allow", Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }, Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "mft" {
  for_each   = module.mft.attachable_policy_arns
  role       = aws_iam_role.task.name
  policy_arn = each.value
}

resource "aws_iam_role_policy_attachment" "integration" {
  role       = aws_iam_role.task.name
  policy_arn = module.integration.application_policy_arn
}

resource "aws_iam_role_policy_attachment" "mft_events" {
  role       = aws_iam_role.task.name
  policy_arn = module.integration.mft_event_publisher_policy_arn
}

resource "aws_iam_role_policy" "documents" {
  role = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17", Statement = [
      {
        Effect = "Allow", Action = local.documents_metadata_actions, Resource = [aws_dynamodb_table.documents.arn, "${aws_dynamodb_table.documents.arn}/index/*"]
      },
      {
        Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"], Resource = "${aws_s3_bucket.documents.arn}/*"
      },
      {
        Effect = "Allow", Action = ["kms:GenerateDataKey", "kms:Decrypt"], Resource = aws_kms_key.storage.arn, Condition = {
          StringEquals = {
            "kms:ViaService" = "s3.eu-west-2.amazonaws.com"
          }
        }
      }
    ]
  })
}

resource "aws_iam_role" "execution" {
  name               = "${local.name}-execution"
  assume_role_policy = aws_iam_role.task.assume_role_policy
}

resource "aws_iam_role_policy" "execution" {
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17", Statement = [
      {
        Effect = "Allow", Action = "ecr:GetAuthorizationToken", Resource = "*"
      },
      {
        Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage"], Resource = aws_ecr_repository.api.arn
      },
      {
        Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.api.arn}:*"
      }
    ]
  })
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/edispark/${var.environment}/api"
  retention_in_days = 30
  kms_key_id        = aws_kms_key.storage.arn
}

resource "aws_cloudwatch_log_group" "flow" {
  name              = "/edispark/${var.environment}/network"
  retention_in_days = 30
  kms_key_id        = aws_kms_key.storage.arn
}

resource "aws_iam_role" "flow" {
  name = "${local.name}-flow-logs"
  assume_role_policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect = "Allow", Principal = {
        Service = "vpc-flow-logs.amazonaws.com"
        }, Action = "sts:AssumeRole", Condition = {
        StringEquals = {
          "aws:SourceAccount" = var.account_id
          }, ArnLike = {
          "aws:SourceArn" = "arn:aws:ec2:eu-west-2:${var.account_id}:vpc-flow-log/*"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "flow" {
  role = aws_iam_role.flow.id
  policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"], Resource = "${aws_cloudwatch_log_group.flow.arn}:*"
    }]
  })
}

resource "aws_flow_log" "vpc" {
  iam_role_arn    = aws_iam_role.flow.arn
  log_destination = aws_cloudwatch_log_group.flow.arn
  traffic_type    = "ALL"
  vpc_id          = aws_vpc.application.id
}

resource "aws_iam_role" "web" {
  name = "${local.name}-web"
  assume_role_policy = jsonencode({
    Version = "2012-10-17", Statement = [{
      Effect = "Allow", Principal = {
        Service = "lambda.amazonaws.com"
      }, Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "web" {
  role = aws_iam_role.web.id
  policy = jsonencode({
    Version = "2012-10-17", Statement = [
      {
        Effect = "Allow", Action = "dsql:DbConnect", Resource = aws_dsql_cluster.auth.arn
      },
      {
        Effect = "Allow", Action = "secretsmanager:GetSecretValue", Resource = aws_secretsmanager_secret.web.arn
      },
      {
        Effect = "Allow", Action = "kms:Decrypt", Resource = aws_kms_key.storage.arn, Condition = {
          StringEquals = {
            "kms:ViaService" = "secretsmanager.eu-west-2.amazonaws.com"
          }
        }
      },
      {
        Effect = "Allow", Action = ["kms:Sign", "kms:GetPublicKey"], Resource = aws_kms_key.jwt.arn
      },
      {
        Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "arn:aws:logs:eu-west-1:${var.account_id}:log-group:/aws/lambda/web/${local.name}:*"
      }
    ]
  })
}

resource "aws_cloudwatch_log_group" "web" {
  provider          = aws.ireland
  name              = "/aws/lambda/web/${local.name}"
  retention_in_days = 30
}

resource "aws_lambdaweb_function" "web" {
  provider      = aws.ireland
  function_name = local.name
}
