# Angular 21 Projekt - Claude Code Anweisungen

## Projekt-Info

- **Framework**: Angular 21 Standalone Components
- **State**: NgRx Signal Store (Feature Store Pattern)
- **UI**: Angular Material 21
- **Testing**: Jest 30 (Unit) + Playwright (E2E lokal)
- **i18n**: ngx-translate (TypeScript Typings, Proxy-basiert)
- **Code-Sprache**: Englisch (FIXIERT)
- **UI-Sprachen**: DE, EN
- **Doc-Sprachen**: DE, EN
- **Deployment**: Production (Spring Boot Backend + SoftNet API)
- **SoftNet API-Doku**: `test-booking-flow copy.mjs` — Feature Discovery (Zeitfilter, Ersatzwagen, Wiederkehr-Kunde); `soft-netOpenAPI.html` — Offizielle OpenAPI Spec
- **SoftNet API-Erkenntnisse** (getestet + bewiesen, INT-System, `scope.api.planning.service`):
  - `checkInTimePreference: "AM"|"PM"` → verschiebt Snap-Verteilung (VM/NM)
  - `replacementPreference: true` → Ersatzwagen (Branch-abhaengig, Muenchen hat keine)
  - `wishlist.note` → Freitext max 2000 chars, wird gespeichert
  - **Wiederkehr-Kunde**: Email reicht — SoftNet erkennt Bestandskunden per Email, liefert: `firstName`, `lastName`, `email`, `brand`, `model`, `license`. Keine eigene DB noetig!
  - **Appointment-Response liefert NICHT**: `address`, `zipCode`, `city`, `phone`, `mobile`, `country` — dafuer braucht man `GET /users/{id}` (401 mit aktuellem Scope). → Mit Prod-System + erweitertem Scope (`scope.api.planning.user`) erneut testen!
  - `vaultitems` Endpoint → 401 (Scope reicht nicht) → mit Prod-System testen

---

## VERBOTEN / PFLICHT Kurzreferenz

| VERBOTEN | STATTDESSEN |
|----------|-------------|
| Inline `template`/`styles` | Separate `.html` + `.scss` Dateien |
| `onInit` im Store für Feature-Daten | Route Resolver + `rxMethod` |
| `ngOnInit` für Data Loading | Route Resolver |
| `@for` mit `track $index` | `track item.id` |
| `{{ method() }}` im Template | `computed()` Signal |
| `any` Type | Interface oder `unknown` |
| `ngModel` | Reactive Forms |
| Hardcoded Farben/px | CSS Variables aus `_variables.scss`, em/rem |
| Dunkler Hintergrund bei Overlays | `var(--color-background-surface)` (weiß) |
| `[innerHTML]` ohne Sanitizer | Angular Template Escaping |
| Hardcoded Strings in Templates | i18n Keys (`{{ featureAlias.form.field | translate }}`) |
| `protected readonly t = i18nKeys` | Feature-Alias: `protected readonly carinformation = i18nKeys.booking.carinformation` |
| Coverage-Thresholds ändern | Coverage-Config ist TABU — nie anfassen! |
| `mat-stroked-button` (outlined) | `mat-flat-button` (filled) — Buttons IMMER filled! |

---

## Architektur (Kurzform)

- **Container/Presentational**: 1 Route = 1 Container (`inject(Store)`, OnPush) + Presentational Children (`input()`/`output()` only)
- **Feature Store**: `withState`, `withComputed`, `withMethods` — `providedIn: 'root'` — KEIN `onInit` für Feature-Daten
- **Component Store**: UI-State (tabs, filters) — provided im Component `providers` Array
- **Services**: API (`Promise<T>`) + Business (Logik) — Store nutzt API in `withMethods`
- **Routing**: Lazy Loading, `ResolveFn<void>`, Functional Guards, `input()` für Route Params
- **Data Loading**: Resolver → Store.rxMethod → Component subscribes Store

> Details: `.claude/skills/angular-architecture.md`, `.claude/skills/routing-patterns.md`

---

## Styling (Kurzform)

- **SCSS**: `_variables.scss` für alles, BEM Nesting, em/rem, Mobile-First
- **Material Overrides**: `_material-overrides.scss` (zentral)
- **Overlays**: Modals, Dropdowns, Menus IMMER `var(--color-background-surface)` (weiß)
- **A11y**: WCAG 2.1 AA, Focus-Styles (`:focus-visible`), Touch 2.75em, Kontrast 4.5:1
- **Responsive**: Mobile < 48em, Tablet >= 48em, Desktop >= 64em
- **Icons**: IMMER mit `.icon-framed` Rahmen

> Details: `.claude/skills/ui-design-system.md`, `.claude/skills/html-styling.md`

---

## i18n / Forms / Security (Kurzform)

- **i18n**: Type-safe Keys via Proxy (`{{ t.feature.key }}`), TypeScript-only, DE + EN
- **Forms**: Reactive Forms, Typed, Validators im Component, KEIN ngModel
- **Security**: Kein eval/innerHTML, JWT in HttpOnly Cookies, Route Guards, `.env` für Secrets

> Details: `.claude/skills/i18n-typings.md`, `.claude/skills/forms.md`, `.claude/skills/security.md`

---

## Code Quality

- ESLint: Imports sortiert, OnPush, kein `any`, `npm run lint:fix` vor Commit
- TypeScript: Explicit Return Types, Interfaces in `models/`, Union Types statt Enums
- Performance: OnPush, `track item.id`, `computed()`, Lazy Loading

