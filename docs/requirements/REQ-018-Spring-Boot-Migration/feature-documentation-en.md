# REQ-018: Spring Boot Migration — Feature Documentation

## Summary

The existing NestJS backend (`backend/`) has been fully replaced by a Spring Boot backend (`server/`). Technology stack: **Java 25 + Spring Boot 4.1 + Gradle (Groovy DSL) + Spring Security**. The REST API interface remains 1:1 identical, ensuring the Angular frontend and Playwright E2E tests work without any changes.

## Architecture

```
Browser → Angular App (Static Files)
              ↓
         Spring Boot (:3000)
              ↓
         SoftNet Planning API (external)
```

### Request Lifecycle

1. **RequestIdFilter** — Generate/pass through X-Request-Id header
2. **LoggingFilter** — Log request/response (JSON structured)
3. **RateLimitFilter** — 100 requests / 15 min per IP (Bucket4j)
4. **OriginFilter** — Validate CORS_ALLOWED_ORIGINS
5. **SessionTokenFilter** — Validate x-session-token (HMAC-SHA256)
6. **Jakarta Validation** — @Valid on request DTOs
7. **Controller → Service → SoftnetApiClient**
8. **ResponseWrapperAdvice** — `{ data: T, meta: { timestamp, requestId } }`
9. **GlobalExceptionHandler** — Unified error format

## REST API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | /api/v1/session/token | Generate session token | No |
| GET | /api/v1/health | Health check | No |
| GET | /api/v1/branches | List branches | Yes |
| GET | /api/v1/branches/{id} | Branch details | Yes |
| GET | /api/v1/branches/{id}/brands | Brands per branch | Yes |
| GET | /api/v1/branches/{id}/jobtypes | Job types | Yes |
| POST | /api/v1/snaps | Search appointment slots | Yes |
| GET | /api/v1/branches/{id}/mobility-alternatives | Mobility alternatives | Yes |
| POST | /api/v1/customers | Create customer | Yes |
| GET | /api/v1/customers/search | Search customers | Yes |
| POST | /api/v1/cars | Create car | Yes |
| POST | /api/v1/appointments | Book appointment | Yes |
| GET | /api/v1/appointments/{id} | Appointment details | Yes |
| PUT | /api/v1/appointments/{id}/cancel | Cancel appointment | Yes |

## Technology Stack

| Component | Technology |
|-----------|-----------|
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

## Configuration

All configuration via `application.yml` + environment variables:

| Variable | Description |
|----------|------------|
| PORT | Server port (default: 3000) |
| SOFTNET_BASE_URL | SoftNet API base URL |
| SOFTNET_AUTH_URL | SoftNet OAuth token endpoint |
| SOFTNET_FINGERPRINT_PATH | Path to fingerprint JSON file |
| GROUP_ID | SoftNet group ID |
| SESSION_SECRET | HMAC secret (min. 32 chars) |
| CORS_ALLOWED_ORIGINS | Allowed origins (comma-separated) |

## Commands

```bash
cd server && ./gradlew bootRun          # Start server (:3000)
cd server && ./gradlew test             # Run tests
cd server && ./gradlew jacocoTestReport # Coverage report
npm run e2e:backend                     # E2E tests with backend
```
