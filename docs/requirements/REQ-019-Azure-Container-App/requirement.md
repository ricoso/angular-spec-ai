# REQ-019: Azure Container App Hosting

| Feld | Wert |
|------|------|
| **ID** | REQ-019 |
| **Titel** | Azure Container App Hosting |
| **Status** | Draft |
| **Prioritaet** | Hoch |
| **Abhaengigkeiten** | REQ-018-Spring-Boot-Migration |
| **Erstellt** | 2026-04-23 |

---

## 1. Zusammenfassung

Die Anwendung (Angular 21 Frontend + Spring Boot Backend, zusammen in einem Container) wird als **Azure Container App** gehostet.

**Rolle:** **Fallback-Hosting.** Die eigentliche Ziel-Plattform ist REQ-020 (OpenShift). REQ-019 deckt den Fall ab, dass die Bereitstellung der OpenShift-Infrastruktur durch das interne Plattform-Team zu lange dauert und wir eine schnelle, betriebsfaehige Alternative brauchen. REQ-019 muss daher mit **minimalem Setup-Aufwand** aufsetzbar sein.

**Region:** `Germany West Central` (Frankfurt) — Datenlokation Deutschland, kuerzeste Latenz zu den Kunden.

**Deploy-Pfad:** **Option B — ACR Task + GitLab Webhook (Zero Pipeline).**
- Kein `.gitlab-ci.yml` noetig
- **Azure Container Registry (ACR) Task** baut das Image direkt aus dem GitLab-Repo
- GitLab feuert bei `git push` einen Webhook an den ACR-Task
- Container App ist im **Continuous-Deployment-Modus** — zieht neue Images automatisch
- Bewusster Trade-off: **keine Tests als Deploy-Gate** — Qualitaet wird lokal + in Dev abgesichert. Fuer eine Fallback-Loesung akzeptabel.

---

## 2. Architektur-Uebersicht

```
                    ┌─────────────────────────────────────────────┐
                    │              GitHub Repository               │
                    │  (Code + Dockerfile + Bicep/Terraform)       │
                    └──────────────┬──────────────────────────────┘
                                   │
                          GitHub Actions (CI/CD)
                                   │
                                   ▼
                    ┌─────────────────────────────────────────────┐
                    │       Azure Container Registry (ACR)         │
                    └──────────────┬──────────────────────────────┘
                                   │
                  ┌────────────────┴────────────────┐
                  │                                 │
         ┌────────▼─────────┐              ┌────────▼─────────┐
         │ Azure Container  │              │ Azure Container  │
         │ App (Dev)        │              │ App (Prod)       │
         │                  │              │                  │
         │ SoftNet Int.     │              │ SoftNet Prod     │
         └──────────────────┘              └──────────────────┘
```

### Datenfluss

```
Browser → Azure FQDN (TLS) → Container App Ingress → App-Container (:3000) → SoftNet API
                                                          │
                                                     Angular App (static)
                                                     Spring Boot Backend
```

---

## 3. Acceptance Criteria

### Container & Registry

- [ ] AC-1: Multi-Stage Dockerfile vorhanden (Frontend-Build + Backend-Build + Runtime)
- [ ] AC-2: Container laeuft als Non-Root User
- [ ] AC-3: Container lauscht auf Port 3000 (konfigurierbar ueber `PORT` Env-Var)
- [ ] AC-4: Health-Check Endpoint `GET /api/v1/health` antwortet mit 200 OK
- [ ] AC-5: Image-Tag-Strategie: Git Commit SHA + `latest`
- [ ] AC-6: Image liegt in **Azure Container Registry (ACR)** — kein oeffentliches Registry

### Azure Container App Konfiguration (Minimal-Sizing fuer ~600 Buchungen/Monat)

**Last-Profil:** ~600 Buchungen/Monat → ~20/Tag → durchschnittlich <1/Stunde, Peak ca. 5/Stunde in Geschaeftszeiten. Eine einzige kleine Instanz reicht locker.

