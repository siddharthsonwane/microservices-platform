########################################################################
# aws/eks/main.tf — Production-grade EKS cluster for the platform
########################################################################
terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.24"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
  }
  backend "s3" {
    bucket         = "platform-terraform-state"
    key            = "eks/terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "platform-terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "microservices-platform"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# ── Variables ──────────────────────────────────────────────────────────────────
variable "aws_region"   { default = "ap-south-1" }
variable "environment"  { default = "prod" }
variable "cluster_name" { default = "platform-eks" }
variable "vpc_cidr"     { default = "10.0.0.0/16" }

# ── Data sources ───────────────────────────────────────────────────────────────
data "aws_availability_zones" "available" {}
data "aws_caller_identity"    "current"   {}

# ── VPC ───────────────────────────────────────────────────────────────────────
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${var.cluster_name}-vpc"
  cidr = var.vpc_cidr

  azs             = slice(data.aws_availability_zones.available.names, 0, 3)
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]

  enable_nat_gateway     = true
  single_nat_gateway     = false   # One NAT per AZ for HA
  enable_vpn_gateway     = false
  enable_dns_hostnames   = true
  enable_dns_support     = true

  public_subnet_tags = {
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
    "kubernetes.io/role/elb"                    = 1
  }
  private_subnet_tags = {
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
    "kubernetes.io/role/internal-elb"           = 1
  }
}

# ── EKS Cluster ───────────────────────────────────────────────────────────────
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = var.cluster_name
  cluster_version = "1.29"

  cluster_endpoint_public_access       = true
  cluster_endpoint_public_access_cidrs = ["0.0.0.0/0"]  # Restrict in production!

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # EKS Managed Node Groups
  eks_managed_node_groups = {
    # General workloads
    general = {
      name           = "general"
      instance_types = ["t3.xlarge"]
      min_size       = 2
      max_size       = 8
      desired_size   = 3
      disk_size      = 50
      capacity_type  = "ON_DEMAND"
      labels = {
        role = "general"
      }
    }

    # Compute-intensive (Saga, Payment processing)
    compute = {
      name           = "compute"
      instance_types = ["c5.2xlarge"]
      min_size       = 1
      max_size       = 5
      desired_size   = 2
      disk_size      = 50
      capacity_type  = "ON_DEMAND"
      labels = {
        role = "compute"
      }
      taints = [{
        key    = "dedicated"
        value  = "compute"
        effect = "NO_SCHEDULE"
      }]
    }

    # Spot instances for cost savings (non-critical services)
    spot = {
      name           = "spot"
      instance_types = ["t3.large", "t3.xlarge", "m5.large"]
      min_size       = 0
      max_size       = 10
      desired_size   = 2
      capacity_type  = "SPOT"
      labels = {
        role = "spot"
      }
    }
  }

  # Cluster add-ons
  cluster_addons = {
    coredns               = { most_recent = true }
    kube-proxy            = { most_recent = true }
    vpc-cni               = { most_recent = true }
    aws-ebs-csi-driver    = { most_recent = true }
    aws-efs-csi-driver    = { most_recent = true }
  }

  # IRSA for service accounts
  enable_irsa = true
}

# ── ECR Repositories ──────────────────────────────────────────────────────────
locals {
  services = [
    "service-discovery", "api-gateway", "order-service",
    "payment-service", "inventory-service",
    "notification-service", "saga-orchestrator"
  ]
}

resource "aws_ecr_repository" "services" {
  for_each             = toset(local.services)
  name                 = "platform/${each.value}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "services" {
  for_each   = aws_ecr_repository.services
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ── RDS PostgreSQL instances (one per service = database-per-service pattern) ─
resource "aws_db_subnet_group" "platform" {
  name       = "${var.cluster_name}-db-subnet-group"
  subnet_ids = module.vpc.private_subnets
}

locals {
  databases = {
    orders    = { db_name = "orders_db",    username = "orders_user",    port = 5432 }
    payments  = { db_name = "payments_db",  username = "payments_user",  port = 5433 }
    inventory = { db_name = "inventory_db", username = "inventory_user", port = 5434 }
    saga      = { db_name = "saga_db",      username = "saga_user",      port = 5435 }
  }
}

resource "aws_db_instance" "services" {
  for_each = local.databases

  identifier        = "${var.cluster_name}-${each.key}"
  engine            = "postgres"
  engine_version    = "16.1"
  instance_class    = "db.t3.medium"
  allocated_storage = 20
  storage_encrypted = true
  storage_type      = "gp3"

  db_name  = each.value.db_name
  username = each.value.username
  password = random_password.db[each.key].result
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.platform.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  deletion_protection     = true
  skip_final_snapshot     = false
  final_snapshot_identifier = "${var.cluster_name}-${each.key}-final"

  performance_insights_enabled = true
  monitoring_interval          = 60
  monitoring_role_arn          = aws_iam_role.rds_monitoring.arn

  tags = { Name = "${var.cluster_name}-${each.key}" }
}

resource "random_password" "db" {
  for_each = local.databases
  length   = 32
  special  = false
}

# ── ElastiCache Redis ─────────────────────────────────────────────────────────
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${var.cluster_name}-redis"
  description                = "Redis for API Gateway rate limiting"
  node_type                  = "cache.t3.medium"
  num_cache_clusters         = 2
  automatic_failover_enabled = true
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  engine_version             = "7.1"
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.redis.id]
  snapshot_retention_limit   = 3
  snapshot_window            = "05:00-06:00"
}

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${var.cluster_name}-redis-subnet"
  subnet_ids = module.vpc.private_subnets
}

# ── MSK (Managed Kafka) ───────────────────────────────────────────────────────
resource "aws_msk_cluster" "kafka" {
  cluster_name           = "${var.cluster_name}-kafka"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = "kafka.t3.small"
    client_subnets  = module.vpc.private_subnets
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT"
      in_cluster    = true
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.kafka.arn
    revision = aws_msk_configuration.kafka.latest_revision
  }

  open_monitoring {
    prometheus {
      jmx_exporter  { enabled_in_broker = true }
      node_exporter { enabled_in_broker = true }
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs { enabled = true; log_group = "/msk/${var.cluster_name}" }
    }
  }
}

resource "aws_msk_configuration" "kafka" {
  kafka_versions = ["3.6.0"]
  name           = "${var.cluster_name}-kafka-config"
  server_properties = <<EOF
auto.create.topics.enable=true
default.replication.factor=3
min.insync.replicas=2
num.partitions=3
log.retention.hours=168
compression.type=lz4
EOF
}

# ── Security Groups ───────────────────────────────────────────────────────────
resource "aws_security_group" "rds" {
  name   = "${var.cluster_name}-rds-sg"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
}

resource "aws_security_group" "redis" {
  name   = "${var.cluster_name}-redis-sg"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
}

resource "aws_security_group" "msk" {
  name   = "${var.cluster_name}-msk-sg"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port   = 9092
    to_port     = 9096
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
}

# ── IAM: RDS Enhanced Monitoring ──────────────────────────────────────────────
resource "aws_iam_role" "rds_monitoring" {
  name = "${var.cluster_name}-rds-monitoring"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# ── Outputs ───────────────────────────────────────────────────────────────────
output "cluster_endpoint"     { value = module.eks.cluster_endpoint }
output "cluster_name"         { value = module.eks.cluster_name }
output "ecr_registry"         { value = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com" }
output "kafka_bootstrap"      { value = aws_msk_cluster.kafka.bootstrap_brokers }
output "redis_endpoint"       { value = aws_elasticache_replication_group.redis.primary_endpoint_address }
