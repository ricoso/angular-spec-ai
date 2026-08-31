# REQ-034: Datensparsames Logging (zentrale PII-Redaction im Log-Pfad)

**Status:** Draft
**Priority:** High
**Type:** Non-Functional
**Created:** 2026-08-18
**Author:** Rico Sonntag

---

## 1. Overview

### 1.1 Purpose

REQ-029 hat für die SoftNet-Fehleranalyse **vollständige Payload-Dumps im Fehlerfall** eingeführt (`SOFTNET_ERROR`-Marker). Diese Payloads enthalten im Klartext personenbezogene Daten: **Name, E-Mail, Telefon, Adresse, Kennzeichen, VIN**. Die vorhandene PII-Maskierung (`PiiRedactor`) greift **nur im Report-/LLM-Pfad** (REQ-030), **nicht** in den Konsolen-Logs, die nach Azure Log Analytics fließen. Der Datenschutz-Hinweis in `docs/infrastructure/azure-read.md` §8 bestätigt: „Die Fehler-Payloads enthalten Kundendaten (Name, E-Mail, Adresse, Kennzeichen)."

Dieses Requirement macht das Logging **datensparsam**, ohne die technische Observability aus REQ-029 zu verlieren:

1. **Zentrale Sanitizing-Schicht** (`LogSanitizer`) im `common/logging`-Package — eine einzige Stelle, durch die alle Payload-Logs laufen.
2. **Metadaten statt Payloads** im Normalfall (correlation_id, route, status, duration, error_code, payload_size).
3. **Redigierte Payloads im Fehler- UND Happy-Case** — bei Erfolg wird die Buchung zusätzlich als `BOOKING_CREATED` (INFO) geloggt. In allen Fällen werden PII-Werte durch ihre **Zeichenanzahl** ersetzt (`***(12)`), bevor die Zeile den Logger erreicht. So bleibt die technische Struktur **und** die Eingabelänge zur Analyse erhalten, der Wert selbst verschwindet.
4. **Zentrale Verbotsliste** sensibler Feldnamen (inkl. realer Projekt-Feldnamen), zweite Schutzschicht.
5. **Automatisierte Tests**, die beweisen, dass Testwerte (`Max Mustermann`, `AB-CD-1234`, `max@example.com`) nicht mehr im erfassten Log-Output erscheinen.

> **Karpathy-Prinzip:** erst bestehendes Logging verstehen (siehe §1.4 Inventar), kein Rundumschlag, Logging **nicht** abschalten, technische Nachvollziehbarkeit erhalten, sensible Felder systematisch entfernen, **zentrale Lösung** statt verteilter Hotfixes, mit Tests absichern.

### 1.2 Scope

> **Nicht-Ziel / ausdrücklich:** Es wird **nur das Logging** angepasst. Die Buchung, die Datenerfassung und die an SoftNet gesendeten Daten bleiben **vollständig und unverändert** — SoftNet braucht und bekommt weiterhin Name, E-Mail, Telefon, Adresse, Kennzeichen, VIN. Maskiert wird ausschließlich, was zusätzlich ins **Log** geschrieben wird.

**Included (Backend `server/`):**
- Zentraler `LogSanitizer` (JSON-Feld-Redaction, wiederverwendet/vereinheitlicht die Feldliste aus `PiiRedactor`).
- Umbau der beiden Klartext-Payload-Logs auf redigierte Ausgabe:
  - `softnet/SoftnetApiClient.java:218` (`SOFTNET_ERROR ... requestBody | responseBody`)
  - `appointments/AppointmentsService.java:67` (`SOFTNET_ERROR ... rawSnap`)
- `common/filter/OriginFilter.java:74`: IP-Adresse (GDPR-personenbezogen) maskieren/kürzen.
- `snaps/SnapsService.java:71`: Full-Object-Debug-Dump des Snaps-Requests auf Metadaten reduzieren.
- Zentrale Feld-Policy + Tests.

