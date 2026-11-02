# REQ-029: Backend-Error-Logging (Trace-ID, Fehler-Payload-Dump, Azure-E-Mail-Alert)

**Status:** Implemented
**Priority:** High
**Type:** Non-Functional
**Created:** 2026-07-06
**Author:** Rico Sonntag

---

## 1. Overview

### 1.1 Purpose

Der SoftNet-API-Betreiber meldet Fehler (502 u. a.), die angeblich durch unsere Requests verursacht werden. Aktuell kann das Backend nicht belegen, welche Daten gesendet und empfangen wurden: Es werden weder Request-/Response-Payloads noch Fehler-Response-Bodies geloggt, und Log-Zeilen eines Requests sind nicht korrelierbar.

Dieses Requirement führt ein:

1. **Trace-ID-Präfix** auf jeder Log-Zeile (Normalfall, via SLF4J MDC).
2. **Vollständiges Payload-Logging NUR im Fehlerfall**: Snap-JSON von SoftNet (unverändert), gepatchter Snap (= Reservierungs-Request-Body), SoftNet-Fehler-Response-Body — markiert mit dem Log-Marker `SOFTNET_ERROR`.
3. **E-Mail-Benachrichtigung** bei SoftNet-Fehlern via Azure Monitor Scheduled-Query-Alert auf Log Analytics, geroutet an die bestehende Action Group `gs-app-alerts`.

### 1.2 Scope

**Included:**
- Spring Boot Backend (`server/`): MDC-Trace-ID, Fehler-Payload-Logging, Log-Marker
- Azure: Log-basierte Alert-Regel (dokumentiert in `docs/infrastructure/azure-read.md` §8, manueller `az`-Befehl)

**Excluded:**
- Frontend-Änderungen (keine)
- Weitergabe der Trace-ID an SoftNet (explizit entschieden: Trace-ID bleibt intern)
- Application Insights SDK / Micrometer / verteiltes Tracing

### 1.3 Related Requirements
- REQ-018: Spring-Boot-Migration (Backend-Basis)
- REQ-020/REQ-022: Azure Container App Hosting + Monitoring-Setup

---

## 2. User Story

**As a** Betreiber/Entwickler des Terminbuchungs-Backends
**I want** korrelierbare Logs mit Trace-ID und vollständige Payload-Dumps im Fehlerfall plus eine E-Mail-Benachrichtigung
**So that** ich SoftNet-Fehler sofort mitbekomme und dem API-Betreiber exakt belegen kann, welche Daten gesendet und empfangen wurden.

**Acceptance Criteria:**
- [x] AC-1: Jede Log-Zeile innerhalb eines HTTP-Requests beginnt (nach Zeitstempel/Level/Logger) mit `<traceId>: `; außerhalb von Requests mit `system: `.
- [x] AC-2: Bei einem fehlgeschlagenen SoftNet-Call wird eine ERROR-Zeile mit Marker `SOFTNET_ERROR` geschrieben, die den gesendeten Request-Body und den SoftNet-Fehler-Response-Body enthält (je max. 8 000 Zeichen, dann Truncation-Hinweis).
- [x] AC-3: Bei einem fehlgeschlagenen Reservierungs-Call wird zusätzlich der **unveränderte** Snap (wie von SoftNet empfangen) geloggt.
- [x] AC-4: Im Erfolgsfall werden keine Payloads geloggt.
- [x] AC-5: Azure-Alert-Regel `alert-softnet-error-logs` (KQL auf `SOFTNET_ERROR`) ist dokumentiert und triggert die Action Group `gs-app-alerts` (manueller Setup-Schritt nach Deploy).

---

## 3. Preconditions

### 3.1 System
- Spring Boot Backend läuft (lokal oder Azure Container App `gs-terminbuchungs-app`)
- Azure: Log Analytics Workspace `workspacedefaultresourcegroupdewc99c4` empfängt `ContainerAppConsoleLogs_CL`

### 3.2 User
- n/a (Cross-Cutting, kein UI)

### 3.3 Data
- SoftNet-Zugangsdaten konfiguriert (`SOFTNET_*` Env-Vars)

---

## 4. Main Flow

**Step 1:** HTTP-Request trifft ein
- **System:** `RequestIdFilter` liest/generiert `X-Request-Id`, setzt sie als MDC-Key `traceId`
- **Expected:** Alle Log-Zeilen dieses Requests tragen das Präfix `<traceId>: `

