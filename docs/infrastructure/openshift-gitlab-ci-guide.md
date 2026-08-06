# Deployment Guide: OpenShift (LHIND DevOps Platform) via GitLab CI/CD

> Referenzanleitung, destilliert aus einem produktiv laufenden Setup (Spring Boot +
> Angular App auf `ocp04`). Dient als Grundlage für `speckit specify` / `speckit plan`,
> wenn ein neues Projekt auf die LHIND DevOps Platform (LDP) deployed werden soll.
> Alle `<...>`-Platzhalter müssen pro Projekt ersetzt werden.

---

## 1. Grundprinzip: Zwei-Repo-GitOps-Modell

LDP trennt **Code** und **Deploy-Konfiguration** strikt in zwei Repositories:

```
Repo 1: <projekt>                  (Application Repository)
  - Anwendungscode (Frontend + Backend)
  - Dockerfile
  - .gitlab-ci.yml  → baut Image, pusht in OCP-Registry, bumped Version
  - Öffnet automatisch eine MR im Environment-Repo

Repo 2: <projekt>.environment      (Environment Repository)
  - Nur Helm-Chart + values.yaml, KEIN Anwendungscode
  - Gemergte MR triggert `helm upgrade --install` gegen den echten Cluster
```

**Warum getrennt:** Wer Code deployen darf und wer Produktions-Konfiguration ändern
darf, ist damit sauber getrennt — zwei Repos, zwei MRs, zwei Reviews.

### Rollout-Flow (Ende-zu-Ende)

```
Push auf feature/req/hotfix-Branch (via Merge Request)
    ↓
App-CI: test → versioning (semantic-release) → build (frontend → backend) → image (buildah)
    ↓
App-CI: Image-Tag "<version>-rc" in DEV-Namespace pushen
    ↓
App-CI: Environment-Repo klonen, appVersion in environments/dev/values.yaml bumpen,
        Branch pushen + automatisch MR öffnen (merge_when_pipeline_succeeds)
    ↓
Env-MR merged (automatisch) → Env-CI: `helm upgrade --install` in DEV-Namespace
    ↓
Merge nach main → App-CI: Release-Image bauen, DEV→PROD-Registry promoten (`oc tag`)
    ↓
App-CI: MR für PROD-values im Environment-Repo öffnen
    ↓
Manueller Merge der PROD-MR → Env-CI: `helm upgrade --install` in PROD-Namespace
```

DEV läuft **automatisch** durch (Approval-Gate = grüne Pipeline), PROD braucht
**immer einen manuellen Merge** — das ist der eingebaute Freigabe-Mechanismus.

---

## 2. Voraussetzungen (vor dem ersten Deploy klären)

### 2.1 EMMA-Tickets (Plattform-Team-Anfragen)

| # | Anfrage | Details |
|---|---------|---------|
| 1 | OpenShift-Namespaces | `<projekt>-dev`, `<projekt>-prod` |
| 2 | ServiceAccount | Image-Pull/Push-Rechte über beide Namespaces |
| 3 | Route-Wildcard-Domain | `*.apps.<cluster>.app.lhind.cloud` |
| 4 | Firewall-Whitelist | Alle externen Hosts, die der Pod erreichen muss (APIs, Auth-Server, ...) |
| 5 | GitLab-Runner-Tags bestätigen | Projektspezifisch — **nicht** die Default-Tags aus dem LDP-Template übernehmen! |

### 2.2 Zu klärende Cluster-Fakten

| Frage | Wo nachsehen |
|-------|--------------|
| Cluster-Name (z. B. `ocp04`) | Plattform-Team / bestehende Projekte auf gleicher Runner-Gruppe |
| Runner-Tags (z. B. `<team>`, `openshift`) | `.gitlab-ci.yml` eines bereits laufenden Referenzprojekts, oder Plattform-Team |
| Verfügbare Runner-Images (JDK/Node-Version) | `image-registry.openshift-image-registry.svc:5000/openshift/<image>-runner:latest` — Verfügbarkeit vom Plattform-Team bestätigen lassen |
| Environment-Repo-Pfad | Muss in derselben GitLab-Gruppe wie das App-Repo liegen |