**Excluded:**
- Frontend-Business-Logik (unverändert). Frontend-`console.*` siehe §8/§13 (nur `trace.service.ts` als optionaler Zusatz).
- Löschung/Bereinigung **bestehender** Logs in Azure — nur Dokumentation der Retention (siehe §7.3). Eine Bereinigung wird separat vorgeschlagen, nicht in diesem REQ ausgeführt.
- Kennzeichen-**Validierung** (das ist ein separates Thema, siehe Analyse zu ungültigen Kennzeichen).
- Verteiltes Tracing / Application Insights SDK.

### 1.3 Related Requirements
- **REQ-029** — Backend-Error-Logging (führte die Payload-Dumps ein, die hier redigiert werden). Direkter Vorgänger.
- **REQ-030** — AI-Error-Report (`PiiRedactor`, `PiiRedactionAdvisor` — Redaction-Bausteine, die hier in den Log-Pfad übernommen werden).
- **REQ-033** — Telefonnummer-Normalisierung (`PhoneNumberNormalizer` — Beispiel für zentralen `common/util`-Baustein).

### 1.4 Logging-Inventar (Ist-Zustand, verifiziert)

> **Lesart der Spalte „PII im Log":** Dieser Wert bewertet **ausschließlich, wie viel personenbezogene Daten die jeweilige Log-Zeile aktuell ins Log schreibt** — also die **Redaction-Priorität fürs Logging**. Er sagt **nichts** über die Buchung, die Datenhaltung oder die an SoftNet gesendeten Daten aus. „VIEL" = diese Log-Zeile druckt gerade die meisten Personendaten → zuerst maskieren. Die Buchung und SoftNet erhalten weiterhin **alle** Daten (siehe §12, AC-11, BR-6) — geändert wird **nur die Log-Ausgabe**.

| Stelle | Aktuell geloggte Daten | PII im Log (Prio) | Log-Änderung nötig |
|--------|------------------------|-------------------|--------------------|
| `softnet/SoftnetApiClient.java:218` | **Outbound-Request-Body + Response-Body** im Fehlerfall: `client.{firstName,lastName,email,mobile,phone,address,zipCode,city}`, `car.{license,VIN}` — **Klartext**, trunc. 8000 | **VIEL** | **Ja** — redigieren |
| `appointments/AppointmentsService.java:67` | **rawSnap** im Fehlerfall (kann Bestandskunden-Daten von SoftNet enthalten) | **VIEL** | **Ja** — redigieren |
| `common/filter/OriginFilter.java:74` | `origin`, `referer`, `path`, **`ip`** | **ETWAS** (IP = GDPR) | **Ja** — IP maskieren |
| `snaps/SnapsService.java:71` | Full-Object-Dump `SoftNet snaps request: {}` (branchId, dates, brand — keine PII, aber Full-Payload) | **WENIG** | **Ja** — auf Metadaten |
| `common/exception/GlobalExceptionHandler.java:117` | `statusCode`, `message`, `exception` — `message` kann Fremd-Exception-Text enthalten | WENIG | Prüfen/absichern |
| `common/filter/LoggingFilter.java:33` | `correlationId`, `sessionId`, `method`, `url` (nur Pfad, **kein** Query-String), `statusCode`, `duration` | KEINE | **Nein** (Referenz-Baseline ✅) |
| `softnet/SoftnetAuthService.java` (mehrere) | Token-Ablauf, Fingerprint-Pfad — technisch | KEINE | Nein |
| `config/**`, `snaps/OperationIdMapper.java` | branchId, groupId, service-keys — technisch | KEINE | Nein |
| `reporting/**` (`ReportAggregator:187-188`) | `piiRedactor.redact(requestBody/responseBody)` **vor** Anzeige | KEINE (bereits geschützt) | Nein — als Vorlage nutzen |
| Frontend `core/http/trace.service.ts:26` | `console.log(msg, data)` — generischer `data`-Parameter (potenzieller PII-Vektor, clientseitig) | WENIG | Optional |