**Step 2:** SoftNet-Call erfolgreich
- **System:** Debug-Log mit Dauer, keine Payloads
- **Expected:** Log bleibt schlank (Normalfall)

**Step 3:** Request endet
- **System:** MDC wird im `finally` geräumt; `LoggingFilter` schreibt Zugriffszeile
- **Expected:** Keine Trace-ID-Leckage in fremde Requests (Thread-Pool)

---

## 5. Alternative Flows

### 5.1 Alt Flow A: Log außerhalb eines HTTP-Requests

**Trigger:** Startup-, Scheduler- oder Cache-Logs ohne Request-Kontext

**Flow:**
1. MDC-Key `traceId` ist nicht gesetzt
2. Logback-Default `%X{traceId:-system}` greift
3. Zeile erscheint mit Präfix `system: `

---

## 6. Exception Flows

### 6.1 Exception E1: SoftNet-Call schlägt fehl (502/4xx/Timeout/Auth)

**Trigger:** `SoftnetApiClient.executeRequest()` wirft Exception

**Flow:**
1. Response-Body wird (falls `RestClientResponseException`) extrahiert
2. ERROR-Zeile: `SOFTNET_ERROR <METHOD> <endpoint> failed after <ms>: <msg> | requestBody=<json> | responseBody=<json>` (Payloads truncated auf 8 000 Zeichen)
3. Exception propagiert → `GlobalExceptionHandler` mappt auf 502/503/504 (unverändert)

### 6.2 Exception E2: Reservierung schlägt fehl

**Trigger:** `AppointmentsService.createAppointment()` — SoftNet-POST wirft Exception

**Flow:**
1. Zusätzliche ERROR-Zeile: `SOFTNET_ERROR appointment creation failed for snap <id> | rawSnap=<unveränderter Snap>`
2. Fehlerbild = 3 benachbarte Zeilen mit gleicher Trace-ID: Roh-Snap → gepatchter Snap + SoftNet-Error-Body → Fehler-Summary

---

## 7. Postconditions

### 7.1 Success
- Log enthält pro Request korrelierbare Zeilen, keine Payloads

### 7.2 Failure
- Log enthält `SOFTNET_ERROR`-Zeile(n) mit allen Payloads
- Azure-Alert feuert (≤ 5-Min-Fenster + Ingestion-Latenz), E-Mail an `gs-app-alerts`-Empfänger

---

## 8. Business Rules

- **BR-1:** Payloads (Kundendaten!) werden ausschließlich im Fehlerfall geloggt — bewusster Datenschutz-Trade-off zur Provider-Klärung.
- **BR-2:** Payloads werden auf 8 000 Zeichen truncated (`LogPayloadUtil.MAX_PAYLOAD_LOG_CHARS`).
- **BR-3:** Der Marker `SOFTNET_ERROR` ist stabil und darf nicht umbenannt werden (Azure-KQL-Alert matcht darauf).
- **BR-4:** Die Trace-ID wird NICHT an SoftNet übertragen.
- **BR-5:** Client-gelieferte `X-Request-Id` wird gegen `^[A-Za-z0-9_-]{1,64}$` validiert — bei Verstoß wird eine UUID generiert (Schutz vor Log-Pollution/-Injection).
- **BR-6:** `LogPayloadUtil.truncate()` kollabiert Zeilenumbrüche zu Leerzeichen — jede Payload bleibt eine einzelne Log-Zeile (KQL-matchbar).

---

## 9. Non-Functional Requirements

### Performance
- Kein Serialisierungs-Overhead im Erfolgsfall (Request-Body-JSON wird ohnehin für den Call erzeugt)

### Security
- Keine Secrets/Bearer-Tokens im Log
- PII nur im Fehlerfall, Retention über Log Analytics Workspace gesteuert

### Usability
- n/a (kein UI)

---

## 10. Data Model

Keine neuen Datenmodelle. Neue Utility-Klasse:

```java
// server/src/main/java/com/autohaus/backend/common/logging/LogPayloadUtil.java
public final class LogPayloadUtil {
    public static final int MAX_PAYLOAD_LOG_CHARS = 8_000;
    public static String truncate(String payload); // null → "", > Limit → Cut + Suffix
}
```

