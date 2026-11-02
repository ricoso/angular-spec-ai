# Azure Container App — Read-Only Inspection

Anleitung zum **Auslesen** der laufenden Azure Container App. Read-only, ändert nichts.
Wer Setup oder Updates fahren will, nutzt die Skripte unter `deploy/azure/` (sobald angelegt).

---

## 1. Identifier (im Klartext OK fuer Repo)

| Schlüssel | Wert |
|-----------|------|
| Subscription-Name | `BU Mobility Ecosystems - Sepc Driven Development - DEV - KST3540` |
| Subscription-ID | `044bbaf1-5d0f-44ee-bb52-c42c729c93d9` |
| Tenant-ID | `d9f5cb22-01c9-4956-b859-4f876f6a5c83` |
| Tenant-Name | `lhindtest01.onmicrosoft.com` |
| Resource Group | `DefaultResourceGroup-DEWC` |
| Region | `Germany West Central` (`westeurope`) |
| Container Registry | `lhindmobility.azurecr.io` (`LHINDMobility`) |
| Container App | `gs-terminbuchungs-app` |
| Public FQDN | `https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/` |

> Bei Subscription-Wechsel oder neuer Resource-Group bitte hier aktualisieren.

---

## 2. Login

Im Dev-Container kein lokaler Browser → device-code-Flow:

```bash
az login --tenant "d9f5cb22-01c9-4956-b859-4f876f6a5c83" \
  --scope "https://management.core.windows.net//.default" \
  --use-device-code
```

Code aus dem Terminal-Output kopieren → URL im Browser deines Mac/PC öffnen → einloggen → Terminal kommt zurück mit "Logged in as ...".

**Tokens laufen nach 12h ab** (Lufthansa Conditional-Access). Symptom: `AADSTS70043: refresh token expired`. Lösung: `az logout` + erneut `az login`.

Subscription pruefen:
```bash
az account show --query "{name:name, id:id, tenant:tenantId}" -o yaml
```

Falls falsche Subscription aktiv:
```bash
az account list -o table
az account set --subscription 044bbaf1-5d0f-44ee-bb52-c42c729c93d9
```

---

## 3. Konfiguration auslesen

Variable einmal setzen:
```bash
APP=gs-terminbuchungs-app
RG=DefaultResourceGroup-DEWC
```

### Komplett-Dump

```bash
az containerapp show -n $APP -g $RG > /tmp/containerapp-config.json
```
→ alles inkl. Konfig, Template, Revisions, Ingress. Keine Secret-Werte (die sind nur Refs).

### Übersicht

```bash
az containerapp show -n $APP -g $RG \
  --query "{fqdn:properties.configuration.ingress.fqdn, image:properties.template.containers[0].image, cpu:properties.template.containers[0].resources.cpu, mem:properties.template.containers[0].resources.memory, minR:properties.template.scale.minReplicas, maxR:properties.template.scale.maxReplicas, revision:properties.latestRevisionName, prov:properties.provisioningState}" \
  -o yaml
```

### Env-Variablen

```bash
az containerapp show -n $APP -g $RG \
  --query "properties.template.containers[0].env[].{name:name, value:value, secretRef:secretRef}" \
  -o table
```
→ Werte werden im Klartext angezeigt **außer bei `secretRef`** (da steht nur die Referenz, nicht der Inhalt).

### Secret-Namen

```bash
az containerapp secret list -n $APP -g $RG -o table
```
→ nur Namen. Werte gibt Azure aus Sicherheit nicht preis.

### Ingress

```bash
az containerapp ingress show -n $APP -g $RG -o yaml
```
→ Port, External/Internal, Allowed-Origins, Sticky-Sessions, IP-Restrictions.

### Aktuelle und vergangene Revisionen

```bash
az containerapp revision list -n $APP -g $RG \
  --query "[].{name:name, active:properties.active, replicas:properties.replicas, healthState:properties.healthState, created:properties.createdTime, image:properties.template.containers[0].image}" \
  -o table
```

### Replicas (laufende Pods)

```bash
az containerapp replica list -n $APP -g $RG \
  --query "[].{name:name, state:properties.runningState, created:properties.createdTime}" \
  -o table
```

### Image / ACR

