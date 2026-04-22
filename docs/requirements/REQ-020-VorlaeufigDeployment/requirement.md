# REQ-020: Vorläufiges Deployment (Azure Container Apps)

| Feld | Wert |
|------|------|
| **ID** | REQ-020 |
| **Titel** | Vorläufiges Deployment — POC-Testinstanz auf Azure Container Apps |
| **Status** | Draft |
| **Priorität** | Critical |
| **Typ** | Non-Functional / Infrastructure |
| **Abhängigkeiten** | REQ-018 (Spring Boot Migration) |
| **Erstellt** | 2026-04-22 |

---

## 1. Zusammenfassung

Bereitstellung einer öffentlich erreichbaren **POC-Testinstanz** der AutoHausTermin-Anwendung auf **Azure Container Apps**, solange der Zugang zur OpenShift-Plattform (REQ-019) nicht geklärt ist.

**Hintergrund:** Die Bereitstellung eines OpenShift-Namespaces durch das Platform-Team verzögert sich. Um dem Kunden zeitnah einen funktionierenden Prototyp zu zeigen, der **nicht auf einem lokalen Entwicklerrechner** läuft, wird eine vorläufige Cloud-Instanz benötigt.

**Lösung:** Azure Container Apps (Consumption Plan) in einer eigenen Azure Subscription mit voller Admin-Berechtigung. Kein eigener Domainname erforderlich — die automatisch generierte `*.azurecontainerapps.io`-Domain mit HTTPS wird genutzt.

---

## 2. User Story

**As a** Projektteam / Product Owner  
**I want** eine öffentlich erreichbare Testinstanz der Buchungsanwendung  
**So that** der Kunde den aktuellen Stand als POC testen kann, ohne dass eine lokale Maschine laufen muss

**Acceptance Criteria:**
- [ ] AC-1: Anwendung ist über eine `*.azurecontainerapps.io` URL per HTTPS erreichbar
- [ ] AC-2: Angular-Frontend und Spring Boot Backend laufen als einzelner Container
- [ ] AC-3: SoftNet-API-Integration funktioniert (Buchungsflow end-to-end)
- [ ] AC-4: Deployment ist per Shell-Script / Azure CLI reproduzierbar (kein manuelles Portal-Clicking)
- [ ] AC-5: Secrets (SoftNet-Credentials, Session-Secret) sind sicher als Container-App-Secrets konfiguriert
- [ ] AC-6: Kosten bleiben minimal (Consumption Plan, 1 Always-Warm Replica, ~15 €/Monat)
- [ ] AC-7: Teardown ist ebenso einfach (eine Resource Group löschen = alles weg)

---

## 3. Scope

### In Scope
- Azure Container Apps Deployment (einzelner Container)
- Azure Container Registry (ACR) für Docker-Image
- Azure CLI Deployment-Script (reproduzierbar)
- Secrets-Management via Container Apps Secrets
- HTTPS über automatische `*.azurecontainerapps.io` Domain

### Out of Scope
- Eigene Domain / DNS-Konfiguration
- CI/CD Pipeline (manuelles Deployment per Script reicht)
- Monitoring / Alerting (Azure-Portal Logs reichen)
- Hochverfügbarkeit / Multi-Region
- Produktiv-Betrieb (das ist REQ-019)

---

## 4. Abgrenzung zu REQ-019

| Aspekt | REQ-019 (Produktion) | REQ-020 (POC) |
|--------|----------------------|----------------|
| **Plattform** | OpenShift (LDP) | Azure Container Apps |
| **Zweck** | Produktivbetrieb | Kundendemonstration / POC |
| **Domain** | Eigene Domain mit TLS | `*.azurecontainerapps.io` (auto-TLS) |
| **CI/CD** | GitLab CI/CD + Helm | Manuelles Script (`deploy.sh`) |
| **Secrets** | OpenShift Secrets | Azure Container Apps Secrets |
| **Lebensdauer** | Permanent | Temporär (bis OpenShift-Zugang steht) |
| **Kosten** | LDP-intern | Azure Subscription (Consumption Plan, ~15 €/Monat) |

---

## 5. Architektur

```
Developer Machine                          Azure Cloud
─────────────────                          ──────────────────────────────────────

  ┌───────────┐     docker build + push     ┌─────────────────────────┐
  │ Dockerfile │ ──────────────────────────► │ Azure Container Registry│
  │ (lokal)    │                             │ (ACR)                   │
  └───────────┘                             └──────────┬──────────────┘
                                                       │
                                                       │ pull image
                                                       ▼
                                            ┌─────────────────────────┐
                                            │ Azure Container Apps    │
                                            │ Environment             │
                                            │                         │
                                            │  ┌───────────────────┐  │
                                            │  │ Container App     │  │
                                            │  │                   │  │
                                            │  │ Spring Boot :3000 │  │
                                            │  │ + Angular Static  │  │
                                            │  └───────────────────┘  │
                                            │                         │
                                            └──────────┬──────────────┘
                                                       │
                                                       │ HTTPS (auto-TLS)
                                                       ▼
                                            ┌─────────────────────────────────────┐
                                            │ https://autohaus-app.               │
                                            │   <random>.germanywestcentral.      │
                                            │   azurecontainerapps.io             │
                                            └─────────────────────────────────────┘
                                                       │
                                                       │ outbound
                                                       ▼
                                            ┌─────────────────────────┐
                                            │ SoftNet API             │
                                            │ (INT / PROD)            │
                                            └─────────────────────────┘
```