- [ ] AC-7: **Eine** Container App Environment (nur Prod — kein Dev, Dev laeuft lokal)
- [ ] AC-8: Ingress aktiviert, extern erreichbar, Target-Port 3000
- [ ] AC-9: TLS via Azure Managed Certificate auf dem **Standard-FQDN** (`<app>.<env>.germanywestcentral.azurecontainerapps.io`) — kostenlos, auto-renew
- [ ] AC-10: **Keine Custom Domain** — Standard-FQDN wird direkt im iframe der Autohaus-Seite eingebunden
- [ ] AC-11: Revision-Mode: `Single` (immer nur eine aktive Revision)
- [ ] AC-12: Scale-Rules (Minimal, aber HA):
  - Min Replicas: **2** (HA — waehrend Rolling-Update immer mind. 1 verfuegbar, kein Cold-Start-Risiko)
  - Max Replicas: **2** (bei 600/Monat keine Skalierung noetig)
  - Kein HTTP-Scale-Trigger
- [ ] AC-13: CPU/Memory: **`0.5 vCPU / 1 Gi`** pro Replica (Minimum fuer Spring Boot; 0.25 vCPU / 0.5 Gi waere zu eng)
- [ ] AC-14: Liveness Probe: `GET /api/v1/health` (initialDelay 30s, period 30s) — 30s wegen Spring-Boot-Startzeit
- [ ] AC-15: Readiness Probe: `GET /api/v1/health` (initialDelay 20s, period 10s)
- [ ] AC-16: Graceful Shutdown via SIGTERM (Spring Boot default, siehe CG-4)
- [ ] AC-16a: **Geschaetzte Kosten:** ca. €30-45/Monat (2× 0.5 vCPU/1 Gi Container App Consumption + ACR Basic + Log Analytics bei diesem Volumen)

### Secrets & Konfiguration

- [ ] AC-17: Secrets werden als **Container App Secrets** abgelegt (verschluesselt) und als Env-Variablen referenziert
- [ ] AC-18: Fingerprint-JSON wird als Container App Secret gespeichert (Env-Wert = JSON-Inhalt direkt, siehe REQ-017 Scope (i))
- [ ] AC-19: Folgende Env-Variablen sind gesetzt (pro Umgebung):

| Variable | Quelle | Typ |
|----------|--------|-----|
| `PORT` | Plain | Variable |
| `SOFTNET_BASE_URL` | Plain | Variable |
| `SOFTNET_AUTH_URL` | Plain | Variable |
| `SOFTNET_FINGERPRINT_PATH` | Secret-Ref (JSON-Inhalt) | Secret |
| `SOFTNET_TIMEOUT` | Plain | Variable |
| `GROUP_ID` | Plain | Variable |
| `BRANCH_ID` | Plain | Variable |
| `CORS_ALLOWED_ORIGINS` | Plain | Variable |
| `IFRAME_ALLOWED_ORIGINS` | Plain | Variable |
| `SESSION_SECRET` | Secret-Ref | Secret |
| `SESSION_TTL_MINUTES` | Plain | Variable |

- [ ] AC-20: Keine `.env`-Datei im Container-Image

### CI/CD — Option B (Zero Pipeline)

- [ ] AC-21: **ACR Task** mit Git-Source auf das GitLab-Repo via **HTTPS + Personal Access Token (PAT)**
  - Kommando: `az acr task create --context https://oauth2:<PAT>@gitlab.example.com/<group>/<repo>.git#main --file Dockerfile`
  - GitLab PAT mit Scope `read_repository` genuegt
  - PAT wird einmalig beim `task create` uebergeben, danach von ACR verschluesselt gespeichert
  - **Kein SSH-Deploy-Key** — nicht unterstuetzt/gewuenscht
- [ ] AC-22: **GitLab Webhook** auf den ACR-Task gepinned (`Project > Webhooks > Push events`)
  - URL vom ACR-Task via `az acr task show --name <task> --query "trigger.sourceTriggers"` holen
  - Webhook-Secret in GitLab setzen, in ACR-Task als `--git-access-token` hinterlegen
