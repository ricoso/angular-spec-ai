# REQ-022 — Fahrplan: Azure Container App Hosting (Alternative)

Checkliste und Leitfaden fuer das Hosting in einer Azure Container App.
Das eigentliche Requirement (AC-Liste) liegt in [`requirement.md`](./requirement.md).

**Rolle:** Alternative Azure-Hosting-Variante. Primaere Ziel-Plattform ist REQ-019 (LDP/OpenShift), POC-Variante ist REQ-020 (Azure POC via deploy.sh). REQ-022 dokumentiert die Zero-Pipeline-Alternative (ACR Task + GitLab Webhook).
**Deploy-Pfad:** Option B — **ACR Task + GitLab Webhook, Zero Pipeline**. Kein `.gitlab-ci.yml`, kein GitHub Actions.
**Auth:** HTTPS + GitLab Personal Access Token (kein SSH).

---

## Verwandte Requirements

| REQ | Inhalt | Relevanz fuer REQ-019 |
|-----|--------|-----------------------|
| [REQ-017](../REQ-017-Loading-Spinner/requirement.md) Scope (h) | iframe-Embedding + X-Frame-Options `SAMEORIGIN`, CSP `frame-ancestors`, Close-Button | App wird im iframe der Autohaus-Seite ausgeliefert — Azure-Ingress muss die Headers nicht stripen |
| [REQ-017](../REQ-017-Loading-Spinner/requirement.md) Scope (i) | `SOFTNET_FINGERPRINT_PATH` enthaelt JSON-Inhalt direkt (nicht Pfad) | Zwingend fuer Container App Secret — ohne das waere ein Volume-Mount noetig |
| [REQ-018](../REQ-018-Spring-Boot-Migration/requirement.md) | Backend auf Spring Boot, 1 Container fuer Frontend + Backend | Ist die deploybare Einheit fuer die Container App |
| [REQ-020](../REQ-020-Deployment/requirement.md) | OpenShift/GitLab-Variante | Alternativer Pfad — nicht aktiv verfolgt |

---

## Was aus REQ-017 bereits abgedeckt wird

Damit wir es nicht doppelt spezifizieren:

- [x] **Fingerprint als Env-Wert (statt Pfad)** — REQ-017 (i). Hier nur noch: Env-Wert als Container App Secret ablegen.
- [x] **iframe-Embedding Headers** — REQ-017 (h):
  - `X-Frame-Options: SAMEORIGIN` (oder entfernen)
  - `Content-Security-Policy: frame-ancestors 'self' https://www.gottfried-schultz.de`
  - Frontend-Close-Button via `window.top.location.href`
- [x] **CORS / Allowed Origins** — REQ-014/REQ-018 `CORS_ALLOWED_ORIGINS` + `IFRAME_ALLOWED_ORIGINS` als Env-Variablen

Was **REQ-017 NICHT** abdeckt und in REQ-019 gehoert:
- Azure-Container-App-Konfiguration selbst
- HTTPS/TLS-Zertifikat (Azure Managed Cert)
- Secrets-Verwaltung in Azure
- Outbound-Firewall fuer SoftNet API
- CI/CD Workflow

---

## Fahrplan (Phasen)

### Phase 1 — Infrastruktur anlegen (Minimal)

- [ ] **Azure Subscription + Resource Group** anlegen (nur Prod — Dev laeuft lokal)
- [ ] **Region:** `Germany West Central` (Frankfurt)
- [ ] **Azure Container Registry (ACR)** provisionieren, SKU `Basic` (reicht fuer 600 Buchungen/Monat)
- [ ] **Log Analytics Workspace** anlegen (Pay-as-you-go, minimales Volumen bei dem Traffic)
- [ ] **Container App Environment** — eine einzige
- [ ] **Container App** — 2 Replicas (HA), 0.5 vCPU / 1 Gi pro Replica

### Phase 2 — Container bauen & pushen

- [ ] `Dockerfile` fuer Spring Boot + Angular Static Files (Multi-Stage)
- [ ] `.dockerignore` (node_modules, build/, .env, coverage)
- [ ] Lokaler Build-Test: `docker build -t autohaus-booking .`
- [ ] Image in ACR pushen (manuell als Smoke-Test)
- [ ] Container App auf das ACR-Image zeigen lassen