### Datenfluss

```
Browser → Azure HTTPS Endpoint → Container App (Spring Boot :3000) → SoftNet API
                                       │
                                  Angular SPA
                                  (Static Files served by Spring Boot)
```

---

## 6. Technische Anforderungen

### 6.1 Azure-Ressourcen

| Ressource | Zweck |
|-----------|-------|
| **Resource Group** | `rg-autohaus-poc` — alles in einer Gruppe für einfachen Teardown |
| **Azure Container Registry** | `acrautohausXXX` — Docker-Image Storage |
| **Container Apps Environment** | Serverless Container Runtime |
| **Container App** | Die eigentliche Anwendung |

### 6.2 Container-Konfiguration

| Parameter | Wert |
|-----------|------|
| **Image** | Aus bestehendem Dockerfile (REQ-019) |
| **Port** | 3000 (Target Port) |
| **Ingress** | External, HTTPS |
| **CPU** | 0.5 vCPU |
| **Memory** | 1.0 Gi |
| **Min Replicas** | 1 (Always-Warm — kein Cold Start für den Kunden) |
| **Max Replicas** | 1 (POC, kein Scaling nötig) |
| **Region** | Germany West Central |

### 6.3 Environment Variables (Konfiguration)

| Variable | Beschreibung |
|----------|-------------|
| `PORT` | Server Port (3000) |
| `SOFTNET_READ_BASE_URL` | SoftNet Read API Base URL |
| `SOFTNET_READ_AUTH_URL` | SoftNet Read Auth URL |
| `SOFTNET_WRITE_BASE_URL` | SoftNet Write API Base URL |
| `SOFTNET_WRITE_AUTH_URL` | SoftNet Write Auth URL |
| `SOFTNET_TIMEOUT` | API Timeout |
| `GROUP_ID` | SoftNet Group ID |
| `BRANCH_ID` | SoftNet Branch ID |
| `CORS_ALLOWED_ORIGINS` | CORS Origins (die Container App URL) |
| `SESSION_TTL_MINUTES` | Session TTL |
| `TLS_ENABLED` | TLS an/aus (false, Azure terminiert TLS) |

### 6.4 Secrets (sensibel)

| Secret | Beschreibung |
|--------|-------------|
| `session-secret` | HMAC-SHA256 Session Secret (32 Byte) |
| `softnet-read-fingerprint` | RSA Fingerprint JSON (private Key für SoftNet Read Auth) |
| `softnet-write-fingerprint` | RSA Fingerprint JSON (private Key für SoftNet Write Auth) |

### 6.5 Kosten-Erwartung

Bei 1 Always-Warm Replica (0.5 vCPU, 1 Gi) + geringer POC-Nutzung:

| Ressource | Erwartete Kosten |
|-----------|------------------|
| Container Apps Compute (Idle-Rate) | ~10 €/Monat (0.5 vCPU + 1 Gi, 24/7 Idle-Rate) |
| Container Registry (Basic) | ~5 €/Monat |
| **Gesamt** | **~15 €/Monat** |

---

## 7. Deployment-Workflow

### Erstmaliges Setup (einmalig)

1. Azure CLI Login (`az login`)
2. Resource Group anlegen
3. Azure Container Registry anlegen
4. Container Apps Environment anlegen
5. Docker-Image bauen + in ACR pushen
6. Container App anlegen mit Secrets + Image
7. URL testen

### Updates deployen (wiederholt)

1. Docker-Image neu bauen
2. In ACR pushen
3. Container App Revision aktualisieren

### Teardown

1. `az group delete --name rg-autohaus-poc` — löscht alles

---

## 8. Risiken

| Risiko | Wahrscheinlichkeit | Impact | Mitigation |
|--------|-------------------|--------|------------|
| SoftNet-API blockiert Azure-IPs | Mittel | Hoch | Vorab mit SoftNet klären, ob Outbound-IP whitelistbar ist |
| Azure Subscription Kosten | Niedrig | Niedrig | Consumption Plan mit 1 Replica → ~15 €/Monat |
| Fingerprint-Dateien als Volume Mount | Niedrig | Mittel | Azure Container Apps unterstützt Secret Volumes |

---

## 9. Offene Fragen

- [ ] Welche SoftNet-Umgebung (INT vs PROD) für das POC?
- [ ] Ist die Outbound-IP von Azure Container Apps bei SoftNet freigeschalten?
- [ ] Soll das POC passwortgeschützt sein (Basic Auth / Azure AD)?
- [ ] Welche Azure Subscription wird verwendet?