> **Zentrale Erkenntnis:** Das Leck ist **konzentriert** an genau zwei `SOFTNET_ERROR`-Stellen, die beide durch `LogPayloadUtil.truncate()` laufen. `common/logging/LogPayloadUtil` ist damit die natürliche zentrale Naht: Redaction dort einhängen → beide Stellen sind mit einer Änderung sauber. **Es wird nur die Log-Zeile angefasst, kein Buchungs- oder SoftNet-Code.**

---

## 2. User Story

**As a** Betreiber/Entwickler des Terminbuchungs-Backends (verantwortlich für Datenschutz/DSGVO)
**I want** Logs, die einen Vorgang über Correlation-/Trace-IDs technisch vollständig nachvollziehbar machen, aber **keine** Namen, Kennzeichen, E-Mails, Telefonnummern, Adressen oder vollständigen Payloads mehr enthalten
**So that** wir SoftNet-Fehler weiterhin belegen können, ohne personenbezogene Daten unnötig in Log Analytics zu vervielfältigen.

**Acceptance Criteria:**
- [ ] AC-1: Namen (`firstName`, `lastName`) erscheinen nicht mehr im Log-Output.
- [ ] AC-2: Kennzeichen (`license`/`licensePlate`) erscheinen nicht mehr im Log-Output.
- [ ] AC-3: E-Mail-Adressen und Telefonnummern (`email`, `phone`, `mobile`) erscheinen nicht mehr im Log-Output.
- [ ] AC-4: Vollständige Request-/Response-Payloads werden nicht mehr im Klartext geloggt; im Fehlerfall nur die **redigierte** Variante (PII-Felder = `***`, Struktur erhalten).
- [ ] AC-5: Authentifizierungsdaten (Authorization, Cookies, Session-Token, JWT, API-Keys, Fingerprint-Inhalt) werden nicht geloggt.
- [ ] AC-6: Erfolgreiche Requests bleiben über `correlation_id`/`traceId` nachvollziehbar (Sunshine-Case-Log erhalten).
- [ ] AC-7: Fehler bleiben technisch analysierbar (error_code, status, endpoint, duration, redigierter Payload, Stacktrace ohne PII).
- [ ] AC-8: DEBUG-Logging enthält ebenfalls keine personenbezogenen Daten (kein Full-Object-Dump).
- [ ] AC-9: Eine zentrale `LogSanitizer`-Policy verhindert versehentliches künftiges Logging sensibler Felder (eine Feldliste, ein Einstiegspunkt).
- [ ] AC-10: Automatisierte Tests prüfen typische Testwerte (`Max Mustermann`, `AB-CD-1234`, `max@example.com`) für Success/Validation/Exception/Inbound/Outbound/Third-Party-Error/Debug.
- [ ] AC-11: Bestehende Business-Logik ist unverändert (nur Logging betroffen; keine geänderten API-Responses, keine geänderten SoftNet-Requests).
- [ ] AC-12: IP-Adressen werden nicht im Klartext geloggt (gekürzt/gehasht).
- [ ] AC-13: Auch erfolgreiche Buchungen werden geloggt (`BOOKING_CREATED`, INFO) — PII-frei, über `traceId` Ende-zu-Ende nachvollziehbar (schließt die Erfolgspfad-Lücke aus der Kennzeichen-Analyse).
- [ ] AC-14: Der PII-Filter ersetzt sensible Werte durch ihre **Zeichenanzahl** (`***(12)`, `***(0)`, `***(null)`), sodass die Eingabelänge (leer/vorhanden/plausibel) sichtbar bleibt, der Wert selbst aber nicht.

---

## 3. Preconditions

### 3.1 System
- Spring Boot Backend läuft (lokal `:3000` oder Azure Container App `gs-terminbuchungs-app`).
- Logback-Pattern aus REQ-029 aktiv: `%d{ISO8601} [%thread] %-5level %logger{36} - %X{traceId:-system}: %msg%n`.
- Azure Log Analytics (`ContainerAppConsoleLogs_CL`) empfängt die Konsolen-Logs.

### 3.2 User
- n/a (Cross-Cutting Concern, kein UI).

### 3.3 Data
- SoftNet-Zugangsdaten konfiguriert; Requests durchlaufen `SoftnetApiClient` / `AppointmentsService`.