### Phase 3 — HTTPS auf Standard-FQDN (keine Custom Domain)

- [ ] **Ingress aktivieren** (External, Target-Port 3000)
- [ ] Standard-FQDN testen: `<app>.<env-id>.germanywestcentral.azurecontainerapps.io`
- [ ] **Azure Managed Certificate** ist auf dem Standard-FQDN automatisch aktiv — nichts weiter zu tun
- [ ] HTTPS-Redirect ist bei External Ingress Default
- [ ] FQDN im iframe der Autohaus-Seite eintragen (via `src="..."`)

### Phase 4 — Secrets & Konfiguration

- [ ] Env-Variablen (plain) definieren — siehe [requirement.md §3 AC-19](./requirement.md)
- [ ] **Container App Secrets** anlegen fuer:
  - `SOFTNET_FINGERPRINT_PATH` → JSON-Inhalt der Fingerprint-Datei (siehe REQ-017 (i))
  - `SESSION_SECRET` → min. 32-Byte HMAC-Secret
- [ ] Secrets als Env-Variablen in die Container App referenzieren (`secretRef`)
- [ ] Cross-Check: keine `.env` im Image, keine Secrets in Logs

### Phase 5 — Firewall / Netzwerk

- [ ] **Outbound nach SoftNet API** funktioniert ohne weitere Config (Default bei External Egress)
- [ ] **Optional (wenn Enterprise-Netzwerk):** VNet-Integration + NSG mit Egress-Rules fuer:
  - `api.soft-nrg.com` (443)
  - `int-api.soft-nrg.com` (443)
  - `auth.soft-nrg.com` (443)
  - `int-auth.soft-nrg.com` (443)
- [ ] **Inbound:** Container App Ingress lauscht nur auf 443 (HTTPS) — 80 wird automatisch auf 443 umgeleitet
- [ ] **iframe-Quelle whitelisten:** `IFRAME_ALLOWED_ORIGINS=https://www.gottfried-schultz.de` als Env-Variable setzen (Spring Boot wertet das beim CSP-Header-Aufbau aus)

### Phase 6 — Initial-Setup via Shell-Script

- [ ] **Setup-Script** `deploy/azure/setup.sh` — idempotent, alle `az`-Kommandos:
  - Resource Group, ACR (Basic), Log Analytics Workspace
  - Container App Environment
  - Container App (Continuous Deployment auf `:latest`)
  - Secrets setzen (SOFTNET_FINGERPRINT_PATH = JSON-Inhalt, SESSION_SECRET)
- [ ] Bicep ist optional — fuer Fallback-Setup reicht Shell-Script

### Phase 7 — ACR Task + GitLab Webhook (Zero Pipeline)

**Kein `.gitlab-ci.yml`, kein GitHub Actions — ACR baut selbst aus dem Repo.**

- [ ] **GitLab Personal Access Token erzeugen** (`Settings > Access Tokens`)
  - Scope: `read_repository`
  - Ablaufdatum notieren → spaetere Rotation planen (siehe AC-26a)
- [ ] **ACR Task anlegen** mit Git-Source + PAT:
  ```bash
  az acr task create \
    --registry <acr-name> \
    --name autohaus-booking-build \
    --context "https://oauth2:<PAT>@gitlab.example.com/<group>/<repo>.git#main" \
    --file Dockerfile \
    --image autohaus-booking:{{.Run.ID}} \
    --image autohaus-booking:latest \
    --git-access-token <PAT> \
    --commit-trigger-enabled true
  ```
- [ ] **Webhook-URL aus ACR auslesen:**
  ```bash
  az acr task show --registry <acr> --name autohaus-booking-build \
    --query "trigger.sourceTriggers[0].sourceRepository.repositoryUrl"
  ```
- [ ] **GitLab Webhook einrichten:**
  - `Project > Settings > Webhooks`
  - URL: aus ACR (siehe vorige Zeile)
  - Trigger: `Push events` auf Branch `main`
  - SSL-Verifikation: aktiv