---

## 11. UI/UX

n/a — Backend-only, kein UI.

---

## 12. API Specification

Keine API-Änderung. Log-Formate:

**Normalfall (jede Zeile):**
```
2026-07-06T10:15:03.123 [http-nio-3000-exec-1] INFO  HTTP - 7f3e…-uuid: {"correlationId":"","sessionId":"","method":"POST","url":"/api/v1/snaps","statusCode":200,"duration":"245ms"}
```

**Fehlerfall (SoftNet):**
```
… ERROR c.a.b.softnet.SoftnetApiClient - <traceId>: SOFTNET_ERROR POST /api/planning/appointments failed after 312ms: 502 Bad Gateway | requestBody={…} | responseBody={…}
… ERROR c.a.b.a.AppointmentsService - <traceId>: SOFTNET_ERROR appointment creation failed for snap <id> | rawSnap={…}
```

---

## 13. Test Cases

### TC-1: MDC-Lifecycle
- **Given:** Request mit `X-Request-Id: trace-42`
- **When:** Filter-Chain läuft / wirft
- **Then:** MDC enthält `trace-42` während der Chain, ist danach leer

### TC-2: 502 mit Response-Body
- **Given:** Lokaler HTTP-Server antwortet 502 mit JSON-Body
- **When:** `createAppointment()`
- **Then:** Exception propagiert; ERROR-Zeile enthält `SOFTNET_ERROR`, Request-Body und Response-Body

### TC-3: Roh-Snap ungepatcht
- **Given:** Snap im Cache, SoftNet wirft `HttpServerErrorException`
- **When:** `createAppointment()`
- **Then:** Geloggte `rawSnap`-Zeile enthält KEINE gepatchten Kundendaten (deepCopy-Nachweis)

### TC-4: Truncation
- **Given:** Payload > 8 000 Zeichen
- **When:** `LogPayloadUtil.truncate()`
- **Then:** Schnitt bei 8 000 + `...[truncated, total N chars]`

---

## 14. Implementation

### Components (Backend)
- [x] `RequestIdFilter` — MDC put/remove (`common/filter/`)
- [x] `application.yml` — Console-Pattern `%X{traceId:-system}: `
- [x] `LoggingFilter` / `GlobalExceptionHandler` — redundante requestId aus Log-Messages entfernt
- [x] `LogPayloadUtil` — neu (`common/logging/`)
- [x] `SoftnetApiClient` — Auth in try-Block, `SOFTNET_ERROR`-Log mit Request-/Response-Body
- [x] `AppointmentsService` — deepCopy vor Patch, `SOFTNET_ERROR`-Log mit Roh-Snap
- [x] Azure-Alert-Regel `alert-softnet-error-logs` — angelegt am 2026-07-06 nach Deploy (Revision `gs-terminbuchungs-app--0000019`), siehe `docs/infrastructure/azure-read.md` §8

### Effort
- Development: 4 hours
- Testing: 2 hours

---

## 15. Dependencies

**Requires:**
- REQ-018: Spring-Boot-Migration
- REQ-020: Azure Container App (für Alert-Teil)

**Blocks:**
- —

---

## 16. Naming Glossary

### Konstanten
- `RequestIdFilter.TRACE_ID_MDC_KEY` = `"traceId"` — MDC-Key
- `LogPayloadUtil.MAX_PAYLOAD_LOG_CHARS` = `8_000` — Truncation-Limit
- Log-Marker: `SOFTNET_ERROR` (literal im Message-Text)

### Azure
- Alert-Regel: `alert-softnet-error-logs`
- Action Group: `gs-app-alerts` (bestehend)

---

## 17. Approval

| Role | Name | Date |
|------|------|------|
| Product Owner | | |
| Tech Lead | | |

---

## 18. Implementation Notes

- Der Marker `SOFTNET_ERROR` steht im Message-Text (kein SLF4J-Marker), damit er sowohl im Text- als auch im JSON-Logging-Profil in `Log_s` per KQL matchbar ist.
- `SnapCache.getRawSnap()` liefert bereits eine `deepCopy()` — der zusätzliche `deepCopy()` in `AppointmentsService` schützt die lokale Referenz für das Fehler-Logging (Roh-Snap vs. gepatchter Snap).
- Error Messages Englisch (Code-Sprache), Doku DE.