---

## 4. Main Flow

**Step 1:** HTTP-Request trifft ein
- **System:** `RequestIdFilter` setzt `traceId` (MDC); `LoggingFilter` schreibt am Ende die Access-Zeile (correlation_id, route, status, duration) — **ohne** Body.
- **Expected:** Vorgang über `correlation_id`/`traceId` referenzierbar, keine PII.

**Step 2:** Erfolgreiche Verarbeitung (Sunshine Case)
- **System:** nur Metadaten-Logs (`request.received` / `mapping.completed` / `outbound.sent` / `response.received` / `request.completed`) mit `correlation_id`, `route`, `duration_ms`, `status`.
- **Expected:** vollständige technische Nachvollziehbarkeit, keine Payloads.

**Step 3:** Fehlerfall
- **System:** `SoftnetApiClient`/`AppointmentsService` reichen den Payload durch `LogSanitizer.redact(...)` → `LogPayloadUtil.truncate(...)`, bevor er geloggt wird.
- **Expected:** ERROR-Zeile mit `error_code`, `endpoint`, `duration`, **redigiertem** Payload (PII = `***`), Trace-ID.

---

## 5. Alternative Flows

### 5.1 Alt Flow A: Payload ist kein gültiges JSON
**Trigger:** Fremd-Response ist Plain-Text/octet-stream (kommt real vor, siehe SoftNet 30s-Timeout).
**Flow:**
1. `LogSanitizer.redact()` kann nicht parsen.
2. **Fail-safe:** Statt ungeprüftem Klartext wird ein Marker geloggt (`[non-json payload, size=N, redaction-skipped]`) bzw. eine regex-basierte Fallback-Maskierung (analog `PiiRedactionAdvisor`) angewandt.
3. Kein halbmaskierter Klartext gelangt ins Log.

### 5.2 Alt Flow B: Log außerhalb eines HTTP-Requests
**Trigger:** Startup/Scheduler/Cache.
**Flow:** MDC `traceId` nicht gesetzt → Logback-Default `system:` (unverändert aus REQ-029).

---

## 6. Exception Flows

### 6.1 Exception E1: SoftNet-Call schlägt fehl
**Trigger:** `SoftnetApiClient.executeRequest()` wirft.
**Flow:**
1. Response-Body extrahieren (falls `RestClientResponseException`).
2. `requestBody` und `responseBody` → `LogSanitizer.redact()` → `LogPayloadUtil.truncate()`.
3. ERROR-Zeile: `SOFTNET_ERROR <METHOD> <endpoint> failed after <ms>: <msg> | requestBody=<redacted> | responseBody=<redacted>`.
4. Exception propagiert unverändert → `GlobalExceptionHandler` (Mapping unverändert).

### 6.2 Exception E2: Reservierung schlägt fehl
**Trigger:** `AppointmentsService.createAppointment()` — SoftNet-POST wirft.
**Flow:** zusätzliche ERROR-Zeile mit `rawSnap` → **ebenfalls** durch `LogSanitizer.redact()`.

### 6.3 Exception E3: Fremd-Exception-Message enthält PII
**Trigger:** Dritt-Bibliothek/Upstream bettet Daten in die Message.
**Flow:** `GlobalExceptionHandler.logError()` loggt nur `exception`-Klassennamen + statischen Text; die (potenziell PII-haltige) `getMessage()` wird nicht 1:1 ins Log übernommen bzw. sanitisiert. Stacktrace nur, wenn ohne eingebettete Payloads.

---

## 7. Postconditions

### 7.1 Success
- Log enthält Metadaten-Kette mit `correlation_id`; kein Payload, keine PII.

### 7.2 Failure
- Log enthält `error_code`/`status`/`endpoint`/redigierten Payload; keine Klartext-PII.