- [ ] AC-23: ACR Task baut Image, taggt mit Commit-SHA + `latest`, pusht in ACR
- [ ] AC-24: Container App mit **Continuous Deployment** auf den `:latest`-Tag — neue Revision wird automatisch aktiviert
- [ ] AC-25: Rollback via `az containerapp revision activate <previous-revision>` (manuell)
- [ ] AC-26: **Kein** Deploy-Gate durch Tests — Quality wird lokal + in Dev-Branches abgesichert
- [ ] AC-26a: **PAT-Rotation dokumentiert** — wenn das Token ablaeuft, ACR-Task mit `az acr task update --git-access-token <new-pat>` aktualisieren

### Infrastructure as Code (minimal)

- [ ] AC-27: Einmalige Initial-Einrichtung per **`az`-CLI-Script** (`deploy/azure/setup.sh`) — Resource Group, ACR, Log Analytics, Container App Env, Container App, ACR Task
- [ ] AC-28: Bicep optional als Alternative — nicht zwingend fuer Fallback-Nutzung
- [ ] AC-29: Nur **eine Stage (Prod)** — kein separates Dev, weil REQ-019 selbst der Fallback ist (Dev laeuft lokal)

### Monitoring & Logging

- [ ] AC-30: Logs fliessen automatisch in **Azure Log Analytics** (per Container App Environment)
- [ ] AC-31: Structured JSON Logging im Spring Boot Backend (siehe CG-5)
- [ ] AC-32: Azure Monitor Alert auf `5xx Rate > 5%` (minimal — mehr erst bei Bedarf)

### Netzwerk & Sicherheit

- [ ] AC-33: Container App ist oeffentlich erreichbar (External Ingress)
- [ ] AC-34: Outbound-Zugriff auf `*.soft-nrg.com` moeglich (Default bei External Egress)
- [ ] AC-35: Keine Private Networking / VNet-Integration — Fallback-Setup soll simpel bleiben

---

## 4. Umgebungs-Matrix

| Aspekt | Lokal | Azure Prod (Fallback) |
|--------|-------|------------------------|
| **Env-Quelle** | `server/.env` | Container App Secrets |
| **SoftNet** | Integration | Production |
| **TLS** | Nein | Managed Cert auf Standard-FQDN |
| **Domain** | `localhost:3000` | `<app>.<env>.germanywestcentral.azurecontainerapps.io` |
| **Min / Max Replicas** | 1 / 1 | 2 / 2 (HA) |
| **CPU / Memory** | — | 0.5 vCPU / 1 Gi pro Replica |
| **Fingerprint** | Datei-Pfad | Container App Secret (JSON) |
| **Kosten** | 0 | ~€30-45/Monat |

---

## 5. Code-Gaps — Was im Code noch fehlt (Implementierungs-Requirements)

Bestandsaufnahme Stand 2026-04-23. Diese Punkte muessen umgesetzt werden, damit die Anwendung auf Azure Container Apps lauffaehig ist.

### 5.1 Backend — Spring Boot

**CG-1: Fingerprint aus Env-Variable parsen (JSON-Inhalt direkt)**
- Datei: `server/src/main/java/com/autohaus/backend/softnet/SoftnetAuthService.java`
- Aktuell: `Files.readString(Path.of(fingerprintPath))` liest eine Datei.
- Soll: Property-Wert direkt als JSON uebergeben (`objectMapper.readTree(fingerprintValue)`), kein File-IO.
- Abhaengig von: REQ-017 Scope (i)
- Tests anpassen: `SoftnetAuthServiceTest.java` darf keine temporaere Datei mehr anlegen.

**CG-2: iframe-Header in `SecurityConfig.java`**
- Datei: `server/src/main/java/com/autohaus/backend/config/SecurityConfig.java:26`
- Aktuell: `.frameOptions(frame -> frame.deny())` — blockiert jedes iframe-Embedding.
- Soll:
  - `.frameOptions(frame -> frame.sameOrigin())` ODER `.frameOptions(frame -> frame.disable())`
  - CSP-Header `Content-Security-Policy: frame-ancestors 'self' <IFRAME_ALLOWED_ORIGINS>` via `addHeaderWriter`
