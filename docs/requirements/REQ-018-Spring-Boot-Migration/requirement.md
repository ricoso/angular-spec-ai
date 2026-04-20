# REQ-018: Spring Boot Migration (NestJS Abloesung)

| Feld | Wert |
|------|------|
| **ID** | REQ-018 |
| **Titel** | Spring Boot Migration — NestJS Backend durch Spring Boot 4 (Gradle + Spring Security) ersetzen |
| **Status** | Draft |
| **Prioritaet** | Critical |
| **Abhaengigkeiten** | REQ-014-NestJS-Backend |
| **Erstellt** | 2026-04-14 |

---

## 1. Zusammenfassung

Das bestehende NestJS-Backend (`backend/`) wird vollstaendig durch ein Spring Boot Backend ersetzt. Technologie-Stack: **Java 25 + Spring Boot 4.1 + Gradle (Groovy DSL) + Spring Security**. Das Projekt liegt im Verzeichnis `server/`. Die REST-API-Schnittstelle (Endpunkte, Request/Response-Formate, Header-Konventionen) bleibt **1:1 identisch**, sodass das Angular-Frontend und die Playwright-E2E-Tests ohne Aenderungen funktionieren.

**Full-Stack-Serving:** Spring Boot bedient sowohl die REST-API (`/api/*`) als auch das Angular-Frontend (statische Dateien aus `dist/`). Ein separater Dev-Server fuer das Frontend ist in Production **nicht noetig** — `./gradlew bootRun` liefert alles ueber einen einzigen Port (:3000).

**Akzeptanzkriterium:** Alle bestehenden Playwright-E2E-Tests (`npm run e2e`) muessen mit dem neuen Spring-Boot-Backend durchlaufen.

---

## 2. Architektur-Uebersicht

```
Browser
    ↓  GET / (oder SPA-Route)
Spring Boot (:3000)
    │
    ├── Static Files (Angular dist/)    → JS, CSS, Assets
    ├── SPA Fallback (index.html)       → Alle Nicht-API, Nicht-Asset Routen
    │       └── CSP-Nonce-Injection     → ngCspNonce in <app-root>
    │
    ├── GET/POST /api/v1/*              → REST-API Controller
    │       ↓
    │   SoftNet Planning API (extern)
    │
    └── GET /api/v1/health              → Health Check (ohne Guard)
```

**Ein einziger Server bedient alles:** Angular-Frontend + REST-API + SPA-Routing + CSP-Nonce.

### Request-Lifecycle (1:1 Nachbau)

```
Incoming HTTP Request
    ↓
RequestIdFilter          → X-Request-Id Header generieren/durchreichen
    ↓
LoggingFilter            → Request/Response loggen (JSON structured)
    ↓
RateLimitFilter          → 100 Requests / 15 Min pro IP
    ↓
OriginFilter             → CORS_ALLOWED_ORIGINS pruefen
    ↓
SessionTokenFilter       → x-session-token validieren (HMAC-SHA256)
    ↓
Validation               → Jakarta Bean Validation (@Valid)
    ↓
Controller → Service → SoftnetApiClient
    ↓
ResponseWrapper          → { data: T, meta: { timestamp, requestId } }
    ↓
ExceptionHandler         → Einheitliches Error-Format
    ↓
HTTP Response
```

---

## 3. Acceptance Criteria

### Funktional (API-Kompatibilitaet)

- [ ] AC-1: **Alle Playwright-E2E-Tests bestehen** (`npm run e2e`) — DAS primaere Gate
- [ ] AC-2: Angular-Frontend funktioniert ohne Code-Aenderungen
- [ ] AC-3: Alle REST-Endpunkte identisch (Pfade, Methoden, Request/Response-Formate)
- [ ] AC-4: Response-Envelope `{ data: T, meta: { timestamp, requestId } }` identisch
- [ ] AC-5: Error-Response-Format identisch: `{ statusCode, messageKey, message, meta }`
- [ ] AC-6: Session-Token-Mechanismus (HMAC-SHA256) kompatibel
- [ ] AC-7: SoftNet-API-Integration (JWT RS256 Auth) funktional
- [ ] AC-8: Static-File-Serving fuer Angular-Build-Artefakte aus `dist/angular-21-project/browser/`
- [ ] AC-8a: SPA-Fallback: Alle Nicht-API, Nicht-Asset Requests liefern `index.html` (Angular Router uebernimmt)
- [ ] AC-8b: Root-Route `/` liefert Angular-App (HTTP 200, `<!DOCTYPE html>`)
- [ ] AC-8c: `http://localhost:3000` im Browser zeigt die vollstaendige Angular-Applikation
- [ ] AC-9: CSP-Nonce-Injection in `index.html` fuer Angular Inline-Styles
- [ ] AC-10a: **Integrierter Angular Build:** `./gradlew bootRun` baut automatisch Angular Frontend vor dem Start
- [ ] AC-10b: **Gradle Up-to-Date-Check:** Angular wird nur neu gebaut wenn `src/`, `angular.json` oder `tsconfig*.json` geaendert
- [ ] AC-10c: **`bootJar`** erzeugt Fat JAR das Angular dist/ enthaelt (Deployment-Artefakt)
- [ ] AC-10d: **`npmInstall`** laeuft nur wenn `node_modules/` fehlt (Erststart / Clean)

### Dokumentation & Konfiguration

- [ ] AC-N1: CLAUDE.md: `Deployment`-Zeile von "NestJS Backend" auf "Spring Boot Backend" aendern
- [ ] AC-N2: CLAUDE.md: Backend-Commands (`npm start` etc.) um Spring-Boot-Commands ergaenzen (`./gradlew bootRun`)
- [ ] AC-N3: **Alle NestJS-Verweise im gesamten Repo entfernen** — Code, Dokumentation, Konfiguration, Skills. Nach Abschluss darf kein Verweis auf NestJS mehr existieren
- [ ] AC-N4: **Neuen Backend-Skill erstellen** (`.claude/skills/spring-backend.md`): Spring Boot Best Practices dokumentieren — `@RestController`, `@Service`, `@ConfigurationProperties`, `@HttpExchange`, Spring Security FilterChain, Jakarta Bean Validation, Structured Logging, Error Handling (`@ControllerAdvice`), Testing (JUnit 5 + Mockito + MockMvc)
- [ ] AC-N5: **`security.md` erweitern:** Backend-Security-Abschnitt hinzufuegen — Spring Security FilterChain, CORS-Config, Security-Header (CSP, HSTS, X-Frame-Options), Rate-Limiting, Session-Token-Validierung (HMAC-SHA256), HTTPS/TLS-Konfiguration
- [ ] AC-N6: **`projekt-setup.md` aktualisieren:** Production-Deployment von "NestJS" auf "Spring Boot" umstellen — `./gradlew bootRun` statt `cd backend && npm start`, `application.yml` statt `.env`, Java Toolchain statt Node.js
- [ ] AC-N7: **`angular-architecture.md` aktualisieren:** API-Services-Abschnitt auf Spring Boot Backend anpassen (Port 3000, `/api/v1/*` Prefix)
- [ ] AC-N8: **Spring MCP Server einbinden:** `spring-ai-mcp` oder Spring-relevanten MCP-Server in `.claude/settings.local.json` konfigurieren fuer Spring-Boot-spezifische Dokumentation und Autovervollstaendigung
- [ ] AC-N9: **NestJS-Bereinigung (Zero-References-Gate):** Nach Abschluss der Migration darf im gesamten Repository kein Verweis auf NestJS mehr existieren — weder in Code, Dokumentation, CLAUDE.md, Skills, Package-Dateien, noch in Konfigurationen. Pruefung: `grep -ri "nestjs\|nest.js\|@nestjs" .` liefert 0 Treffer

