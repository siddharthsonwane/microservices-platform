# syntax=docker/dockerfile:1.4
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace
COPY --link mvnw mvnw
COPY --link .mvn .mvn
COPY --link pom.xml pom.xml
COPY --link common-lib common-lib
COPY --link payment-service payment-service
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && \
    ./mvnw -pl common-lib,payment-service -am install -DskipTests -q

FROM eclipse-temurin:17-jdk-alpine AS extractor
WORKDIR /workspace
COPY --from=builder /workspace/payment-service/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=extractor /workspace/dependencies          ./dependencies
COPY --from=extractor /workspace/spring-boot-loader    ./spring-boot-loader
COPY --from=extractor /workspace/snapshot-dependencies ./snapshot-dependencies
COPY --from=extractor /workspace/application           ./application
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh","-c","java $JAVA_OPTS org.springframework.boot.loader.JarLauncher"]
