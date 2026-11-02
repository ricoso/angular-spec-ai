# Feature-Dokumentation: Backend-Error-Logging (Trace-ID, Fehler-Payload-Dump, Azure-E-Mail-Alert)

**Erstellt:** 2026-07-06
**Requirement:** REQ-029-Backend-Error-Logging
**Sprache:** DE
**Status:** Implementiert

---

## Übersicht

Der SoftNet-API-Betreiber meldet Fehler (u. a. 502), die angeblich durch unsere Requests verursacht werden. Bislang konnte das Backend nicht belegen, welche Daten tatsächlich gesendet und empfangen wurden: Weder Request-/Response-Payloads noch Fehler-Response-Bodies wurden geloggt, und die Log-Zeilen eines Requests waren nicht korrelierbar.

Dieses Feature schließt die Lücke mit drei Bausteinen:

1. **Trace-ID-Präfix auf jeder Log-Zeile** (Normalfall): Der `RequestIdFilter` liest bzw. generiert die `X-Request-Id` und setzt sie als SLF4J-MDC-Key `traceId`. Das Logback-Console-Pattern stellt sie jeder Zeile voran; außerhalb von HTTP-Requests (Startup, Scheduler, Cache) greift der Default `system: `.
2. **Fehler-Payload-Dump NUR im Fehlerfall**: Schlägt ein SoftNet-Call fehl, schreibt das Backend eine ERROR-Zeile mit dem stabilen Marker `SOFTNET_ERROR` — inklusive gesendetem Request-Body und SoftNet-Fehler-Response-Body. Bei einem fehlgeschlagenen Reservierungs-Call wird zusätzlich der **unveränderte** Roh-Snap (wie von SoftNet empfangen) geloggt. Alle Payloads werden auf 8 000 Zeichen truncated. Im Erfolgsfall werden **keine** Payloads geloggt.
3. **Azure-E-Mail-Alert**: Eine Scheduled-Query-Alert-Regel (`alert-softnet-error-logs`) auf dem Log Analytics Workspace matcht per KQL auf `SOFTNET_ERROR` und benachrichtigt die bestehende Action Group `gs-app-alerts` per E-Mail.

Damit ist jeder SoftNet-Fehler sofort sichtbar (E-Mail) und gegenüber dem API-Betreiber exakt beweisbar (Payloads + Trace-ID-Korrelation).

---

## Benutzerführung

n/a — Backend-Feature ohne UI. Es gibt keine Screenshots und keine Mockups; an ihrer Stelle dokumentieren die folgenden System-Flows und Log-Format-Beispiele das Verhalten.

---

## Funktionsweise (System-Flows)

### Schritt 1: HTTP-Request trifft ein

**System:** Der `RequestIdFilter` liest den Header `X-Request-Id` (oder generiert eine UUID, falls er fehlt) und setzt den Wert als MDC-Key `traceId`.

**Ergebnis:** Alle Log-Zeilen dieses Requests tragen das Präfix `<traceId>: ` (nach Zeitstempel/Level/Logger).

### Schritt 2: SoftNet-Call erfolgreich

**System:** Der `SoftnetApiClient` schreibt lediglich ein Debug-Log mit der Dauer des Calls — **keine** Payloads.

**Ergebnis:** Das Log bleibt im Normalfall schlank; Kundendaten landen nicht im Log.

### Schritt 3: Request endet

**System:** Der MDC-Key wird im `finally`-Block geräumt; der `LoggingFilter` schreibt die Zugriffszeile (Methode, URL, Statuscode, Dauer).

**Ergebnis:** Keine Trace-ID-Leckage in fremde Requests (Thread-Pool-Wiederverwendung).

### Alternativ-Flow: Log außerhalb eines HTTP-Requests

Startup-, Scheduler- oder Cache-Logs haben keinen Request-Kontext: Der MDC-Key `traceId` ist nicht gesetzt, das Logback-Pattern `%X{traceId:-system}` greift, die Zeile erscheint mit dem Präfix `system: `.

### Fehler-Flow E1: SoftNet-Call schlägt fehl (502/4xx/Timeout/Auth)

