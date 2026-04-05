# 🏗️ Enterprise Microservices Platform

**Java 17 · Spring Boot 3.2 · Spring Cloud 2023 · Kafka · PostgreSQL · Docker · Kubernetes · AWS EKS/ECS**

---

## Architecture Overview

```
                          ┌──────────────────────────────────────────────────────┐
                          │                    AWS Cloud                         │
                          │                                                      │
  Client ──HTTPS──► ALB ──► API Gateway (8080)                                   │
                          │      │  JWT Auth · Rate Limit (Redis) · Circuit Breaker│
                          │      ├──► Order Service      (8081) ──► PostgreSQL    │
                          │      ├──► Payment Service    (8082) ──► PostgreSQL    │
                          │      ├──► Inventory Service  (8083) ──► PostgreSQL    │
                          │      └──► Notification Svc   (8084)                  │
                          │                                                      │
                          │  Saga Orchestrator (8085) ──► PostgreSQL             │
                          │         │                                             │
                          │    Apache Kafka (MSK)                                │
                          │    ┌────┴──────────────────────┐                    │
                          │    │  Topics:                  │                    │
                          │    │  order.created            │                    │
                          │    │  inventory.reserve/d      │                    │
                          │    │  payment.process/ed       │                    │
                          │    │  notification.send        │                    │
                          │    └───────────────────────────┘                    │
                          │                                                      │
                          │  Service Discovery (Eureka 8761)                     │
                          │  Monitoring: Prometheus · Grafana · Jaeger · Loki   │
                          └──────────────────────────────────────────────────────┘
```

## Saga Pattern Flow — Order Processing

```
  POST /api/v1/orders
        │
        ▼
  Order Service ──publish──► [order.created]
                                    │
                                    ▼
                          Saga Orchestrator
                                    │
              ┌─────────── Step 1: RESERVE INVENTORY ───────────┐
              │                                                   │
              ▼ (success)                              (failure) │
  Inventory Service ──► [inventory.reserved]     [inventory.failed] ──► CANCEL_ORDER
              │
              ▼
  Saga Orchestrator ── Step 2: PROCESS PAYMENT
              │
              ├── (success) ──► Payment Service ──► [payment.processed]
              │                       │
              │                       ▼
              │               Step 3: SEND NOTIFICATION ──► COMPLETE_ORDER ✅
              │
              └── (failure) ──► RELEASE INVENTORY (compensate) ──► CANCEL_ORDER ❌
```

## Module Structure

```
microservices-platform/
├── common-lib/                 # Shared: events, DTOs, exceptions, Kafka config
├── service-discovery/          # Eureka Server (port 8761)
├── api-gateway/                # Spring Cloud Gateway (port 8080)
│   ├── JWT auth (OAuth2 Resource Server)
│   ├── Rate limiting (Redis token bucket)
│   └── Circuit breaker (Resilience4j) + fallbacks
├── order-service/              # Orders API (port 8081) + own PostgreSQL
├── payment-service/            # Payment processing (port 8082) + own PostgreSQL
├── inventory-service/          # Stock management (port 8083) + own PostgreSQL
├── notification-service/       # Email/SMS/Push (port 8084)
├── saga-orchestrator/          # Distributed transaction coordinator (port 8085)
├── docker-compose.yml          # Full local stack
├── k8s/base/                   # Kubernetes manifests (EKS)
├── aws/eks/main.tf             # Terraform — EKS cluster + RDS + MSK + ElastiCache
├── aws/ecs/main.tf             # Terraform — ECS Fargate alternative
├── .github/workflows/ci-cd.yml # GitHub Actions CI/CD
├── monitoring/                 # Prometheus, Grafana, Loki, OTel configs
└── scripts/                    # dev-start.sh, deploy-eks.sh
```

## Key Patterns Implemented

| Pattern                     | Implementation                                      |
|-----------------------------|-----------------------------------------------------|
| **API Gateway**             | Spring Cloud Gateway with JWT, rate-limit, CB       |
| **Service Discovery**       | Netflix Eureka (server + client on every service)   |
| **Saga (Orchestration)**    | Dedicated saga-orchestrator + Kafka events          |
| **Database per Service**    | Separate PostgreSQL instance per microservice       |
| **Circuit Breaker**         | Resilience4j on gateway + inter-service calls       |
| **Distributed Tracing**     | OpenTelemetry → Jaeger via OTLP                     |
| **Centralized Logging**     | Loki + Promtail + Grafana; MDC trace correlation    |
| **Metrics**                 | Micrometer → Prometheus → Grafana dashboards        |
| **Idempotency**             | Event de-duplication via DB checks (exists query)   |
| **Optimistic Locking**      | `@Version` on all JPA entities                      |
| **Pessimistic Locking**     | Inventory reservation uses `FOR UPDATE` lock        |
| **Dead Letter Queue**       | Kafka DLQ via `DefaultErrorHandler` + 3 retries     |
| **Schema Migration**        | Flyway on every service at startup                  |
| **Outbox Pattern**          | `outbox_events` table in order service              |
| **Health Checks**           | Spring Actuator liveness + readiness probes         |
| **Container Security**      | Non-root user, distroless JRE base image            |
| **HPA**                     | CPU/Memory-based auto-scaling on all services       |
| **PDB**                     | PodDisruptionBudget for zero-downtime deploys       |

