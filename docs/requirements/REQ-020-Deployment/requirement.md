# REQ-020: Deployment & Infrastruktur

| Feld | Wert |
|------|------|
| **ID** | REQ-020 |
| **Titel** | Deployment & Infrastruktur |
| **Status** | Draft |
| **Prioritaet** | Hoch |
| **Abhaengigkeiten** | REQ-014-NestJS-Backend |
| **Erstellt** | 2026-04-09 |

---

## 1. Zusammenfassung

Einrichtung der vollstaendigen Deployment-Infrastruktur fuer die Angular 21 + NestJS Backend Anwendung. Die Anwendung wird als Docker-Container ueber **GitLab CI/CD** auf **OpenShift 4.14+** deployed. Es gibt zwei Umgebungen (Dev + Prod) mit unterschiedlichen SoftNet-API-Konfigurationen. Alle Secrets werden ueber den **GitLab Secrets Store** verwaltet und als Environment Variables in die OpenShift Pods injiziert. **Helm Charts** werden fuer die OpenShift-Deployments verwendet.

---

## 2. Architektur-Uebersicht

```
                    ┌─────────────────────────────────────────────┐
                    │              GitLab Repository               │
                    │  (Code + Helm Charts + .gitlab-ci.yml)       │
                    └──────────────┬──────────────────────────────┘
                                   │
                           GitLab CI/CD
                                   │
                    ┌──────────────┴──────────────────────────────┐
                    │         GitLab Container Registry            │
                    │    registry.gitlab.com/<group>/<project>     │
                    └──────────────┬──────────────────────────────┘
                                   │
                  ┌────────────────┴────────────────┐
                  │                                 │
           ┌──────▼──────┐                  ┌───────▼──────┐
           │  OpenShift   │                  │  OpenShift   │
           │  Dev (NS)    │                  │  Prod (NS)   │
           │              │                  │              │
           │ SoftNet Int. │                  │ SoftNet Prod │
           └──────────────┘                  └──────────────┘
```

### Datenfluss im Pod

```
Browser → OpenShift Route (TLS) → NestJS Pod (:3000) → SoftNet API
                                       │
                                  Angular App
                                  (Static Files)
```

---

## 3. Acceptance Criteria

### Docker & Container

- [ ] AC-1: Multi-Stage Dockerfile erstellt (Build + Runtime), Endimage basiert auf `node:22-alpine`
- [ ] AC-2: Docker-Image enthaelt Angular Build-Artefakte (`dist/angular-21-project/browser/`) + NestJS Backend (`backend/dist/`)
- [ ] AC-3: Container laeuft als Non-Root User (`node`, UID 1000)
- [ ] AC-4: Container lauscht auf Port 3000 (konfigurierbar ueber `PORT` env var)
- [ ] AC-5: Health-Check Endpoint `/api/v1/health` antwortet mit 200 OK
- [ ] AC-6: Docker-Image Groesse < 200 MB (compressed)
- [ ] AC-7: `.dockerignore` schliesst `node_modules`, `.git`, `coverage`, `.env` aus

### GitLab CI/CD Pipeline

- [ ] AC-8: `.gitlab-ci.yml` mit Stages: `build` → `test` → `deploy-dev` → `deploy-prod`
- [ ] AC-9: `build` Stage: Docker Image bauen + in GitLab Container Registry pushen
- [ ] AC-10: `test` Stage: Frontend Tests (`npm test`), Backend Tests (`cd backend && npm test`), E2E Tests (`npm run e2e:backend`)
- [ ] AC-11: `deploy-dev` Stage: Automatisch bei Push auf `main` — deployed auf OpenShift Dev Namespace
- [ ] AC-12: `deploy-prod` Stage: Manueller Trigger (Manual Job) — deployed auf OpenShift Prod Namespace
- [ ] AC-13: Image-Tag-Strategie: `latest` + Git Commit SHA (z.B. `registry.gitlab.com/group/project:abc1234`)
- [ ] AC-14: Pipeline-Cache fuer `node_modules` (Frontend + Backend)

### GitLab Secrets Store

- [ ] AC-15: Alle Secrets werden in GitLab CI/CD Variables verwaltet (`Settings > CI/CD > Variables`)
- [ ] AC-16: Secrets sind nach Umgebung (Environment) getrennt: `dev` und `prod`
- [ ] AC-17: Folgende Secrets sind konfiguriert (pro Umgebung):

