########################################################################
# aws/ecs/main.tf — ECS Fargate alternative deployment
########################################################################
terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  backend "s3" {
    bucket         = "platform-terraform-state"
    key            = "ecs/terraform.tfstate"
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

variable "aws_region"  { default = "ap-south-1" }
variable "environment" { default = "prod" }
variable "ecr_registry" {}

data "aws_vpc"            "main"    { tags = { Name = "platform-eks-vpc" } }
data "aws_subnets"        "private" { filter { name = "tag:Name"; values = ["*private*"] } }
data "aws_caller_identity" "current" {}

# ── ECS Cluster ───────────────────────────────────────────────────────────────
resource "aws_ecs_cluster" "platform" {
  name = "platform-ecs"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  configuration {
    execute_command_configuration {
      logging = "OVERRIDE"
      log_configuration {
        cloud_watch_log_group_name = aws_cloudwatch_log_group.ecs.name
      }
    }
  }
}

resource "aws_ecs_cluster_capacity_providers" "platform" {
  cluster_name       = aws_ecs_cluster.platform.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    base              = 1
    weight            = 70
    capacity_provider = "FARGATE"
  }
  default_capacity_provider_strategy {
    weight            = 30
    capacity_provider = "FARGATE_SPOT"
  }
}

# ── CloudWatch Logs ───────────────────────────────────────────────────────────
resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/platform"
  retention_in_days = 30
}

# ── IAM Roles ─────────────────────────────────────────────────────────────────
resource "aws_iam_role" "ecs_task_execution" {
  name = "platform-ecs-task-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_secrets" {
  name = "platform-ecs-secrets"
  role = aws_iam_role.ecs_task_execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue", "ssm:GetParameters"]
      Resource = "*"
    }]
  })
}

resource "aws_iam_role" "ecs_task" {
  name = "platform-ecs-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

# ── Application Load Balancer ─────────────────────────────────────────────────
resource "aws_lb" "platform" {
  name               = "platform-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = data.aws_subnets.private.ids

  enable_deletion_protection       = true
  enable_cross_zone_load_balancing = true
  enable_http2                     = true
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.platform.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.acm_certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api_gateway.arn
  }
}

resource "aws_lb_target_group" "api_gateway" {
  name        = "platform-api-gateway"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    interval            = 30
    timeout             = 10
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200"
  }
}

# ── ECS Services (Fargate) ────────────────────────────────────────────────────
locals {
  services = {
    api-gateway = {
      port        = 8080
      cpu         = 512
      memory      = 1024
      min_count   = 2
      max_count   = 10
      image       = "${var.ecr_registry}/platform/api-gateway:latest"
    }
    order-service = {
      port        = 8081
      cpu         = 512
      memory      = 1024
      min_count   = 2
      max_count   = 8
      image       = "${var.ecr_registry}/platform/order-service:latest"
    }
    payment-service = {
      port        = 8082
      cpu         = 512
      memory      = 1024
      min_count   = 2
      max_count   = 6
      image       = "${var.ecr_registry}/platform/payment-service:latest"
    }
    inventory-service = {
      port        = 8083
      cpu         = 256
      memory      = 512
      min_count   = 2
      max_count   = 6
      image       = "${var.ecr_registry}/platform/inventory-service:latest"
    }
    notification-service = {
      port        = 8084
      cpu         = 256
      memory      = 512
      min_count   = 1
      max_count   = 4
      image       = "${var.ecr_registry}/platform/notification-service:latest"
    }
    saga-orchestrator = {
      port        = 8085
      cpu         = 512
      memory      = 1024
      min_count   = 1
      max_count   = 2
      image       = "${var.ecr_registry}/platform/saga-orchestrator:latest"
    }
  }
}

resource "aws_ecs_task_definition" "services" {
  for_each = local.services

  family                   = "platform-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name  = each.key
    image = each.value.image

    portMappings = [{
      containerPort = each.value.port
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "ecs" },
      { name = "SERVER_PORT",            value = tostring(each.value.port) }
    ]

    secrets = [
      { name = "DB_PASSWORD", valueFrom = "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:platform/db-password" }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = each.key
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost:${each.value.port}/actuator/health || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 60
    }
  }])
}

resource "aws_ecs_service" "services" {
  for_each = local.services

  name            = each.key
  cluster         = aws_ecs_cluster.platform.id
  task_definition = aws_ecs_task_definition.services[each.key].arn
  desired_count   = each.value.min_count

  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 70
    base              = 1
  }
  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = 30
  }

  network_configuration {
    subnets          = data.aws_subnets.private.ids
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = each.key == "api-gateway" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.api_gateway.arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_controller {
    type = "ECS"
  }

  enable_execute_command = true

  lifecycle {
    ignore_changes = [desired_count]
  }
}

# ── Auto Scaling ──────────────────────────────────────────────────────────────
resource "aws_appautoscaling_target" "services" {
  for_each = local.services

  max_capacity       = each.value.max_count
  min_capacity       = each.value.min_count
  resource_id        = "service/${aws_ecs_cluster.platform.name}/${each.key}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  for_each = local.services

  name               = "platform-${each.key}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.services[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.services[each.key].scalable_dimension
  service_namespace  = aws_appautoscaling_target.services[each.key].service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 70.0
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}

# ── Security Groups ───────────────────────────────────────────────────────────
resource "aws_security_group" "alb" {
  name   = "platform-alb-sg"
  vpc_id = data.aws_vpc.main.id

  ingress { from_port = 80;  to_port = 80;  protocol = "tcp"; cidr_blocks = ["0.0.0.0/0"] }
  ingress { from_port = 443; to_port = 443; protocol = "tcp"; cidr_blocks = ["0.0.0.0/0"] }
  egress  { from_port = 0;   to_port = 0;   protocol = "-1";  cidr_blocks = ["0.0.0.0/0"] }
}

resource "aws_security_group" "ecs_tasks" {
  name   = "platform-ecs-tasks-sg"
  vpc_id = data.aws_vpc.main.id

  ingress {
    from_port       = 8080
    to_port         = 8086
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  ingress {
    from_port = 8080
    to_port   = 8086
    protocol  = "tcp"
    self      = true  # Allow inter-service communication
  }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}

variable "acm_certificate_arn" { description = "ACM certificate ARN for HTTPS" }

output "alb_dns_name"  { value = aws_lb.platform.dns_name }
output "cluster_name"  { value = aws_ecs_cluster.platform.name }
