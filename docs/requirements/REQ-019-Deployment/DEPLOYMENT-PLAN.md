# REQ-019 Deployment Plan — AutoHausTermin on LHIND DevOps Platform

**Status:** PLAN (not yet implemented)  
**Date:** 2026-04-21  
**Backend:** Spring Boot 4.1 (Java 25, Gradle) — per REQ-018  
**Frontend:** Angular 21 (served as static files by Spring Boot)  
**Target:** LHIND DevOps Platform (LDP) — OpenShift on `ocp04`  
**Reference:** LDP Java Template (project ID 2290 + environment 2291)

---

## Table of Contents

1. [Executive Summary & Key Corrections to REQ-019](#1-executive-summary--key-corrections-to-req-019)
2. [Current Codebase Overview](#2-current-codebase-overview)
3. [LDP Environment Constraints](#3-ldp-environment-constraints)
4. [Architecture: Two-Repo GitOps Model](#4-architecture-two-repo-gitops-model)
5. [Phase 1: Dockerfile (Spring Boot)](#5-phase-1-dockerfile-spring-boot)
6. [Phase 2: Application Repository CI/CD](#6-phase-2-application-repository-cicd)
7. [Phase 3: Environment Repository & Helm Charts](#7-phase-3-environment-repository--helm-charts)
8. [Phase 4: Secrets & External Secrets Operator](#8-phase-4-secrets--external-secrets-operator)
9. [Phase 5: OpenShift Namespace Setup](#9-phase-5-openshift-namespace-setup)
10. [CI/CD Variables to Configure](#10-cicd-variables-to-configure)
11. [Service Requests (EMMA Tickets)](#11-service-requests-emma-tickets)
12. [Open Questions for Platform Team](#12-open-questions-for-platform-team)
13. [Risk Register](#13-risk-register)
14. [Implementation Checklist](#14-implementation-checklist)

---

## 1. Executive Summary & Key Corrections to REQ-019

The current REQ-019 requirement document was written assuming a **NestJS backend**. Per REQ-018, the backend has been migrated to **Spring Boot 4.1 (Java 25, Gradle)**. Additionally, REQ-019 assumed infrastructure that does **not exist** on the LDP. The following corrections are critical:

### What REQ-019 Gets Wrong

| REQ-019 Assumption | LDP Reality | Impact |
|---|---|---|
| **GitLab Container Registry** (`registry.gitlab.com/...`) | **NOT available** on LDP. Must use **OpenShift Internal Registry** (`image-registry.openshift-image-registry.svc:5000`) | All image push/pull references must change |
| **Docker** for image building | **Buildah** only (no Docker socket). LDP provides `ocp.yml` CI template | Dockerfile stays but build command changes |
| **`docker:24-dind`** service in CI | Not available. Use `registry.access.redhat.com/ubi8/buildah:latest` | Build stage completely different |
| **Helm chart in-repo** (`deploy/helm/`) | LDP pattern: **separate environment repository** with Helm base chart published as GitLab Package | Need to create a 2nd repo |
| **`node:22-alpine`** base image | Must use **approved base images** — for Spring Boot: `registry.access.redhat.com/ubi9/openjdk-17:latest` (or Java 21 equivalent). External images must be whitelisted. | Dockerfile base image changes |
| **NestJS backend** (`node backend/dist/main.js`) | **Spring Boot** (`java -jar app.jar`) on port 3000 | Dockerfile, health checks, and all backend references change |
| **`alpine/helm:3.14`** for deployment | Use `image-registry.openshift-image-registry.svc:5000/openshift/lhind-ocp-tools` | Deploy stage image changes |
| **Secrets as GitLab CI/CD vars → Helm `--set`** | **OpenShift-managed Secrets** created via OpenShift Console/CLI. Helm references pre-existing secrets by name. No ESO needed. | Secrets architecture fundamentally different |
| **`Deployment`** (standard K8s) | **`Deployment`** (OpenShift supports it, not DeploymentConfig) + `ImageStream` annotation trigger | Template follows LDP pattern |
| **Ingress** | **Route** (OpenShift Route with TLS edge termination) | Already correct in REQ-019 |
| **Custom SecurityContext** (UID 1001) | OpenShift `restricted-v2` SCC: UID is **auto-assigned** (random). Use group `0` for permissions | Dockerfile and Helm securityContext change |

### What REQ-019 Gets Right

- TLS Edge Termination via OpenShift Route ✅
- Non-root container ✅ (but UID must be random, not 1001)
- Health endpoint at `/api/v1/health` ✅
- Environment-specific values files (dev/prod) ✅
- Semantic versioning ✅
- Structured JSON logging ✅

---

## 2. Current Codebase Overview

### Project Structure

```
autohausterminbuchung/
├── src/                          # Angular 21 frontend
│   ├── app/                      # Components, services, NgRx store
│   ├── styles/                   # SCSS
│   └── index.html
├── server/                       # Spring Boot 4.1 backend (Java 25)
│   ├── build.gradle              # Gradle build config
│   ├── gradlew                   # Gradle wrapper
│   └── src/main/java/com/autohaus/backend/
│       ├── BackendApplication.java
│       ├── config/               # SecurityConfig, WebConfig, SoftnetProperties, DotenvConfig
│       ├── softnet/              # SoftnetApiClient, SoftnetAuthService (RSA JWT)
│       ├── appointments/         # Booking CRUD
│       ├── branches/             # Workshop locations
│       ├── brands/               # Car brands
│       ├── cars/                 # Customer vehicles
│       ├── customers/            # Customer lookup
│       ├── health/               # Health check
│       ├── jobtypes/             # Service types
│       ├── mobility/             # Replacement cars
│       ├── session/              # Session token (HMAC-SHA256)
│       ├── snaps/                # Time slots (cached)
│       └── common/               # Filters, exception handling, DTOs
├── playwright/                   # E2E tests
├── docs/requirements/            # All requirements
├── angular.json, package.json    # Frontend config
└── proxy.conf.json               # Dev proxy
```

### Key Architecture Facts

- **Single process serves everything:** Spring Boot on `:3000` serves Angular static files + REST API
- **SoftNet integration:** All business data via `SoftnetApiClient` with RSA JWT authentication
- **Session tokens:** HMAC-SHA256 signed, validated per-request via `SessionTokenFilter`
- **Filters:** RequestId → Logging → RateLimit → Origin → SessionToken
- **Fingerprint files:** RSA private key JSON files for SoftNet auth, loaded from `SOFTNET_FINGERPRINT_JSON` env var (inline JSON, local dev) or `SOFTNET_FINGERPRINT_PATH` file fallback (K8s volume mount)
- **Configuration:** `application.yml` with `${ENV_VAR:default}` pattern + `DotenvConfig` for local `.env`

---

## 3. LDP Environment Constraints

### Runner Configuration

| Property | Value |
|---|---|
| Runner ID | `#581 (jdhAjbXh)` |
| Type | Group runner (`atapp`) |
| Tags | `openshift`, `atapp`, `ATAPP` |
| Status | Online / Idle |
| Cluster | `ocp04` |

**Critical:** The CI pipeline must use tags `atapp` and `openshift` (not `example-projects` like the template).

### Available Infrastructure

| Service | Available | Details |
|---|---|---|
| GitLab CI/CD | ✅ | Runners available via group runner |
| OpenShift Internal Registry | ✅ | `image-registry.openshift-image-registry.svc:5000/<namespace>/` |
| JFrog Artifactory | ✅ | `lhindrepo02.jfrog.io` for build artifacts + vulnerability scanning |
| SonarQube | ✅ | `codequality01.lhind.app.lufthansa.com` (needs EMMA service request) |
| GitLab Container Registry | ❌ | NOT available on LDP |
| Docker/DinD | ❌ | Buildah only |
| External image pull | ⚠️ | Must be whitelisted |

### Buildah CI Template

The LDP provides shared CI templates at `lhind-devops-platform/gitlab-ci-templates`:

- **`ocp.yml`** — `ocp-build` (buildah build only), `ocp-build-and-scan` (buildah + JFrog Xray — requires Xray credentials, deferred), `ocp-push` (push to OCP registry)
- **`artifactory.yml`** — JFrog CLI configuration + wrapper scripts (npm/mvn/pip → jf)
- **`frogbot.yml`** — Merge request security scanning

### Image Strategy

```
Build → image-registry.openshift-image-registry.svc:5000/autohandel-terminbuchungs-app-dev/<app>:<tag>
Promote → oc tag autohandel-terminbuchungs-app-dev/<app>:<tag>  autohandel-terminbuchungs-app-prod/<app>:<tag>
```

Images are **not pushed to JFrog**. They live in the OpenShift internal registry and are promoted between namespaces via `oc tag`.

---

## 4. Architecture: Two-Repo GitOps Model

Following the LDP Java Template pattern, deployment uses **two separate repositories**:

### Repo 1: Application Repository (`autohausterminbuchung`)

**Contains:**
- Angular frontend source (`src/`)
- Spring Boot backend source (`server/`)
- Dockerfile
- `.gitlab-ci.yml` — build, test, image build, version bump, trigger env repo MR
- `version.txt`, `version_full.txt` — version tracking (shell-based, no semantic-release — PAT push not reliable on this GitLab instance)

**CI Pipeline Stages:**
```
test → versioning → build (frontend → backend) → image → rollout
```

### Repo 2: Environment Repository (`autohausterminbuchung.environment`)

**Contains:**
- `base/` — reusable Helm chart (templates + defaults)
- `environments/dev/` — dev values override
- `environments/prod/` — prod values override  
- `.gitlab-ci.yml` — `package` (helm package + push) → `deploy-dev` → `deploy-prod`

**CI Pipeline Stages:**
```
package → deploy-dev → deploy-prod → validate
```

### Flow

```
Developer pushes to feature/* branch
    ↓
App CI: test → build jar → buildah image → push to dev registry
    ↓
App CI: update version in env repo → create MR in env repo
    ↓
Env MR merged → Env CI: helm upgrade --install to dev namespace
    ↓
Release to main → App CI: build release image → promote to prod registry
    ↓
App CI: create MR for prod values in env repo
    ↓
Manual merge of prod MR → Env CI: helm upgrade --install to prod namespace
```

---

## 5. Phase 1: Dockerfile (Spring Boot)

The Dockerfile must be rewritten for Spring Boot (replacing the NestJS Dockerfile in REQ-019).

### Key Differences from REQ-019 Dockerfile

| Aspect | REQ-019 (NestJS) | Corrected (Spring Boot) |
|---|---|---|
| Base build image | `node:22-alpine` | `image-registry...:5000/openshift/jdk-25-runner` + `nodejs-22-runner` (split build) |
| Base runtime image | `node:22-alpine` | `registry.access.redhat.com/ubi9/openjdk-25` |
| Build command | `npm run build` + `npm ci` | `./gradlew bootJar` (builds fat JAR with Angular assets) |
| Runtime command | `node backend/dist/main.js` | `java -jar app.jar` |
| User | `appuser:1001` | No explicit UID (OpenShift assigns random UID, must be in group `0`) |
| Angular assets | Separate COPY | Bundled inside the JAR by Gradle `bootJar` task |
| Port | 3000 | 3000 (configured in `application.yml`) |
| Health check | `wget` | `curl` or remove (OpenShift probes handle this) |

### Proposed Dockerfile

```dockerfile
# ===== Stage 1: Build =====
FROM registry.access.redhat.com/ubi9/nodejs-22 AS frontend-build

USER root
WORKDIR /app

# Build Angular frontend
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY src/ ./src/
COPY angular.json tsconfig*.json ./
RUN npm run build

# ===== Stage 2: Build Spring Boot JAR =====
FROM registry.access.redhat.com/ubi9/openjdk-25 AS backend-build

USER root
WORKDIR /app

# Copy Gradle wrapper and config
COPY server/gradlew server/build.gradle server/settings.gradle ./server/
COPY server/gradle/ ./server/gradle/
COPY server/src/ ./server/src/

# Copy pre-built Angular dist from frontend stage
COPY --from=frontend-build /app/dist/ ./dist/

# Build Spring Boot fat JAR (skip Angular build — dist/ already exists)
WORKDIR /app/server
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x buildAngular

# ===== Stage 3: Runtime =====
FROM registry.access.redhat.com/ubi9/openjdk-25

WORKDIR /app

# Copy the fat JAR
COPY --from=build /app/server/build/libs/*.jar /app/app.jar

# Config directory for fingerprint files (mounted as Secret volume)
RUN mkdir -p /app/config && \
    chgrp -R 0 /app/config && \
    chmod -R g=u /app/config

EXPOSE 3000

CMD ["java", "-jar", "/app/app.jar"]
```

### Important Notes

1. **No explicit USER directive** — OpenShift `restricted-v2` SCC assigns a random UID. The UID is always a member of group `0` (root group), so `chgrp -R 0` + `chmod g=u` grants write access.
2. **Three-stage build** — Stage 1 (`ubi9/nodejs-22`) builds Angular, Stage 2 (`ubi9/openjdk-25`) builds the Spring Boot JAR with pre-built Angular assets, Stage 3 (`ubi9/openjdk-25`) is the minimal runtime.
3. **Java version:** ✅ Verified — `jdk-25-runner` (project 4835) is available on LDP with `ubi9/openjdk-25` base. No `build.gradle` changes needed.
4. **Gradle `-x buildAngular` flag:** The `bootJar` task must skip the Angular build when `dist/` is pre-provided. This requires the Gradle `buildAngular` task to be skippable (add `onlyIf { !file('dist/...').exists() }` or use `-x buildAngular`).

### ✅ Java Version — Verified

**JDK 25 is available** on LDP as `image-registry.openshift-image-registry.svc:5000/openshift/jdk-25-runner:latest` (project 4835, base `ubi9/openjdk-25`). No changes to `build.gradle` required.

Also available: JDK 21 (project 4768), JDK 17 (project 354).

### ⚠️ Split Build Required — JDK Runner Lacks Node.js

**Verified:** JDK runner images contain only JDK + oc + jf + helm + sonar-scanner + grype. **No Node.js.**

Since `./gradlew bootJar` calls `buildAngular` (which runs `npm run build`), the CI must split into two stages:

1. **`frontend build`** — `nodejs-22-runner` runs `npm ci && npm run build` → artifact `dist/`
2. **`backend build`** — `jdk-25-runner` runs `./gradlew bootJar` using pre-built `dist/` from artifacts

The Gradle `buildAngular` task must detect pre-existing `dist/` and skip the Angular build, OR we add a Gradle flag/task that only packages the JAR without triggering `npm run build`.

**Alternative:** Create a custom runner image with both JDK 25 + Node.js 22 (request via EMMA). This is cleaner but adds a dependency on image maintenance.

---

## 6. Phase 2: Application Repository CI/CD

### `.gitlab-ci.yml` (Application Repo)

This replaces the NestJS-based CI in REQ-019 entirely. Modeled after the LDP Java Template.

```yaml
# .gitlab-ci.yml — Application Repository
stages:
  - test
  - versioning
  # - sonarqube_scan   # DEFERRED — uncomment once SonarQube project is provisioned
  - build
  - image
  - rollout

default:
  tags:
    - atapp       # ← project-specific runner tag
    - openshift

# ─── Rule Anchors ───
.rules_main_only: &rules_main_only
  - if: '$CI_COMMIT_BRANCH == "main"'
    changes:
      - src/**/*
      - server/**/*
      - package.json
      - package-lock.json
      - angular.json
    when: on_success

.rules_main_or_feature: &rules_main_or_feature
  - if: '$CI_COMMIT_BRANCH == "main"'
    changes: [src/**/*, server/**/*, package.json, angular.json]
    when: on_success
  - if: '$CI_COMMIT_REF_NAME =~ /^(feature|hotfix)\/.+$/ && $CI_PIPELINE_SOURCE == "merge_request_event"'
    changes: [src/**/*, server/**/*, package.json, angular.json]
    when: on_success

.rules_feature_only: &rules_feature_only
  - if: '$CI_COMMIT_REF_NAME =~ /^(feature|hotfix)\/.+$/ && $CI_PIPELINE_SOURCE == "merge_request_event"'
    changes: [src/**/*, server/**/*, package.json, angular.json]
    when: on_success

# ─── External Templates ───
include:
  - project: 'lhind-devops-platform/gitlab-ci-templates'
    file: 'ocp.yml'
  - project: 'lhind-devops-platform/gitlab-ci-templates'
    file: 'artifactory.yml'
  - project: 'lhind-devops-platform/gitlab-ci-templates'
    file: 'frogbot.yml'
  - template: Security/Secret-Detection.gitlab-ci.yml

# ─── .pre: Vulnerability Scan ───
vulnerability scan:
  stage: .pre
  image: image-registry.openshift-image-registry.svc:5000/openshift/jdk-17-runner:latest
  extends: .artifactory-config
  script:
    - jf audit --watches <YOUR_WATCHER_NAME>
  allow_failure: true
  rules: *rules_main_or_feature

# ─── TEST: Gradle tests ───
gradle tests:
  stage: test
  image: image-registry.openshift-image-registry.svc:5000/openshift/jdk-17-runner:latest
  extends: .artifactory-base
  variables:
    ARTIFACTORY_VIRTUAL_REPO: <YOUR_MVN_VIRTUAL_REPO>
    MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
  cache:
    key: gradle-cache
    paths:
      - server/.gradle/
      - .m2/repository
  script:
    - cd server && ./gradlew test
  artifacts:
    when: always
    reports:
      junit: server/build/test-results/test/*.xml
    paths:
      - server/build/reports/
  rules: *rules_main_or_feature

# ─── TEST: Frontend lint + unit tests ───
frontend tests:
  stage: test
  image: image-registry.openshift-image-registry.svc:5000/openshift/nodejs-20-runner:latest
  cache:
    key: npm-cache
    paths:
      - node_modules/
  script:
    - npm ci
    - npm run lint
    - npm run test -- --ci --coverage
  artifacts:
    when: always
    paths:
      - coverage/
  rules: *rules_main_or_feature

# ─── VERSIONING: local shell-based semver from conventional commits ───
# No remote access required. Analyzes commits since last tag to determine
# major/minor/patch bump. Writes version.txt + version_full.txt.
semantic versioning:
  stage: versioning
  image: image-registry.openshift-image-registry.svc:5000/openshift/nodejs-22-runner:latest
  variables:
    GIT_DEPTH: "0"
  script:
    - |
      LATEST_TAG=$(git tag --sort=-v:refname | grep -E '^v?[0-9]+\.[0-9]+\.[0-9]+$' | head -1 || true)
      if [ -z "$LATEST_TAG" ]; then
        LATEST_TAG="0.0.0"; RANGE="HEAD"
      else
        RANGE="${LATEST_TAG}..HEAD"
      fi
      BASE=$(echo "$LATEST_TAG" | tr -d 'v')
      MAJOR=$(echo "$BASE" | cut -d. -f1)
      MINOR=$(echo "$BASE" | cut -d. -f2)
      PATCH=$(echo "$BASE" | cut -d. -f3)
      HAS_BREAKING=false; HAS_FEAT=false; HAS_FIX=false
      while IFS= read -r msg; do
        case "$msg" in
          *"BREAKING CHANGE"*|*"!"*) HAS_BREAKING=true ;;
          feat*) HAS_FEAT=true ;;
          fix*) HAS_FIX=true ;;
        esac
      done <<< "$(git log --format='%s' $RANGE 2>/dev/null)"
      if [ "$HAS_BREAKING" = true ]; then MAJOR=$((MAJOR+1)); MINOR=0; PATCH=0
      elif [ "$HAS_FEAT" = true ]; then MINOR=$((MINOR+1)); PATCH=0
      elif [ "$HAS_FIX" = true ]; then PATCH=$((PATCH+1))
      else PATCH=$((PATCH+1)); fi
      VERSION="${MAJOR}.${MINOR}.${PATCH}"
      SLUG=$(echo "$CI_COMMIT_REF_NAME" | sed 's/[^a-zA-Z0-9.-]/-/g')
      VERSION_FULL="${VERSION}-${SLUG}.${CI_COMMIT_SHORT_SHA}"
      echo "$VERSION" > version.txt
      echo "$VERSION_FULL" > version_full.txt
      echo "Version: $VERSION"
      echo "Version full: $VERSION_FULL"
  artifacts:
    paths:
      - version.txt
      - version_full.txt
    expire_in: 1 day
  rules: *rules_main_or_feature

# ─── SONARQUBE (DEFERRED — enable once SonarQube project is provisioned) ───
.sonarqube scan:    # leading dot disables this job; remove dot to enable
  stage: sonarqube_scan
  image: image-registry.openshift-image-registry.svc:5000/openshift/jdk-17-runner:latest
  extends: .artifactory-base
  variables:
    SONAR_HOST_URL: "https://codequality01.lhind.app.lufthansa.com/"
    SONAR_PROJECT_KEY: "<YOUR_SONAR_PROJECT_KEY>"
    ARTIFACTORY_VIRTUAL_REPO: <YOUR_MVN_VIRTUAL_REPO>
  script:
    - cd server
    - ./gradlew test jacocoTestReport sonar
        -Dsonar.projectKey=$SONAR_PROJECT_KEY
        -Dsonar.host.url=$SONAR_HOST_URL
        -Dsonar.token=$SONAR_TOKEN
        -Dsonar.branch.name=$CI_COMMIT_REF_NAME
  rules: *rules_main_or_feature

# ─── BUILD: Split into frontend (nodejs) + backend (jdk) ───
# JDK runner has no Node.js; microdnf/dnf blocked by restricted-v2 SCC.
frontend build:
  stage: build
  image: image-registry.openshift-image-registry.svc:5000/openshift/nodejs-22-runner:latest
  cache:
    key:
      files:
        - package-lock.json
    paths:
      - node_modules/
  script:
    - npm ci --legacy-peer-deps
    - npm run build
  artifacts:
    paths:
      - dist/
    expire_in: 1 day
  rules: *rules_main_or_feature

backend build:
  stage: build
  image: image-registry.openshift-image-registry.svc:5000/openshift/jdk-25-runner:latest
  needs:
    - frontend build
  cache:
    key:
      files:
        - server/build.gradle
    paths:
      - server/.gradle/
  script:
    - cd server && ./gradlew --no-daemon bootJar -x buildAngular -x npmInstall
  artifacts:
    paths:
      - server/build/libs/*.jar
    expire_in: 1 day
  rules: *rules_main_or_feature

# ─── IMAGE: Build RC image (feature/hotfix) ───
build dev rc image:
  stage: image
  image: registry.access.redhat.com/ubi8/buildah:latest
  extends: .ocp-build
  needs:
    - frontend build
    - backend build
    - semantic versioning
  script:
    - VERSION=$(cat version_full.txt)
    - TAG_RC="${VERSION}-rc"
    # ocp-build-and-scan skipped — JFrog Xray credentials not provisioned yet (deferred ticket D2)
    - ocp-build ./Dockerfile autohandel-terminbuchungs-app-dev $CI_PROJECT_NAME:$TAG_RC
    - ocp-push autohandel-terminbuchungs-app-dev $CI_PROJECT_NAME:$TAG_RC
  rules: *rules_feature_only

# ─── IMAGE: Build release image (main) ───
build release image:
  stage: image
  image: registry.access.redhat.com/ubi8/buildah:latest
  extends: .ocp-build
  needs:
    - frontend build
    - backend build
    - semantic versioning
  script:
    - VERSION=$(cat version.txt)
    - TAG="v${VERSION}"
    # ocp-build-and-scan skipped — JFrog Xray credentials not provisioned yet (deferred ticket D2)
    - ocp-build ./Dockerfile autohandel-terminbuchungs-app-dev $CI_PROJECT_NAME:$TAG
    - ocp-push autohandel-terminbuchungs-app-dev $CI_PROJECT_NAME:$TAG
  rules: *rules_main_only

# ─── IMAGE: Promote release to prod ───
promote release image:
  stage: image
  image: image-registry.openshift-image-registry.svc:5000/openshift/lhind-ocp-tools
  needs:
    - build release image
  script:
    - VERSION=$(cat version.txt)
    - TAG="v${VERSION}"
    - oc tag --reference-policy=local autohandel-terminbuchungs-app-dev/$CI_PROJECT_NAME:$TAG autohandel-terminbuchungs-app-prod/$CI_PROJECT_NAME:$TAG
  rules: *rules_main_only

# ─── ROLLOUT: Update env repo + create MR ───
deploy feature:
  stage: rollout
  image: image-registry.openshift-image-registry.svc:5000/openshift/lhind-ocp-tools
  variables:
    TARGET_PROJECT_URL: "<GROUP_PATH>/autohausterminbuchung.environment.git"
    TARGET_PROJECT_BRANCH: "main"
  needs:
    - semantic versioning
    - build dev rc image
  script:
    - |
      VERSION=$(cat version_full.txt)
      TAG_RC="${VERSION}-rc"
      # ENV_REPO_TOKEN: Project Access Token from env repo (Developer role, read+write_repository).
      # CI_JOB_TOKEN does not support cross-project push on this GitLab instance.
      git clone https://oauth2:${ENV_REPO_TOKEN}@$CI_SERVER_HOST/$TARGET_PROJECT_URL
      cd autohausterminbuchung.environment
      git checkout $TARGET_PROJECT_BRANCH
      BRANCH_NAME="version-update-${CI_COMMIT_SHORT_SHA}"
      git checkout -b $BRANCH_NAME
      git config --global user.email "ci@autohaus.lhind.app"
      git config --global user.name "CI Bot"
      yq eval ".global.appVersion = \"$TAG_RC\"" -i environments/dev/values.yaml
      git add environments/dev/values.yaml
      git commit -m "Update appVersion in dev to $TAG_RC"
      git push https://oauth2:${ENV_REPO_TOKEN}@$CI_SERVER_HOST/$TARGET_PROJECT_URL HEAD:$BRANCH_NAME \
        -o merge_request.create=true \
        -o merge_request.target=$TARGET_PROJECT_BRANCH \
        -o merge_request.remove_source_branch \
        -o merge_request.merge_when_pipeline_succeeds
  rules: *rules_feature_only

create release:
  stage: rollout
  image: image-registry.openshift-image-registry.svc:5000/openshift/lhind-ocp-tools
  variables:
    TARGET_PROJECT_URL: "<GROUP_PATH>/autohausterminbuchung.environment.git"
    TARGET_PROJECT_BRANCH: "main"
  needs:
    - semantic versioning
    - build release image
    - promote release image
  script:
    - |
      VERSION=$(cat version.txt)
      TAG="v${VERSION}"
      # ENV_REPO_TOKEN: Project Access Token from env repo (Developer role, read+write_repository).
      # CI_JOB_TOKEN does not support cross-project push on this GitLab instance.
      git clone https://oauth2:${ENV_REPO_TOKEN}@$CI_SERVER_HOST/$TARGET_PROJECT_URL
      cd autohausterminbuchung.environment
      git checkout $TARGET_PROJECT_BRANCH
      git config --global user.email "ci@autohaus.lhind.app"
      git config --global user.name "CI Bot"
      # DEV + PROD MRs
      BRANCH_DEV="release/$TAG-DEV"
      git checkout -b $BRANCH_DEV
      yq eval ".global.appVersion = \"$TAG\"" -i environments/dev/values.yaml
      git add environments/dev/values.yaml
      git commit -m "Release $TAG to dev"
      git push https://oauth2:${ENV_REPO_TOKEN}@$CI_SERVER_HOST/$TARGET_PROJECT_URL HEAD:$BRANCH_DEV \
        -o merge_request.create=true \
        -o merge_request.target=$TARGET_PROJECT_BRANCH \
        -o merge_request.remove_source_branch \
        -o merge_request.merge_when_pipeline_succeeds
      git checkout $TARGET_PROJECT_BRANCH
      BRANCH_PROD="release/$TAG"
      git checkout -b $BRANCH_PROD
      yq eval ".global.appVersion = \"$TAG\"" -i environments/prod/values.yaml
      git add environments/prod/values.yaml
      git commit -m "Release $TAG to prod"
      git push https://oauth2:${ENV_REPO_TOKEN}@$CI_SERVER_HOST/$TARGET_PROJECT_URL HEAD:$BRANCH_PROD \
        -o merge_request.create=true \
        -o merge_request.target=$TARGET_PROJECT_BRANCH \
        -o merge_request.remove_source_branch
  rules: *rules_main_only
```

### Notes

- **Tags:** Changed from `example-projects` + `ocp03` to `atapp` + `openshift` to match the assigned runner.
- **Node.js in JDK runner:** ✅ **Verified — JDK runners do NOT include Node.js.** The CI must use a split build: `nodejs-22-runner` for Angular → `jdk-25-runner` for Gradle. Installing Node.js via `microdnf` is blocked by `restricted-v2` SCC (non-root).
- **`ENV_REPO_TOKEN`:** Project Access Token created **on the env repo** with **Developer** role and `read_repository` + `write_repository` scopes. Stored as masked+protected CI variable in the app repo. `CI_JOB_TOKEN` cross-project push is not supported on this GitLab instance. Token role must be ≥ Developer (Guest/Reporter cannot read or write repos regardless of scopes).
- **Image build:** Uses `ocp-build` (build-only). `ocp-build-and-scan` requires JFrog Xray credentials (deferred ticket D2).
- **Auto-merge:** The bot user created by the env repo PAT needs merge permissions on `main`. Either allow Developers to merge in protected branch settings, or merge MRs manually.

---

## 7. Phase 3: Environment Repository & Helm Charts

### Create New Repository

Create `autohausterminbuchung.environment` in the same GitLab group as the app repo.

### Directory Structure

```
autohausterminbuchung.environment/
├── .gitlab-ci.yml
├── version.txt
├── README.md
├── base/
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── _helpers.tpl
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── route.yaml
│       ├── serviceaccount.yaml
│       ├── configmap.yaml
│       ├── secretstore.yaml
│       ├── externalsecret.yaml
│       └── hpa.yaml          (optional)
├── environments/
│   ├── dev/
│   │   ├── Chart.yaml
│   │   └── values.yaml
│   └── prod/
│       ├── Chart.yaml
│       └── values.yaml
```

### `base/values.yaml` (Defaults)

```yaml
global:
  app:
    name: autohausterminbuchung
  appVersion: "0.1.0"

deployment:
  replicaCount: 1
  serviceAccountName: autohausterminbuchung
  image:
    repository: image-registry.openshift-image-registry.svc:5000/autohandel-terminbuchungs-app-dev/autohausterminbuchung
    pullPolicy: Always
  service:
    type: ClusterIP
  ports:
    - name: http
      containerPort: 3000
      port: 3000
      protocol: TCP
  resources:
    requests:
      memory: "256Mi"
      cpu: "100m"
    limits:
      memory: "512Mi"
      cpu: "500m"
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 25%

route:
  host: ""       # Override per environment
  targetPort: 3000

env:
  keyValue:
    - name: PORT
      value: "3000"
    - name: SOFTNET_BASE_URL
      value: ""                  # override per environment
    - name: SOFTNET_AUTH_URL
      value: ""
    - name: SOFTNET_WRITE_BASE_URL
      value: ""
    - name: SOFTNET_WRITE_AUTH_URL
      value: ""
    - name: SOFTNET_FINGERPRINT_PATH
      value: "/app/config/fingerprint.json"
    - name: SOFTNET_TIMEOUT
      value: "10000"
    - name: GROUP_ID
      value: ""
    - name: BRANCH_ID
      value: ""
    - name: CORS_ALLOWED_ORIGINS
      value: ""
    - name: SESSION_TTL_MINUTES
      value: "20"
    - name: TLS_ENABLED
      value: "false"
  fromSecrets:
    - name: autohausterminbuchung-secrets
      include: true
      keys:
        - SESSION_SECRET

# Fingerprint file + setup.json + SESSION_SECRET in one Secret, volume-mounted
secrets:
  - name: autohausterminbuchung-secrets
    include: true
    mountPath: /app/config

externalSecrets:
  enabled: false
```

### `environments/dev/values.yaml`

```yaml
global:
  app:
    name: autohausterminbuchung
  appVersion: "0.1.0"

base:
  deployment:
    replicaCount: 1
    serviceAccountName: autohausterminbuchung
    image:
      repository: image-registry.openshift-image-registry.svc:5000/autohandel-terminbuchungs-app-dev/autohausterminbuchung
      pullPolicy: Always
    ports:
      - name: http
        containerPort: 3000
        port: 3000
        protocol: TCP
    resources:
      requests:
        memory: "256Mi"
        cpu: "100m"
      limits:
        memory: "512Mi"
        cpu: "500m"
  route:
    host: autohausterminbuchung-dev.apps.ocp04.app.lhind.cloud
    targetPort: 3000
  env:
    keyValue:
      - name: PORT
        value: "3000"
      - name: SPRING_PROFILES_ACTIVE
        value: "dev"
      - name: SOFTNET_BASE_URL
        value: "https://api.soft-nrg.com"
      - name: SOFTNET_AUTH_URL
        value: "https://auth.soft-nrg.com/oauth/token"
      - name: SOFTNET_WRITE_BASE_URL
        value: "https://int-api.soft-nrg.com"
      - name: SOFTNET_WRITE_AUTH_URL
        value: "https://int-auth.soft-nrg.com/oauth/token"
      - name: SOFTNET_FINGERPRINT_PATH
        value: "/app/config/fingerprint.json"
      - name: SOFTNET_TIMEOUT
        value: "10000"
      - name: GROUP_ID
        value: "698b34c24dd8eea4d6d42179"
      - name: BRANCH_ID
        value: "58341c5cbe856f6dc47e9f05"
      - name: CORS_ALLOWED_ORIGINS
        value: "https://autohausterminbuchung-dev.apps.ocp04.app.lhind.cloud"
      - name: SESSION_TTL_MINUTES
        value: "20"
      - name: TLS_ENABLED
        value: "false"
    fromSecrets:
      - name: autohausterminbuchung-secrets
        include: true
        keys:
          - SESSION_SECRET
  secrets:
    - name: autohausterminbuchung-secrets
      include: true
      mountPath: /app/config
  externalSecrets:
    enabled: false
```

### `environments/prod/values.yaml`

```yaml
global:
  app:
    name: autohausterminbuchung
  appVersion: "0.1.0"

base:
  deployment:
    replicaCount: 2
    serviceAccountName: autohausterminbuchung
    image:
      repository: image-registry.openshift-image-registry.svc:5000/autohandel-terminbuchungs-app-prod/autohausterminbuchung
      pullPolicy: Always
    ports:
      - name: http
        containerPort: 3000
        port: 3000
        protocol: TCP
    resources:
      requests:
        memory: "256Mi"
        cpu: "200m"
      limits:
        memory: "512Mi"
        cpu: "500m"
  route:
    host: autohausterminbuchung.apps.ocp04.app.lhind.cloud
    targetPort: 3000
  env:
    keyValue:
      - name: PORT
        value: "3000"
      - name: SPRING_PROFILES_ACTIVE
        value: "prod"
      - name: SOFTNET_BASE_URL
        value: "https://api.soft-nrg.com"
      - name: SOFTNET_AUTH_URL
        value: "https://auth.soft-nrg.com/oauth/token"
      - name: SOFTNET_WRITE_BASE_URL
        value: "https://api.soft-nrg.com"
      - name: SOFTNET_WRITE_AUTH_URL
        value: "https://auth.soft-nrg.com/oauth/token"
      - name: SOFTNET_FINGERPRINT_PATH
        value: "/app/config/fingerprint.json"
      - name: SOFTNET_TIMEOUT
        value: "10000"
      - name: GROUP_ID
        value: "<prod-group-id>"
      - name: BRANCH_ID
        value: "<prod-branch-id>"
      - name: CORS_ALLOWED_ORIGINS
        value: "https://autohausterminbuchung.apps.ocp04.app.lhind.cloud"
      - name: SESSION_TTL_MINUTES
        value: "20"
      - name: TLS_ENABLED
        value: "false"
    fromSecrets:
      - name: autohausterminbuchung-secrets
        include: true
        keys:
          - SESSION_SECRET
  secrets:
    - name: autohausterminbuchung-secrets
      include: true
      mountPath: /app/config
  externalSecrets:
    enabled: false
```

### Environment Repo `.gitlab-ci.yml`

```yaml
stages:
  - package
  - deploy
  - validate

default:
  interruptible: true
  tags:
    - atapp
    - openshift

variables:
  HELM_RELEASE_NAME: "autohausterminbuchung"
  HELM_REPO_NAME: "gitlab-repo"
  HELM_REPO_URL: "https://git02.lhind.app.lufthansa.com/api/v4/projects/<ENV_REPO_PROJECT_ID>/packages/helm/stable"
  HELM_CHART_NAME: "autohausterminbuchung"

.deploy_template:
  stage: deploy
  image: image-registry.openshift-image-registry.svc:5000/openshift/lhind-ocp-tools
  script:
    - |
      helm repo add ${HELM_REPO_NAME} ${HELM_REPO_URL} \
        --username gitlab-ci-token \
        --password $CI_JOB_TOKEN
      helm repo update
      helm dependency build ${HELM_CHART_PATH}
      helm upgrade --install ${HELM_RELEASE_NAME} ${HELM_CHART_PATH} \
        --namespace=${KUBERNETES_NAMESPACE} \
        -f ${VALUES_FILE} \
        --atomic

.helm_package_job:
  stage: package
  image: image-registry.openshift-image-registry.svc:5000/openshift/lhind-ocp-tools
  before_script:
    - helm plugin install https://github.com/chartmuseum/helm-push.git || true
  script:
    - helm lint base/
    - helm package base/ -d .
    - |
      helm repo add ${HELM_REPO_NAME} ${HELM_REPO_URL} \
        --username gitlab-ci-token \
        --password $CI_JOB_TOKEN
    - helm cm-push base-*.tgz ${HELM_REPO_NAME}

trigger-helm-package:
  stage: package
  extends: .helm_package_job
  allow_failure: true
  rules:
    - if: '$CI_COMMIT_REF_NAME =~ /^(main|feature\/.+|hotfix\/.+)$/'
      changes:
        - base/**/*

deploy-dev:
  extends: .deploy_template
  variables:
    KUBERNETES_NAMESPACE: "autohandel-terminbuchungs-app-dev"
    HELM_CHART_PATH: "environments/dev"
    VALUES_FILE: "environments/dev/values.yaml"
  rules:
    - if: '$CI_COMMIT_REF_NAME == "main"'
      changes:
        - environments/dev/**

deploy-prod:
  extends: .deploy_template
  variables:
    KUBERNETES_NAMESPACE: "autohandel-terminbuchungs-app-prod"
    HELM_CHART_PATH: "environments/prod"
    VALUES_FILE: "environments/prod/values.yaml"
  rules:
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == "main"'
      changes:
        - environments/prod/**
```

---

## 8. Phase 4: Secrets (OpenShift-Managed)

### Decision: OpenShift Console / CLI for Secrets Management ✅

Secrets are created and managed directly in OpenShift via the Red Hat Console or `oc` CLI.
Helm charts **reference pre-existing secrets by name** — they do not create them.

This was chosen over External Secrets Operator (ESO) because:
- **Better access control** — OpenShift RBAC per namespace (only ADMINS can view/edit secrets)
- **Secrets never leave the cluster** — RSA fingerprint keys stay in etcd, never pass through GitLab
- **Simpler architecture** — no SecretStore/ExternalSecret CRDs, no GitLab token dependency
- **Clear audit trail** — OpenShift audit log tracks all secret access
- **No ESO dependency** — works regardless of whether ESO is installed on `ocp04`

```
Admin creates Secrets (OpenShift Console or `oc` CLI)
        ↓ (stored in etcd, encrypted at rest)
Helm chart references Secret by name
        ↓ (mounted as env vars or volumes)
Pod
```

### Secret Layout (per namespace)

**One secret** per namespace — the SoftNet fingerprint JSON file, branch/group setup config (`setup.json`), + `SESSION_SECRET`.

All other variables (`SOFTNET_BASE_URL`, `GROUP_ID`, etc.) are **not sensitive** — they're just URLs and identifiers. Those go into plain `env.keyValue` entries in the Helm values.

#### What's actually sensitive?

The fingerprint JSON contains the **RSA private key** + client identity used for SoftNet OAuth2 JWT-bearer auth:

| Field in fingerprint.json | Purpose |
|---|---|
| `client_email` | Service account identity |
| `client_id` | OAuth2 client ID |
| `private_key_id` | Key version identifier |
| `private_key` | **RSA private key (PEM)** — the actual secret |

`SESSION_SECRET` is the only other truly sensitive value (HMAC-SHA256 session signing key).

#### Secret: `autohausterminbuchung-secrets`

Combines the fingerprint file, setup.json (branch/group config), and SESSION_SECRET in one secret:

```bash
# Create the secret with fingerprint, setup.json, and SESSION_SECRET
oc create secret generic autohausterminbuchung-secrets \
  --from-file=fingerprint.json=./fingerprint-read.json \
  --from-file=setup.json=./server/config/setup.json \
  --from-literal=SESSION_SECRET=$(openssl rand -hex 16) \
  -n autohandel-terminbuchungs-app-dev
```

Or create via **OpenShift Console** → Project → Secrets → Create.

The secret is consumed in two ways:
- **Volume mount** at `/app/config/` → exposes `fingerprint.json` + `setup.json` as files
- **Env var** → `SESSION_SECRET` injected into the pod environment

**Fingerprint loading strategy:** `SoftnetAuthService.loadFingerprint()` first checks `SOFTNET_FINGERPRINT_JSON` (inline JSON env var, used in local dev). If empty, falls back to reading `SOFTNET_FINGERPRINT_PATH` (file path, used in K8s where the secret is volume-mounted at `/app/config/fingerprint.json`).

### How Helm References Pre-Existing Secrets

The Helm values use `env.fromSecrets` to inject secret keys as env vars, and `secrets` for volume mounts. The deployment template's existing logic handles both:

```yaml
# In values.yaml
env:
  keyValue:     # non-sensitive — plain text in Helm values
    - name: SOFTNET_BASE_URL
      value: "https://api.soft-nrg.com"
    - name: GROUP_ID
      value: "698b34c24dd8eea4d6d42179"
    # ... etc
  fromSecrets:  # sensitive — pulled from pre-existing OpenShift Secret
    - name: autohausterminbuchung-secrets
      include: true
      keys:
        - SESSION_SECRET

secrets:        # fingerprint file — volume-mounted from same Secret
  - name: autohausterminbuchung-secrets
    include: true
    mountPath: /app/config
```

### Rotation Procedure

1. Admin updates Secret in OpenShift Console (or `oc edit secret <name> -n <namespace>`)
2. Trigger pod restart: `oc rollout restart deployment/autohausterminbuchung -n <namespace>`
3. New pod picks up updated secret values

### Access Control

| Role | Can view secrets? | Can edit secrets? |
|---|---|---|
| ADMINS (Owners) | ✅ | ✅ |
| DEVS (Developers) | ❌ | ❌ |
| READERS | ❌ | ❌ |

---

## 9. Phase 5: OpenShift Namespace Setup

### Prerequisites (via EMMA Service Requests)

1. **Request OpenShift namespaces:**
   - `atapp-dev` — development
   - `atapp-prod` — production
2. **Request ServiceAccount** with image pull/push permissions across namespaces
3. **Request Route wildcard domain:** `*.apps.ocp04.app.lhind.cloud`
4. **Whitelist external access:** The pod needs outbound HTTPS access to:
   - `api.soft-nrg.com` (SoftNet Read API)
   - `auth.soft-nrg.com` (SoftNet Auth)
   - `int-api.soft-nrg.com` (SoftNet Write Integration — dev only)
   - `int-auth.soft-nrg.com` (SoftNet Write Auth Integration — dev only)

### Kyverno Policies — Required Compliance

Per LDP docs, the following Kyverno policies must be satisfied:

| Policy | How We Comply |
|---|---|
| No "default" ServiceAccount | Helm creates a dedicated `autohausterminbuchung` ServiceAccount |
| Resource requests + limits required | Defined in values.yaml for all containers |
| No root/privileged | No `privileged: true`, no explicit UID, uses `restricted-v2` SCC |

### Deployment Manifest Highlights

The LDP template's `deployment.yaml` includes an `image.openshift.io/triggers` annotation that references an `ImageStreamTag`. This serves **two purposes**:

1. **Digest pinning** — OpenShift resolves the mutable image tag (e.g., `v1.2.0`) to an immutable SHA digest at deployment time, making deployments more deterministic.
2. **Auto-rollout on `oc tag`** — if someone manually runs `oc tag` to move a tag between namespaces, the annotation triggers a pod rollout automatically.

**In our flow, both mechanisms coexist without conflict:**
- `helm upgrade --install` (via env repo MR) is the **primary deploy gate** — this is where traceability and approval lives.
- The ImageStream annotation is a **digest-pinning helper**, not the deploy trigger. The auto-rollout side effect only fires on manual `oc tag` operations, which don't happen in the normal MR-driven flow.

We keep the annotation per the LDP template — no need to deviate.

---

## 10. CI/CD Variables to Configure

### Application Repo — GitLab CI/CD Variables

**Required for first deployment:**

| Variable | Value | Type | Protected | Purpose |
|---|---|---|---|---|
| `ENV_REPO_TOKEN` | `glpat-...` | Masked | Yes | Project Access Token from **env repo** (Developer role, `read_repository` + `write_repository`). Used by rollout jobs to clone/push branches + create MRs. `CI_JOB_TOKEN` cross-project push not supported on this instance. ✅ Done 2026-04-24. |
| `GL_TOKEN` | `glpat-...` | Masked | No | Project Access Token (app repo, `write_repository`). Currently unused — kept for potential future use (e.g., git tag push). |
| `JF_USER` | `<jfrog-username>` | Variable | — | JFrog Artifactory auth (Gradle dependency proxy) |
| `JF_PASSWORD` | `<jfrog-password>` | Masked | — | JFrog Artifactory auth (Gradle dependency proxy) |

**Deferred (add later):**

| Variable | Value | Type | Purpose |
|---|---|---|---|
| `SONAR_TOKEN` | `<sonarqube-token>` | Masked | SonarQube analysis — only needed once SonarQube project is provisioned |

### Environment Repo — GitLab CI/CD Variables

**No application secrets in GitLab.** Only CI/CD operational variables are needed here:

| Variable | Purpose |
|---|---|
| *(none required for secrets)* | All application secrets are managed in OpenShift directly |

### OpenShift Secrets (managed via OpenShift Console/CLI)

Created once per namespace by an ADMIN. Not managed by CI/CD.

| Secret Name | Namespace | Type | Purpose |
|---|---|---|---|
| `autohausterminbuchung-secrets` | `autohandel-terminbuchungs-app-dev` | Opaque (files + literal) | RSA fingerprint JSON (`fingerprint.json`) + branch/group config (`setup.json`) + `SESSION_SECRET`. Mounted at `/app/config/` + env var |
| `autohausterminbuchung-secrets` | `autohandel-terminbuchungs-app-prod` | Opaque (files + literal) | Same — production fingerprint + production setup.json + production SESSION_SECRET |

---

## 11. Service Requests (EMMA Tickets)

The following must be requested from the LDP team via EMMA (`lhind.ams-devops-buildchain@lhind.dlh.de`):

### Required for first deployment

| # | Request | Details |
|---|---|---|
| 1 | **OpenShift Namespaces** | ✅ Both provisioned on `ocp04`: `autohandel-terminbuchungs-app-dev` + `autohandel-terminbuchungs-app-prod` |
| 2 | **GitLab Runner Verification** | Confirm runner `#581` has access to new repos and correct tags |
| 3 | **External Network Egress** | Whitelist `*.soft-nrg.com` for outbound HTTPS from `autohandel-terminbuchungs-app-*` namespaces |
| 4 | **Base Image Whitelist** | Allow pull of `ubi9/openjdk-25`, `ubi9/nodejs-22`, `ubi8/buildah` |
| 5 | **Route Domain** | Confirm `autohausterminbuchung-dev.apps.ocp04.app.lhind.cloud` is available |
| 6 | **User Roles** | Submit ADMINS/DEVS/READERS list for Azure Entra group mapping |

### Deferred (add after first successful deployment)

| # | Request | Why deferred |
|---|---|---|
| D1 | **SonarQube Project** | Code quality gate — not blocking deployment. Add later when ready to enforce quality metrics in CI |
| D2 | **JFrog project-specific repo / Xray watcher** | Vulnerability scanning — not blocking. `ocp-build-and-scan` replaced with `ocp-build` (Xray returns 401 Bad Credentials). Re-enable scan after EMMA ticket provisions JFrog Xray credentials for the project |

---

## 12. Open Questions for Platform Team

> These augment the existing `platform-team-questions.md` with LDP-specific questions.

### Resolved (we can answer ourselves)

| # | Question | Answer |
|---|---|---|
| ~~1~~ | ~~What Java version is available?~~ | ✅ JDK 17, 21, 25 all available as runner images |
| ~~2~~ | ~~Does JDK runner include Node.js?~~ | ✅ No. JDK runners only have JDK + oc + jf + helm + sonar + grype. Need split build (nodejs-22 → jdk-25) |
| ~~4~~ | ~~Is External Secrets Operator installed on `ocp04`?~~ | ✅ N/A — secrets managed directly in OpenShift Console/CLI |
| ~~5~~ | ~~What Artifactory repo for Maven/Gradle dependencies?~~ | ✅ `devopsplatform-lhind-px-mvn-all` (verified from LDP Java template `.gitlab-ci.yml` line 119, 143, 165). This is the shared LHIND virtual repo for all Java projects |
| ~~7~~ | ~~Do we need a custom SCC for the fingerprint volume mount?~~ | ✅ No. `restricted-v2` SCC permits Secret volume mounts by default — that's a base K8s feature, not a privileged operation. Only matters for hostPath, hostNetwork, or RunAsRoot |
| ~~8~~ | ~~Can we use Gradle instead of Maven?~~ | ✅ Yes. The Gradle wrapper (`./gradlew`) is fully self-contained — it downloads its own Gradle distribution + dependencies on first run. Just needs JDK + network access (which the JDK runner already has via Artifactory). Only catch: configure Gradle to fetch dependencies through `devopsplatform-lhind-px-mvn-all` instead of Maven Central directly (see below). The LDP template happens to use Maven but there's no platform constraint forcing Maven |
| ~~9~~ | ~~Is JDK 21 runner image available?~~ | ✅ Yes — `jdk-21-runner` (project 4768). But we're using JDK 25 (`jdk-25-runner`, project 4835) since the project requires it |

### Still open (need platform team input)

| # | Question | Why we can't answer ourselves |
|---|---|---|
| ~~3~~ | ~~What are the **namespace names**?~~ | ✅ **RESOLVED** — Dev: `autohandel-terminbuchungs-app-dev` (confirmed on `ocp04`). Prod: `autohandel-terminbuchungs-app-prod` (naming convention) |
| ~~6~~ | ~~Is the cluster route domain?~~ | ✅ **RESOLVED** — Cluster is `ocp04`, domain is `*.apps.ocp04.app.lhind.cloud` (confirmed from OpenShift Console URL) |
| 10 | What's the **GitLab group path** for the new repos? (`atapp/`? `lhind-projects/atapp/`?) | We need to know where to create the env repo + which group the runner is bound to |
| 11 | Are there **EMMA approval lead times** we should plan around? | Need to know expected wait time per ticket type to sequence the rollout realistically |

### Gradle + Artifactory configuration (since we're using Gradle)

To make `./gradlew` resolve dependencies through Artifactory instead of Maven Central, add to `server/build.gradle`:

```gradle
repositories {
    maven {
        url "https://lhindrepo02.jfrog.io/artifactory/devopsplatform-lhind-px-mvn-all/"
        credentials {
            username = System.getenv("JF_USER")
            password = System.getenv("JF_PASSWORD")
        }
    }
}
```

Or via `~/.gradle/init.gradle` injected in CI (so we don't hard-code Artifactory URL in the source).

---

## 13. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ~~**JDK runner lacks Node.js** → Angular build fails in CI~~ | ~~N/A~~ | ~~N/A~~ | ✅ **RESOLVED** — Split into `frontend build` (nodejs-22-runner) + `backend build` (jdk-25-runner) with `-x buildAngular -x npmInstall`. `microdnf`/`dnf` install also blocked by restricted-v2 SCC (non-root can't write `/var/cache/yum`). |
| ~~Java 25 not available on LDP~~ | ~~N/A~~ | ~~N/A~~ | ✅ **RESOLVED** — `jdk-25-runner` exists (project 4835, base `ubi9/openjdk-25`) |
| **Gradle not pre-configured** in runner images (only Maven) | Medium | Low | Gradle wrapper (`./gradlew`) is self-contained — should work regardless |
| ~~**External Secrets Operator not installed**~~ | ~~N/A~~ | ~~N/A~~ | ✅ **RESOLVED** — Not using ESO; secrets managed directly in OpenShift |
| **Network egress blocked** to `*.soft-nrg.com` | Medium | Critical | Request firewall whitelist early via EMMA |
| **Fingerprint JSON too large for GitLab variable** (16KB limit) | Low | Medium | Use manual OpenShift Secret + volume mount |
| **`ubi9/openjdk-*`** not whitelisted for pull | Medium | Medium | Request whitelist or use internal registry mirror |

---

## 14. Implementation Checklist

### What can be done now vs. what is blocked

| Phase | Actionable now (in this repo) | Blocked on |
|---|---|---|
| **1: Dockerfile** | ✅ Write `Dockerfile` + test locally with Docker | Nothing |
| **2: App CI/CD** | ✅ Write `.gitlab-ci.yml`, `release.config.cjs`, `version.txt` (all in THIS repo) | CI/CD variables need GitLab UI access; pipeline testing needs runner + namespaces |
| **3: Env Repo** | ⚠️ Prepare Helm files locally in a temp folder — but they belong in a **SEPARATE new repo** (`autohausterminbuchung.environment`), NOT this one | Repo creation requires GitLab group path (open question #10) + git02 create rights |
| **4: Secrets** | ❌ Nothing in this repo — all done via `oc` CLI / OpenShift Console after namespaces exist | OpenShift namespaces (EMMA ticket #1) |
| **5: Validation** | ❌ Nothing | Phases 1–4 done + namespaces provisioned |

> **Important:** Phase 3's Helm charts (Section 7) do NOT go into this `autohausterminbuchung` repo.
> They go into a brand-new sibling repo `autohausterminbuchung.environment` on git02.
> Until we know the GitLab group path, we can only draft the files locally.

### Pre-Implementation (Admin/Setup)

- [x] ~~Submit EMMA ticket for OpenShift namespaces~~ → `autohandel-terminbuchungs-app-dev` provisioned on `ocp04`
- [ ] Submit EMMA ticket for network egress whitelist (`*.soft-nrg.com`)
- [ ] Submit EMMA ticket for base image whitelist (`ubi9/openjdk-25`, `ubi9/nodejs-22`, `ubi8/buildah`)
- [ ] Confirm runner `#581` tags and access
- [ ] Confirm access to shared JFrog repo `devopsplatform-lhind-px-mvn-all` from runner `#581`
- [x] ~~Confirm Java version available in runner images~~ → JDK 25 available (`jdk-25-runner`, project 4835)
- [x] ~~Confirm if JDK runner has Node.js~~ → No, must split CI build into nodejs-22 + jdk-25 stages
- [ ] Create environment repository `autohausterminbuchung.environment` on git02
- [ ] Set up Azure Entra group memberships (ADMINS/DEVS/READERS)

**Deferred (add after first successful deployment):**

- [ ] Submit EMMA ticket for SonarQube project (then add `SONAR_TOKEN` CI variable + enable `sonarqube scan` job)
- [ ] Submit EMMA ticket for JFrog project-specific repo + Xray watcher (if needed beyond shared repo)

### Phase 1: Dockerfile ✅ COMPLETE (2026-04-21)

- [x] ~~Decide Java version~~ → Keep Java 25 (`jdk-25-runner` available)
- [x] Write multi-stage Dockerfile (build + runtime) — see [`Dockerfile`](../../../Dockerfile)
- [x] ~~Verify Gradle `bootJar` task bundles Angular assets into JAR~~ → **Adjusted**: Angular `dist/` is copied alongside the JAR in the runtime stage (WebConfig serves from filesystem, not classpath). `bootJar` is invoked with `-x buildAngular` since the dist is pre-built in the frontend stage.
- [x] Test Docker build locally (or via `buildah`) — verified with `podman build --platform linux/arm64 -t autohaus-termin:phase1 .`
- [x] Verify container runs as non-root with random UID — verified with `podman run --user 1000260000`: `id` returns `uid=1000260000 gid=0(root) groups=0(root),1000260000`. App boots successfully, fingerprint Secret mount readable.
- [x] Verify `/api/v1/health` responds from container — `HTTP 200`, SoftNet dependency healthy.

**Phase 1 notes:**
- Dockerfile uses `USER 1001` as default (non-root for plain podman/docker runs); OpenShift `restricted-v2` SCC will override this with a random high UID. Both modes verified to work.
- Total image size: ~488 MB runtime base + jar + dist. Larger than the REQ-019 target of `<200 MB compressed` — ubi9/openjdk-25 alone is 488 MB. To shrink, consider switching the runtime stage to `ubi9-minimal/openjdk-25-runtime` (JRE only, ~140 MB) once available, or use jlink to build a custom JRE.
- Local validation uses `.env.local` (gitignored) with INT-system SoftNet credentials and the existing fingerprint JSON in `server/`.

### Phase 2: Application CI/CD ⏳ DRAFTED LOCALLY (2026-04-21)

- [x] Create `.gitlab-ci.yml` in app repo (without `sonarqube_scan` stage) — see [`.gitlab-ci.yml`](../../../.gitlab-ci.yml). YAML validated with `js-yaml`. SonarQube job present but disabled (`.sonarqube scan:` — leading dot).
- [x] ~~Create `release.config.cjs` for semantic-release~~ — replaced with shell-based versioning (semantic-release PAT push not reliable on this GitLab instance). `release.config.cjs` is orphaned.
- [x] Create `version.txt` (initial `0.1.0`) — see [`version.txt`](../../../version.txt).
- [x] Set up CI/CD variable `GL_TOKEN` (Project Access Token, `write_repository`, unprotected, masked) — required for semantic-release tag push. ✅ Done 2026-04-23.
- [x] Set up CI/CD variable `ENV_REPO_TOKEN` (Project Access Token from env repo, `write_repository`, protected, masked) — required for rollout jobs to push to env repo. `CI_JOB_TOKEN` push not supported on this GitLab instance. ✅ Done 2026-04-24.
- [ ] Set up CI/CD variables: `JF_USER`, `JF_PASSWORD` (`SONAR_TOKEN` deferred) — **blocked on JFrog provisioning**
- [ ] Test pipeline on feature branch (test + build stages) — **blocked on runner access**
- [ ] Test image build via buildah → push to OCP registry — **blocked on namespace + runner**
- [ ] Test rollout stage (env repo MR creation) — **blocked on env repo (Phase 3)**

**Phase 2 notes:**
- ✅ **Build split confirmed working (2026-04-24):** `microdnf install nodejs npm` was blocked by restricted-v2 SCC (non-root can't write `/var/cache/yum`; `dnf` not installed). Switched to split build: `frontend build` (nodejs-22-runner: `npm ci && npm run build` → `dist/` artifact) + `backend build` (jdk-25-runner: `./gradlew bootJar -x buildAngular -x npmInstall`). Same pattern as the multi-stage Dockerfile.
- `ENV_REPO_PATH` is set to `atapp/autohausterminbuchung.environment` as a placeholder; final value depends on open question #10 (GitLab group path).
- All five stages use `tags: [atapp, openshift]` to bind to the assigned group runner `#581`.

### Phase 3: Environment Repository — ✅ PUSHED (2026-04-23)

GitLab repo created (project ID 4865): `indautomotive/atapp/autohausterminbuchung.environment`
Initial commit pushed to `main` on 2026-04-23 after obtaining Owner access.

**Completed:**
- [x] Create `autohausterminbuchung.environment` repo on GitLab (project ID 4865)
- [x] Create `base/` Helm chart (templates: deployment, service, route, serviceaccount, configmap, hpa, externalsecret, secretstore)
- [x] Create `environments/dev/` and `environments/prod/` value overlays
- [x] Create `.gitlab-ci.yml` for env repo (package → deploy-dev → deploy-prod → validate)
- [x] Validate with `helm lint` and `helm template` — all templates render correctly
- [x] Push initial commit to GitLab — commit `b446e0b` on `main`
- [x] Fix runner tags to `atapp` + `openshift` — commit `8e9add3`
- [x] Add `.gitlab-ci.yml` to `changes:` filters — commit `2cf00b9`
- [x] Verify `trigger-helm-package` CI job publishes base chart to GitLab Package Registry — ✅ succeeded, base chart available
- [x] Verify `deploy-dev` CI job runs end-to-end — ✅ Helm dependency resolution works, deployment attempted. Failed with `context deadline exceeded` — expected: no app image exists in OCP registry yet. Will succeed once app CI builds first image.
- [ ] Test `helm upgrade --install` to dev namespace with real app image (blocked on app repo CI — Phase 2)

### Phase 4: Secrets — DEV ✅ READY (namespace exists on `ocp04`)

**Dev namespace `autohandel-terminbuchungs-app-dev` is provisioned.** Execute via OpenShift Console or `oc` CLI:

```bash
# 1. Login to ocp04
oc login https://api.ocp04.app.lhind.cloud:6443

# 2. Switch to dev namespace
oc project autohandel-terminbuchungs-app-dev

# 3. Create the secret (fingerprint file + setup.json + SESSION_SECRET)
oc create secret generic autohausterminbuchung-secrets \
  --from-file=fingerprint.json=./server/cognigy.net_fingerprint-e6ced285a2bfce274426c261cca6edcd.json \
  --from-file=setup.json=./server/config/setup.json \
  --from-literal=SESSION_SECRET=$(openssl rand -hex 16) \
  -n autohandel-terminbuchungs-app-dev

# 4. Verify
oc get secret autohausterminbuchung-secrets -n autohandel-terminbuchungs-app-dev -o yaml
```

**Or via OpenShift Console:**
1. Go to https://console-openshift-console.apps.ocp04.app.lhind.cloud/k8s/ns/autohandel-terminbuchungs-app-dev/secrets
2. Create → Key/Value Secret
3. Name: `autohausterminbuchung-secrets`
4. Add key `fingerprint.json` → upload `server/cognigy.net_fingerprint-e6ced285a2bfce274426c261cca6edcd.json`
5. Add key `SESSION_SECRET` → paste output of `openssl rand -hex 16`

**Checklist:**
- [x] Create `autohausterminbuchung-secrets` in `autohandel-terminbuchungs-app-dev` — created 2026-04-23 via `oc create secret generic` (keys: `fingerprint.json`, `SESSION_SECRET`). Updated 2026-04-24: added `setup.json` (branch/group config required by `SetupService.init()`).
- [x] Verify dev secret key names match Helm template expectations — confirmed: `fingerprint.json` + `setup.json` (volume mount → `/app/config/`) + `SESSION_SECRET` (env var via `secretKeyRef`)
- [x] Verify Helm deployment template renders correctly — `helm template` confirms: `secretKeyRef` for SESSION_SECRET, volume mount at `/app/config`, `SOFTNET_FINGERPRINT_PATH=/app/config/fingerprint.json`
- [x] Verify fingerprint file accessible at `/app/config/fingerprint.json` in pod — confirmed 2026-04-24. File was mounted correctly but `SoftnetAuthService` only read from `SOFTNET_FINGERPRINT_JSON` env var (not set in K8s). Fixed: added `SOFTNET_FINGERPRINT_PATH` file fallback to `SoftnetAuthService.loadFingerprint()` (env var → file priority).
- [ ] Verify `SESSION_SECRET` env var injected correctly (blocked on redeployment with fingerprint fix)
- [ ] Create `autohausterminbuchung-secrets` in `autohandel-terminbuchungs-app-prod` (deferred — create when ready for prod deployment)
- [ ] Document secret rotation procedure for the team

### Phase 5: End-to-End Validation

- [ ] Push code change → app CI runs → image built → env repo MR created
- [ ] Merge env MR → Helm deploys to dev namespace
- [ ] Verify app accessible via Route URL
- [ ] Verify health endpoint responds
- [ ] Verify SoftNet API connectivity from pod
- [ ] Test production deployment flow (manual MR merge)
- [ ] Update REQ-019 requirement document with corrections

---

## Appendix A: LDP CI Template Reference

| Template | Project | Purpose |
|---|---|---|
| `ocp.yml` | `lhind-devops-platform/gitlab-ci-templates` | `ocp-build` (buildah only; `ocp-build-and-scan` disabled until Xray creds provisioned), `ocp-push` |
| `artifactory.yml` | `lhind-devops-platform/gitlab-ci-templates` | JFrog CLI config + npm/mvn wrappers |
| `frogbot.yml` | `lhind-devops-platform/gitlab-ci-templates` | MR security scanning |
| Java Template App | `lhind-devops-platform/example-projects/java-template/java-template` (ID 2290) | Reference `.gitlab-ci.yml`, Dockerfile, pom.xml |
| Java Template Env | `lhind-devops-platform/example-projects/java-template/java-template.environment` (ID 2291) | Reference Helm charts, env CI, deployment templates |

## Appendix B: Key Differences Summary — REQ-019 (Old) vs This Plan

| Topic | REQ-019 (NestJS) | This Plan (Spring Boot + LDP) |
|---|---|---|
| Backend | NestJS + Node.js | Spring Boot 4.1 + Java 21 |
| Container base | `node:22-alpine` | `ubi9/openjdk-21` |
| Build tool | `npm run build` | `./gradlew bootJar` |
| Runtime command | `node backend/dist/main.js` | `java -jar app.jar` |
| Image registry | GitLab Container Registry | OpenShift Internal Registry |
| Image builder | Docker-in-Docker | Buildah (via `ocp.yml` template) |
| Deploy method | `helm upgrade --install` from app CI | Two-repo GitOps: app CI → MR → env CI → helm |
| Secrets | GitLab vars → `helm --set` | OpenShift-managed Secrets (Console/CLI). No ESO. |
| Helm charts | In-repo (`deploy/helm/`) | Separate env repo (`*.environment`) |
| CI runner | Generic Docker executor | LDP runner `#581` (tags: `atapp`, `ocp04`) |
| User UID | 1001 (explicit) | Random (OpenShift `restricted-v2` SCC) |