| Variable | Dev-Wert | Prod-Wert | Typ |
|----------|----------|-----------|-----|
| `PORT` | `3000` | `3000` | Variable |
| `SOFTNET_READ_BASE_URL` | `https://api.soft-nrg.com` | `https://api.soft-nrg.com` | Variable |
| `SOFTNET_READ_AUTH_URL` | `https://auth.soft-nrg.com/oauth/token` | `https://auth.soft-nrg.com/oauth/token` | Variable |
| `SOFTNET_WRITE_BASE_URL` | `https://int-api.soft-nrg.com` | `https://api.soft-nrg.com` | Variable |
| `SOFTNET_WRITE_AUTH_URL` | `https://int-auth.soft-nrg.com/oauth/token` | `https://auth.soft-nrg.com/oauth/token` | Variable |
| `SOFTNET_TIMEOUT` | `10000` | `10000` | Variable |
| `GROUP_ID` | `698b34c24dd8eea4d6d42179` | `<prod-group-id>` | Variable |
| `BRANCH_ID` | `58341c5cbe856f6dc47e9f05` | `<prod-branch-id>` | Variable |
| `CORS_ALLOWED_ORIGINS` | `https://dev.example.com` | `https://www.example.com` | Variable |
| `IFRAME_ALLOWED_ORIGINS` | `` | `https://www.kundenwebsite.de` | Variable |
| `SESSION_SECRET` | `<dev-secret-32-chars>` | `<prod-secret-32-chars>` | **Masked Variable** |
| `SESSION_TTL_MINUTES` | `20` | `20` | Variable |
| `TLS_ENABLED` | `false` | `false` | Variable |
| `SOFTNET_READ_FINGERPRINT` | `<json-content>` | `<json-content>` | **File Variable** |
| `SOFTNET_WRITE_FINGERPRINT` | `<json-content>` | `<json-content>` | **File Variable** |

- [ ] AC-18: Sensitive Secrets (SESSION_SECRET, Fingerprints) sind als **Masked** und/oder **File** Variables konfiguriert
- [ ] AC-19: Keine `.env`-Datei wird in den Container oder Pod deployed — NestJS `ConfigModule` liest `process.env`

### OpenShift Deployment

- [ ] AC-20: Helm Chart im Repository unter `deploy/helm/` mit Values-Dateien pro Umgebung
- [ ] AC-21: OpenShift `DeploymentConfig` oder `Deployment` mit:
  - Replicas: Dev=1, Prod=2 (min)
  - Resource Limits: CPU 500m, Memory 512Mi
  - Resource Requests: CPU 100m, Memory 128Mi
  - Liveness Probe: `GET /api/v1/health` (initialDelay: 15s, period: 30s)
  - Readiness Probe: `GET /api/v1/health` (initialDelay: 5s, period: 10s)
- [ ] AC-22: OpenShift `Route` mit TLS Edge Termination (OpenShift Router handled TLS)
- [ ] AC-23: OpenShift `Service` (ClusterIP) auf Port 3000
- [ ] AC-24: Fingerprint-JSON-Dateien werden als **OpenShift Secrets** (Volume Mount) in den Pod gemountet unter `/app/config/`
- [ ] AC-25: Environment Variables werden aus GitLab CI/CD in die Helm Values injiziert
- [ ] AC-26: Graceful Shutdown: NestJS faehrt sauber herunter bei SIGTERM (30s Timeout)
- [ ] AC-27: Pod laeuft als Non-Root (OpenShift SecurityContextConstraints: `restricted-v2`)

### GitHub Actions (Dev-Workflow)

- [ ] AC-28: Bestehende GitHub Actions fuer PR-Checks bleiben erhalten (Lint, Type-Check, Unit Tests)
- [ ] AC-29: GitHub Actions fuehrt E2E-Tests mit Backend aus (`npm run e2e:backend`)
- [ ] AC-30: GitHub Secrets enthalten Dev-Werte fuer die E2E-Tests (SoftNet Integration)
- [ ] AC-31: E2E-Tests in GitHub Actions erzeugen `.env`-Datei aus GitHub Secrets vor dem Test-Lauf

