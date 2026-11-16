terraform {
  required_version = "= 1.14.7"
  backend "s3" {
  }
  required_providers {
    aws = {
      source = "hashicorp/aws", version = "= 6.73.0"
    }
  }
}

provider "aws" {
  region              = "eu-west-2"
  allowed_account_ids = [var.account_id]
  default_tags {
    tags = {
      Application = "edispark", Environment = var.environment, ManagedBy = "terraform"
    }
  }
}

provider "aws" {
  alias               = "ireland"
  region              = "eu-west-1"
  allowed_account_ids = [var.account_id]
}