```bash
az acr repository show-tags --name LHINDMobility --repository autohaus-app -o table
```

---

## 4. Logs

### Live-Stream (letzten 100 Zeilen + folge weitere)

```bash
az containerapp logs show -n $APP -g $RG --follow --tail 100
```
→ Strg+C zum Stoppen.

### Letzte Stunde, gefiltert auf Errors

```bash
az containerapp logs show -n $APP -g $RG --tail 500 \
  | grep -E "ERROR|Exception|401|403|500"
```

### Log Analytics (KQL — historisch + gefiltert)

Workspace-ID raussuchen:
```bash
az containerapp env show \
  -n <env-name> -g $RG \
  --query "properties.appLogsConfiguration.logAnalyticsConfiguration.customerId" -o tsv
```

Dann KQL gegen den Workspace (im Azure Portal → Log Analytics → Query):

```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == "gs-terminbuchungs-app"
| where TimeGenerated > ago(1h)
| where Log_s contains "ERROR" or Log_s contains "Exception"
| project TimeGenerated, Log_s
| order by TimeGenerated desc
```

---

## 5. Health-Check

### Ingress / FQDN funktioniert?

```bash
curl -sSf -o /dev/null -w "HTTP %{http_code}\n" \
  https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/api/v1/health
```

→ erwartet `HTTP 200`. Wenn 503 → Container nicht ready / Replica down.

### Health vom Container selbst

```bash
az containerapp show -n $APP -g $RG \
  --query "properties.template.containers[0].probes" -o yaml
```

---

## 6. Was beim Onboarding zu tun ist

1. `az login --use-device-code` (siehe oben)
2. Subscription auf `044bbaf1-...` setzen
3. RBAC pruefen: `az role assignment list --assignee $(az ad signed-in-user show --query id -o tsv) -o table`
4. Erwartet: mindestens **Reader** auf der RG. Fuer Updates: **Contributor**.
5. Falls keine Rolle → bei IT-Owner anfragen (siehe Owner-Liste in REQ-022)

---

## 7. Was NICHT im Repo

| Nie committen | Wo stattdessen |
|---------------|----------------|
| `SOFTNET_FINGERPRINT_JSON` Inhalt | Container App Secret |
| `SESSION_SECRET` Inhalt | Container App Secret |
| ACR Admin-Password | Managed Identity oder Azure Key Vault |
| Personal Access Tokens / Login-Cookies | Nie |
| `cognigy.net_fingerprint-*.json` Files | `.gitignore` |
| `Names_and_IDs.xlsx` | `.gitignore` |
| `server/.env` | `.gitignore` |

---

---

## 8. Monitoring (Health-Endpoint Alerts)

### Bestehende Komponenten

| Komponente | Name | Zweck |
|------------|------|-------|
| Application Insights | `gs-terminbuchung-insights` | Sammelt Availability-Daten + Logs (workspace-based, Workspace `workspacedefaultresourcegroupdewc99c4`) |
| Action Group | `gs-app-alerts` | Email-Empfaenger fuer Alerts (4 Empfaenger: maximilian, florian, damon, rico — alle `@lhind.dlh.de`) |
| URL Ping Test | `gs-backend-health` | `https://gs-terminbuchungs-app.../api/v1/health` — alle 5 Min, 5 Lokationen (Amsterdam, Dublin, Prag, Stockholm, Sydney) |
| URL Ping Test | `gs-softnet-api` | `https://api.soft-nrg.com/` — Erreichbarkeit der SoftNet API |
| URL Ping Test | `gs-softnet-auth` | `https://auth.soft-nrg.com/oauth/token` — Erreichbarkeit Auth-Endpoint |
| Alert Rule | `alert-backend-health-down` | Trigger: 3+ Lokationen failen 5 Min — Severity 2 |
| Alert Rule | `alert-softnet-api-down` | Trigger: 3+ Lokationen failen 5 Min — Severity 2 |
| Alert Rule | `alert-softnet-auth-down` | Trigger: 3+ Lokationen failen 5 Min — Severity 2 |

> **Empfaenger der Bestaetigungs-Mail bestaetigen!** Azure schickt bei Erstaktivierung einer Email-Action eine Bestaetigungs-Mail; Empfaenger muessen einmalig den Link klicken, sonst kommt kein Alert an.