### Monitoring & Observability

- [ ] AC-32: Structured JSON Logging (NestJS Logger) — kein `console.log` in Production
- [ ] AC-33: Request-ID in jedem Log-Eintrag (bereits durch `TransformInterceptor` generiert)
- [ ] AC-34: Health-Endpoint gibt SoftNet-Konnektivitaet zurueck (`/api/v1/health`)
- [ ] AC-35: Metriken-Endpoint `/api/v1/metrics` (optional, fuer Prometheus/Grafana)

---

## 4. Umgebungs-Matrix

| Aspekt | Lokal | GitHub Actions | OpenShift Dev | OpenShift Prod |
|--------|-------|---------------|---------------|----------------|
| **Env-Quelle** | `.env`-Datei | GitHub Secrets → `.env` | GitLab Secrets → Env Vars | GitLab Secrets → Env Vars |
| **SoftNet Read** | Production API | Production API | Production API | Production API |
| **SoftNet Write** | Integration API | Integration API | Integration API | Production API |
| **E2E** | `npm run e2e:backend` | `npm run e2e:backend` | — | — |
| **TLS** | Nein (localhost) | Nein | OpenShift Route (Edge) | OpenShift Route (Edge) |
| **Replicas** | 1 | 1 | 1 | 2+ |
| **Fingerprint** | `./config/*.json` | GitHub Secret → Datei | Secret Volume Mount | Secret Volume Mount |

---

## 5. Dockerfile (Multi-Stage)

```dockerfile
# ===== Stage 1: Build Frontend =====
FROM node:22-alpine AS frontend-build
WORKDIR /app
COPY package*.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npm run build

# ===== Stage 2: Build Backend =====
FROM node:22-alpine AS backend-build
WORKDIR /app/backend
COPY backend/package*.json ./
RUN npm ci --ignore-scripts
COPY backend/ .
RUN npm run build

# ===== Stage 3: Runtime =====
FROM node:22-alpine AS runtime
WORKDIR /app

# Security: Non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -S appuser -u 1001 -G appgroup

# Backend dependencies (production only)
COPY backend/package*.json ./backend/
RUN cd backend && npm ci --only=production --ignore-scripts

# Backend build output
COPY --from=backend-build /app/backend/dist ./backend/dist

# Frontend build output (served by NestJS ServeStaticModule)
COPY --from=frontend-build /app/dist/angular-21-project/browser ./dist/angular-21-project/browser

# Config directory for fingerprint files (mounted as volume)
RUN mkdir -p /app/config && chown appuser:appgroup /app/config

USER appuser
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/api/v1/health || exit 1

CMD ["node", "backend/dist/main.js"]
```

---

## 6. GitLab CI/CD Pipeline