- Abhaengig von: REQ-017 Scope (h)

**CG-3: `IFRAME_ALLOWED_ORIGINS` Env-Variable**
- Datei: `server/src/main/java/com/autohaus/backend/config/AppProperties.java`
- Soll: Neue Record-Property `IframeProperties(String allowedOrigins)` analog zu `CorsProperties`.
- `application.yml`: `app.iframe.allowed-origins: ${IFRAME_ALLOWED_ORIGINS:}` hinzufuegen.
- `SecurityConfig` liest die Property und baut den CSP-`frame-ancestors`-Wert.

**CG-4: Graceful Shutdown**
- Datei: `server/src/main/resources/application.yml`
- Soll: `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 30s`
- Grund: Azure Container App sendet SIGTERM vor Pod-Restart — saubere Request-Beendigung.

**CG-5: Structured JSON Logging**
- Datei: `server/src/main/resources/logback-spring.xml` (neu) oder `logstash-logback-encoder` Dependency.
- Soll: JSON-Log-Format fuer Production (Profile-basiert aktivieren), damit Azure Log Analytics die Felder indexieren kann.
- Aktuell: Pattern-Log (`%d{ISO8601} [%thread] ...`) — fuer Log Analytics schlecht durchsuchbar.

**CG-6 (optional): Health-Endpoint mit SoftNet-Check**
- Datei: `server/src/main/java/com/autohaus/backend/health/HealthController.java`
- Aktuell: Liefert nur Backend-Status.
- Soll (optional, Phase 2): Spring Actuator `/actuator/health` mit `SoftnetHealthIndicator` — prueft `GET /api/planning/branches` reachability.
- Begruendung: Azure Liveness reicht, aber ein echter Readiness-Check verhindert Traffic auf defekte Revisionen.

### 5.2 Frontend — Angular

**CG-7: iframe Close-Button im Header**
- Datei: `src/app/core/components/header/*` (oder aequivalent)
- Soll:
  - X-Button nur sichtbar wenn `window.self !== window.top`
  - Click: `window.top.location.href = <return-url>` (Return-URL aus `environment.ts`)
- Abhaengig von: REQ-017 Scope (h)

**CG-8: Return-URL konfigurierbar**
- Datei: `src/environments/environment.ts` + `environment.prod.ts`
- Soll: `returnUrl: string` (z.B. `'https://www.gottfried-schultz.de/werkstatt-termin/'`)
- Alternative: Return-URL per Query-Param `?returnUrl=...` (flexibler, aber Redirect-Target whitelisten im Backend).

### 5.3 Build & Deployment

**CG-9: Dockerfile (Multi-Stage)**
- Datei: `Dockerfile` (Root, neu)
- Stages:
  1. `frontend-build`: `node:22-alpine` — Angular Build (`npm ci && npm run build`)
  2. `backend-build`: `eclipse-temurin:21-jdk-alpine` — Gradle Build (`./gradlew bootJar`)
  3. `runtime`: `eclipse-temurin:21-jre-alpine` — JAR + Angular `dist/` in ein Image, Non-Root User
- `EXPOSE 3000`, `CMD ["java", "-jar", "app.jar"]`
- Healthcheck: `curl -f http://localhost:3000/api/v1/health`

**CG-10: `.dockerignore`**
- Datei: `.dockerignore` (Root, neu)
- Inhalt: `node_modules/`, `dist/`, `coverage/`, `.git/`, `.env`, `server/build/`, `server/.gradle/`, `*.md`

**CG-11: ACR Task Setup-Script (Option B)**
- Datei: `deploy/azure/setup-acr-task.sh` (neu)
- Inhalt:
  - `az acr task create` mit `--context https://oauth2:<PAT>@gitlab.example.com/<group>/<repo>.git#main`
  - `--file Dockerfile`, `--image autohaus-booking:{{.Run.ID}}` + `autohaus-booking:latest`
  - `--commit-trigger-enabled true` (wird per GitLab Webhook gefeuert)