### 2.3 GitLab-CI/CD-Variablen (App-Repo)

| Variable | Typ | Protected | Zweck |
|----------|-----|-----------|-------|
| `ENV_REPO_TOKEN` | Masked | Ja | Project Access Token **des Environment-Repos** (Rolle: Developer, Scopes `read_repository` + `write_repository`). Wird gebraucht, weil `CI_JOB_TOKEN` kein Cross-Project-Push erlaubt. |
| `GL_TOKEN` | Masked | Nein | Project Access Token **des App-Repos** (Rolle: Developer, gleiche Scopes) — für `semantic-release`, um Git-Tags zu pushen. |
| `JF_USER` / `JF_PASSWORD` | Variable / Masked | — | JFrog-Artifactory-Auth, falls Gradle/npm über einen internen Proxy laufen. |
| `SONAR_TOKEN` | Masked | — | Optional, erst wenn SonarQube-Projekt provisioniert ist. |

> **Wichtig:** Es gehören **keine Anwendungs-Secrets** (API-Keys, Passwörter) in
> GitLab-CI-Variablen für den Deploy-Pfad — die liegen direkt in OpenShift (siehe
> Abschnitt 6).

---

## 3. Dockerfile — OpenShift-`restricted-v2`-SCC-kompatibel

OpenShift läuft Container standardmäßig mit einer **zufälligen UID** (kein festes
`USER 1001` wie bei generischem Kubernetes) und verlangt, dass alle beschreibbaren
Verzeichnisse für **Gruppe 0** beschreibbar sind.

```dockerfile
# syntax=docker/dockerfile:1.6
# Multi-Stage-Build, Basis-Images spiegeln die LDP-CI-Runner-Images,
# damit Build- und Runtime-Verhalten identisch sind.

# ===== Stage 1: Frontend-Build (falls SPA vorhanden) =====
FROM registry.access.redhat.com/ubi9/nodejs-<version> AS frontend-build
USER root
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY <frontend-quellcode> ./
RUN npm run build

# ===== Stage 2: Backend-Build =====
FROM registry.access.redhat.com/ubi9/<runtime-base>:<version> AS backend-build
USER root
WORKDIR /workspace
COPY <backend-build-dateien> ./
# Falls das Backend-Build-Tool den Frontend-Build selbst antriggert (z. B. Gradle-Plugin):
# vorgebautes dist/ hier reinkopieren und den Task per Flag überspringen (-x buildFrontend)
COPY --from=frontend-build /app/dist/ ./dist/
RUN <backend-build-kommando>

# ===== Stage 3: Runtime =====
FROM registry.access.redhat.com/ubi9/<runtime-base>:<version>
USER root
WORKDIR /app
COPY --from=backend-build <build-artefakt> /app/app.jar
# oder je nach Stack: COPY --from=backend-build /workspace/dist/ /app/

# KEIN festes USER — OpenShift weist zur Laufzeit eine zufällige UID zu.
# Diese UID ist immer Mitglied der Gruppe 0 → deshalb chgrp/chmod statt chown.
RUN mkdir -p /app/config && \
    chgrp -R 0 /app && \
    chmod -R g=u /app

# Für lokale `docker run`/`podman run` ohne SCC trotzdem non-root:
USER 1001

EXPOSE <port>
ENTRYPOINT ["sh", "-c", "exec <startkommando>"]
```

**Merksätze:**
- Kein Docker-Daemon auf den CI-Runnern → Image-Build läuft über **`buildah`**, nicht
  `docker build` (siehe Abschnitt 4).
- JDK-Runner-Images enthalten in der Regel **kein Node.js** (und umgekehrt) → wenn
  Frontend + Backend in einem Repo liegen, den Build in zwei CI-Jobs mit
  unterschiedlichen Runner-Images splitten und das Frontend-Artefakt an den
  Backend-Job weiterreichen (siehe Abschnitt 4.3).
- Nur **freigegebene Base-Images** verwenden (i. d. R. `registry.access.redhat.com/ubi9/...`
  oder die projektspezifisch freigegebene UBI-Variante) — externe Images (`node:22-alpine`
  etc.) sind auf LDP **nicht** erlaubt/verfügbar.

