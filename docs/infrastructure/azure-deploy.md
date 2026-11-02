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

## 9. Env-Variablen REQ-038 (Statistik-Dashboard)

REQ-038 bringt die Tabs „Statistik | Bugs" auf `/internal/report` (Kapazitäts-Scan,
Standort-Rangliste, Servicegruppen, Buchungszeiten, Buchungsstrecke). **Alle neuen Schalter
werden mit `false` ausgeliefert** — ohne Env-Override zeigt der Statistik-Tab nur die
Nutzungsstatistik (S5) und die Messgrenzen (S7), wie in §5.1 Zeile 1 des Requirements.

> **Aktivierungsentscheidung PO (24.09.2026):** Beim Deploy werden **alle fünf** REQ-038-Schalter
> eingeschaltet (`USAGE_INSIGHTS_ENABLED`, `USAGE_FUNNEL_ENABLED`, `USAGE_DROPOFF_ENABLED`,
> `CAPACITY_SCAN_ENABLED`, `CAPACITY_SHOW_SLOT_COUNT`), **bevor** die Gates S-1, S-2, G-DSB und
> G-VVT (9.2) formal abgeschlossen sind. Begründung: ausschließlich anonyme Aggregate, die
> Buchungszeile ist von der Buchung entkoppelt (9.1). Offene Nacharbeiten: DSB-Kenntnisnahme,
> VVT-Eintrag/Datenschutzerklärung, Information an soft-nrg zum nächtlichen Scan. Befehl: 9.3,
> Variante A. Details: REQ-038 `requirement.md` §19 „Aktivierung".

Keine neue Azure-Ressource, keine neue Rollenzuweisung, keine neuen Secrets: die
System-Managed-Identity hat bereits „Log Analytics Reader" (REQ-030) und „Storage Blob Data
Contributor" auf dem Container `usage-stats` (REQ-037); die beiden neuen Blobs liegen im selben
Container.

### 9.1 Variablen