## Quick Start — Local Development

```bash
# Prerequisites: Docker, Docker Compose, Java 17, Maven

git clone <repo> && cd microservices-platform

# Build everything
./mvnw clean package -DskipTests

# Start full stack (infra + all services)
chmod +x scripts/dev-start.sh && ./scripts/dev-start.sh
```

### Endpoints after startup

| Service              | URL                                      |
|----------------------|------------------------------------------|
| API Gateway          | http://localhost:8080                    |
| Eureka Dashboard     | http://localhost:8761 (admin/admin123)   |
| Kafka UI             | http://localhost:9000                    |
| Jaeger Tracing       | http://localhost:16686                   |
| Prometheus           | http://localhost:9090                    |
| Grafana              | http://localhost:3000 (admin/admin123)   |

### Test the Saga flow

```bash
# 1. Create an order (triggers full saga)
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d '{
    "customerId": "CUST-001",
    "shippingAddress": "123 Main St, Mumbai 400001",
    "items": [
      { "productId": "PROD-001", "productName": "Laptop Pro 15", "quantity": 1, "unitPrice": 85000.00 },
      { "productId": "PROD-002", "productName": "Wireless Mouse", "quantity": 2, "unitPrice": 1500.00 }
    ]
  }'

# 2. Check order status
curl http://localhost:8080/api/v1/orders/{orderId}

# 3. Watch Jaeger for distributed traces
open http://localhost:16686
```

## Deploy to AWS EKS

```bash
# 1. Provision infrastructure
cd aws/eks
terraform init && terraform plan && terraform apply

# 2. Export outputs
export AWS_REGION=ap-south-1
export CLUSTER_NAME=platform-eks
export ACM_CERTIFICATE_ARN=$(terraform output -raw acm_certificate_arn)

# 3. Build + push + deploy
chmod +x scripts/deploy-eks.sh && ./scripts/deploy-eks.sh
```

## Deploy to AWS ECS (Fargate)

```bash
cd aws/ecs
terraform init && terraform plan && terraform apply
# CI/CD auto-deploys on push to develop branch via GitHub Actions
```

## Environment Variables Reference

| Variable                        | Description                     | Default              |
|---------------------------------|---------------------------------|----------------------|
| `SPRING_PROFILES_ACTIVE`        | Active profile                  | `default`            |
| `EUREKA_URL`                    | Eureka server URL               | `http://localhost:8761/eureka/` |
| `KAFKA_BOOTSTRAP_SERVERS`       | Kafka brokers                   | `localhost:9092`     |
| `DB_HOST`                       | PostgreSQL hostname             | `localhost`          |
| `DB_USER` / `DB_PASSWORD`       | Database credentials            | service-specific     |
| `REDIS_HOST` / `REDIS_PORT`     | Redis for rate limiting         | `localhost:6379`     |
| `OTEL_EXPORTER_OTLP_ENDPOINT`  | OpenTelemetry collector         | `http://localhost:4318/v1/traces` |
| `JWT_ISSUER_URI`                | OAuth2 JWT issuer               | `http://localhost:9000` |

## Technology Stack

| Layer              | Technology                                          |
|--------------------|-----------------------------------------------------|
| Language           | Java 17 (records, sealed classes, text blocks)      |
| Framework          | Spring Boot 3.2, Spring Cloud 2023                  |
| Gateway            | Spring Cloud Gateway (reactive/WebFlux)             |
| Service Discovery  | Netflix Eureka                                      |
| Messaging          | Apache Kafka 3.6                                    |
| Persistence        | Spring Data JPA, Hibernate 6, Flyway               |
| Database           | PostgreSQL 16 (per service)                         |
| Cache/Rate-limit   | Redis 7 (Lettuce client)                            |
| Circuit Breaker    | Resilience4j 2.x                                   |
| Tracing            | OpenTelemetry → Jaeger                              |
| Metrics            | Micrometer → Prometheus → Grafana                  |
| Logging            | Logback + MDC + Loki + Promtail                     |
| Containers         | Docker (multi-stage, layered jars)                  |
| Orchestration      | Kubernetes 1.29 (EKS)                              |
| Infra-as-Code      | Terraform 1.6                                       |
| CI/CD              | GitHub Actions                                      |
| Registry           | Amazon ECR                                          |
| Cloud              | AWS (EKS, ECS Fargate, RDS, MSK, ElastiCache)      |