- Auth: GitLab Personal Access Token mit Scope `read_repository`
- **Kein GitHub Actions, kein `.gitlab-ci.yml`** — Build passiert vollstaendig in ACR
- Nach dem `create`: GitLab-Webhook-URL aus `az acr task show ...` auslesen und im GitLab-Repo eintragen

**CG-12: Azure-Setup-Script (einmalige Initialeinrichtung)**
- Datei: `deploy/azure/setup.sh` (neu)
- Inhalt (idempotent):
  - `az group create --name rg-autohaus-booking --location germanywestcentral`
  - `az acr create --sku Basic`
  - `az monitor log-analytics workspace create`
  - `az containerapp env create`
  - `az containerapp create` — mit Continuous Deployment auf `<acr>/autohaus-booking:latest`
  - Secrets setzen: `SOFTNET_FINGERPRINT_PATH` (JSON-Inhalt), `SESSION_SECRET`
- Bicep optional — fuer Fallback-Setup ist Shell-Script schneller aufzusetzen

**CG-13: Betriebs-README**
- Datei: `deploy/azure/README.md` (neu)
- Inhalt:
  - Setup-Schritte (setup.sh + setup-acr-task.sh)
  - GitLab-PAT erzeugen + einbauen
  - GitLab-Webhook setzen
  - Rollback: `az containerapp revision list` + `revision activate`
  - Log-Queries fuer Log Analytics
  - PAT-Rotation (AC-26a)
  - Secrets-Rotation (`az containerapp secret set`)

### 5.4 Konfiguration / Laufzeit

**CG-14: `application.yml` um `app.iframe.allowed-origins` erweitern**
- Siehe CG-3.

**CG-15: Production-Profile `application-prod.yml`**
- Datei: `server/src/main/resources/application-prod.yml` (neu)
- Enthaelt: JSON-Logging aktivieren, `logging.level.com.autohaus.backend: INFO` (statt DEBUG in Dev), `server.shutdown: graceful`.

### 5.5 Tests

**CG-16: Security-Header E2E-Test**
- Datei: `playwright/specs/security-headers.spec.ts` (neu)
- Prueft: `Content-Security-Policy` enthaelt `frame-ancestors`, `X-Frame-Options` ist `SAMEORIGIN` (oder fehlt), HTTPS-Redirect aktiv (nur in Azure, nicht lokal).

**CG-17: iframe-Embedding E2E-Test**
- Datei: `playwright/specs/iframe-embedding.spec.ts` (neu)
- Prueft: App laedt in iframe, Close-Button sichtbar und funktional.

---

## 6. Offene Punkte (TBD mit Plattform-Team / Kunde)

- Azure Subscription-ID + Tenant-ID (nur Prod, Dev bleibt lokal)
- Resource-Group-Name (Vorschlag: `rg-autohaus-booking`)
- ACR-Name (Vorschlag: `acrautohausbooking`)
- Container-App-Name (Vorschlag: `ca-autohaus-booking`)
- Custom Domain (Prod) — wer verwaltet DNS?
- GitLab-URL + Gruppen-/Projekt-Pfad fuer ACR-Task Git-Context
- GitLab Personal Access Token (Scope `read_repository`) — von wem erzeugt?
- Kostenlimit / Budget Alert
- Secrets-Rotation: SESSION_SECRET / Fingerprint / PAT — Intervall?

---

## 7. Dependencies

**Requires:**
- REQ-017 Scope (i): Fingerprint-Env-Variable enthaelt JSON-Inhalt direkt (nicht Datei-Pfad) — zwingend fuer Container App Secret
- REQ-018: Spring-Boot-Migration

**Blocks:**
- Keine

---

## 8. Out-of-Scope (Referenz)

- OpenShift / GitLab CI — siehe REQ-020 (alternativer Deployment-Pfad, derzeit nicht aktiv verfolgt)
- On-Prem Deployment
- Multi-Region Setup

---

## 9. Approval

| Rolle | Name | Datum |
|-------|------|-------|
| Product Owner | | |
| Tech Lead | | |
| Platform Team | | |