```yaml
# .gitlab-ci.yml
stages:
  - build
  - test
  - deploy-dev
  - deploy-prod

variables:
  DOCKER_IMAGE: $CI_REGISTRY_IMAGE
  DOCKER_TAG: $CI_COMMIT_SHORT_SHA

# ===== Build =====
build:
  stage: build
  image: docker:24
  services:
    - docker:24-dind
  script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker build -t $DOCKER_IMAGE:$DOCKER_TAG -t $DOCKER_IMAGE:latest .
    - docker push $DOCKER_IMAGE:$DOCKER_TAG
    - docker push $DOCKER_IMAGE:latest
  only:
    - main
    - merge_requests

# ===== Test =====
test:
  stage: test
  image: node:22-alpine
  cache:
    key: npm-cache
    paths:
      - node_modules/
      - backend/node_modules/
  before_script:
    - npm ci
    - cd backend && npm ci && cd ..
  script:
    - npm run lint
    - npm run type-check
    - npm run test:coverage
    - cd backend && npm test && cd ..
  only:
    - main
    - merge_requests

e2e:
  stage: test
  image: mcr.microsoft.com/playwright:v1.52.0-noble
  cache:
    key: npm-cache
    paths:
      - node_modules/
      - backend/node_modules/
  before_script:
    - npm ci
    - cd backend && npm ci && cd ..
    # Fingerprint-Dateien aus GitLab File Variables sind automatisch als Pfade verfuegbar
    - mkdir -p backend/config
    - cp $SOFTNET_READ_FINGERPRINT backend/config/fingerprint-read.json
    - cp $SOFTNET_WRITE_FINGERPRINT backend/config/fingerprint-write.json
    # .env aus GitLab Variables generieren
    - |
      cat > backend/.env << EOF
      PORT=$PORT
      SOFTNET_READ_BASE_URL=$SOFTNET_READ_BASE_URL
      SOFTNET_READ_AUTH_URL=$SOFTNET_READ_AUTH_URL
      SOFTNET_READ_FINGERPRINT_PATH=./config/fingerprint-read.json
      SOFTNET_WRITE_BASE_URL=$SOFTNET_WRITE_BASE_URL
      SOFTNET_WRITE_AUTH_URL=$SOFTNET_WRITE_AUTH_URL
      SOFTNET_WRITE_FINGERPRINT_PATH=./config/fingerprint-write.json
      SOFTNET_TIMEOUT=$SOFTNET_TIMEOUT
      GROUP_ID=$GROUP_ID
      BRANCH_ID=$BRANCH_ID
      CORS_ALLOWED_ORIGINS=$CORS_ALLOWED_ORIGINS
      IFRAME_ALLOWED_ORIGINS=$IFRAME_ALLOWED_ORIGINS
      SESSION_SECRET=$SESSION_SECRET
      SESSION_TTL_MINUTES=$SESSION_TTL_MINUTES
      TLS_ENABLED=false
      EOF
    - npm run build
    - cd backend && npm run build && cd ..
  script:
    - npm run e2e:backend
  artifacts:
    when: always
    paths:
      - playwright-report/
    expire_in: 7 days
  only:
    - main
    - merge_requests
  environment:
    name: dev

# ===== Deploy Dev =====
deploy-dev:
  stage: deploy-dev
  image: alpine/helm:3.14
  script:
    - helm upgrade --install app-dev deploy/helm/
      --namespace $OPENSHIFT_DEV_NAMESPACE
      --set image.repository=$DOCKER_IMAGE
      --set image.tag=$DOCKER_TAG
      --values deploy/helm/values-dev.yaml
      --set-string env.PORT="$PORT"
      --set-string env.SOFTNET_READ_BASE_URL="$SOFTNET_READ_BASE_URL"
      --set-string env.SOFTNET_READ_AUTH_URL="$SOFTNET_READ_AUTH_URL"
      --set-string env.SOFTNET_WRITE_BASE_URL="$SOFTNET_WRITE_BASE_URL"
      --set-string env.SOFTNET_WRITE_AUTH_URL="$SOFTNET_WRITE_AUTH_URL"
      --set-string env.SOFTNET_TIMEOUT="$SOFTNET_TIMEOUT"
      --set-string env.GROUP_ID="$GROUP_ID"
      --set-string env.BRANCH_ID="$BRANCH_ID"
      --set-string env.CORS_ALLOWED_ORIGINS="$CORS_ALLOWED_ORIGINS"
      --set-string env.SESSION_SECRET="$SESSION_SECRET"
      --set-string env.SESSION_TTL_MINUTES="$SESSION_TTL_MINUTES"
  environment:
    name: dev
    url: https://dev-booking.example.com
  only:
    - main

# ===== Deploy Prod (Manual) =====
deploy-prod:
  stage: deploy-prod
  image: alpine/helm:3.14
  script:
    - helm upgrade --install app-prod deploy/helm/
      --namespace $OPENSHIFT_PROD_NAMESPACE
      --set image.repository=$DOCKER_IMAGE
      --set image.tag=$DOCKER_TAG
      --values deploy/helm/values-prod.yaml
      --set-string env.PORT="$PORT"
      --set-string env.SOFTNET_READ_BASE_URL="$SOFTNET_READ_BASE_URL"
      --set-string env.SOFTNET_READ_AUTH_URL="$SOFTNET_READ_AUTH_URL"
      --set-string env.SOFTNET_WRITE_BASE_URL="$SOFTNET_WRITE_BASE_URL"
      --set-string env.SOFTNET_WRITE_AUTH_URL="$SOFTNET_WRITE_AUTH_URL"
      --set-string env.SOFTNET_TIMEOUT="$SOFTNET_TIMEOUT"
      --set-string env.GROUP_ID="$GROUP_ID"
      --set-string env.BRANCH_ID="$BRANCH_ID"
      --set-string env.CORS_ALLOWED_ORIGINS="$CORS_ALLOWED_ORIGINS"
      --set-string env.SESSION_SECRET="$SESSION_SECRET"
      --set-string env.SESSION_TTL_MINUTES="$SESSION_TTL_MINUTES"
  environment:
    name: prod
    url: https://booking.example.com
  when: manual
  only:
    - main
```