| Variable | Default | Zweck |
|---|---|---|
| `USAGE_INSIGHTS_ENABLED` | `false` | Kill-Switch K2: Puffer und Tagesflush von `USAGE_BOOKING` (23:59:59), KQL 1+2, Rollup, S0 (I-2/I-4), S2–S4, Buchungsteil von `insights.json` |
| `USAGE_INSIGHTS_COLLECTING_SINCE` | leer | Erfassungsbeginn K2 (ISO-Datum); leer = erster voller Berliner Tag nach der Aktivierung, beim ersten Rollup im Blob festgehalten |
| `USAGE_INSIGHTS_ROLLUP_CRON` | `0 45 3 * * *` | Nächtlicher Insights-Rollup (nach `USAGE_ROLLUP_CRON` 03:30); `-` schaltet **nur den Rollup** ab, **nicht** die Aufbewahrung — der feste Aufbewahrungslauf 04:15 bleibt |
| `USAGE_INSIGHTS_BLOB` | `usage-insights.json` | Blob-Name im Container `USAGE_STORAGE_CONTAINER` |
| `USAGE_FUNNEL_ENABLED` | `false` | Kill-Switch Trichter F-1–F-3 (S6); wirkt nur mit `USAGE_STATS_ENABLED=true` |
| `USAGE_FUNNEL_COLLECTING_SINCE` | leer | Erfassungsbeginn des Trichters; leer = Aktivierungstag + 1 |
| `USAGE_DROPOFF_ENABLED` | `false` | Kill-Switch Absprungszenarien F-4: Access-Feld `hits`, Dropoff-Abfrage, `dropoff` im Blob, `.fn-dropoff`; wirkt nur mit `USAGE_STATS_ENABLED` **und** `USAGE_FUNNEL_ENABLED` |
| `USAGE_DROPOFF_COLLECTING_SINCE` | leer | Erfassungsbeginn der Absprungszenarien; leer = Aktivierungstag + 1 |
| `CAPACITY_SCAN_ENABLED` | `false` | Kill-Switch K1: nächtlicher SoftNet-Scan, S1, I-1–I-3, `capacity.json` |
| `CAPACITY_SCAN_CRON` | `0 30 22 * * *` | Scan-Zeitpunkt (Europe/Berlin, plus 0–300 s Jitter); `-` deaktiviert |
| `CAPACITY_REFERENCE_SERVICE` | `wiper-blades` | Referenz-Leistung („Wischerblätter erneuern") |
| `CAPACITY_SHOW_SLOT_COUNT` | `false` | K-2 (Anzahl freier Termine) anzeigen — nur, wenn Spike S-1 keine Kappung zeigt |
| `CAPACITY_BLOB` | `capacity-daily.json` | Blob-Name im Container `USAGE_STORAGE_CONTAINER` |
| Bestand | — | `USAGE_STATS_ENABLED`, `USAGE_STORAGE_ACCOUNT`, `USAGE_STORAGE_CONTAINER`, `USAGE_ZONE`, `USAGE_RETENTION_MONTHS`, `LOG_WORKSPACE_ID`, `REPORT_ADMIN_USER`, `REPORT_ADMIN_PASSWORD`, `RADWECHSEL_ENABLED`, `SOFTNET_TIMEOUT` — unverändert, mitgenutzt |

Ohne `USAGE_STORAGE_ACCOUNT` laufen weder Scan noch Insights-Persistenz (kein Claim möglich);
die Seite zeigt dann „Historie derzeit nicht verfügbar".

**Schalter-Werte:** Die fünf REQ-038-Schalter (`USAGE_INSIGHTS_ENABLED`, `USAGE_FUNNEL_ENABLED`,
`USAGE_DROPOFF_ENABLED`, `CAPACITY_SCAN_ENABLED`, `CAPACITY_SHOW_SLOT_COUNT`) werden einheitlich über
Spring Boots Binder gelesen: `true`/`1`/`yes`/`on` = an, `false`/`0`/`no`/`off` = aus
(Groß-/Kleinschreibung egal, getrimmt). Seite, JSON und Scheduler folgen damit immer demselben Wert.
Ein **leerer oder unbekannter** Wert verhindert den Start der App (Properties-Binding lehnt ihn ab) —
also nie `CAPACITY_SCAN_ENABLED=` setzen, sondern die Variable entfernen oder `false` setzen.
`USAGE_STATS_ENABLED` (REQ-037) weiterhin nur mit `true`/`false` setzen.

**`USAGE_ZONE` nie leer setzen:** Fehlt die Variable, gilt Europe/Berlin. Eine ungültige Zone
verhindert den Start; ein **leerer** Wert führt zwar nur zu einem `WARN usage.zone '' is blank or not
a valid time zone — falling back to Europe/Berlin`, die nächtlichen Crons (Scan, Rollup, Tagesflush,
Aufbewahrung) laufen dann aber in der JVM-Standardzone statt in Europe/Berlin.

**Entkopplung der Buchungszeile (PO-Entscheidung „nur anonyme Daten, nie wer"):** Eine Buchung
schreibt im Request **keine** `USAGE_BOOKING`-Zeile. Das anonyme Ereignis wird im Speicher gepuffert
(max. 10 000) und täglich um **23:59:59** (`usage.zone`) sowie beim geordneten Herunterfahren
(`@PreDestroy`: Neustart, Deploy, Scale-in) gebündelt, gemischt und unter dem MDC-Marker
`job.usagebooking` geschrieben (Format `v:2` mit Feld `day`, Berliner Buchungstag ohne Uhrzeit).
Nur ein **Absturz** ohne geordnetes Herunterfahren verliert die Ereignisse des laufenden Tages.
Folge für den Betrieb: `az containerapp update` (neue Revision) flusht die alte Revision beim
Beenden — dabei entstehen die Zeilen des bisherigen Tages vorzeitig; das ist beabsichtigt und wird
im Rollup über `day` korrekt zugeordnet.

**Aufbewahrungslauf:** `InsightsRetentionJob` verdichtet und löscht `usage-insights.json` zusätzlich
zum Rollup in einem **eigenen, nicht abschaltbaren Tageslauf um 04:15** (`usage.zone`, Cron fest im
Code, keine Env-Variable). Er läuft, sobald `USAGE_STORAGE_ACCOUNT` gesetzt ist — unabhängig von
allen Schaltern, von einem aktiven Insights-Scheduler und von `USAGE_INSIGHTS_ROLLUP_CRON=-`. Er ist
idempotent und schreibt nur bei Änderung (`If-Match`, max. 3 Versuche). Zusage: Standort-Tageswerte
werden spätestens am 8. des Folgemonats verdichtet, auch wenn der Rollup aus ist.

> **Nur lokal / E2E:** `SPRING_PROFILES_ACTIVE=e2e-seed` und `E2E_SEED_SET` (`full` | `thin` |
> `fail`) füllen die Statistik mit Fixtures aus `playwright/fixtures/req038/`. **Nie in Azure
> setzen** — der Seed-Loader verweigert den Start, sobald `USAGE_STORAGE_ACCOUNT` gesetzt ist.

### 9.2 Gates vor dem Einschalten

Formale Grundlage laut Requirement. Für den ersten Deploy gilt abweichend die
PO-Aktivierungsentscheidung vom 24.09.2026 (Hinweis oben in §9): Die Gates werden nachgeholt, die
Schalter sind schon an.

| Gate | Prüfung | Verantwortlich | Schaltet frei |
|---|---|---|---|
| **S-1** SoftNet-Spike | Kappung der Snap-Liste, Minuten-Raster, `FirstAppDays`, Reservierung (Fehler 779), Fair-Use, Erreichbarkeit um 22:30, Form von `snap.branch`; Abstimmung mit soft-nrg (≈ 55 Calls/Nacht) | Tech Lead + PO | `CAPACITY_SCAN_ENABLED` (bei Kappung bleibt `CAPACITY_SHOW_SLOT_COUNT=false`) |
| **S-2** Wishlist-Rückgabe | Liefert `snap.wishlist.operations` die angefragten IDs zurück? | Tech Lead | `USAGE_INSIGHTS_ENABLED` (sonst erst Fallback `SnapCache`) |
| **G-VVT** | VVT-Eintrag und Datenschutzerklärung um „Auswertung der Online-Buchungen je Standort" ergänzt | Verantwortliche/r Datenschutzerklärung | `USAGE_INSIGHTS_ENABLED` |
| **G-DSB** | Freigabe der `sessionId` als Zählgrundlage; für F-4 ausdrücklich auch die Auswertung je `sessionId` (letzter Status, Zählerlücken, Anzahl Suchen, `hits`) | Datenschutzbeauftragte/r | `USAGE_FUNNEL_ENABLED`, `USAGE_DROPOFF_ENABLED` (nur bei ausdrücklicher Abdeckung) |

### 9.3 Aktivierungsreihenfolge

**Variante A — PO-Entscheidung 24.09.2026: alle fünf Schalter in einer Revision**

```bash
APP=gs-terminbuchungs-app; RG=DefaultResourceGroup-DEWC
# Erfassungsbeginn = Berliner Folgetag der Aktivierung (der Aktivierungstag ist ein Teiltag und zählt nie).
START=$(TZ=Europe/Berlin date -v+1d +%F)        # Linux: TZ=Europe/Berlin date -d tomorrow +%F

# USAGE_STATS_ENABLED (REQ-037, Default true) ist Voraussetzung für Trichter und Absprungszenarien.
az containerapp update -n $APP -g $RG --set-env-vars \
  USAGE_STATS_ENABLED=true \
  CAPACITY_SCAN_ENABLED=true CAPACITY_SHOW_SLOT_COUNT=true \
  USAGE_INSIGHTS_ENABLED=true USAGE_INSIGHTS_COLLECTING_SINCE=$START \
  USAGE_FUNNEL_ENABLED=true USAGE_FUNNEL_COLLECTING_SINCE=$START \
  USAGE_DROPOFF_ENABLED=true USAGE_DROPOFF_COLLECTING_SINCE=$START
```

Die offenen Nacharbeiten (DSB-Kenntnisnahme, VVT/Datenschutzerklärung, Information an soft-nrg)
bleiben bestehen; bei einem negativen Ergebnis den betroffenen Schalter per `--set-env-vars
<SCHALTER>=false` zurücknehmen (9.5).

**Variante B — gestaffelt nach Gates (ursprünglicher Plan):** jeder Schritt erst nach seinem Gate,
jeweils eigene Revision, danach Verifikation (9.4).

```bash
APP=gs-terminbuchungs-app; RG=DefaultResourceGroup-DEWC
START=$(TZ=Europe/Berlin date -v+1d +%F)        # Linux: TZ=Europe/Berlin date -d tomorrow +%F

# 1. Nach S-1: Kapazitäts-Scan
az containerapp update -n $APP -g $RG --set-env-vars CAPACITY_SCAN_ENABLED=true

# 2. Nach S-2 + G-VVT: Buchungs-Insights (K2)
az containerapp update -n $APP -g $RG \
  --set-env-vars USAGE_INSIGHTS_ENABLED=true USAGE_INSIGHTS_COLLECTING_SINCE=$START

# 3. Nach G-DSB (Trichter)
az containerapp update -n $APP -g $RG \
  --set-env-vars USAGE_FUNNEL_ENABLED=true USAGE_FUNNEL_COLLECTING_SINCE=$START

# 4. Nur wenn G-DSB die Auswertung je sessionId ausdrücklich abdeckt: Absprungszenarien
az containerapp update -n $APP -g $RG \
  --set-env-vars USAGE_DROPOFF_ENABLED=true USAGE_DROPOFF_COLLECTING_SINCE=$START
```

- Die Schritte sind voneinander unabhängig; Schritt 4 wirkt nur zusammen mit Schritt 3 und
  `USAGE_STATS_ENABLED=true`.
- `*_COLLECTING_SINCE` leer lassen hat dieselbe Wirkung (Aktivierungstag + 1); der explizite Wert
  macht den Beginn unabhängig von Neustarts vor dem ersten Rollup.
- `CAPACITY_SHOW_SLOT_COUNT=true` nur nach S-1 ohne Kappung.

### 9.4 Verifikation nach dem ersten Lauf

Scan läuft um 22:30 (+ Jitter, ≈ 5 Min Laufzeit), der Tagesflush der Buchungszeilen um 23:59:59,
der Insights-Rollup um 03:45 (+ Jitter), der Aufbewahrungslauf um 04:15.

> **Achtung:** `USAGE_BOOKING`-Zeilen erscheinen erst nach dem Tagesflush um 23:59:59 (bzw. nach
> einem Neustart oder Deploy, der die alte Revision geordnet beendet). Eine Log-Prüfung direkt nach
> einer Testbuchung findet also **keine** Zeile — das ist korrekt, kein Fehler.

```bash
URL=https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io
# Passwort aus dem Secret-Store (REPORT_ADMIN_PASSWORD), nie ins Repo
curl -s -u "admin:$REPORT_ADMIN_PASSWORD" $URL/internal/report/capacity.json | jq '{scanDay, targetDay, state, warnings}'
curl -s -u "admin:$REPORT_ADMIN_PASSWORD" $URL/internal/report/insights.json | jq '{asOf, bookingsEnabled, funnelEnabled, warnings}'

# USAGE_BOOKING-Zeilen des Tagesflushs: genau die Felder aus REQ-038 §10.2 (v:2, day, branch, group,
# variant, ops, bookingDaypart, slotWeekday, slotDaypart, leadTime, mobility), keine Zusatzfelder
az monitor log-analytics query -w f4bd94f3-490e-44e2-b5e3-f98bcc5aea38 --analytics-query "
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == 'gs-terminbuchungs-app' and TimeGenerated > ago(1d)
| where Log_s contains_cs ' - job.usagebooking: USAGE_BOOKING '
| project TimeGenerated, Log_s | take 5" -o table

# Job-Logs (MDC traceId job.capacityprobe / job.insightsrollup / job.usagebooking)
az containerapp logs show -n $APP -g $RG --tail 300 | grep -E "capacity scan|usage insights rollup|usage insights retention|USAGE_BOOKING (buffer full|flush)"
```

Erwartet: `capacity.json` mit dem Scan-Tag von gestern und `state` `complete`/`partial`
(`partial` ist mit der Default-Referenz normal: beide Leverkusener Standorte haben kein
„Wischerblätter erneuern"); `insights.json` mit `asOf` = gestern; `USAGE_BOOKING`-Zeilen nur mit
dem Präfix `job.usagebooking`, `"v":2` und `"day":"<Buchungstag>"`, in gemischter Reihenfolge und
je Flush mit (fast) demselben Zeitstempel; die Seite
`/internal/report` zeigt dieselben Werte; Scan- und Rollup-Fehler erscheinen **nicht** im
Bugs-Tab. `/internal/report/bugs` funktioniert unabhängig von allen Schaltern.

### 9.5 Außerbetriebnahme einer Quelle

Schalter aus **und** Blob löschen (NFR-Datenschutz-7) — sonst bleiben die Daten bis zum Ende der
Aufbewahrung (`USAGE_RETENTION_MONTHS`) im Blob.

```bash
# Kapazität
az containerapp update -n $APP -g $RG --set-env-vars CAPACITY_SCAN_ENABLED=false
az storage blob delete --account-name <account> -c usage-stats -n capacity-daily.json --auth-mode login

# Insights (K2, Trichter und Absprungszenarien teilen sich usage-insights.json)
az containerapp update -n $APP -g $RG \
  --set-env-vars USAGE_INSIGHTS_ENABLED=false USAGE_FUNNEL_ENABLED=false USAGE_DROPOFF_ENABLED=false
az storage blob delete --account-name <account> -c usage-stats -n usage-insights.json --auth-mode login
```

- `usage-insights.json` erst löschen, wenn **alle drei** Insights-Schalter aus sind. Wird nur
  eine Quelle abgeschaltet, schreibt sie keine neuen Werte mehr (`funnel: null` bzw.
  `dropoff: null`); ihre Altwerte laufen über die Aufbewahrung aus.
- `usage-daily.json` (REQ-037) wird von REQ-038 nie gelesen oder geschrieben — nicht löschen.
- Nach dem Löschen startet die nächste Aktivierung wie eine Erstinbetriebnahme (leere Historie).

---

## 10. Verwandte Dokumente

- [`azure-read.md`](./azure-read.md) — Konfig auslesen, Logs, Monitoring
- `.gitlab-ci.yml` — separate Pipeline fuer OpenShift/LDP-Rollout (laeuft
  automatisch auf jedem main-Push, deployt NICHT auf Azure)
- [`deploy/azure/`](../../deploy/azure/) — sobald Skripte vorliegen,
  wird der Workflow daraus geladen statt manuell ausgefuehrt.
- [`REQ-038 requirement.md`](../requirements/REQ-038-Statistik-Dashboard/requirement.md) —
  Schalter-Matrix (§5.1), Gates (§14.5), Betrieb (§14.4)