### 7.3 Retention (Dokumentation, keine Aktion in diesem REQ)
- **Logquelle:** Azure Log Analytics Workspace `workspacedefaultresourcegroupdewc99c4`, Tabelle `ContainerAppConsoleLogs_CL`.
- **Aktuelle Retention:** Standard des Workspace (zu bestätigen; Container-Apps-Konsolen-Logs typ. 30 Tage — im Datenbestand aktuell 2026-07-19 bis 2026-08-18 sichtbar).
- **Altbestand:** Vor dem Deploy dieses REQ enthaltene `SOFTNET_ERROR`-Zeilen enthalten weiterhin Klartext-PII bis zum Ablauf der Retention. **Empfehlung (separat):** Purge/Verkürzung der Retention für den betroffenen Zeitraum — als eigener Vorgang, nicht Teil dieses REQ.

---

## 8. Business Rules

- **BR-1 (Verbotsliste):** Folgende Felder dürfen **nie** im Klartext geloggt werden (case-insensitive), Basis + reale Projekt-Feldnamen:
  `firstName`, `lastName`, `fullName`/`customerName`, `email`, `phone`, `mobile`, `address`, `street`, `zipCode`/`postalCode`, `city`, `license`/`licensePlate`, `registrationPlate`, `numberPlate`, `note` (Freitext), `salutation`, `VIN` (Default redigiert), sowie Auth: `Authorization`, `Cookie`, `Set-Cookie`, `X-Session-Id`-**Wert**, JWT/API-Keys, `SOFTNET_FINGERPRINT_*`.
- **BR-2 (Metadata-first):** Im Normalfall nur Metadaten (`correlation_id`, `route`, `status`, `duration_ms`, `payload_size`, `error_code`).
- **BR-3 (Redaction-on-error):** Payloads nur redigiert und nur im Fehlerfall; Redaction läuft **vor** dem Logger-Aufruf (Werte gar nicht erst übergeben, wo möglich; Redaction als zweite Schutzschicht).
- **BR-4 (Allowlist Header):** Falls Header geloggt werden, nur Allowlist (`content-type`, `correlation-id`, `request-id`) — nie Auth-Header.
- **BR-5 (VIN):** VIN nur loggen, wenn im Datenschutzkonzept ausdrücklich erlaubt; Default = redigiert. Technische Referenz = `traceId`/`correlation_id`, nicht VIN/Kennzeichen.
- **BR-6 (kein Verhaltenseingriff):** Redaction betrifft ausschließlich Log-Ausgaben — nie die an SoftNet gesendeten Daten oder API-Responses.

---

## 9. Non-Functional Requirements

### Performance
- Redaction (Jackson-Tree-Walk auf max. 8000 Zeichen) nur im Fehlerfall → vernachlässigbarer Overhead im Normalpfad (dort kein Payload-Logging).

### Security / Datenschutz
- Datensparsamkeit (DSGVO Art. 5 Abs. 1 c). Zweischichtig: (1) Metadaten statt Payload, (2) Redaction als Fallback.
- Keine Auth-Secrets im Log.

### Wartbarkeit
- **Eine** Feldliste, **ein** `LogSanitizer` — neue sensible Felder an genau einer Stelle ergänzbar. Vereinheitlichung mit `PiiRedactor` (Report-Pfad), um doppelte Wahrheiten zu vermeiden.

### Observability
- Trace-/Correlation-ID-Nachverfolgbarkeit aus REQ-029 bleibt vollständig erhalten.

---

## 10. Data Model

```java
// server/src/main/java/com/autohaus/backend/common/logging/LogSanitizer.java
/**
 * Zentrale PII-Redaction für Log-Payloads / Central PII redaction for log payloads.
 * Ersetzt sensible Feldwerte durch "***", bevor eine Zeile den Logger erreicht.
 * Vereinheitlicht die Feldliste mit {@link com.autohaus.backend.reporting.PiiRedactor}.
 */
public final class LogSanitizer {

    /** Case-insensitive Verbotsliste (BR-1). Single source of truth. */
    public static final Set<String> SENSITIVE_FIELDS = Set.of(
        "firstname", "lastname", "fullname", "customername", "drivername",
        "email", "phone", "mobile",
        "address", "street", "zipcode", "postalcode", "city",
        "license", "licenseplate", "registrationplate", "numberplate",
        "vin", "note", "salutation",
        "authorization", "cookie", "set-cookie");

    /** Redigiert JSON-Feldwerte rekursiv; Nicht-JSON → Fail-safe-Marker. */
    public static String redact(String payload);

    /** redact(...) + LogPayloadUtil.truncate(...) in einem Schritt. */
    public static String redactAndTruncate(String payload);
}
```