---

## 7. Helm Chart Struktur

```
deploy/
└── helm/
    ├── Chart.yaml
    ├── values.yaml              # Defaults
    ├── values-dev.yaml          # Dev Overrides
    ├── values-prod.yaml         # Prod Overrides
    └── templates/
        ├── deployment.yaml
        ├── service.yaml
        ├── route.yaml           # OpenShift Route (statt Ingress)
        ├── secret.yaml          # Fingerprint Files
        ├── configmap.yaml       # Non-sensitive Env Vars
        └── _helpers.tpl
```

### values.yaml (Defaults)

```yaml
replicaCount: 1

image:
  repository: registry.gitlab.com/group/project
  tag: latest
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 3000

route:
  enabled: true
  host: ""
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect

resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 500m
    memory: 512Mi

probes:
  liveness:
    path: /api/v1/health
    initialDelaySeconds: 15
    periodSeconds: 30
    timeoutSeconds: 5
  readiness:
    path: /api/v1/health
    initialDelaySeconds: 5
    periodSeconds: 10
    timeoutSeconds: 5

# Environment Variables (injected from GitLab Secrets via Helm --set)
env: {}

# Fingerprint files (mounted as Secret Volume)
fingerprint:
  mountPath: /app/config
  readFile: fingerprint-read.json
  writeFile: fingerprint-write.json

securityContext:
  runAsNonRoot: true
  runAsUser: 1001
  fsGroup: 1001
```

### values-dev.yaml

```yaml
replicaCount: 1

route:
  host: dev-booking.example.com

resources:
  requests:
    cpu: 100m
    memory: 128Mi
  limits:
    cpu: 250m
    memory: 256Mi
```

### values-prod.yaml

```yaml
replicaCount: 2

route:
  host: booking.example.com

resources:
  requests:
    cpu: 200m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 512Mi
```

---

## 8. OpenShift-spezifische Konfiguration

### Route (TLS Edge Termination)

```yaml
# templates/route.yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: {{ include "app.fullname" . }}
spec:
  host: {{ .Values.route.host }}
  port:
    targetPort: http
  tls:
    termination: {{ .Values.route.tls.termination }}
    insecureEdgeTerminationPolicy: {{ .Values.route.tls.insecureEdgeTerminationPolicy }}
  to:
    kind: Service
    name: {{ include "app.fullname" . }}
    weight: 100
```

> **TLS:** OpenShift Router uebernimmt TLS-Terminierung (Edge).
> Das Backend selbst laeuft OHNE TLS — `TLS_ENABLED=false` in allen Umgebungen.
> Das vereinfacht das Setup und ist der OpenShift-Standard.

### SecurityContextConstraints

Der Pod laeuft unter der `restricted-v2` SCC (OpenShift Default):
- Non-Root User (UID 1001)
- Read-Only Root Filesystem (optional, `/app/config` als writable Volume)
- No privilege escalation
- Seccomp Profile: RuntimeDefault

### Fingerprint Files als Secret

```yaml
# templates/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "app.fullname" . }}-fingerprints
type: Opaque
data:
  fingerprint-read.json: {{ .Values.fingerprint.readContent | b64enc }}
  fingerprint-write.json: {{ .Values.fingerprint.writeContent | b64enc }}
```

Die Fingerprint-Dateien werden als Kubernetes Secret erstellt und als Volume in den Pod gemountet:

```yaml
# In deployment.yaml
volumes:
  - name: fingerprints
    secret:
      secretName: {{ include "app.fullname" . }}-fingerprints
containers:
  - name: app
    volumeMounts:
      - name: fingerprints
        mountPath: /app/config
        readOnly: true
    env:
      - name: SOFTNET_READ_FINGERPRINT_PATH
        value: /app/config/fingerprint-read.json
      - name: SOFTNET_WRITE_FINGERPRINT_PATH
        value: /app/config/fingerprint-write.json
```