### Nicht-Funktional

- [ ] AC-10: Java 25 + Spring Boot 4.1 + Gradle Groovy DSL
- [ ] AC-11: Spring Security fuer Session-Token-Validierung + Origin-Guard + Rate-Limiting
- [ ] AC-12: Structured JSON Logging (SLF4J + Logback)
- [ ] AC-13: Helmet-aequivalente Security-Header (CSP, X-Content-Type-Options, etc.)
- [ ] AC-14: Timeout: 30s global, 10s SoftNet-Calls (konfigurierbar)
- [ ] AC-15: Port 3000 (konfigurierbar via `PORT` env var)
- [ ] AC-16: Unit-Tests mit JUnit 5 + Mockito (>80% Coverage)

---

## 4. Technologie-Stack

| Komponente | NestJS (aktuell) | Spring Boot (neu) |
|-----------|-----------------|-------------------|
| Runtime | Node.js 22 | Java 25 (JDK) |
| Framework | NestJS 11 | Spring Boot 4.1 |
| Build | npm / tsc | Gradle Groovy DSL (inkl. Angular Build) |
| Frontend Build | Separater `npm run build` | Integriert via Gradle `buildAngular` Task |
| HTTP Client | Axios / @nestjs/axios | Declarative HTTP Interface (`@HttpExchange`, `@GetExchange`, `@PostExchange`) |
| Validation | class-validator | Jakarta Bean Validation |
| Security | Custom Guards | Spring Security FilterChain |
| Rate Limiting | @nestjs/throttler | Bucket4j (`bucket4j_jdk17-core:8.14.0`) |
| JWT Signing | jsonwebtoken | nimbus-jose-jwt (`10.3`) |
| Logging | NestJS Logger | SLF4J + Logback (JSON, `logstash-logback-encoder:8.0`) |
| Testing | Jest | JUnit 5 + Mockito + MockMvc + JaCoCo |
| Static Files | @nestjs/serve-static | Spring ResourceHandler + SPA ViewController |
| Config | @nestjs/config (dotenv) | application.yml + Environment |

---

## 5. REST-API-Spezifikation (1:1 Nachbau)

### 5.1 Session Management

#### `GET /api/v1/session/token` — KEIN Guard

**Response:**
```json
{
  "data": {
    "token": "<timestamp_base64url>.<hmac_base64url>",
    "expiresAt": "2026-04-14T11:00:00.000Z"
  },
  "meta": {
    "timestamp": "2026-04-14T10:40:00.000Z",
    "requestId": "uuid-v4"
  }
}
```

**Token-Format:**
- Part 1: `Base64URL(currentTimestampMillis)`
- Part 2: `Base64URL(HEX(HMAC-SHA256(timestampString, SESSION_SECRET)))`
- Validierung: HMAC pruefen + Alter <= `SESSION_TTL_MINUTES`

---

### 5.2 Health Check

#### `GET /api/v1/health` — KEIN Guard

