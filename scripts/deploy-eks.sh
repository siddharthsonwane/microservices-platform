#!/usr/bin/env bash
# scripts/deploy-eks.sh — Build, push, and deploy to AWS EKS
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-south-1}"
CLUSTER_NAME="${CLUSTER_NAME:-platform-eks}"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"
NAMESPACE="platform"

SERVICES=(
  "service-discovery"
  "api-gateway"
  "order-service"
  "payment-service"
  "inventory-service"
  "notification-service"
  "saga-orchestrator"
)

echo "🚀 Deploying platform to EKS: ${CLUSTER_NAME}"
echo "   Region:    ${AWS_REGION}"
echo "   Registry:  ${ECR_REGISTRY}"
echo "   Image Tag: ${IMAGE_TAG}"
echo ""

# Build all
echo "📦 Building Maven project..."
./mvnw clean package -DskipTests -q

# Auth ECR
echo "🔑 Authenticating to ECR..."
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

# Build & push each service
for svc in "${SERVICES[@]}"; do
  echo "🐳 Building & pushing ${svc}..."
  docker build \
    -f "${svc}/Dockerfile" \
    --build-arg SERVICE_NAME="${svc}" \
    -t "${ECR_REGISTRY}/platform/${svc}:${IMAGE_TAG}" \
    -t "${ECR_REGISTRY}/platform/${svc}:latest" \
    .
  docker push "${ECR_REGISTRY}/platform/${svc}:${IMAGE_TAG}"
  docker push "${ECR_REGISTRY}/platform/${svc}:latest"
  echo "   ✓ ${svc} pushed"
done

# Update kubeconfig
echo "☸️  Updating kubeconfig for ${CLUSTER_NAME}..."
aws eks update-kubeconfig --name "${CLUSTER_NAME}" --region "${AWS_REGION}"

# Substitute env vars in manifests
TMP_DIR=$(mktemp -d)
cp -r k8s/ "${TMP_DIR}/"
find "${TMP_DIR}" -name "*.yaml" -exec \
  sed -i \
    "s|\${ECR_REGISTRY}|${ECR_REGISTRY}|g; \
     s|\${IMAGE_TAG}|${IMAGE_TAG}|g; \
     s|\${ACM_CERTIFICATE_ARN}|${ACM_CERTIFICATE_ARN:-arn:aws:acm:placeholder}|g; \
     s|\${WAF_ACL_ARN}|${WAF_ACL_ARN:-}|g" \
  {} +

# Apply manifests
echo "📋 Applying Kubernetes manifests..."
kubectl apply -f "${TMP_DIR}/k8s/base/00-namespace.yaml"
kubectl apply -f "${TMP_DIR}/k8s/base/01-configmaps-secrets.yaml"
kubectl apply -f "${TMP_DIR}/k8s/base/02-service-discovery.yaml"
kubectl rollout status deployment/service-discovery -n "${NAMESPACE}" --timeout=120s

kubectl apply -f "${TMP_DIR}/k8s/base/03-api-gateway.yaml"
kubectl rollout status deployment/api-gateway -n "${NAMESPACE}" --timeout=120s

kubectl apply -f "${TMP_DIR}/k8s/base/04-microservices.yaml"
for svc in order-service payment-service inventory-service notification-service saga-orchestrator; do
  echo "   Waiting for ${svc}..."
  kubectl rollout status deployment/"${svc}" -n "${NAMESPACE}" --timeout=120s
done

rm -rf "${TMP_DIR}"

echo ""
echo "✅ Deployment complete!"
kubectl get pods -n "${NAMESPACE}" -o wide
echo ""
INGRESS=$(kubectl get ingress api-gateway-ingress -n "${NAMESPACE}" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")
echo "🌐 API endpoint: https://${INGRESS}"