---

## 9. Secrets-Flow

### Uebersicht: Woher kommen die Secrets?

```
┌───────────────────────────────────────────┐
│        GitLab CI/CD Variables             │
│   (Settings > CI/CD > Variables)          │
│                                           │
│  Environment: dev                         │
│  ├── PORT = 3000                          │
│  ├── SOFTNET_READ_BASE_URL = ...          │
│  ├── SESSION_SECRET = *** (masked)        │
│  ├── SOFTNET_READ_FINGERPRINT (file)      │
│  └── ...                                  │
│                                           │
│  Environment: prod                        │
│  ├── PORT = 3000                          │
│  ├── SOFTNET_READ_BASE_URL = ...          │
│  ├── SESSION_SECRET = *** (masked)        │
│  ├── SOFTNET_READ_FINGERPRINT (file)      │
│  └── ...                                  │
└──────────────┬────────────────────────────┘
               │
        GitLab CI/CD Pipeline
               │
   ┌───────────┴───────────┐
   │                       │
   ▼                       ▼
helm --set env.XXX     kubectl create secret
(Env Vars)             (Fingerprint Files)
   │                       │
   ▼                       ▼
┌──────────────────────────────────────────┐
│           OpenShift Pod                   │
│                                           │
│  process.env.PORT = 3000                  │
│  process.env.SOFTNET_READ_BASE_URL = ...  │
│  process.env.SESSION_SECRET = ***         │
│                                           │
│  /app/config/fingerprint-read.json        │
│  /app/config/fingerprint-write.json       │
│                                           │
│  NestJS ConfigModule liest process.env    │
│  (KEINE .env Datei im Container!)         │
└──────────────────────────────────────────┘
```

### Wichtige Regeln

1. **Keine `.env`-Datei im Container** — NestJS `ConfigModule` liest `process.env`
2. **Keine Secrets im Code** — `.env` und `*.json` Fingerprints sind in `.gitignore`
3. **Masked Variables** fuer Secrets (SESSION_SECRET) — werden in Pipeline-Logs nicht angezeigt
4. **File Variables** fuer Fingerprints — GitLab stellt den Inhalt als temporaere Datei bereit
5. **Environment Scoping** — Dev- und Prod-Secrets sind getrennt konfiguriert
6. **Rotation** — Secrets koennen in GitLab geaendert werden, neues Deployment uebernimmt sie

---

## 10. GitHub Actions (Dev-Workflow fuer PRs)

Die bestehende GitHub Actions Pipeline bleibt fuer PR-Checks erhalten. Sie fuehrt folgende Schritte aus:

```yaml
# .github/workflows/ci.yml (Ergaenzung)
e2e-backend:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4

    - uses: actions/setup-node@v4
      with:
        node-version: 22
        cache: npm

    - name: Install dependencies
      run: |
        npm ci
        cd backend && npm ci && cd ..
        npx playwright install chromium

    - name: Create backend .env from secrets
      run: |
        cat > backend/.env << EOF
        PORT=3000
        SOFTNET_READ_BASE_URL=${{ secrets.SOFTNET_READ_BASE_URL }}
        SOFTNET_READ_AUTH_URL=${{ secrets.SOFTNET_READ_AUTH_URL }}
        SOFTNET_READ_FINGERPRINT_PATH=./config/fingerprint-read.json
        SOFTNET_WRITE_BASE_URL=${{ secrets.SOFTNET_WRITE_BASE_URL }}
        SOFTNET_WRITE_AUTH_URL=${{ secrets.SOFTNET_WRITE_AUTH_URL }}
        SOFTNET_WRITE_FINGERPRINT_PATH=./config/fingerprint-write.json
        SOFTNET_TIMEOUT=10000
        GROUP_ID=${{ secrets.GROUP_ID }}
        BRANCH_ID=${{ secrets.BRANCH_ID }}
        CORS_ALLOWED_ORIGINS=http://localhost:3000
        SESSION_SECRET=${{ secrets.SESSION_SECRET }}
        SESSION_TTL_MINUTES=20
        TLS_ENABLED=false
        EOF

    - name: Create fingerprint files from secrets
      run: |
        mkdir -p backend/config
        echo '${{ secrets.SOFTNET_READ_FINGERPRINT }}' > backend/config/fingerprint-read.json
        echo '${{ secrets.SOFTNET_WRITE_FINGERPRINT }}' > backend/config/fingerprint-write.json

    - name: Build
      run: |
        npm run build
        cd backend && npm run build && cd ..

    - name: Run E2E tests
      run: npm run e2e:backend

    - name: Upload Playwright Report
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: playwright-report
        path: playwright-report/
        retention-days: 7
```

