# ============================================================================
# Multi-Stage Dockerfile — Angular 21 + Spring Boot (one container)
# Target: Azure Container App (REQ-019)
# ============================================================================

# ----------------------------------------------------------------------------
# Stage 1 — Frontend build (Angular 21)
# ----------------------------------------------------------------------------
FROM node:22-alpine AS frontend-build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

COPY angular.json tsconfig.json tsconfig.app.json ./
COPY public ./public
COPY src ./src
RUN npm run build

# Output: /build/dist/angular-21-project/browser/

# ----------------------------------------------------------------------------
# Stage 2 — Backend build (Spring Boot via Gradle)
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /build

COPY server/gradlew server/settings.gradle server/build.gradle ./
COPY server/gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

COPY server/src ./src
RUN ./gradlew bootJar --no-daemon

# Output: /build/build/libs/autohaus-backend-0.0.1-SNAPSHOT.jar

# ----------------------------------------------------------------------------
# Stage 3 — Runtime
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN apk add --no-cache curl \
 && addgroup -g 1001 -S appgroup \
 && adduser -S appuser -u 1001 -G appgroup \
 && chown -R appuser:appgroup /app

COPY --from=backend-build --chown=appuser:appgroup /build/build/libs/autohaus-backend-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=frontend-build --chown=appuser:appgroup /build/dist/angular-21-project/browser /app/dist/angular-21-project/browser

USER appuser
EXPOSE 3000

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:3000/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