```text
// Log-Format Zielbild (siehe §11)
// Sunshine:
request.completed correlation_id=<id> route=/api/v1/appointments status=200 duration_ms=184
// Fehler:
SOFTNET_ERROR POST /api/planning/appointments failed after 412ms: 400 ValidationError \
  | requestBody={"wishlist":{"client":{"firstName":"***","lastName":"***","email":"***",...},"car":{"license":"***","VIN":"***",...}}} \
  | responseBody={"code":"ValidationError",...}
```

---

## 11. UI/UX

**Kein User-facing UI** — Cross-Cutting Backend-Concern (analog REQ-029, das ebenfalls kein Mockup hat). Der visuelle „Deliverable" ist das **Log-Ausgabeformat** (Vorher/Nachher):

| Aspekt | Vorher (REQ-029) | Nachher (REQ-034) |
|--------|------------------|-------------------|
| Sunshine | Access-Zeile (bereits sauber) | unverändert ✅ |
| Fehler-Request-Body | `"firstName":"Deborah","license":"N BM9015 E"` | `"firstName":"***","license":"***"` |
| rawSnap | Klartext | redigiert |
| OriginFilter | `ip="203.0.113.7"` | `ip="203.0.113.0/24"` bzw. gehasht |
| Snaps-Debug | Full-Object-Dump | `route=/snaps mode=date-range items=…` |

---

## 12. API Specification

**Keine API-Änderung.** SoftNet-Requests und Client-Responses bleiben byte-identisch. Nur Log-Ausgaben ändern sich. (Bewusst: keine neuen Endpoints, keine geänderten DTOs.)

---

## 13. Test Cases

> Umsetzung: `OutputCaptureExtension` (Spring Boot Test) bzw. Logback `ListAppender`, um den erzeugten Log-Output zu erfassen und auf Testwerte zu prüfen.

**Fixture-Testwerte:** `firstName=Max`, `lastName=Mustermann`, `email=max@example.com`, `mobile=+49 151 1234567`, `licensePlate=AB-CD-1234`, `vin=WVWZZZ1JZXW000001`.

### TC-1: LogSanitizer maskiert alle Verbotsfelder
- **Given:** JSON mit allen Feldern aus BR-1
- **When:** `LogSanitizer.redact(json)`
- **Then:** kein Testwert enthalten; Schlüssel + `"***"` erhalten; JSON bleibt valide.

### TC-2: SoftNet-Fehler (Outbound) loggt redigiert
- **Given:** `SoftnetApiClient` POST wirft; Request-Body enthält Testwerte
- **When:** Fehler wird geloggt
- **Then:** Log enthält `SOFTNET_ERROR`, `error_code`, keinen Testwert.

### TC-3: Reservierungsfehler (rawSnap) loggt redigiert
- **Given:** `AppointmentsService.createAppointment()` scheitert; Snap enthält Bestandskunden-Daten
- **Then:** `rawSnap`-Log ohne PII.

### TC-4: Validierungsfehler
- **Given:** Request mit leerem Pflichtfeld
- **When:** 400
- **Then:** Log enthält `field`/`error_code`, keinen Feldwert.

### TC-5: Non-JSON-Payload (Fail-safe)
- **Given:** octet-stream/Plaintext-Response mit einem Namen
- **Then:** Fallback-Marker/Regex-Maskierung; kein Klartext-Name.

### TC-6: Sunshine Case bleibt nachvollziehbar
- **Given:** erfolgreicher Request
- **Then:** Log enthält `correlation_id` + `status=200`, aber keinen Payload.