> **Wichtig:** GitHub Secrets enthalten die **Dev-Werte** (SoftNet Integration).
> Prod-Deployment laeuft NICHT ueber GitHub Actions, sondern ueber GitLab CI/CD → OpenShift.

---

## 11. Datei-Struktur (zu erstellen)

```
/
├── Dockerfile                              # Multi-Stage Build
├── .dockerignore                           # Exclude node_modules, .git, .env
├── .gitlab-ci.yml                          # GitLab CI/CD Pipeline
├── deploy/
│   └── helm/
│       ├── Chart.yaml
│       ├── values.yaml                     # Defaults
│       ├── values-dev.yaml                 # Dev Overrides
│       ├── values-prod.yaml                # Prod Overrides
│       └── templates/
│           ├── deployment.yaml
│           ├── service.yaml
│           ├── route.yaml                  # OpenShift Route
│           ├── secret.yaml                 # Fingerprint Secrets
│           ├── configmap.yaml
│           └── _helpers.tpl
├── .github/
│   └── workflows/
│       └── ci.yml                          # GitHub Actions (PR Checks + E2E)
├── backend/
│   ├── .env.example                        # Template (kein Secret!)
│   └── config/                             # Fingerprint-Dateien (gitignored)
│       ├── fingerprint-read.json
│       └── fingerprint-write.json
└── src/                                    # Angular Frontend
```

---

## 12. Sicherheitsanforderungen

- [ ] SEC-1: Keine Secrets im Docker Image (nur ueber Env Vars zur Laufzeit)
- [ ] SEC-2: Non-Root Container (UID 1001, `restricted-v2` SCC)
- [ ] SEC-3: TLS via OpenShift Route (Edge Termination) — kein TLS im Container
- [ ] SEC-4: GitLab Masked Variables fuer sensitive Secrets
- [ ] SEC-5: Fingerprint-Dateien als Kubernetes Secrets (verschluesselt at rest)
- [ ] SEC-6: Image Scanning in GitLab CI/CD (optional: `container_scanning` Stage)
- [ ] SEC-7: `.env`-Datei, `config/*.json`, Docker Socket sind in `.gitignore`
- [ ] SEC-8: Kein `latest` Tag in Prod — immer spezifischer Commit SHA Tag

---

## 13. Test Cases

### TC-DEPLOY-1: Docker Build

| Feld | Wert |
|------|------|
| **ID** | TC-DEPLOY-1 |
| **Beschreibung** | Docker-Image baut erfolgreich |
| **Vorbedingung** | Dockerfile existiert |
| **Schritte** | `docker build -t test .` |
| **Erwartung** | Build erfolgreich, Image < 200 MB |

### TC-DEPLOY-2: Container Start

| Feld | Wert |
|------|------|
| **ID** | TC-DEPLOY-2 |
| **Beschreibung** | Container startet und Health-Check antwortet |
| **Vorbedingung** | Docker Image gebaut, `.env`-Datei vorhanden |
| **Schritte** | `docker run -p 3000:3000 --env-file .env test` → `curl http://localhost:3000/api/v1/health` |
| **Erwartung** | HTTP 200, `{ "status": "ok" }` |

### TC-DEPLOY-3: Non-Root User

| Feld | Wert |
|------|------|
| **ID** | TC-DEPLOY-3 |
| **Beschreibung** | Container laeuft als Non-Root |
| **Schritte** | `docker exec <container> whoami` |
| **Erwartung** | `appuser` (nicht `root`) |

### TC-DEPLOY-4: GitLab Pipeline

| Feld | Wert |
|------|------|
| **ID** | TC-DEPLOY-4 |
| **Beschreibung** | GitLab CI/CD Pipeline laeuft erfolgreich durch |
| **Vorbedingung** | `.gitlab-ci.yml` + Secrets konfiguriert |
| **Schritte** | Push auf `main` |
| **Erwartung** | Alle Stages (build, test, deploy-dev) gruen |