---

## 4. App-Repo: `.gitlab-ci.yml`

### 4.1 Stages

```
test → versioning → build → image → rollout
```

### 4.2 Shared LDP-Templates einbinden

```yaml
stages:
  - test
  - versioning
  - build
  - image
  - rollout

default:
  tags:
    - <projekt-team-tag>   # z.B. "atapp" — vom Plattform-Team bestätigen lassen!
    - openshift
  interruptible: true

include:
  - project: 'lhind-devops-platform/gitlab-ci-templates'
    file: 'ocp.yml'          # stellt .ocp-build (Buildah-Wrapper) bereit
  - project: 'lhind-devops-platform/gitlab-ci-templates'
    file: 'artifactory.yml'  # falls JFrog-Proxy genutzt wird
  - template: Security/Secret-Detection.gitlab-ci.yml

variables:
  NAMESPACE_DEV: "<projekt>-dev"
  NAMESPACE_PROD: "<projekt>-prod"
  ENV_REPO_PATH: "<gitlab-gruppe>/<projekt>.environment"
```

### 4.3 Build-Stage — bei Mixed-Stack (Frontend + Backend) SPLITTEN

```yaml
frontend build:
  stage: build
  image: image-registry.openshift-image-registry.svc:5000/openshift/nodejs-<version>-runner:latest
  script:
    - npm ci --legacy-peer-deps
    - npm run build
  artifacts:
    paths: [dist/]
    expire_in: 1 day

backend build:
  stage: build
  image: image-registry.openshift-image-registry.svc:5000/openshift/<jdk-version>-runner:latest
  needs: [frontend build]
  script:
    - <backend-build-kommando, z.B. "cd server && ./gradlew --no-daemon bootJar -x buildFrontend">
  artifacts:
    paths: [<build-artefakt-pfad>]
    expire_in: 1 day
```

### 4.4 Versionierung (Conventional Commits → Semver)

```yaml
semantic versioning:
  stage: versioning
  image: image-registry.openshift-image-registry.svc:5000/openshift/nodejs-<version>-runner:latest
  variables:
    GIT_DEPTH: "0"
    GITLAB_TOKEN: "${GL_TOKEN}"
  before_script:
    - git config --global user.email "ci@<projekt>.lhind.app"
    - git config --global user.name "CI Bot"
    - npm install -g semantic-release conventional-changelog-conventionalcommits
        @semantic-release/commit-analyzer @semantic-release/release-notes-generator
        @semantic-release/exec
  script:
    - npx semantic-release --no-ci
  artifacts:
    paths: [version.txt, version_full.txt]
    expire_in: 1 day
```

### 4.5 Image-Stage — Buildah statt Docker

```yaml
build dev rc image:
  stage: image
  image: registry.access.redhat.com/ubi8/buildah:latest
  extends: .ocp-build
  needs: [frontend build, backend build, semantic versioning]
  script:
    - VERSION=$(cat version_full.txt)
    - TAG_RC="${VERSION}-rc"
    - ocp-build ./Dockerfile $NAMESPACE_DEV $CI_PROJECT_NAME:$TAG_RC
    - ocp-push $NAMESPACE_DEV $CI_PROJECT_NAME:$TAG_RC
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'

build release image:
  stage: image
  image: registry.access.redhat.com/ubi8/buildah:latest
  extends: .ocp-build
  needs: [frontend build, backend build, semantic versioning]
  script:
    - VERSION=$(cat version.txt)
    - TAG="v${VERSION}"
    - ocp-build ./Dockerfile $NAMESPACE_DEV $CI_PROJECT_NAME:$TAG
    - ocp-push $NAMESPACE_DEV $CI_PROJECT_NAME:$TAG
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'

promote release image:
  stage: image
  image: image-registry.openshift-image-registry.svc:5000/openshift/lhind-ocp-tools:latest
  needs: [build release image]
  script:
    - VERSION=$(cat version.txt)
    - TAG="v${VERSION}"
    - oc tag --reference-policy=local "$NAMESPACE_DEV/$CI_PROJECT_NAME:$TAG" "$NAMESPACE_PROD/$CI_PROJECT_NAME:$TAG"
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
```