1. `SoftnetApiClient.executeRequest()` wirft eine Exception; ist es eine `RestClientResponseException`, wird der Response-Body extrahiert.
2. ERROR-Zeile: `SOFTNET_ERROR <METHOD> <endpoint> failed after <ms>: <msg> | requestBody=<json> | responseBody=<json>` — beide Payloads auf 8 000 Zeichen truncated.
3. Die Exception propagiert unverändert; der `GlobalExceptionHandler` mappt sie wie bisher auf 502/503/504.

### Fehler-Flow E2: Reservierung schlägt fehl

1. `AppointmentsService.createAppointment()` loggt zusätzlich: `SOFTNET_ERROR appointment creation failed for snap <id> | rawSnap=<unveränderter Snap>`.
2. Das vollständige Fehlerbild besteht aus **drei benachbarten Zeilen mit derselben Trace-ID**: Roh-Snap → gepatchter Snap + SoftNet-Error-Body → Fehler-Summary.

### Azure-E-Mail-Alert

Eine Scheduled-Query-Alert-Regel prüft alle 5 Minuten den Log Analytics Workspace (`ContainerAppConsoleLogs_CL`) auf Zeilen mit `SOFTNET_ERROR` und triggert bei Treffern die Action Group `gs-app-alerts` (E-Mail). `--mute-actions-duration PT30M` verhindert Mail-Stürme bei längeren Ausfällen. Das vollständige `az monitor scheduled-query create`-Kommando steht in [`docs/infrastructure/azure-read.md`](../../infrastructure/azure-read.md) (Abschnitt „Log-basierter Alert: SOFTNET_ERROR (REQ-029)").

---

## Log-Format-Beispiele

**Normalfall (jede Zeile trägt das Trace-ID-Präfix):**

```
2026-07-06T10:15:03.123 [http-nio-3000-exec-1] INFO  HTTP - 7f3e…-uuid: {"correlationId":"","sessionId":"","method":"POST","url":"/api/v1/snaps","statusCode":200,"duration":"245ms"}
```

**Fehlerfall (SoftNet) — Marker `SOFTNET_ERROR`:**

```
… ERROR c.a.b.softnet.SoftnetApiClient - <traceId>: SOFTNET_ERROR POST /api/planning/appointments failed after 312ms: 502 Bad Gateway | requestBody={…} | responseBody={…}
… ERROR c.a.b.a.AppointmentsService - <traceId>: SOFTNET_ERROR appointment creation failed for snap <id> | rawSnap={…}
```

**Log außerhalb eines Requests (Startup/Scheduler/Cache):**

```
… INFO  c.a.b.snaps.SnapCache - system: cache warmed up
```

**Truncation (Payload > 8 000 Zeichen):**

```
…{gekürzter Payload}...[truncated, total N chars]
```

---

## Responsive Ansichten

n/a — Backend-Feature ohne UI.

---

## Barrierefreiheit

n/a — Backend-Feature ohne UI.

---

## Fehler-Analyse-Anleitung

Wenn eine Alert-E-Mail eintrifft (oder ein SoftNet-Fehler gemeldet wird), im Log Analytics Workspace `workspacedefaultresourcegroupdewc99c4` per KQL analysieren:

**1. `SOFTNET_ERROR`-Zeilen finden (enthalten die Payloads und die Trace-ID):**

```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == "gs-terminbuchungs-app"
| where Log_s contains "SOFTNET_ERROR"
| project TimeGenerated, Log_s
| order by TimeGenerated desc
```

**2. Alle Log-Zeilen EINES Requests über die Trace-ID ziehen (Korrelation):**

```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == "gs-terminbuchungs-app"
| where Log_s contains "<traceId>"
| project TimeGenerated, Log_s
| order by TimeGenerated asc
```

**Vorgehen:** Aus der `SOFTNET_ERROR`-Zeile die Trace-ID (Präfix vor dem `: `) kopieren und mit Query 2 den kompletten Request-Verlauf ziehen. Bei Reservierungs-Fehlern liegen Roh-Snap, gepatchter Snap (= Request-Body), SoftNet-Error-Body und Fehler-Summary als benachbarte Zeilen unter derselben Trace-ID vor — das ist der Beweis-Datensatz für den SoftNet-Betreiber.

**Alert testen:** Temporär eine ungültige `SOFTNET_BASE_URL` als Env-Var setzen (oder eine Buchung mit abgelaufenem Snap auslösen), einen Snaps-Request absetzen, ~15–20 Min warten, E-Mail-Eingang prüfen, Env-Var zurücksetzen.

---

## Betriebshinweise

- **Alert erst NACH dem Deploy anlegen:** Die Alert-Regel `alert-softnet-error-logs` ist ein einmaliger, manueller Setup-Schritt, nachdem die REQ-029-Änderung auf der Azure Container App deployed wurde (Befehl siehe `docs/infrastructure/azure-read.md`, Abschnitt 8).
- **Ingestion-Latenz:** `ContainerAppConsoleLogs_CL` hat ~5–15 Min Ingestion-Latenz — die Alert-E-Mail kommt entsprechend verzögert (Auswertungsfenster 5 Min + Latenz).
- **Mail-Stürme:** `--mute-actions-duration PT30M` unterdrückt Folge-Mails für 30 Minuten nach einem Alert.
- **Kosten:** Scheduled-Query-Regeln werden pro Regel abgerechnet (~1,5 USD/Monat bei 5-Min-Frequenz).
- **Empfänger:** Müssen die Bestätigungsmail der Action Group `gs-app-alerts` bestätigt haben.
- **Datenschutz:** Die Fehler-Payloads enthalten Kundendaten (Name, E-Mail, Adresse, Kennzeichen). Sie werden **ausschließlich im Fehlerfall** geloggt (bewusster Trade-off zur Provider-Klärung); die Aufbewahrung richtet sich nach der Retention des Log Analytics Workspace. Es werden keine Secrets/Bearer-Tokens geloggt.
- **Stabiler Marker:** `SOFTNET_ERROR` darf nicht umbenannt werden — die Azure-KQL-Alert-Regel matcht darauf. Die Trace-ID wird NICHT an SoftNet übertragen.

---

## Technische Details

| Eigenschaft | Wert |
|-------------|------|
| Route | n/a — Backend-Feature ohne UI |
| Container Component | n/a — Backend-Feature ohne UI |
| Store | n/a — Backend-Feature ohne UI |
| API Service | n/a — kein Frontend-Service betroffen |

**Geänderte/neue Backend-Klassen** (`server/src/main/java/com/autohaus/backend/`):

| Klasse/Datei | Änderung |
|--------------|----------|
| `common/filter/RequestIdFilter.java` | MDC put/remove des Keys `traceId` (`TRACE_ID_MDC_KEY`), Quelle: Header `X-Request-Id` (Fallback: generierte UUID) |
| `common/logging/LogPayloadUtil.java` | **Neu** — `truncate()` kürzt Payloads auf `MAX_PAYLOAD_LOG_CHARS = 8_000` Zeichen (+ Truncation-Hinweis) |
| `softnet/SoftnetApiClient.java` | Auth in try-Block; `SOFTNET_ERROR`-Log mit Request-/Response-Body und Call-Dauer im Fehlerfall |
| `appointments/AppointmentsService.java` | `deepCopy()` vor dem Patch; `SOFTNET_ERROR`-Log mit unverändertem Roh-Snap bei fehlgeschlagener Reservierung |
| `common/filter/LoggingFilter.java` | Redundante requestId aus Log-Messages entfernt (steckt jetzt im MDC-Präfix) |
| `common/exception/GlobalExceptionHandler.java` | Redundante requestId aus Log-Messages entfernt; Fehler-Mapping (502/503/504) unverändert |
| `src/main/resources/application.yml` | Console-Pattern erweitert um `%X{traceId:-system}: ` |

**Azure (manueller Setup-Schritt):**

| Ressource | Wert |
|-----------|------|
| Alert-Regel | `alert-softnet-error-logs` (Scheduled Query, 5-Min-Fenster/-Frequenz, Severity 2) |
| Action Group | `gs-app-alerts` (bestehend, E-Mail) |
| Log Analytics Workspace | `workspacedefaultresourcegroupdewc99c4` (Tabelle `ContainerAppConsoleLogs_CL`) |

**Verwandte Dokumente:**
- Requirement: [`requirement.md`](./requirement.md)
- Azure-Alert-Setup + KQL: [`docs/infrastructure/azure-read.md`](../../infrastructure/azure-read.md)