> Details: `.claude/skills/eslint.md`

---

## Workflow (STRIKT EINHALTEN!)

**Jeder Step ist PFLICHT. Kein Step darf übersprungen werden.**

> **Board-Integration:** Branch-Erstellung, Status-Updates und REQUIREMENTS.md-Sync
> werden vom Kanban Board (`tools/requirements-board`) automatisch erledigt.
> Die Workflows fokussieren sich auf die reine Implementierung + PR-Erstellung.

### /implement-requirement — Pflicht-Ablauf:

| Step | Gate | Beschreibung |
|------|------|--------------|
| 1 | REQ lesen | requirement.md → Feature-Name, Sections 10/11/14/16 |
| **2** | **5 Skills lesen** | **ALLE: code-language, architecture, i18n, routing, forms** |
| 3 | Implementieren | Models → Store → Services → Container → Components → i18n → Routes |
| 4 | Styling | `_variables.scss`, Mobile-First, WCAG 2.1 AA |
| 5 | Tests | Jest >80% Coverage |
| **6** | **Tech-Check (3 PARALLEL AGENTS)** | **`lint:fix` + `type-check` + `test:coverage`** |
| **7** | **Quality Gate** | **`/check-all` Score >= 90 + qualitaets.md generiert** |
| 8 | Fertigmeldung | Änderungen LOKAL — kein Commit/Push (User prüft selbst) |

> **Branch-Regel:** Kein separater `feat/`-Branch — Implementierung auf `req/REQ-XXX`.
> **Kein Auto-Commit:** Änderungen bleiben lokal bis der User selbst committed.
> **STOP bei Step 6 FAIL!** Erst fixen, dann weiter.
> **STOP bei Step 7 Score < 90!** Erst fixen, Step 6+7 wiederholen.
> Details: `.claude/commands/implement-requirement.md`

### /create-requirement — Pflicht-Ablauf:

| Phase | Beschreibung |
|-------|--------------|
| 1 Setup | Branch `req/REQ-XXX` + Ordner + Template |
| 2 Parallel | **2 Agents:** Mockup (HTML+CSS) + Requirement (17 Sections) |
| 3 Finalize | `/check-requirement` + Fertigmeldung (alles lokal, kein Commit/Push/PR) |

> Details: `.claude/commands/create-requirement.md`

### Weitere Commands

```
/check-requirement REQ-XXX-Name      → Prüft Vollständigkeit
/check-all <feature>                 → 13 Checks, qualitaets.md
```

**Checks:** `/check-architecture`, `/check-stores`, `/check-routing`, `/check-security`, `/check-eslint`, `/check-typescript`, `/check-performance`, `/check-styling`, `/check-i18n`, `/check-forms`, `/check-code-language`, `/check-e2e`, `/check-documentation`

**Gruppiert:** `/check-arch`, `/check-quality`

---

## Commands

```bash
# Frontend
npm start              # Angular Dev Server (:4200, braucht Backend auf :3000)
npm test               # Jest Watch
npm run test:coverage  # Coverage Report
npm run lint:fix       # ESLint Auto-fix
npm run type-check     # TypeScript Check

# Backend (Spring Boot)
cd server && ./gradlew bootRun          # Spring Boot Server (:3000)
cd server && ./gradlew test             # Backend JUnit Tests
cd server && ./gradlew jacocoTestReport # Backend Coverage

# E2E (IMMER mit Backend!)
npm run e2e:backend    # Build + Backend + Playwright (3 Viewports)
```

---

## Naming & Commits

```
user-container.component.ts/.html/.scss  # Container (3 Dateien!)
user-list.component.ts                   # Presentational
user-api.service.ts                      # API Service
user.store.ts                            # Feature Store
```

```
feat(REQ-XXX): Add user feature
fix(REQ-XXX): Fix validation
test(REQ-XXX): Add 85% coverage
```

---

## Project Structure

```
src/app/
├── core/           # Guards, Interceptors
├── shared/         # Reusable Components
├── features/       # Feature Modules
│   └── <feature>/
│       ├── <feature>-container.component.ts/.html/.scss
│       ├── components/    # Presentational
│       ├── services/      # API + Business
│       ├── store/         # NgRx Signal Store
│       └── models/        # Interfaces
└── app.routes.ts

server/                    # Spring Boot Backend (Java 21)
├── build.gradle
├── src/main/java/com/autohaus/backend/
│   ├── config/            # SecurityConfig, WebConfig, Properties
│   ├── softnet/           # SoftNet API Client + Auth
│   ├── session/           # Session Token (HMAC-SHA256)
│   ├── health/            # Health Check
│   ├── branches/          # Branches Controller/Service
│   ├── brands/            # Brands Controller/Service
│   ├── jobtypes/          # JobTypes Controller/Service
│   ├── snaps/             # Snaps + Cache + OperationIdMapper
│   ├── appointments/      # Appointments + SnapPatcher
│   ├── customers/         # Customers Controller/Service
│   ├── cars/              # Cars Controller/Service
│   ├── mobility/          # Mobility Controller/Service
│   └── common/            # Filters, Exception Handler, DTOs
└── src/main/resources/application.yml

docs/requirements/
├── REQUIREMENTS.md
├── REQ-XXX-Name/
│   ├── requirement.md
│   ├── qualitaets.md          # /check-all Report
│   ├── feature-documentation-de.md
│   ├── feature-documentation-en.md
│   └── screenshots/
```