### 4.6 Rollout-Stage — MR im Environment-Repo automatisch öffnen

```yaml
.rollout-base:
  stage: rollout
  image: image-registry.openshift-image-registry.svc:5000/openshift/lhind-ocp-tools:latest
  variables:
    TARGET_PROJECT_BRANCH: "main"
  before_script:
    - git config --global user.email "ci@<projekt>.lhind.app"
    - git config --global user.name "CI Bot"

deploy feature:
  extends: .rollout-base
  needs: [semantic versioning, build dev rc image]
  script:
    - |
      VERSION=$(cat version_full.txt)
      TAG_RC="${VERSION}-rc"
      git clone "https://oauth2:${ENV_REPO_TOKEN}@${CI_SERVER_HOST}/${ENV_REPO_PATH}.git"
      cd <projekt>.environment
      git checkout "$TARGET_PROJECT_BRANCH"
      BRANCH_NAME="version-update-${CI_COMMIT_SHORT_SHA}"
      git checkout -b "$BRANCH_NAME"
      yq eval ".global.appVersion = \"$TAG_RC\"" -i environments/dev/values.yaml
      git add environments/dev/values.yaml
      git commit -m "chore(dev): bump appVersion to $TAG_RC"
      git push "https://oauth2:${ENV_REPO_TOKEN}@${CI_SERVER_HOST}/${ENV_REPO_PATH}.git" "HEAD:$BRANCH_NAME" \
        -o merge_request.create=true \
        -o merge_request.target="$TARGET_PROJECT_BRANCH" \
        -o merge_request.remove_source_branch \
        -o merge_request.merge_when_pipeline_succeeds
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
```

Für den PROD-Release-Zweig dasselbe Muster, aber **ohne**
`merge_request.merge_when_pipeline_succeeds` (manueller Merge = Freigabe-Gate).

---

## 5. Environment-Repo (`<projekt>.environment`)

### 5.1 Struktur

```
<projekt>.environment/
├── .gitlab-ci.yml            # package → deploy-dev → deploy-prod
├── base/
│   ├── Chart.yaml
│   ├── values.yaml           # Defaults
│   └── templates/
│       ├── _helpers.tpl
│       ├── deployment.yaml   # Deployment (NICHT DeploymentConfig!)
│       ├── service.yaml
│       ├── route.yaml        # OpenShift Route statt Ingress
│       ├── serviceaccount.yaml
│       ├── configmap.yaml
│       └── hpa.yaml          # optional
└── environments/
    ├── dev/
    │   ├── Chart.yaml
    │   └── values.yaml       # überschreibt Image-Repo, Route-Host, Env-Vars für DEV
    └── prod/
        ├── Chart.yaml
        └── values.yaml
```

### 5.2 `environments/dev/values.yaml` (Beispiel)

```yaml
global:
  app:
    name: <projekt>
  appVersion: "0.1.0"   # wird von der CI per yq überschrieben

base:
  deployment:
    replicaCount: 1
    serviceAccountName: <projekt>
    image:
      repository: image-registry.openshift-image-registry.svc:5000/<projekt>-dev/<projekt>
      pullPolicy: Always
    ports:
      - name: http
        containerPort: <port>
        port: <port>
        protocol: TCP
    resources:
      requests: { memory: "256Mi", cpu: "100m" }
      limits:   { memory: "512Mi", cpu: "500m" }
  route:
    host: <projekt>-dev.apps.<cluster>.app.lhind.cloud
    targetPort: <port>
  env:
    keyValue:
      - name: SPRING_PROFILES_ACTIVE   # oder projekt-äquivalent
        value: "dev"
      # ... weitere nicht-sensible Env-Vars
    fromSecrets:
      - name: <projekt>-secrets
        include: true
        keys:
          - <SECRET_ENV_VAR_NAME>
```

**Kyverno-Policy-Pflichten (LDP-Standard):**
- Kein Deployment mit dem `"default"`-ServiceAccount → eigener SA per Helm-Template
- `resources.requests` **und** `resources.limits` für jeden Container Pflicht
- Kein `privileged: true`, keine feste UID (siehe Abschnitt 3)

