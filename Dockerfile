# syntax=docker/dockerfile:1.6
# REQ-019 Phase 1 — LDP-compliant multi-stage Dockerfile.
#
# Bases mirror the LDP runner images so behaviour matches CI:
#   - frontend stage : ubi9/nodejs-22
#   - backend stage  : ubi9/openjdk-25
#   - runtime stage  : ubi9/openjdk-25
#
# OpenShift restricted-v2 SCC compliance:
#   - The base image's default user is non-root
#   - All writable directories are owned by group 0 with g=u perms
#   - At runtime OpenShift will assign a random UID; we set USER 1001 here
#     so plain `docker/podman run` (no SCC) also runs non-root
#
# Note: WebConfig serves Angular from the filesystem (./dist/...) next to the
# JAR, so we COPY the dist alongside the jar in the runtime stage instead of
# bundling it inside the JAR via Spring Boot's classpath.

# ===== Stage 1: Build Angular frontend =====
FROM registry.access.redhat.com/ubi9/nodejs-22 AS frontend-build

USER root
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps --ignore-scripts

COPY angular.json tsconfig.json tsconfig.app.json tsconfig.spec.json ./
COPY src/ ./src/
COPY public/ ./public/
RUN npm run build

# ===== Stage 2: Build Spring Boot fat JAR =====
FROM registry.access.redhat.com/ubi9/openjdk-25 AS backend-build

USER root
WORKDIR /workspace

# Gradle wrapper + build files first for layer caching
COPY server/gradlew server/build.gradle server/settings.gradle ./server/
COPY server/gradle/ ./server/gradle/

# Pre-warm dependency cache (best effort)
RUN chmod +x ./server/gradlew && \
    cd server && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Backend sources
COPY server/src/ ./server/src/

# Pre-built Angular dist from stage 1 — placed where the buildAngular task
# would have produced it, so we can skip that task with -x buildAngular.
COPY --from=frontend-build /app/dist/ ./dist/

RUN cd server && ./gradlew --no-daemon bootJar -x buildAngular -x test

# ===== Stage 3: Runtime =====
FROM registry.access.redhat.com/ubi9/openjdk-25

USER root
WORKDIR /app

# Fat JAR
COPY --from=backend-build /workspace/server/build/libs/*.jar /app/app.jar

# Angular dist next to the jar (WebConfig probes ./dist/angular-21-project/browser)
COPY --from=backend-build /workspace/dist/ /app/dist/

# Writable config dir for fingerprint Secret mount.
# OpenShift restricted-v2 SCC: random UID at runtime, always in group 0.
RUN mkdir -p /app/config && \
    chgrp -R 0 /app && \
    chmod -R g=u /app

# Default to non-root for plain podman/docker runs.
# OpenShift will override this with a random UID via SCC.
USER 1001

EXPOSE 3000

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