### TC-DEPLOY-5: Secrets nicht im Image

| Feld | Wert |
|------|------|
| **ID** | TC-DEPLOY-5 |
| **Beschreibung** | Keine Secrets im Docker Image enthalten |
| **Schritte** | `docker run test cat /app/backend/.env` |
| **Erwartung** | Datei nicht vorhanden (Exit 1) |

### TC-DEPLOY-6: E2E mit Backend in CI

| Feld | Wert |
|------|------|
| **ID** | TC-DEPLOY-6 |
| **Beschreibung** | E2E-Tests laufen in der CI/CD Pipeline mit Backend |
| **Vorbedingung** | GitHub Secrets oder GitLab Variables konfiguriert |
| **Schritte** | PR erstellen oder Push auf `main` |
| **Erwartung** | E2E-Tests (Playwright) bestehen alle |

---

## 14. Rollback-Strategie

1. **Helm Rollback:** `helm rollback app-prod <revision>` — sofortiges Rollback auf vorherige Version
2. **Image Tag:** Prod verwendet immer Commit-SHA-Tags, nicht `latest` — altes Image ist in der Registry verfuegbar
3. **OpenShift:** `oc rollout undo deployment/app` — OpenShift-nativer Rollback
4. **Datenbank:** Kein eigener Datenspeicher — Backend ist stateless (alle Daten in SoftNet API)

---

## 15. Monitoring (Phase 2)

Fuer spaeter vorgesehen, nicht Teil der initialen Implementierung:

- Prometheus Metrics Endpoint (`/api/v1/metrics`)
- Grafana Dashboards (Request Rate, Latency, Error Rate)
- Alert Rules (5xx Rate > 1%, Latency P95 > 2s)
- OpenShift Monitoring Integration

---

## 16. Abhaengigkeiten

| Requirement | Beziehung |
|------------|-----------|
| REQ-014-NestJS-Backend | **Voraussetzung** — Backend muss implementiert sein |
| REQ-017 (SoftNet Integration) | **Optional** — weitere SoftNet-Endpunkte |

---

## 17. Offene Fragen

- [ ] Exakte OpenShift-Namespace-Namen (Dev + Prod)
- [ ] Domain-Namen fuer die Routes (Dev + Prod)
- [ ] GitLab-Projekt-URL (fuer Container Registry Pfad)
- [ ] Soll Image Scanning (Trivy/Clair) in der Pipeline aktiviert werden?
- [ ] Soll HorizontalPodAutoscaler (HPA) fuer Prod konfiguriert werden?

---

## 18. Implementation Notes

**Code-Sprache:** ENGLISCH — Alle Dateien (Dockerfile, Helm Charts, CI/CD Config) in Englisch.

**Gleicher Code, verschiedene Konfiguration:** Der gleiche Docker-Container laeuft in Dev und Prod. Der einzige Unterschied sind die Environment Variables (aus GitLab Secrets Store). Kein separater Build fuer Dev vs Prod.

**OpenShift vs Kubernetes:** OpenShift 4.14+ basiert auf Kubernetes, hat aber eigene Ressourcen wie `Route` (statt `Ingress`), `DeploymentConfig` (legacy, wir nutzen Standard `Deployment`), und `SecurityContextConstraints`. Die Helm Charts nutzen OpenShift-spezifische Templates wo noetig.

**TLS-Vereinfachung:** Da OpenShift Router TLS-Edge-Termination uebernimmt, braucht das Backend KEIN eigenes TLS. `TLS_ENABLED=false` in allen Umgebungen. Die TLS-Vorbereitung in REQ-014 (Zertifikat-Pfade in `.env`) bleibt als Option fuer Nicht-OpenShift-Deployments erhalten.

**Fingerprint-Handling:** Die SoftNet Fingerprint-JSON-Dateien enthalten private RSA-Keys und sind daher hochsensitiv. Sie werden als GitLab File Variables gespeichert, in der CI/CD Pipeline als temporaere Dateien bereitgestellt, und in OpenShift als Kubernetes Secrets (Volume Mount) in den Pod gemountet. Sie werden NIEMALS ins Git-Repository committed.
