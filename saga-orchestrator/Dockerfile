# syntax=docker/dockerfile:1.4
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace
COPY --link mvnw mvnw
COPY --link .mvn .mvn
COPY --link pom.xml pom.xml
COPY --link common-lib/pom.xml            common-lib/pom.xml
COPY --link service-discovery/pom.xml     service-discovery/pom.xml
COPY --link api-gateway/pom.xml           api-gateway/pom.xml
COPY --link order-service/pom.xml         order-service/pom.xml
COPY --link payment-service/pom.xml       payment-service/pom.xml
COPY --link inventory-service/pom.xml     inventory-service/pom.xml
COPY --link notification-service/pom.xml  notification-service/pom.xml
COPY --link saga-orchestrator/pom.xml     saga-orchestrator/pom.xml
COPY --link common-lib/src        common-lib/src
COPY --link saga-orchestrator/src saga-orchestrator/src
COPY --link auth-service/pom.xml auth-service/pom.xml
COPY --link auth-service/src auth-service/src
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && \
    ./mvnw -pl common-lib,saga-orchestrator -am install -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=builder /workspace/saga-orchestrator/target/*.jar app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