### Auslesen

```bash
# Webtests + Status
az resource list -g DefaultResourceGroup-DEWC \
  --resource-type "microsoft.insights/webtests" \
  --query "[].{name:name, location:location}" -o table

# Aktuelle Availability (letzten 24h)
az monitor app-insights metrics show \
  --app gs-terminbuchung-insights -g DefaultResourceGroup-DEWC \
  --metrics availabilityResults/availabilityPercentage \
  --interval PT1H

# Alert Rules listen
az monitor metrics alert list -g DefaultResourceGroup-DEWC \
  --query "[].{name:name, enabled:enabled, severity:severity, lastUpdate:lastUpdatedTime}" -o table

# Action Group Empfaenger
az monitor action-group show -n gs-app-alerts -g DefaultResourceGroup-DEWC \
  --query "emailReceivers[].{name:name, email:emailAddress, status:status}" -o table
```

### Manuelle Aenderungen

```bash
# Empfaenger hinzufuegen
az monitor action-group update -n gs-app-alerts -g DefaultResourceGroup-DEWC \
  --add-action email <kuerzel> "<email>" usecommonalertschema

# Empfaenger entfernen
az monitor action-group update -n gs-app-alerts -g DefaultResourceGroup-DEWC \
  --remove-action <kuerzel>

# Alert deaktivieren (z.B. waehrend geplanter Wartung)
az monitor metrics alert update -n alert-backend-health-down -g DefaultResourceGroup-DEWC --enabled false

# Test-Failure simulieren: einfach Container-App fuer 10 Min stoppen
az containerapp update -n gs-terminbuchungs-app -g DefaultResourceGroup-DEWC --min-replicas 0 --max-replicas 0
# ... 10 Min spaeter wieder hochfahren ...
az containerapp update -n gs-terminbuchungs-app -g DefaultResourceGroup-DEWC --min-replicas 1 --max-replicas 1
```

### Wie das Setup angelegt wurde (Reproduzierbarkeit)

Falls in einer anderen Sub/RG repliziert werden muss:

```bash
SUB=044bbaf1-5d0f-44ee-bb52-c42c729c93d9
RG=DefaultResourceGroup-DEWC
APP_INSIGHTS_ID="/subscriptions/$SUB/resourceGroups/$RG/providers/microsoft.insights/components/gs-terminbuchung-insights"
ACTION_GROUP_ID="/subscriptions/$SUB/resourceGroups/$RG/providers/microsoft.insights/actionGroups/gs-app-alerts"
WORKSPACE_ID="/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.OperationalInsights/workspaces/workspacedefaultresourcegroupdewc99c4"

# 1. App Insights (workspace-based)
az monitor app-insights component create \
  --app gs-terminbuchung-insights --location westeurope -g $RG \
  --workspace "$WORKSPACE_ID" --kind web

# 2. Action Group mit 4 Empfaengern
az monitor action-group create -n gs-app-alerts -g $RG --short-name gs-alerts \
  --action email maximilian "maximilian.schmidt@lhind.dlh.de" usecommonalertschema \
  --action email florian "florian.meier@lhind.dlh.de" usecommonalertschema \
  --action email damon "damon.mueller@lhind.dlh.de" usecommonalertschema \
  --action email rico "rico.sonntag@lhind.dlh.de" usecommonalertschema

# 3. Webtests + Alerts: siehe Skript am Ende dieses Doks
```

Das vollstaendige Setup-Skript (Webtests + Alerts) liegt im Repo unter
`deploy/azure/setup-monitoring.sh` (kommt in der naechsten Iteration), bis dahin
sind die Befehle ausschliesslich in dieser Doku.

### Log-basierter Alert: SOFTNET_ERROR (REQ-029)

Das Backend loggt fehlgeschlagene SoftNet-Calls als ERROR-Zeile mit dem stabilen
Marker `SOFTNET_ERROR` (inkl. Request-Body, SoftNet-Fehler-Response-Body und beim
Reservierungs-Fehler zusaetzlich dem unveraenderten Roh-Snap — je max. 8 000 Zeichen).
Jede Zeile traegt das Trace-ID-Praefix aus dem `X-Request-Id`-Header (MDC).