**Response:**
```json
{
  "data": {
    "status": "healthy | degraded | unhealthy",
    "uptime": 12345,
    "timestamp": "2026-04-14T10:40:00.000Z",
    "dependencies": {
      "softnet": {
        "status": "healthy | unhealthy",
        "responseTime": 245
      }
    }
  },
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

---

### 5.3 Branches

#### `GET /api/v1/branches` — SessionTokenGuard

**Response:**
```json
{
  "data": [
    {
      "id": "branch-1",
      "name": "Filiale Mitte",
      "description": "Mitte",
      "brands": ["BMW", "Audi"]
    }
  ],
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

**Business-Logik:**
1. Parallel: `GET /api/planning/groups` (ohne Group-Header) + `GET /api/planning/branches` (ohne Group-Header)
2. Gruppe nach `GROUP_ID` filtern
3. Nur Branches der Gruppe zurueckgeben
4. `description`: Fallback `label` → `company` → `""`
5. `brands`: Aus `group.brand[]` (nicht vom Branch)
6. `group.branches` kann String-IDs ODER `{ id, name }` Objekte enthalten — beides handeln

#### `GET /api/v1/branches/{branchId}` — SessionTokenGuard

**Response:** Einzelnes Branch-Objekt (gleiche Struktur)

**Business-Logik:**
1. Parallel: `GET /api/planning/branches/{branchId}` + `GET /api/planning/groups`
2. Gleiche Mapping-Logik
3. KEIN Check ob Branch zur Gruppe gehoert (bewusstes Verhalten)

---

### 5.4 Brands

#### `GET /api/v1/branches/{branchId}/brands` — SessionTokenGuard

**Response:**
```json
{
  "data": [
    { "id": "brand-123", "name": "Toyota" }
  ],
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

**Business-Logik:**
1. Parallel: `GET /api/planning/brands` (mit Group-Header) + `GET /api/planning/groups` (ohne Group-Header)
2. Gruppe nach `GROUP_ID` filtern
3. Nur Brands zurueckgeben deren `name` (case-insensitive) in `group.brand[]` vorkommt
4. **Achtung:** `branchId` wird akzeptiert aber NICHT verwendet

---

### 5.5 Job Types + Operations

#### `GET /api/v1/branches/{branchId}/jobtypes` — SessionTokenGuard

**Response:**
```json
{
  "data": [
    {
      "id": "jt-1",
      "name": "Inspektion",
      "description": "Inspektion",
      "operations": [
        { "id": "op-1", "name": "Oelwechsel" }
      ]
    }
  ],
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

**Business-Logik:**
1. Parallel: `GET /api/planning/jobtypes` (mit Group-Header) + `GET /api/planning/operations` (mit Group-Header)
2. Jeder JobType bekommt ALLE Operations (keine Filterung nach Branch)
3. `description` = `name` (gleicher Wert)
4. **Achtung:** `branchId` wird akzeptiert aber NICHT verwendet

---

### 5.6 Snaps (Terminslots)

#### `POST /api/v1/snaps` — SessionTokenGuard

**Request:**
```json
{
  "branchId": "branch-1",
  "jobTypeIds": ["inspection", "oil-change"],
  "fromDate": "2026-04-15",
  "toDate": "2026-04-29",
  "mode": "next-available | date-range",
  "brandName": "BMW"
}
```

**Validation:**
- `branchId`: @NotBlank
- `jobTypeIds`: @NotEmpty, Array of Strings
- `fromDate`: @NotBlank
- `toDate`: @NotBlank
- `mode`: Enum `next-available` | `date-range`
- `brandName`: Optional
- `timePreference`: Optional, Enum `AM` | `PM` (→ wird als `checkInTimePreference` an SoftNet weitergereicht)
- `replacementCar`: Optional, boolean (→ wird als `replacementPreference` an SoftNet weitergereicht)

**Response:**
```json
{
  "data": [
    {
      "date": "2026-04-15",
      "displayDate": "15.04.2026",
      "dayAbbreviation": "Mi",
      "slots": [
        {
          "id": "snap-internal-id",
          "time": "14:30",
          "displayTime": "14:30 Uhr",
          "available": true,
          "resource": "Werkstatt A"
        }
      ]
    }
  ],
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

**Business-Logik:**
1. Frontend-JobType-IDs → SoftNet-Operation-IDs mappen (Hardcoded Mapping-Tabelle, siehe Section 9)
2. SoftNet-Request bauen:
   ```json
   {
     "groupid": "GROUP_ID",
     "brand": "brandName || ''",
     "car": { "brand": "brandName || ''" },
     "operations": ["mapped-op-ids"],
     "preferredBranch": "branchId",
     "checkInDatePreference": "asSoonAsPossible | date",
     "checkInDate": "fromDate",
     "checkInTimePreference": "any | AM | PM",
     "replacementPreference": true
   }
   ```
   - `checkInDate`: NUR bei `mode=date-range`
   - `checkInTimePreference`: Getestet + bewiesen — verschiebt AM/PM-Verteilung (siehe Section 10b)
   - `replacementPreference`: Getestet + bewiesen — aktiviert Ersatzwagen wenn Branch es unterstuetzt (siehe Section 10b)
3. `POST /api/planning/snaps` (mit Group-Header) aufrufen
4. Raw-Snaps cachen (In-Memory, TTL 30 Min, Key = `snap.internal`)
5. Gruppieren nach Datum (aus `checkInTime` extrahiert, UTC)
6. Pro Snap → TimeSlot: `id=internal`, `time=HH:mm`, `displayTime=HH:mm Uhr`, `available=true`
7. Tagesabkuerzungen: `{0:'So', 1:'Mo', 2:'Di', 3:'Mi', 4:'Do', 5:'Fr', 6:'Sa'}`
8. `displayDate`: `DD.MM.YYYY`
9. Sortierung: aufsteigend nach Datum

**Snap-Cache:** 
- `Map<String, CachedSnap>` mit 30-Min-TTL
- Deep-Copy beim Cachen (Mutation verhindern)
- Wird von AppointmentsService via `getRawSnap(snapId)` gelesen

---

### 5.7 Mobility Alternatives

#### `GET /api/v1/branches/{branchId}/mobility-alternatives` — SessionTokenGuard

**Response:**
```json
{
  "data": [
    { "id": "mob-1", "name": "Leihwagen" }
  ],
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

**Business-Logik:**
1. `GET /api/planning/mobilityalternatives` (mit Group-Header)
2. Map zu `{ id, name }`
3. **Achtung:** `branchId` wird akzeptiert aber NICHT verwendet

---

### 5.8 Customers

#### `POST /api/v1/customers` — SessionTokenGuard

**Request:**
```json
{
  "email": "max@example.de",
  "salutation": "mr | ms",
  "firstName": "Max",
  "lastName": "Mustermann",
  "street": "Musterweg 1",
  "postalCode": "30159",
  "city": "Berlin",
  "mobilePhone": "017012345678"
}
```

**Validation:**
- `email`: @Email
- `salutation`: Enum `mr` | `ms`
- `firstName`: 2-50 Zeichen, Pattern `^[a-zA-ZaeoeueAeOeUess\\s\\-']+$`
- `lastName`: 2-50 Zeichen, gleiches Pattern
- `street`: @NotBlank, max 100
- `postalCode`: Pattern `^\\d{4,5}$`
- `city`: 2-50 Zeichen, Pattern `^[a-zA-ZaeoeueAeOeUess\\s\\-]+$`
- `mobilePhone`: Pattern `^\\+?[\\d\\s\\-/()]{6,20}$`

**Response:**
```json
{
  "data": { "id": "vault-123", "email": "max@example.de", "firstName": "Max", "lastName": "Mustermann" },
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

**Business-Logik:**
1. `POST /api/planning/vaultitems` (mit Group-Header)
2. Field-Mapping: `postalCode` → `zip` (Umbenennung)

#### `GET /api/v1/customers/search?licensePlate=...&lastName=...&email=...` — SessionTokenGuard

**Query-Parameter:** Alle optional, mindestens einer muss vorhanden sein.

**Response:**
```json
{
  "data": [
    { "id": "vault-123", "email": "max@example.de", "firstName": "Max", "lastName": "Mustermann" }
  ],
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

**Business-Logik:**
1. Dynamisch SearchParams aus vorhandenen Query-Parametern bauen
2. `GET /api/planning/vaultitems?${params}` (mit Group-Header)

---

### 5.9 Cars

#### `POST /api/v1/cars` — SessionTokenGuard

**Request:**
```json
{
  "licensePlate": "B-MS1234",
  "mileage": 50000,
  "vin": "WVWZZZ3CZWE123456",
  "brandId": "brand-123"
}
```

**Validation:**
- `licensePlate`: Pattern `^[A-ZAEOUE]{1,3}[\\s\\-]?[A-Z]{1,2}[\\s\\-]?\\d{1,4}[A-Z]?$`
- `mileage`: @Min(0) @Max(999999)
- `vin`: Genau 17 Zeichen, Pattern `^[A-HJ-NPR-Z0-9]{17}$`
- `brandId`: Optional

**Response:**
```json
{
  "data": { "id": "car-123", "licensePlate": "B-MS1234" },
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

**Business-Logik:**
1. `POST /api/planning/cars` (mit Group-Header)
2. `licensePlate` im Response: SoftNet-Wert bevorzugt, Fallback auf DTO-Wert

---

### 5.10 Appointments

#### `POST /api/v1/appointments` — SessionTokenGuard

**Request:**
```json
{
  "snapId": "snap-internal-id",
  "customer": {
    "email": "max@example.de",
    "firstName": "Max",
    "lastName": "Mustermann",
    "mobile": "017012345678",
    "phone": "030123456",
    "address": "Musterweg 1",
    "zipCode": "30159",
    "city": "Berlin",
    "country": "DE",
    "language": "de",
    "salutation": "mr"
  },
  "vehicle": {
    "licensePlate": "B-MS1234",
    "mileage": 50000,
    "vin": "UNKNOWN",
    "model": "Golf",
    "firstRegMonth": "01",
    "firstRegYear": "2020"
  },
  "note": "Bitte Klimaanlage pruefen",
  "mobilityOption": "Leihwagen"
}
```

**Response:**
```json
{
  "data": {
    "id": "appointment-123",
    "status": "confirmed",
    "date": "2026-04-15",
    "time": "14:30",
    "branchName": "Filiale Mitte",
    "services": [],
    "customerName": "Max Mustermann",
    "licensePlate": "B-MS1234"
  },
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

**Business-Logik (KRITISCH — exakt nachbauen):**

1. **Cached Snap laden:** `snapCache.get(snapId)` → wenn nicht gefunden oder abgelaufen: HTTP 404 mit Message `"Snap \"${snapId}\" not found in cache. The appointment slot may have expired. Please select a new time slot."`

2. **Snap mit Buchungsdaten patchen** (`patchSnapWithBookingData`):
   ```
   snap.wishlist = snap.wishlist || {}
   
   snap.wishlist.client = {
     email, lastName, firstName, mobile,
     phone: dto.phone || dto.mobile,        // Fallback auf mobile
     address, zipCode, city,
     country: dto.country || "DE",           // Default
     language: dto.language || "de"           // Default
   }
   
   snap.wishlist.car = {
     ...existingCar,                         // Bestehende Car-Daten beibehalten
     brand: existingCar?.brand || "",
     license: dto.licensePlate,
     VIN: dto.vin || "UNKNOWN",              // Default
     currentMileage: dto.mileage,
     firstRegMonth: dto.firstRegMonth || "01",  // Default
     firstRegYear: dto.firstRegYear || "2020",  // Default
     model: dto.model || existingCar?.model || "Unknown"
   }
   
   if (dto.note) snap.wishlist.note = dto.note
   ```

3. **SoftNet API aufrufen:** `POST /api/planning/appointments` mit dem gepatchten Snap-Objekt (als JSON Body)

4. **Response mappen:**
   ```
   branchName = result.branchid?.branchLabel 
     || result.branch?.branchLabel 
     || result.branch?.name 
     || ""
   
   status = result.status || result.statusCode || "confirmed"
   date = checkInTime.split("T")[0]
   time = checkInTime.split("T")[1].substring(0, 5)
   services = []  (immer leer)
   customerName = dto.firstName + " " + dto.lastName
   licensePlate = dto.licensePlate
   ```

#### `GET /api/v1/appointments/{id}` — SessionTokenGuard

**Response:** Gleiche Struktur wie POST Response.

**Business-Logik:**
1. `GET /api/planning/appointments/{id}` (mit Group-Header)
2. Gleiches Mapping, aber `customerName` aus `result.userid` (Fallback)
3. `licensePlate` aus `result.carid?.license`

#### `PUT /api/v1/appointments/{id}/cancel` — SessionTokenGuard

**Response:** Gleiche Struktur, `status` wird auf `"cancelled"` ueberschrieben.

**Business-Logik:**
1. `PUT /api/planning/appointments/{id}/cancel` (mit Group-Header)
2. Gleiches Mapping
3. `status` explizit auf `"cancelled"` setzen (unabhaengig von SoftNet-Response)

---

## 6. SoftNet-API-Client (Declarative HTTP Interface)

### 6.1 Authentifizierung (JWT RS256)

**Flow:**
1. Fingerprint-Datei laden (`SOFTNET_FINGERPRINT_PATH` → JSON mit `client_email`, `client_id`, `private_key`, `private_key_id`)
2. JWT erstellen:
   ```
   Header: { alg: RS256, typ: JWT }
   Payload: {
     id: fingerprint.private_key_id,
     scope: "scope.api.planning.service",
     sub: fingerprint.client_email,
     iss: fingerprint.client_id,
     aud: SOFTNET_AUTH_URL,
     iat: now,
     exp: now + 300
   }
   ```
3. Mit RSA Private Key signieren (RS256)
4. POST an `SOFTNET_AUTH_URL`:
   - Content-Type: `application/x-www-form-urlencoded`
   - Body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJwt}`
5. Response: `{ access_token, token_type, expires_in }`
6. Token cachen, refreshen wenn Ablauf < 60 Sekunden

### 6.2 Declarative HTTP Interface (`@HttpExchange`)

**KEIN manueller WebClient/RestTemplate!** Stattdessen werden Spring 6 Declarative HTTP Interfaces verwendet.

#### Interface-Definition (zwei Interfaces wegen Group-Header-Unterschied):

```java
// SoftNet-Endpunkte OHNE x-nrg-group Header
@HttpExchange
public interface SoftnetPlanningClient {

    @GetExchange("/api/planning/groups")
    List<SoftnetGroup> getGroups();

    @GetExchange("/api/planning/groups/{groupId}")
    SoftnetGroup getGroup(@PathVariable String groupId);

    @GetExchange("/api/planning/branches")
    List<SoftnetBranch> getBranches();

    @GetExchange("/api/planning/branches/{branchId}")
    SoftnetBranch getBranch(@PathVariable String branchId);
}

// SoftNet-Endpunkte MIT x-nrg-group Header
@HttpExchange
public interface SoftnetGroupClient {

    @GetExchange("/api/planning/brands")
    List<SoftnetBrand> getBrands();

    @GetExchange("/api/planning/jobtypes")
    List<SoftnetJobType> getJobTypes();

    @GetExchange("/api/planning/operations")
    List<SoftnetOperation> getOperations();

    @GetExchange("/api/planning/operations/{operationId}")
    SoftnetOperation getOperation(@PathVariable String operationId);

    @PostExchange("/api/planning/snaps")
    List<JsonNode> getSnaps(@RequestBody SoftnetSnapRequest request);
    // JsonNode weil Raw-Snaps als generisches JSON gecacht werden muessen

    @GetExchange("/api/planning/mobilityalternatives")
    List<SoftnetMobilityAlternative> getMobilityAlternatives();

    @PostExchange("/api/planning/vaultitems")
    SoftnetVaultItem createVaultItem(@RequestBody Map<String, Object> customer);

    @GetExchange("/api/planning/vaultitems")
    List<SoftnetVaultItem> searchVaultItems(@RequestParam Map<String, String> query);

    @PostExchange("/api/planning/cars")
    SoftnetCar createCar(@RequestBody Map<String, Object> car);

    @PostExchange("/api/planning/appointments")
    SoftnetAppointment createAppointment(@RequestBody Map<String, Object> appointment);

    @GetExchange("/api/planning/appointments/{appointmentId}")
    SoftnetAppointment getAppointment(@PathVariable String appointmentId);

    @PutExchange("/api/planning/appointments/{appointmentId}/cancel")
    SoftnetAppointment cancelAppointment(@PathVariable String appointmentId);
}
```

#### Proxy-Factory-Konfiguration:

```java
@Configuration
public class SoftnetClientConfig {

    @Bean
    public SoftnetPlanningClient softnetPlanningClient(
            SoftnetAuthService authService,
            SoftnetProperties props) {
        
        RestClient restClient = RestClient.builder()
            .baseUrl(props.getBaseUrl())
            .requestInterceptor((req, body, exec) -> {
                req.getHeaders().setBearerAuth(authService.getAccessToken());
                return exec.execute(req, body);
            })
            .build();

        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(SoftnetPlanningClient.class);
    }

    @Bean
    public SoftnetGroupClient softnetGroupClient(
            SoftnetAuthService authService,
            SoftnetProperties props) {
        
        RestClient restClient = RestClient.builder()
            .baseUrl(props.getBaseUrl())
            .requestInterceptor((req, body, exec) -> {
                req.getHeaders().setBearerAuth(authService.getAccessToken());
                req.getHeaders().set("x-nrg-group", props.getGroupId());
                return exec.execute(req, body);
            })
            .build();

        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(SoftnetGroupClient.class);
    }
}
```

**Vorteile gegenueber manuellem WebClient:**
- Deklarativ, sauber testbar (Interface-Mocking)
- Header-Logik (Bearer Token + Group-Header) einmalig im RestClient-Interceptor
- Kein Boilerplate pro API-Call

### 6.3 Methoden-Uebersicht

| Interface | Methode | HTTP | Endpunkt | Group-Header |
|-----------|---------|------|----------|:---:|
| `SoftnetPlanningClient` | `getGroups()` | GET | `/api/planning/groups` | nein |
| `SoftnetPlanningClient` | `getGroup(id)` | GET | `/api/planning/groups/{id}` | nein |
| `SoftnetPlanningClient` | `getBranches()` | GET | `/api/planning/branches` | nein |
| `SoftnetPlanningClient` | `getBranch(id)` | GET | `/api/planning/branches/{id}` | nein |
| `SoftnetGroupClient` | `getBrands()` | GET | `/api/planning/brands` | ja |
| `SoftnetGroupClient` | `getJobTypes()` | GET | `/api/planning/jobtypes` | ja |
| `SoftnetGroupClient` | `getOperations()` | GET | `/api/planning/operations` | ja |
| `SoftnetGroupClient` | `getOperation(id)` | GET | `/api/planning/operations/{id}` | ja |
| `SoftnetGroupClient` | `getSnaps(req)` | POST | `/api/planning/snaps` | ja |
| `SoftnetGroupClient` | `getMobilityAlternatives()` | GET | `/api/planning/mobilityalternatives` | ja |
| `SoftnetGroupClient` | `createVaultItem(data)` | POST | `/api/planning/vaultitems` | ja |
| `SoftnetGroupClient` | `searchVaultItems(query)` | GET | `/api/planning/vaultitems?${params}` | ja |
| `SoftnetGroupClient` | `createCar(data)` | POST | `/api/planning/cars` | ja |
| `SoftnetGroupClient` | `createAppointment(data)` | POST | `/api/planning/appointments` | ja |
| `SoftnetGroupClient` | `getAppointment(id)` | GET | `/api/planning/appointments/{id}` | ja |
| `SoftnetGroupClient` | `cancelAppointment(id)` | PUT | `/api/planning/appointments/{id}/cancel` | ja |
| — | `ping()` | — | intern: `getGroups()` | nein |

---

## 7. Security & Middleware (Spring Security FilterChain)

### 7.1 SessionTokenFilter

**Pruefung:** Header `x-session-token`
**Ausnahmen:** `/api/v1/session/token`, `/api/v1/health`, statische Dateien
**Fehler:** HTTP 403 mit `{ messageKey: "error.invalidSessionToken", message: "Session token is missing|invalid" }`

### 7.2 OriginFilter

**Pruefung:** `Origin` oder `Referer` Header gegen `CORS_ALLOWED_ORIGINS`
**Ausnahmen:** `/api/v1/health`, statische Dateien, localhost
**Fehler:** HTTP 403 mit `{ messageKey: "error.unauthorizedOrigin" }`

### 7.3 RateLimitFilter

**Limit:** 100 Requests / 15 Minuten pro IP
**Fehler:** HTTP 429

### 7.4 CORS-Konfiguration

```
allowedOrigins: CORS_ALLOWED_ORIGINS (comma-separated)
allowedMethods: GET, POST, PUT, DELETE
allowedHeaders: Content-Type, X-Session-Token, X-Correlation-Id, X-Session-Id
credentials: false
```

### 7.5 Security-Header (Helmet-Aequivalent)

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), microphone=(), camera=()
Content-Security-Policy:
  default-src 'self';
  script-src 'self';
  style-src 'self' 'unsafe-inline' https://fonts.googleapis.com;
  font-src 'self' https://fonts.gstatic.com;
  img-src 'self' data: https:;
  script-src-attr 'unsafe-inline'
```

---

## 8. Error-Handling

### 8.1 SoftNet-Fehler → HTTP-Status-Mapping

| SoftNet-Fehler | Backend-Status | messageKey |
|----------------|---------------|------------|
| Timeout (kein Response) | 504 Gateway Timeout | `error.upstreamTimeout` |
| 401/403 | 503 Service Unavailable | `error.serviceUnavailable` |
| 4xx | 400 Bad Request | `error.validationFailed` |
| 5xx | 502 Bad Gateway | `error.upstreamError` |

### 8.2 Allgemeine Fehler → messageKey-Mapping

| HTTP-Status | messageKey |
|-------------|------------|
| 400 | `error.validationFailed` |
| 403 | `error.invalidSessionToken` |
| 404 | `error.notFound` |
| 408 | `error.requestTimeout` |
| 429 | `error.rateLimitExceeded` |
| 502 | `error.upstreamError` |
| 503 | `error.serviceUnavailable` |
| 504 | `error.upstreamTimeout` |
| sonstige | `error.internalError` |

### 8.3 Error-Response-Format

```json
{
  "statusCode": 502,
  "messageKey": "error.upstreamError",
  "message": "SoftNet API returned an error",
  "meta": {
    "timestamp": "2026-04-14T10:40:00.000Z",
    "requestId": "uuid-v4"
  }
}
```

---

## 9. Frontend-to-SoftNet Operation-ID Mapping

Diese hardcodierte Mapping-Tabelle muss 1:1 uebernommen werden:

| Frontend-ID | SoftNet Operation-ID | Service |
|---|---|---|
| `inspection` | `698b34c24dd8eea4d6d4219e` | Fahrzeug-Check |
| `oil-change` | `698b34c24dd8eea4d6d4219a` | Motoroel |
| `brake-check` | `698b34c24dd8eea4d6d4219b` | Bremse |
| `wiper-blades` | `698b34c24dd8eea4d6d4219c` | Wischerblaetter |
| `tuv` | `698b34c24dd8eea4d6d42199` | HU/AU |
| `tire-change` | `698b34c24dd8eea4d6d4219f` | Raederwechsel |
| `actions-checks` | `698b34c24dd8eea4d6d42197` | Aktionen |
| `repair` | `698b34c24dd8eea4d6d421a0` | Fehlermeldung |
| `brake-fluid` | `698b34c24dd8eea4d6d4219b` | Bremse |
| `bodywork` | `698b34c24dd8eea4d6d4219d` | Sonstige |
| `dialog-acceptance` | `698b34c24dd8eea4d6d4219e` | Fahrzeug-Check |
| `maintenance-contract` | `698b34c24dd8eea4d6d4219e` | Fahrzeug-Check |
| `emergency-oil` | `698b34c24dd8eea4d6d4219a` | Motoroel |
| `uvv-inspection` | `698b34c24dd8eea4d6d42199` | HU/AU |
| `ac-cleaning` | `698b34c24dd8eea4d6d4219d` | Sonstige |
| `bring-own-tires` | `698b34c24dd8eea4d6d4219f` | Raederwechsel |
| `stored-tires` | `698b34c24dd8eea4d6d4219f` | Raederwechsel |
| `pickup-delivery` | `698b34c24dd8eea4d6d4219f` | Raederwechsel |
| `store-tires` | `698b34c24dd8eea4d6d4219f` | Raederwechsel |
| `warranty-check` | `698b34c24dd8eea4d6d42198` | Saison-Check |
| `safety-check` | `698b34c24dd8eea4d6d4219e` | Fahrzeug-Check |
| `fluid-check` | `698b34c24dd8eea4d6d4219a` | Motoroel |
| `battery-check` | `698b34c24dd8eea4d6d4219e` | Fahrzeug-Check |
| `diagnostics` | `698b34c24dd8eea4d6d421a0` | Fehlermeldung |
| `noises` | `698b34c24dd8eea4d6d421a0` | Fehlermeldung |
| `ac-malfunction` | `698b34c24dd8eea4d6d421a0` | Fehlermeldung |
| `cost-estimate` | `698b34c24dd8eea4d6d4219d` | Sonstige |
| `windshield-replacement` | `698b34c24dd8eea4d6d4219d` | Sonstige |

**Mapping-Logik:**
- IDs die bereits 24 Hex-Zeichen sind → direkt durchreichen
- Unbekannte IDs → Warnung loggen, nicht in Anfrage aufnehmen
- Deduplizierung via `Set<String>`

---

## 10. SoftNet-API Typen (Java-Aequivalente)

### Authentifizierung

```java
// Fingerprint (aus JSON-Datei)
record SoftnetFingerprint(
    String client_email,
    String client_id,
    String private_key,      // RSA Private Key (PEM)
    String private_key_id,
    String token_uri
) {}

// Auth Response
record SoftnetAuthResponse(
    String access_token,
    String token_type,
    int expires_in
) {}
```

### Domain-Typen

```java
record SoftnetGroup(
    String id,
    String cryptid,
    String name,
    String description,
    List<String> brand,
    boolean allowOtherBrands,
    List<Object> branches,        // String ODER SoftnetGroupBranch
    boolean acceptPassingCustomer,
    int apptMaxDaysInFuture,
    int apptMaxCount
) {}

record SoftnetGroupBranch(String id, String name, String branchLabel) {}

record SoftnetBranch(
    String id, String cryptid, String name,
    String label, String company, String zip
) {}

record SoftnetBrand(String id, String name) {}
record SoftnetJobType(String id, String name) {}
record SoftnetOperation(String id, String name) {}
record SoftnetMobilityAlternative(String id, String name) {}

record SoftnetVaultItem(
    String id, String cryptid, String email,
    String firstName, String lastName, String salutation,
    String street, String zip, String city, String mobilePhone
) {}

record SoftnetCar(
    String id, String cryptid,
    String licensePlate, Integer mileage, String vin
) {}
```

### Snap-Typen

```java
// Snap Request (POST Body an SoftNet)
record SoftnetSnapRequest(
    String groupid,
    String brand,
    Map<String, String> car,    // { "brand": "BMW" }
    List<String> operations,
    String preferredBranch,
    String checkInDatePreference,  // "asSoonAsPossible" | "date"
    String checkInDate             // nur bei mode=date-range
) {}

// Raw Snap Response (wird gecacht)
// Flexibles JSON-Objekt mit mindestens:
//   ranking, score, checkInTime, checkOutTime, daySpan,
//   totalDurationTime, checkInResource, internal, branch, wishlist
// MUSS als Map<String, Object> oder JsonNode deserialisiert werden
// da das vollstaendige Objekt spaeter an createAppointment zurueckgesendet wird

// Appointment Response
record SoftnetAppointment(
    String id, String cryptid, String status, String statusCode,
    String checkInTime, String checkOutTime,
    SoftnetAppointmentBranch branchid,
    SoftnetAppointmentUser userid,
    SoftnetAppointmentCar carid,
    SoftnetSnapBranch branch  // deprecated Fallback
) {}

record SoftnetAppointmentBranch(String id, String branchLabel) {}
record SoftnetAppointmentUser(String id, String email, String firstName, String lastName) {}
record SoftnetAppointmentCar(String id, String brand, String model, String license) {}
record SoftnetSnapBranch(String _id, String id, String databaseid, String name, String branchLabel) {}
```

**WICHTIG:** Raw-Snap-Objekte muessen als generische JSON-Struktur (`Map<String, Object>` oder Jackson `JsonNode`) gecacht werden, NICHT als typisiertes Record. Das gesamte Objekt wird spaeter mit Customer/Vehicle-Daten gepatcht und an `POST /api/planning/appointments` gesendet.

---

## 10b. SoftNet-API Erkenntnisse (getestet + bewiesen)

> **Quelle:** `test-booking-flow copy.mjs` — Feature-Discovery-Tests gegen das INT-System (`scope.api.planning.service`).
> Diese Erkenntnisse sind verifiziert und MUESSEN im Spring Boot Backend beruecksichtigt werden.

### Snap Time Filter (`checkInTimePreference`)

| Parameter | Wert | Effekt |
|-----------|------|--------|
| `checkInTimePreference` | `"AM"` | Verschiebt Snap-Verteilung Richtung Vormittag (<12 Uhr) |
| `checkInTimePreference` | `"PM"` | Verschiebt Snap-Verteilung Richtung Nachmittag (>=12 Uhr) |
| `checkInTimePreference` | `"any"` | Keine Praeferenz (Default) |
| `checkInDatePreference` | `"morning"` / `"afternoon"` | **KEIN Effekt** — nicht verwenden! |

**Implementierung im Backend:**
1. Client sendet `"AM"` oder `"PM"` → Backend setzt `checkInTimePreference` im SoftNet Snap-Request
2. Zusaetzlich Backend-seitig nach `checkInTime` filtern (`<12` = VM, `>=12` = NM) fuer exakte Trennung

### Mobility / Ersatzwagen (`replacementPreference`)

| Parameter | Wert | Effekt |
|-----------|------|--------|
| `replacementPreference` | `true` | Leichter Effekt auf Snap-Verteilung; `mobilityAlternativeResource` wird befuellt wenn Branch Ersatzwagen hat |
| `replacementPreference` | `false` / nicht gesetzt | Kein Ersatzwagen |

**Implementierung im Backend:**
- `replacementPreference` im Snap-Request an SoftNet durchreichen
- `mobilityAlternativeResource` aus Snap-Response auswerten
- **Achtung:** Branch-abhaengig — z.B. Muenchen hat keine Ersatzwagen (`mobilityAlternativeResource` bleibt `null`)

### Wiederkehr-Kunde (Email-basierter Lookup)

**SoftNet erkennt Bestandskunden per Email — keine eigene DB noetig!**

| Szenario | Gesendet | Ergebnis |
|----------|----------|----------|
| Erstbuchung | Volle Client-Daten + Email | User + Car in SoftNet angelegt |
| Zweitbuchung | NUR `{ email }` + `{ VIN, license }` | SoftNet liefert vollen User zurueck: `firstName`, `lastName`, `email`, `brand`, `model`, `license` |
| Ohne Email | Nur Kennzeichen | **Schlaegt fehl** — Email ist Pflichtfeld |

**Booking-Flow fuer Wiederkehr-Kunden:**
1. Kunde gibt Email ein
2. Backend sendet Booking mit `wishlist.client = { email }` + `wishlist.car = { VIN, license }`
3. SoftNet liefert vollen User + Car in Appointment-Response
4. Keine eigene Kundendatenbank noetig

### wishlist.note (Freitext)

- Max. 2000 Zeichen
- Wird von SoftNet gespeichert und in der Response zurueckgegeben
- Nutzung: Kundenhinweise, Sonderwuensche

### Bekannte Einschraenkungen (INT-System)

| Endpunkt / Feature | Status | Bemerkung |
|---------------------|--------|-----------|
| `GET /users/{id}` | 401 | Scope `scope.api.planning.user` fehlt — mit Prod-System testen |
| `GET /api/planning/vaultitems` | 401 | Scope reicht nicht — mit Prod-System testen |
| Appointment-Response | Unvollstaendig | Liefert NICHT: `address`, `zipCode`, `city`, `phone`, `mobile`, `country` |

### SoftNet Snap-Request Referenz (komplett)

```json
{
  "groupid": "GROUP_ID",
  "brand": "bmw",
  "car": { "brand": "bmw" },
  "operations": ["698b34c24dd8eea4d6d4219e"],
  "preferredBranch": "BRANCH_ID",
  "checkInDatePreference": "asSoonAsPossible | date",
  "checkInDate": "2026-04-15",
  "checkInTimePreference": "any | AM | PM",
  "replacementPreference": true
}
```

### SoftNet Appointment-Request Referenz (Snap-Patching)

```json
{
  "...snap-felder...": "...",
  "wishlist": {
    "client": {
      "email": "max@example.de",
      "lastName": "Mustermann",
      "firstName": "Max",
      "mobile": "+491771234567",
      "phone": "+491771234567",
      "address": "Musterweg 1",
      "zipCode": "80333",
      "city": "Muenchen",
      "country": "DE",
      "language": "de"
    },
    "car": {
      "brand": "bmw",
      "license": "M-RK-2026",
      "VIN": "WBA11111111111111",
      "currentMileage": 45000,
      "firstRegMonth": "03",
      "firstRegYear": "2020",
      "model": "330e"
    },
    "note": "Kundenwunsch: Fruehtermin bevorzugt"
  }
}
```

---

## 11. Environment-Variablen

| Variable | Typ | Default | Beschreibung |
|----------|-----|---------|-------------|
| `PORT` | int | 3000 | Server-Port |
| `SOFTNET_BASE_URL` | String | — | SoftNet API Basis-URL |
| `SOFTNET_AUTH_URL` | String | — | SoftNet OAuth Token-Endpunkt |
| `SOFTNET_FINGERPRINT_PATH` | String | — | Pfad zur Fingerprint-JSON |
| `SOFTNET_TIMEOUT` | int | 10000 | SoftNet HTTP-Timeout (ms) |
| `GROUP_ID` | String | — | SoftNet Gruppen-ID |
| `BRANCH_ID` | String | — | Default Branch-ID |
| `SESSION_SECRET` | String | — | HMAC-Secret (min. 32 Zeichen) |
| `SESSION_TTL_MINUTES` | int | 20 | Session-Token-Lebensdauer |
| `CORS_ALLOWED_ORIGINS` | String | — | Erlaubte Origins (kommasepariert) |
| `TLS_ENABLED` | boolean | false | HTTPS aktivieren |
| `TLS_CERT_PATH` | String | — | TLS-Zertifikat-Pfad |
| `TLS_KEY_PATH` | String | — | TLS-Key-Pfad |

---

## 12. Static-File-Serving & CSP-Nonce (Full-Stack-Serving)

Spring Boot bedient das Angular-Frontend direkt — kein separater Webserver noetig.

### Angular-Build-Artefakte

```
Verzeichnis: dist/angular-21-project/browser/
Index:        dist/angular-21-project/browser/index.html
```

**Pfad-Aufloesung** (`WebConfig`-Konstruktor):
1. Relativ: `server/../dist/angular-21-project/browser/` (wenn aus `server/` gestartet)
2. Fallback: `./dist/angular-21-project/browser/` (Projekt-Root)
3. Wenn dist/ nicht existiert: Warning-Log, nur API aktiv

### Request-Routing

| Request | Handler | Ergebnis |
|---------|---------|----------|
| `/` (Root) | `addViewControllers` → forward `/index.html` | Angular SPA |
| `/home`, `/home/brand`, etc. | `SpaResourceResolver` Fallback | `index.html` mit CSP-Nonce |
| `/*.js`, `/*.css`, `/assets/*` | Spring `ResourceHandler` | Statische Dateien direkt |
| `/api/*` | REST Controller | API-Response (JSON) |

**Implementierung:**
- `addViewControllers("/")` → forward zu `/index.html` (loest `NoResourceFoundException` fuer Root)
- `addResourceHandlers("/**")` → `file:${distPath}/` mit `SpaResourceResolver`
- `SpaResourceResolver` → bekannte Datei? Direkt ausliefern. Unbekannter Pfad (kein `/api/`)? → `index.html` mit CSP-Nonce (SPA Fallback)

### CSP-Nonce-Injection

Fuer jeden Request der `index.html` ausliefert:
1. 16 Bytes Zufall generieren → Base64
2. In `index.html`: `<app-root>` ersetzen durch `<app-root ngCspNonce="${nonce}">`
3. CSP-Header mit Nonce ergaenzen

### Voraussetzung

Angular muss **vorher gebaut** werden (`npm run build`), damit `dist/` existiert. Im Development-Workflow:
```bash
npm run build                    # Angular bauen
cd server && ./gradlew bootRun   # Full-Stack auf :3000
```

---

## 13. Projekt-Struktur (Vorschlag)

```
server/
├── build.gradle
├── settings.gradle
├── src/main/java/com/autohaus/backend/
│   ├── BackendApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java          # Spring Security FilterChain
│   │   ├── WebConfig.java               # CORS, Static Files, CSP
│   │   ├── SoftnetProperties.java       # @ConfigurationProperties
│   │   └── AppProperties.java
│   ├── softnet/
│   │   ├── SoftnetPlanningClient.java   # @HttpExchange Interface (ohne Group-Header)
│   │   ├── SoftnetGroupClient.java      # @HttpExchange Interface (mit Group-Header)
│   │   ├── SoftnetClientConfig.java     # RestClient + HttpServiceProxyFactory Beans
│   │   ├── SoftnetAuthService.java      # JWT-Signierung + Token-Cache
│   │   └── dto/                         # SoftNet-Typen (Records)
│   ├── session/
│   │   ├── SessionController.java
│   │   └── SessionTokenService.java
│   ├── health/
│   │   ├── HealthController.java
│   │   └── HealthService.java
│   ├── branches/
│   │   ├── BranchesController.java
│   │   ├── BranchesService.java
│   │   └── dto/BranchResponseDto.java
│   ├── brands/
│   │   ├── BrandsController.java
│   │   ├── BrandsService.java
│   │   └── dto/BrandResponseDto.java
│   ├── jobtypes/
│   │   ├── JobTypesController.java
│   │   ├── JobTypesService.java
│   │   └── dto/
│   ├── snaps/
│   │   ├── SnapsController.java
│   │   ├── SnapsService.java
│   │   ├── SnapCache.java               # In-Memory Cache (30 Min TTL)
│   │   ├── OperationIdMapper.java       # Hardcoded Mapping-Tabelle
│   │   └── dto/
│   ├── appointments/
│   │   ├── AppointmentsController.java
│   │   ├── AppointmentsService.java
│   │   ├── SnapPatcher.java             # Snap-Patching-Logik
│   │   └── dto/
│   ├── customers/
│   │   ├── CustomersController.java
│   │   ├── CustomersService.java
│   │   └── dto/
│   ├── cars/
│   │   ├── CarsController.java
│   │   ├── CarsService.java
│   │   └── dto/
│   ├── mobility/
│   │   ├── MobilityController.java
│   │   ├── MobilityService.java
│   │   └── dto/
│   └── common/
│       ├── filter/
│       │   ├── RequestIdFilter.java
│       │   ├── LoggingFilter.java
│       │   ├── SessionTokenFilter.java
│       │   ├── OriginFilter.java
│       │   └── RateLimitFilter.java
│       ├── interceptor/
│       │   └── ResponseWrapperAdvice.java  # @ControllerAdvice
│       ├── exception/
│       │   ├── GlobalExceptionHandler.java # @ControllerAdvice
│       │   └── SoftnetExceptionHandler.java
│       └── dto/
│           ├── ApiResponse.java
│           └── ErrorResponse.java
├── src/main/resources/
│   ├── application.yml
│   └── logback-spring.xml               # JSON Structured Logging
└── src/test/java/
    └── (Mirror-Struktur mit Tests)
```

---

## 14. Gradle Build (inkl. Angular Frontend)

### 14.1 Dependencies

```groovy
// build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0-SNAPSHOT'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'jacoco'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'com.nimbusds:nimbus-jose-jwt:10.3'                     // JWT RS256
    implementation 'com.bucket4j:bucket4j_jdk17-core:8.14.0'              // Rate Limiting
    implementation 'net.logstash.logback:logstash-logback-encoder:8.0'    // JSON Logging
    implementation 'com.fasterxml.jackson.core:jackson-databind'

    developmentOnly 'org.springframework.boot:spring-boot-devtools'        // Hot-Reload

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

### 14.2 Integrierter Angular Build

Der Gradle Build baut **automatisch** das Angular Frontend, bevor Spring Boot startet. Ein einziger Befehl fuer den Full-Stack:

```bash
cd server && ./gradlew bootRun
# → npmInstall (falls node_modules fehlt) → buildAngular → compileJava → bootRun
```

#### Gradle-Tasks

```groovy
def angularDir = file("${rootProject.projectDir}/..")
def angularDist = file("${angularDir}/dist/angular-21-project/browser")

// npm install — nur wenn node_modules/ nicht existiert
tasks.register('npmInstall', Exec) {
    workingDir = angularDir
    commandLine 'npm', 'install', '--legacy-peer-deps'
    inputs.file("${angularDir}/package-lock.json")
    outputs.dir("${angularDir}/node_modules")
    onlyIf { !file("${angularDir}/node_modules").exists() }
}

// Angular Build — mit Gradle Up-to-Date-Check
tasks.register('buildAngular', Exec) {
    dependsOn 'npmInstall'
    workingDir = angularDir
    commandLine 'npm', 'run', 'build'
    inputs.dir("${angularDir}/src")
    inputs.file("${angularDir}/angular.json")
    inputs.file("${angularDir}/tsconfig.json")
    inputs.file("${angularDir}/tsconfig.app.json")
    outputs.dir(angularDist)
}

// bootRun und bootJar haengen vom Angular Build ab
tasks.named('bootRun') { dependsOn 'buildAngular' }
tasks.named('bootJar') { dependsOn 'buildAngular' }
```

#### Up-to-Date-Verhalten

| Szenario | Ergebnis |
|----------|----------|
| `src/` unveraendert | `> Task :buildAngular UP-TO-DATE` — kein Rebuild |
| `src/` geaendert | Angular wird neu gebaut (~10-15s) |
| `node_modules/` fehlt | `npmInstall` laeuft automatisch, dann Build |
| `node_modules/` vorhanden | `npmInstall` wird uebersprungen (`onlyIf`) |

#### Alle Gradle-Befehle

```bash
cd server
./gradlew bootRun           # Full-Stack: Angular Build + Spring Boot (:3000)
./gradlew bootJar           # Fat JAR (inkl. Angular dist/) fuer Deployment
./gradlew buildAngular      # Nur Angular bauen (ohne Backend)
./gradlew test              # JUnit Tests + JaCoCo Report
./gradlew jacocoTestReport  # Coverage Report (HTML + XML)
./gradlew clean bootRun     # Clean Build + Start
```

### 14.3 Spring Boot DevTools (Hot-Reload)

`spring-boot-devtools` ist als `developmentOnly`-Dependency eingebunden und ermoeglicht automatischen Server-Restart bei Java-Aenderungen.

**Konfiguration** (`application.yml`):
```yaml
spring:
  devtools:
    restart:
      enabled: true
      additional-exclude: "static/**,dist/**"
```

- **Restart-Trigger:** Aenderungen an `.class`-Dateien im Classpath → automatischer Tomcat-Neustart (~1-2s)
- **Exclude:** `static/` und `dist/` sind ausgeschlossen — Angular-Rebuilds triggern KEINEN Backend-Restart
- **Production:** DevTools wird automatisch deaktiviert bei `java -jar` (nicht im Fat JAR enthalten dank `developmentOnly`)

### 14.4 Development-Workflow

#### Nur Backend entwickeln (Java-Aenderungen)
```bash
cd server && ./gradlew bootRun
# DevTools erkennt Java-Aenderungen → automatischer Restart (~1-2s)
```

#### Frontend + Backend parallel entwickeln
```bash
# Terminal 1: Spring Boot Backend (API + initiales Frontend)
cd server && ./gradlew bootRun

# Terminal 2: Angular Watch (rebuilds dist/ bei Frontend-Aenderungen)
npm run watch
# Browser-Refresh zeigt Aenderungen sofort (dist/ wird von Spring Boot ausgeliefert)
```

#### Einmaliger Full-Stack-Start (z.B. fuer E2E oder Demo)
```bash
cd server && ./gradlew bootRun
# Baut Angular automatisch, startet Spring Boot auf :3000
# → http://localhost:3000 zeigt die komplette App
```

---

## 15. Test-Strategie

### Unit-Tests (JUnit 5 + Mockito)
- Jeder Service: Business-Logik mit gemocktem SoftnetApiClient
- Jeder Controller: MockMvc-Tests mit Request/Response-Validierung
- SessionTokenService: Token-Generierung und -Validierung
- SnapCache: TTL-Verhalten, Expiration
- OperationIdMapper: Mapping-Korrektheit

### Integrations-Tests
- `@SpringBootTest` mit MockServer fuer SoftNet-API
- Kompletter Request-Lifecycle testen

### E2E-Gate (PRIMAERES AKZEPTANZKRITERIUM)
```bash
# Ein Befehl: Angular bauen + Spring Boot starten + E2E
cd server && ./gradlew bootRun &   # Full-Stack auf :3000 (baut Angular automatisch)
npm run e2e                          # Playwright gegen :3000
```
Alle Playwright-Tests muessen bestehen.

---

## 16. Migration-Schritte (Reihenfolge)

| Step | Beschreibung |
|------|-------------|
| 1 | Gradle-Projekt aufsetzen (`server/`) |
| 2 | `application.yml` + `@ConfigurationProperties` |
| 3 | `SoftnetAuthService` (JWT RS256 + Token-Cache) |
| 4 | `SoftnetPlanningClient` + `SoftnetGroupClient` (`@HttpExchange` Interfaces, RestClient-Config) |
| 5 | Security-Filter: RequestId, Logging, SessionToken, Origin, RateLimit |
| 6 | `ResponseWrapperAdvice` + `GlobalExceptionHandler` |
| 7 | Controller/Services: Session → Health → Branches → Brands → JobTypes |
| 8 | Controller/Services: Snaps (inkl. Cache + OperationIdMapper) |
| 9 | Controller/Services: Appointments (inkl. SnapPatcher) |
| 10 | Controller/Services: Customers → Cars → Mobility |
| 11 | Static-File-Serving + CSP-Nonce + SPA ViewController (`/` → `index.html`) |
| 11a | **Integrierter Angular Build in Gradle:** `npmInstall` + `buildAngular` Tasks, `bootRun`/`bootJar` abhaengig |
| 12 | Unit-Tests (>80% Coverage) |
| 13 | **Skills + Doku (AC-N4 bis AC-N8):** `spring-backend.md` Skill erstellen, `security.md`/`projekt-setup.md`/`angular-architecture.md` aktualisieren, Spring MCP einbinden |
| 14 | **NestJS-Bereinigung (AC-N9):** `backend/`-Verzeichnis entfernen, alle NestJS-Verweise in Doku/Config/Skills durch Spring-Aequivalente ersetzen (96 Dateien betroffen, davon ~70 in `backend/`) |
| 15 | E2E-Tests (`npm run e2e`) → **MUSS bestehen** |

---

## 17. Approval

| Role | Name | Date |
|------|------|------|
| Product Owner | | |
| Tech Lead | | |
