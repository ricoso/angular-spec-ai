# Projekt Setup

**Trigger:** Neues Projekt / Setup noch nicht ausgeführt

## Schritt 1: User fragen

Stelle diese 3 Fragen (AskUserQuestion):

**Q1 — Deployment:** Click-Dummy oder Production?
**Q2 — Code-Sprache:** Deutsch oder Englisch? _(projektfixiert, REQ-Sprache irrelevant)_
**Q3 — UI-Sprachen:** Welche? (Mehrfachauswahl: `de`, `en`, `fr`, `es` — min. 2)

## Schritt 2: Vorgaben in Skills schreiben

Ersetze `<!-- SETUP:VORGABE -->` in jedem Skill mit dem passenden Block.

### 2a. `routing-patterns.md`

**Click-Dummy →**
```
- Deployment: Click-Dummy (GitHub Pages)
- HashLocation: AKTIV (`withHashLocation()`)
- URL: `https://<user>.github.io/<repo>/#/<route>`
- CI: `.github/workflows/deploy-gh-pages.yml`
```

**Production →**
```
- Deployment: Production (Spring Boot Backend)
- HashLocation: DEAKTIVIERT
- Backend: Spring Boot 3.x (Java 21), `./gradlew bootRun`
- Config: `application.yml` (server port, SoftNet API, CORS, TLS)
- Server: Java 21 / Docker / Cloud Run
```

### 2b. `angular-architecture.md`

**Click-Dummy →**
```
- Typ: Click-Dummy (kein Backend, kein SSR)
- API Services: Mock-Daten / lokale JSON
- Auth: Keine (oder simuliert)
```

**Production →**
```
- Typ: Production (Spring Boot Backend + SoftNet API)
- API Services: HTTP calls to Spring Boot Backend (:3000, /api/v1/*)
- Auth: Session Token (HMAC-SHA256)
```

### 2c. `security.md`

**Click-Dummy →**
```
- Typ: Click-Dummy (Static Hosting)
- Keine Server-Side Security
- localStorage OK für Demo-Daten
```

**Production →**
```
- Typ: Production (Spring Boot Backend)
- HTTPS: PFLICHT (TLS_ENABLED, TLS_CERT_PATH, TLS_KEY_PATH)
- CSRF: Disabled (stateless API)
- Auth: Session Token (HMAC-SHA256, X-Session-Token header)
- Headers: CSP, X-Frame-Options DENY, X-Content-Type-Options nosniff
- Rate Limiting: 100 req/15min per IP (Bucket4j)
- Secrets: NUR `.env` / environment variables (in `.gitignore`)
```

### 2d. `code-language.md`

**Deutsch →**
```
- Code-Sprache: Deutsch (FIXIERT)
- REQ-Sprache irrelevant → Code IMMER Deutsch
```

**Englisch →**
```
- Code-Sprache: Englisch (FIXIERT)
- REQ-Sprache irrelevant → Code IMMER Englisch
```

### 2e. `i18n-typings.md`

Dynamisch je nach Q3-Auswahl:
```
- UI-Sprachen: {ausgewählte Sprachen kommagetrennt}
- Default: erste ausgewählte Sprache
- ALLE Sprachen PFLICHT bei jedem Feature
```

## Schritt 3: CLAUDE.md Projekt-Info

Zeilen setzen:
```
- **Deployment**: {Click-Dummy (GitHub Pages, HashLocation) | Production (Spring Boot Backend + SoftNet API)}
- **Code-Sprache**: {Deutsch | Englisch}
- **UI-Sprachen**: {z.B. DE, EN, FR}
```

## Schritt 4: Code

**Click-Dummy → `app.config.ts`:**
```typescript
import { provideRouter, withHashLocation } from '@angular/router';
providers: [provideRouter(routes, withHashLocation())]
```

**Production → `app.config.ts`:**
```typescript
providers: [provideRouter(routes)]
```

**Production → Spring Boot Backend:** `./gradlew bootRun`
- Config: `server/src/main/resources/application.yml`
- Java 21 Toolchain (Gradle `java.toolchain.languageVersion = JavaLanguageVersion.of(21)`)

## Schritt 5: CI/CD

**Click-Dummy:** `.github/workflows/deploy-gh-pages.yml`
**Production:** Docker / Cloud Run (Spring Boot JAR, Java 21)

## Schritt 6: Auto Memory aufbauen

**Nach dem Setup** eine `MEMORY.md` im Auto-Memory-Verzeichnis anlegen, die die Projekt-Konfiguration und Skill-Zusammenfassungen enthält.

**Pfad:** `.claude/projects/<project-path>/memory/MEMORY.md`

**Inhalt generieren aus allen Skills:**

```markdown
# Project Memory

## Project Setup (Verified)

- **Code Language:** {Englisch/Deutsch} (FIXED)
- **UI Languages:** {DE, EN, ...}
- **Deployment:** {Click-Dummy/Production} ({Details})
- **Setup placeholders:** All 5 skills have been filled

## Unit Test Pattern (Verified)

- **Mocked Template:** overrideComponent mit `set: { template: '...' }` — UI via Playwright E2E
- **Protected access:** `component as unknown as { method: ... }` typed cast
- **Inputs:** Immer via `fixture.componentRef.setInput()`

## Skill Summary

| Skill | Kernregel |
|-------|-----------|
| angular-architecture | Container/Presentational, Feature Store + Component Store, Mocked Template Tests |
| routing-patterns | Lazy Loading, Route Resolver für Data Loading, ResolveFn<void> |
| code-language | Code-Sprache FIXIERT, REQ-Sprache irrelevant |
| i18n-typings | TypeScript-only, Proxy-based Keys, localStorage `app-language` |
| forms | Reactive Forms, Typed Forms, FormGroup via input() |
| security | {Click-Dummy: localStorage OK / Production: JWT HttpOnly} |
| eslint | OnPush, sorted imports, no `any`, explicit return types |
| performance | OnPush, track by ID, computed statt Methoden, Lazy Loading |
| html-styling | Mobile-First, BEM, em/rem, WCAG 2.1 AA, CSS Variables |
| ui-design-system | _variables.scss, _utilities.scss, Material Overrides |

## Known Issues

- `jest.config.ts` has duplicate `testEnvironment` key
- `tsconfig.spec.json` needs path mappings: `@angular/material/*`, `@ngrx/signals/rxjs-interop`
- `jest.config.ts` needs moduleNameMapper: `@angular/material/*` → fesm2022
- ESLint flags SignalStore naming + signal reads as call expressions
```

**Wichtig:**
- Skill Summary aus **allen** `.claude/skills/*.md` Dateien generieren
- Nur Kernregeln (1 Satz pro Skill), keine Details
- Known Issues aus aktuellem Projektzustand befüllen
- Memory wird bei jedem neuen Projekt-Setup frisch generiert

## Checklist

- [ ] Q1–Q3 beantwortet
- [ ] 5 Skills → `<!-- SETUP:VORGABE -->` ersetzt
- [ ] CLAUDE.md → Projekt-Info gesetzt
- [ ] `app.config.ts` konfiguriert
- [ ] CI/CD eingerichtet
- [ ] Auto Memory (`MEMORY.md`) mit Skill Summary generiert
