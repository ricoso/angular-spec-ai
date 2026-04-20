# REQ-018: Spring Boot Migration — Feature-Dokumentation

## Zusammenfassung

Das bestehende NestJS-Backend (`backend/`) wurde vollständig durch ein Spring Boot Backend (`server/`) ersetzt. Der Technologie-Stack umfasst **Java 25 + Spring Boot 4.1 + Gradle (Groovy DSL) + Spring Security**. Die REST-API-Schnittstelle bleibt 1:1 identisch, sodass das Angular-Frontend und die Playwright-E2E-Tests ohne Änderungen funktionieren.

## Architektur

```
Browser → Angular App (Static Files)
              ↓
         Spring Boot (:3000)
              ↓
         SoftNet Planning API (extern)
```

### Request-Lifecycle

1. **RequestIdFilter** — X-Request-Id Header generieren/durchreichen
2. **LoggingFilter** — Request/Response loggen (JSON structured)
3. **RateLimitFilter** — 100 Requests / 15 Min pro IP (Bucket4j)
4. **OriginFilter** — CORS_ALLOWED_ORIGINS prüfen
5. **SessionTokenFilter** — x-session-token validieren (HMAC-SHA256)
6. **Jakarta Validation** — @Valid auf Request DTOs
7. **Controller → Service → SoftnetApiClient**
8. **ResponseWrapperAdvice** — `{ data: T, meta: { timestamp, requestId } }`
9. **GlobalExceptionHandler** — Einheitliches Error-Format

## REST-API Endpunkte

| Methode | Pfad | Beschreibung | Auth |
|---------|------|--------------|------|
| GET | /api/v1/session/token | Session Token generieren | Nein |
| GET | /api/v1/health | Health Check | Nein |
| GET | /api/v1/branches | Filialen auflisten | Ja |
| GET | /api/v1/branches/{id} | Filiale Details | Ja |
| GET | /api/v1/branches/{id}/brands | Marken pro Filiale | Ja |
| GET | /api/v1/branches/{id}/jobtypes | Auftragsarten | Ja |
| POST | /api/v1/snaps | Terminslots suchen | Ja |
| GET | /api/v1/branches/{id}/mobility-alternatives | Mobilitätsalternativen | Ja |
| POST | /api/v1/customers | Kunde anlegen | Ja |
| GET | /api/v1/customers/search | Kunde suchen | Ja |
| POST | /api/v1/cars | Fahrzeug anlegen | Ja |
| POST | /api/v1/appointments | Termin buchen | Ja |
| GET | /api/v1/appointments/{id} | Termin Details | Ja |
| PUT | /api/v1/appointments/{id}/cancel | Termin stornieren | Ja |

## Technologie-Stack

| Komponente | Technologie |
|-----------|-------------|
| Runtime | Java 25 (JDK) |
| Framework | Spring Boot 4.1 |
| Build | Gradle 9.4 (Groovy DSL) |
| HTTP Client | RestClient (Spring 6) |
| Validation | Jakarta Bean Validation |
| Security | Spring Security FilterChain |
| Rate Limiting | Bucket4j |
| JWT Signing | nimbus-jose-jwt (RS256) |
| Logging | SLF4J + Logback (JSON) |
| Testing | JUnit 5 + Mockito + JaCoCo |

## Konfiguration

Alle Konfiguration erfolgt über `application.yml` + Umgebungsvariablen:

| Variable | Beschreibung |
|----------|-------------|
| PORT | Server-Port (default: 3000) |
| SOFTNET_BASE_URL | SoftNet API Basis-URL |
| SOFTNET_AUTH_URL | SoftNet OAuth Token-Endpunkt |
| SOFTNET_FINGERPRINT_PATH | Pfad zur Fingerprint-JSON |
| GROUP_ID | SoftNet Gruppen-ID |
| SESSION_SECRET | HMAC-Secret (min. 32 Zeichen) |
| CORS_ALLOWED_ORIGINS | Erlaubte Origins (kommasepariert) |

## Commands

```bash
cd server && ./gradlew bootRun          # Server starten (:3000)
cd server && ./gradlew test             # Tests ausführen
cd server && ./gradlew jacocoTestReport # Coverage Report
npm run e2e:backend                     # E2E Tests mit Backend
```