### TC-7: Auth-Header nie geloggt
- **Given:** Request mit `Authorization`/`Cookie`
- **Then:** keine dieser Werte im Log.

### TC-8: DEBUG-Level
- **Given:** Log-Level DEBUG, Snaps-Request
- **Then:** Metadaten statt Full-Object-Dump; keine PII.

### TC-9: OriginFilter IP maskiert
- **Given:** blockierter Origin
- **Then:** IP gekürzt/gehasht.

### TC-10: Regression-Sweep
- Repo-Grep nach `requestBody`/`response.body`/`JSON.stringify(request`/`log.*(customer|plate|name|email` findet keine ungeschützte Stelle mehr.

---

## 14. Implementation

### Components (Backend)
- [ ] `common/logging/LogSanitizer.java` — zentrale Redaction (neu)
- [ ] `common/logging/LogSanitizerTest.java` — TC-1, TC-5, TC-7
- [ ] `softnet/SoftnetApiClient.java:218` — `requestBody`/`responseBody` → `LogSanitizer.redactAndTruncate(...)`
- [ ] `appointments/AppointmentsService.java:67` — `rawSnap` → `LogSanitizer.redactAndTruncate(...)`
- [ ] `common/filter/OriginFilter.java:74` — IP maskieren/kürzen
- [ ] `snaps/SnapsService.java:71` — Full-Dump → Metadaten
- [ ] `common/exception/GlobalExceptionHandler.java:117` — Message-Sanitizing absichern (E3)
- [ ] `reporting/PiiRedactor.java` — Feldliste auf `LogSanitizer.SENSITIVE_FIELDS` vereinheitlichen (eine Wahrheit)
- [ ] Integrationstests mit `OutputCaptureExtension`/`ListAppender` (TC-2..TC-4, TC-6, TC-8, TC-9)

### Optional (Frontend)
- [ ] `core/http/trace.service.ts` — generischen `data`-Parameter nur in Dev loggen / whitelisten

### Effort (Schätzung)
- Development: ~6 h · Testing: ~4 h

---

## 15. Dependencies

**Requires:**
- REQ-029 (Payload-Logging, `LogPayloadUtil`, Trace-ID) — wird hier redigiert.
- REQ-030 (`PiiRedactor` als Redaction-Vorlage).

**Blocks:**
- Keine. Rein additiv/absichernd; keine anderen REQ warten darauf.

---

## 16. Naming Glossary

### LogSanitizer (neu, `common/logging`)
- `redact(String payload)` — JSON-Feld-Redaction, Nicht-JSON → Fail-safe
- `redactAndTruncate(String payload)` — `redact` + `LogPayloadUtil.truncate`
- `SENSITIVE_FIELDS` — zentrale Verbotsliste (Single Source of Truth)

### Betroffene bestehende Methoden
- `SoftnetApiClient.executeRequest()` — Fehlerpfad redigiert
- `AppointmentsService.createAppointment()` — rawSnap redigiert
- `OriginFilter.doFilterInternal()` — IP maskiert
- `GlobalExceptionHandler.logError()` — Message-Sanitizing

### Log-Event-Namen (Konvention, technisch)
- `request.received`, `mapping.completed`, `outbound.sent`, `response.received`, `request.completed`, `SOFTNET_ERROR`

---

## 17. Approval

| Role | Name | Date |
|------|------|------|
| Product Owner | | |
| Tech Lead | | |

---

## 18. Implementation Notes

**Code bilingual** (siehe `.claude/skills/bilingual-code.md`): Kommentare DE + EN, Error-Messages Englisch, JSDoc/Javadoc bilingual.

**Karpathy-Leitplanken für die Umsetzung:**
- Nicht das Logging abschalten — nur PII entfernen, Technik behalten.
- Zentral (`LogSanitizer`) statt verstreuter String-Replacements.
- Feldliste mit `PiiRedactor` **zusammenführen**, nicht duplizieren.
- Nach der Umsetzung: Regression-Grep (TC-10) + lokaler Laufzeittest (`npm run test:local:serve`, Buchung mit Testdaten auslösen, Log prüfen).