- [ ] **Continuous Deployment auf Container App:**
  - `az containerapp registry set --server <acr>.azurecr.io --identity system`
  - Container App zieht automatisch neues `:latest`-Image bei Push in ACR
- [ ] **Test-Push:** Commit in `main` → ACR Task lauft → Container App neue Revision
- [ ] **Rollback-Doku:**
  - `az containerapp revision list --name ca-autohaus-booking`
  - `az containerapp revision activate --revision <name>`
- [ ] **PAT-Rotation dokumentieren** (wenn Token ablaeuft):
  - `az acr task update --git-access-token <new-pat>`

### Phase 8 — Monitoring

- [ ] Log Analytics Query-Packs fuer die haeufigsten Troubleshooting-Faelle
- [ ] **Alerts:**
  - 5xx Rate > 5% in 5 Min
  - Replica Restart Count > 3 in 10 Min
  - SoftNet-Auth-Fehler im Log (Query-based Alert)
- [ ] Dashboard in Azure Monitor (Requests, Replicas, Memory, CPU)
- [ ] Application Insights — entscheiden ob jetzt oder spaeter

### Phase 9 — Go-Live

- [ ] **Smoke-Test auf Dev:** kompletter Buchungs-Flow durchklicken, iframe-Einbettung von einer Test-Seite aus
- [ ] **Load-Test:** ca. 50 Concurrent Bookings (erwartete Last), Scale-Out pruefen
- [ ] **Security-Review:** `security-review` Skill ueber den Deployment-Code
- [ ] **DNS-Cutover Prod:** CNAME vom aktuellen Hosting auf Azure umschalten
- [ ] **Rollback-Plan dokumentiert**
- [ ] **Betriebshandbuch:** Wie logge ich ein, wie lese ich Logs, wie skaliere ich manuell, wie rollback

---

## Offene Fragen an Plattform-Team / Kunde

| # | Frage | Antwort |
|---|-------|---------|
| 1 | Azure Subscription-ID + Tenant-ID (Prod; Dev bleibt lokal) | _TBD — wird erst in Phase 6 gebraucht_ |
| 2 | Resource-Group-Name + Region | **Region: `Germany West Central`** — RG-Name TBD (Vorschlag: `rg-autohaus-booking`) |
| 2a | GitLab-URL + Gruppe/Projekt fuer ACR-Task Git-Context | _TBD_ |
| 2b | GitLab Personal Access Token (Scope `read_repository`) — wer erzeugt? | _TBD — nur HTTPS+PAT, kein SSH_ |
| 2c | Custom Domain | **Nicht benoetigt** — Standard-FQDN wird direkt im iframe eingebunden |
| 2d | Sizing | **2 Replicas à 0.5 vCPU / 1 Gi** fuer ~600 Buchungen/Monat (HA) |
| 3 | Bestehende ACR vorhanden oder neu? | |
| 4 | Custom Domain Dev und Prod? Wer verwaltet DNS? | |
| 5 | Enterprise-VNet-Integration noetig, oder reicht Public Ingress + Egress? | |
| 6 | Budget-Limit pro Monat? Scale-Obergrenzen? | |
| 7 | Wer hat Owner-Rechte auf die Azure-Ressourcen (Rotation, Secrets)? | |
| 8 | Compliance-Vorgaben (TLS-Version, Logs-Retention, Datenlokation)? | |
| 9 | Secrets-Rotation: SESSION_SECRET / Fingerprint — wie oft? | |
| 10 | Wird Application Insights bereitgestellt oder nutzen wir nur Log Analytics? | |

---

## Referenzen

- [Azure Container Apps — Overview](https://learn.microsoft.com/azure/container-apps/overview)
- [Manage Secrets](https://learn.microsoft.com/azure/container-apps/manage-secrets)
- [Custom Domains + Managed Certificate](https://learn.microsoft.com/azure/container-apps/custom-domains-certificates)
- [GitHub Actions + OIDC to Azure](https://learn.microsoft.com/azure/developer/github/connect-from-azure)
- [Bicep fuer Container Apps](https://learn.microsoft.com/azure/templates/microsoft.app/containerapps)
