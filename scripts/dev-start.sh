#!/usr/bin/env bash
# scripts/dev-start.sh — Start full local stack
set -euo pipefail

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

info "═══════════════════════════════════════════════════"
info "  Enterprise Microservices Platform — Dev Startup  "
info "═══════════════════════════════════════════════════"

# Check prerequisites
command -v docker      >/dev/null 2>&1 || error "Docker not installed"
command -v docker compose >/dev/null 2>&1 || error "Docker Compose not installed"
command -v java        >/dev/null 2>&1 || error "Java not installed"
command -v mvn         >/dev/null 2>&1 || warn  "Maven not found; using mvnw"

# Build all services
info "Building all services..."
./mvnw clean package -DskipTests -q \
  || error "Maven build failed"

info "Starting infrastructure (Kafka, Redis, PostgreSQL, Monitoring)..."
docker compose up -d \
  zookeeper kafka redis \
  postgres-orders postgres-payments postgres-inventory postgres-saga \
  otel-collector prometheus grafana loki promtail jaeger kafka-ui

info "Waiting for Kafka to be ready..."
timeout 60 bash -c 'until docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092 &>/dev/null; do sleep 2; done'
info "Kafka is ready ✓"

info "Creating Kafka topics..."
topics=(
  "order.created" "order.cancelled" "order.completed"
  "payment.process" "payment.processed" "payment.failed" "payment.refund"
  "inventory.reserve" "inventory.reserved" "inventory.failed" "inventory.release"
  "notification.send"
)
for topic in "${topics[@]}"; do
  docker exec kafka kafka-topics --create \
    --bootstrap-server localhost:9092 \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists 2>/dev/null || true
  docker exec kafka kafka-topics --create \
    --bootstrap-server localhost:9092 \
    --topic "${topic}.dlq" \
    --partitions 1 \
    --replication-factor 1 \
    --if-not-exists 2>/dev/null || true
done
info "Kafka topics created ✓"

info "Starting application services..."
docker compose up -d \
  service-discovery api-gateway \
  order-service payment-service inventory-service \
  notification-service saga-orchestrator

info "Waiting for services to be healthy..."
SERVICES=("service-discovery:8761" "api-gateway:8080" "order-service:8081" "payment-service:8082" "inventory-service:8083")
for svc in "${SERVICES[@]}"; do
  name="${svc%%:*}"
  port="${svc##*:}"
  echo -n "  Waiting for $name..."
  timeout 120 bash -c "until curl -sf http://localhost:${port}/actuator/health >/dev/null 2>&1; do sleep 3; done"
  echo -e " ${GREEN}✓${NC}"
done

echo ""
info "═══════════════════════════════ READY ══════════════════════════"
info "  API Gateway:       http://localhost:8080"
info "  Eureka Dashboard:  http://localhost:8761  (admin/admin123)"
info "  Kafka UI:          http://localhost:9000"
info "  Jaeger Tracing:    http://localhost:16686"
info "  Prometheus:        http://localhost:9090"
info "  Grafana:           http://localhost:3000  (admin/admin123)"
info "═══════════════════════════════════════════════════════════════"
echo ""

# Quick smoke test
info "Running smoke test..."
RESPONSE=$(curl -sf -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || echo "000")
if [[ "$RESPONSE" == "200" ]]; then
  info "Smoke test PASSED ✓ (Gateway returned $RESPONSE)"
else
  warn "Smoke test FAILED — Gateway returned $RESPONSE. Check docker compose logs."
fi