**Alert-Regel anlegen** (einmalig, NACH Deploy der REQ-029-Aenderung):

```bash
SUB=044bbaf1-5d0f-44ee-bb52-c42c729c93d9
RG=DefaultResourceGroup-DEWC
WORKSPACE_ID="/subscriptions/$SUB/resourceGroups/$RG/providers/Microsoft.OperationalInsights/workspaces/workspacedefaultresourcegroupdewc99c4"
ACTION_GROUP_ID="/subscriptions/$SUB/resourceGroups/$RG/providers/microsoft.insights/actionGroups/gs-app-alerts"

az monitor scheduled-query create \
  -n alert-softnet-error-logs -g "$RG" \
  --scopes "$WORKSPACE_ID" \
  --condition "count 'SoftnetErrors' > 0" \
  --condition-query SoftnetErrors="ContainerAppConsoleLogs_CL | where ContainerAppName_s == 'gs-terminbuchungs-app' | where Log_s contains 'SOFTNET_ERROR'" \
  --window-size 5m --evaluation-frequency 5m --severity 2 \
  --mute-actions-duration PT30M \
  --auto-mitigate false \
  --action-groups "$ACTION_GROUP_ID" \
  --description "SoftNet-Fehler im Backend-Log (Marker SOFTNET_ERROR) — Payload-Details im Log unter der Trace-ID"
```

> **Status:** Regel wurde am 2026-07-06 angelegt (enabled, Severity 2).

- `--mute-actions-duration PT30M` verhindert Mail-Stuerme bei laengeren Ausfaellen.
- `--auto-mitigate false` ist PFLICHT in Kombination mit `--mute-actions-duration` (Azure-API-Constraint: "Auto mitigation must be disabled when action suppression is set").
- Kosten: Scheduled-Query-Regeln werden pro Regel abgerechnet (~1,5 USD/Monat bei 5-Min-Frequenz).
- Ingestion-Latenz von `ContainerAppConsoleLogs_CL`: ~5–15 Min — die Mail kommt entsprechend verzoegert.
- Empfaenger muessen die Action-Group-Bestaetigungsmail bestaetigt haben (siehe oben).

**Fehler untersuchen** (Log Analytics, KQL):

```kusto
// 1. SOFTNET_ERROR-Zeilen finden (enthalten die Payloads und die Trace-ID)
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == "gs-terminbuchungs-app"
| where Log_s contains "SOFTNET_ERROR"
| project TimeGenerated, Log_s
| order by TimeGenerated desc

// 2. Alle Log-Zeilen EINES Requests ueber die Trace-ID ziehen
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == "gs-terminbuchungs-app"
| where Log_s contains "<traceId>"
| project TimeGenerated, Log_s
| order by TimeGenerated asc
```

**Alert testen:** Temporaer eine ungueltige `SOFTNET_BASE_URL` als Env-Var setzen
(oder eine Buchung mit abgelaufenem Snap ausloesen), einen Snaps-Request absetzen,
~15–20 Min warten, E-Mail-Eingang pruefen, Env-Var zuruecksetzen.

> **Datenschutz-Hinweis:** Die Fehler-Payloads enthalten Kundendaten (Name, E-Mail,
> Adresse, Kennzeichen). Sie werden NUR im Fehlerfall geloggt; die Aufbewahrung
> richtet sich nach der Retention des Log Analytics Workspace.

---

## 9. Verwandte Dokumente

- [REQ-022 — Azure Container App Hosting Plan](../requirements/REQ-022-Azure-Alternative/README.md)
- [REQ-020 — Deployment Solution Proposal](../requirements/REQ-020-VorlaeufigDeployment/SOLUTION-PROPOSAL.md)
- [REQ-019 — LDP/OpenShift Deployment Plan](../requirements/REQ-019-Deployment/DEPLOYMENT-PLAN.md)
- [REQ-021 — Multi-Branch-Setup (Env-Vars: `SETUP_JSON`, `RADWECHSEL_ENABLED`)](../requirements/REQ-021-Multi-Branch-Setup/requirement.md)
- [REQ-017 — iframe-Embedding (Env-Var: `APP_FRAME_ANCESTORS`)](../requirements/REQ-017-Loading-Spinner/requirement.md)