---

## 6. Secrets — bewusst NICHT in GitLab

Anwendungs-Secrets (API-Keys, DB-Passwörter, Zertifikate) werden **einmalig von
einem Admin direkt im OpenShift-Namespace** angelegt, nicht über GitLab-CI:

```bash
oc create secret generic <projekt>-secrets \
  --from-literal=<SECRET_ENV_VAR_NAME>=<wert> \
  --from-file=<dateibasiertes-secret>=<pfad> \
  -n <projekt>-dev
```

Helm referenziert das Secret nur per Name (`fromSecrets` in `values.yaml`, siehe
oben) bzw. mountet es als Volume — der Secret-**Inhalt** taucht nirgendwo im Git
oder in CI-Logs auf. Für PROD denselben Befehl mit `-n <projekt>-prod` und den
produktiven Werten wiederholen.

---

## 7. Glossar / Abweichungen von "Standard-Kubernetes"

| Begriff/Konzept | OpenShift/LDP-Besonderheit |
|---|---|
| Image-Build | **Buildah** statt `docker build` — kein Docker-Socket auf den Runnern |
| Image-Registry | **OpenShift-interne Registry** (`image-registry.openshift-image-registry.svc:5000`), keine externe Container-Registry |
| `Ingress` | **`Route`** (OpenShift-eigenes Objekt, TLS Edge Termination eingebaut) |
| `DeploymentConfig` | Auf LDP wird das **Standard-`Deployment`** verwendet, nicht das ältere OpenShift-eigene `DeploymentConfig` |
| Feste `USER`-UID im Dockerfile | Wird von der `restricted-v2`-SCC ignoriert/verboten — OpenShift weist zur Laufzeit random UIDs zu, Gruppe 0 ist der gemeinsame Nenner |
| `image.openshift.io/triggers`-Annotation | Löst automatischen Rollout aus, wenn jemand manuell `oc tag` ausführt — im MR-gesteuerten Flow nur ein Digest-Pinning-Nebeneffekt, nicht der primäre Deploy-Trigger (das ist `helm upgrade --install`) |
| Secrets-Management | Kein External-Secrets-Operator nötig, wenn Secrets manuell im Namespace angelegt werden — einfacher, aber Admin-Schritt pro Environment |

---

## 8. Setup-Checkliste (neues Projekt)

- [ ] EMMA-Tickets gestellt (Namespaces, ServiceAccount, Route-Domain, Firewall)
- [ ] Runner-Tags vom Plattform-Team bestätigt (`default.tags` in `.gitlab-ci.yml`)
- [ ] Verfügbare Runner-Images (JDK-/Node-Version) bestätigt
- [ ] Dockerfile: Multi-Stage, UBI-Base-Images, `chgrp -R 0`/`chmod g=u`, kein festes `USER`
- [ ] App-Repo `.gitlab-ci.yml` mit den LDP-Templates (`ocp.yml`) angelegt
- [ ] `GL_TOKEN` + `ENV_REPO_TOKEN` als CI/CD-Variablen hinterlegt (beide: Developer-Rolle, `read_repository` + `write_repository`)
- [ ] Zweites Repo `<projekt>.environment` angelegt (gleiche GitLab-Gruppe)
- [ ] Helm-Chart (`base/` + `environments/dev|prod/`) im Environment-Repo
- [ ] Secrets einmalig manuell in beiden Namespaces angelegt (`oc create secret ...`)
- [ ] Erster Test-Durchlauf: Feature-Branch-MR → DEV-Deploy beobachten
- [ ] Erst danach: Merge nach `main` → PROD-Release-Flow testen (manueller MR-Merge!)

---

## 9. Referenzprojekt

Dieses Setup wurde 1:1 aus einem produktiv laufenden LHIND-Projekt (Spring Boot +
Angular, Gradle) destilliert — siehe `.gitlab-ci.yml` und
`docs/requirements/REQ-019-Deployment/DEPLOYMENT-PLAN.md` in diesem Repo als
vollständige Referenz (konkrete Kyverno-Policy-Texte, vollständige
Helm-Template-Dateien, `SecretStore`/`ExternalSecret`-Varianten).
