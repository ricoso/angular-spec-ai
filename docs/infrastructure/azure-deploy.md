# Azure Container App — Deploy / Update

Step-by-Step-Anleitung für ein **Update der laufenden App** auf Azure
(`gs-terminbuchungs-app`). Komplementär zu `azure-read.md` (Read-Only).

> Wenn der Login klemmt oder die Identifier sich geändert haben: erst in
> `azure-read.md` reinschauen, das ist die Quelle der Wahrheit für
> Tenant/Subscription/Resource-Group/Registry.

---

## 1. Voraussetzungen

| Was | Wie pruefen |
|-----|-------------|
| `az` CLI installiert | `az --version` |
| `main` ist gepusht | `git status` clean, `git log origin/main..main` leer |
| Du hast `Contributor` (oder mehr) auf RG + ACR | `az role assignment list --assignee $(az ad signed-in-user show --query id -o tsv) -o table` |

`docker` lokal wird **nicht** gebraucht — der Build laeuft remote in der ACR.

---

## 2. Login

```bash
# Empfohlen: Browser-Login (oeffnet automatisch deinen Default-Browser auf macOS).
az login --tenant d9f5cb22-01c9-4956-b859-4f876f6a5c83

# Fallback (wenn der Browser nicht aufgeht, z.B. headless / SSH):
az login --tenant d9f5cb22-01c9-4956-b859-4f876f6a5c83 --use-device-code
```

> **Hinweis — Lufthansa Conditional Access:** Wenn nach erfolgreicher
> Anmeldung die Fehlerseite *"Hierauf koennen Sie zurzeit nicht zugreifen"*
> erscheint, brauchst du i.d.R. **VPN aktiv** oder **Edge-Browser mit
> Intune-Compliance**. Device-Code-Login schlaegt im Conditional Access oft
> haerter fehl als Browser-Login.

Richtige Subscription setzen (DEV — KST3540):

```bash
az account set --subscription 044bbaf1-5d0f-44ee-bb52-c42c729c93d9
az account show --query "{name:name, id:id}" -o yaml
```

---

## 3. Image-Tag ableiten

Konvention: `<kontext>-<YYYYMMDDhhmm>`.

```bash
# Beispiel: heutiges Datum + Uhrzeit + kurzer Kontext-Slug
TAG="fix-overview-$(date +%Y%m%d%H%M)"
IMAGE="lhindmobility.azurecr.io/gs-terminbuchungs-app:${TAG}"
echo "$IMAGE"
```

Bisherige Tags zur Orientierung:

```bash
az acr repository show-tags --name LHINDMobility \
  --repository gs-terminbuchungs-app -o table | tail -5
```

---

## 4. Image bauen (Remote-Build in ACR)

```bash
az acr build \
  --registry LHINDMobility \
  --image gs-terminbuchungs-app:${TAG} \
  .
```

- Liest das `Dockerfile` im aktuellen Verzeichnis (Multi-Stage: Node-Build
  fuer Angular → JDK-Image mit Spring-Boot-JAR + dist).
- Laeuft auf Azure, kein lokales Docker.
- Dauer typischerweise 3-6 Min (Angular-Build dominiert).
- Output endet mit `Run ID: ca123 was successful after ...`.

Wenn der Build fehlschlaegt:
- `az acr task logs --registry LHINDMobility --run-id <ca123>` fuer Detail-Logs.
- Lokal vorher `docker build .` durchziehen, um das Dockerfile zu pruefen.

---

## 5. Container App auf neue Revision updaten

```bash
az containerapp update \
  -n gs-terminbuchungs-app \
  -g DefaultResourceGroup-DEWC \
  --image lhindmobility.azurecr.io/gs-terminbuchungs-app:${TAG}
```

- Erzeugt eine **neue Revision** (z.B. `gs-terminbuchungs-app--0000014`).
- Default-Traffic-Mode ist *Single Revision* → neue Revision uebernimmt
  100 % Traffic, alte wird automatisch deaktiviert.
- Provisioning-State sollte am Ende `Succeeded` sein.

---

## 6. Verifikation (Smoke)

```bash
# Health-Endpoint (Spring Boot Actuator-aehnlich)
curl -sSf -o /dev/null -w "HTTP %{http_code}\n" \
  https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/api/v1/health
# Erwartet: HTTP 200

# Aktive Revision sehen
az containerapp revision list \
  -n gs-terminbuchungs-app -g DefaultResourceGroup-DEWC \
  --query "[?properties.active].{name:name, image:properties.template.containers[0].image, healthState:properties.healthState, replicas:properties.replicas}" \
  -o table

# Live-Logs (Strg+C zum Stoppen)
az containerapp logs show \
  -n gs-terminbuchungs-app -g DefaultResourceGroup-DEWC \
  --follow --tail 50
```

Browser-Smoke auf `https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/`
(Hash-Routing → Wizard durchklicken).

---

## 7. Rollback

Alte Revision wieder aktivieren — kein Re-Build noetig:

```bash
# Vorletzte Revision rausfinden
az containerapp revision list \
  -n gs-terminbuchungs-app -g DefaultResourceGroup-DEWC \
  --query "[].{name:name, active:properties.active, created:properties.createdTime, image:properties.template.containers[0].image}" \
  -o table

# Aktivieren (Beispiel)
az containerapp revision activate \
  -n gs-terminbuchungs-app -g DefaultResourceGroup-DEWC \
  --revision gs-terminbuchungs-app--0000013

# Neue (kaputte) Revision deaktivieren
az containerapp revision deactivate \
  -n gs-terminbuchungs-app -g DefaultResourceGroup-DEWC \
  --revision gs-terminbuchungs-app--0000014
```

---

## 8. End-to-End-Kurzfassung (Copy-Paste)

```bash
# 1. Login + Subscription
az login --tenant d9f5cb22-01c9-4956-b859-4f876f6a5c83
az account set --subscription 044bbaf1-5d0f-44ee-bb52-c42c729c93d9

# 2. Tag + Build
TAG="fix-overview-$(date +%Y%m%d%H%M)"
az acr build --registry LHINDMobility \
  --image gs-terminbuchungs-app:${TAG} .

# 3. Deploy
az containerapp update -n gs-terminbuchungs-app -g DefaultResourceGroup-DEWC \
  --image lhindmobility.azurecr.io/gs-terminbuchungs-app:${TAG}

# 4. Smoke
curl -sSf -o /dev/null -w "HTTP %{http_code}\n" \
  https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io/api/v1/health
```

---

## 9. Verwandte Dokumente

- [`azure-read.md`](./azure-read.md) — Konfig auslesen, Logs, Monitoring
- `.gitlab-ci.yml` — separate Pipeline fuer OpenShift/LDP-Rollout (laeuft
  automatisch auf jedem main-Push, deployt NICHT auf Azure)
- [`deploy/azure/`](../../deploy/azure/) — sobald Skripte vorliegen,
  wird der Workflow daraus geladen statt manuell ausgefuehrt.
