# Angular 21 Template-Projekt

Angular 21 Standalone Components mit NgRx Signal Store, Angular Material und Claude Code Integration.

## Projekt starten

```bash
npm install
npm start                    # http://localhost:4200
```

### Optional: MCP Server + Playwright

```bash
npm run mcp:setup            # NgRx + Material MCP Server installieren
npx playwright install       # Browser fuer E2E Tests
```

## Claude Code einrichten

### 1. Setup-Wizard (neues Projekt konfigurieren)

```
Lies .claude/skills/projekt-setup.md und fuehre den Setup aus
```

Konfiguriert: Projektname, Features, Routing, i18n-Keys, Store-Setup.

### 2. Memory anlegen (Skills lernen)

```
Lies alle .claude/skills/*.md und erstelle MEMORY.md
```

Claude lernt die Architektur-Patterns und kann Requirements selbststaendig implementieren.

### 3. Requirement implementieren

```
/create-requirement REQ-001-MeinFeature
/implement-requirement REQ-001-MeinFeature
```

## Konfiguration

| Einstellung | Wert |
|-------------|------|
| Code-Sprache | Englisch (fixiert) |
| UI-Sprachen | Deutsch + Englisch |
| Deployment | Production (Spring Boot Backend + SoftNet API) |
| State Management | NgRx Signal Store |
| UI Framework | Angular Material 21 |
| Unit Tests | Jest 30 (>80% Coverage) |
| E2E Tests | Playwright (3 Viewports) |

## Scripts

```bash
npm start              # Dev Server
npm test               # Jest Watch
npm run test:coverage  # Coverage Report
npm run lint:fix       # ESLint Auto-fix
npm run type-check     # TypeScript Check
npm run e2e            # Playwright E2E
```

## Projektstruktur

```
src/app/
├── core/              # Guards, Interceptors, i18n
├── shared/            # Reusable Components
├── features/          # Feature Modules (Lazy Loaded)
│   └── <feature>/
│       ├── components/    # Presentational Components
│       ├── services/      # API + Business Services
│       ├── stores/        # NgRx Signal Store
│       └── models/        # Interfaces
└── app.routes.ts

docs/requirements/     # Requirement Specs + Quality Reports
.claude/commands/      # Claude Code Slash-Commands
.claude/skills/        # Architektur-Skills + Setup-Wizard
```

## Dokumentation

- **CLAUDE.md** — Projekt-Regeln und Workflow fuer Claude Code
- **.claude/commands/README.md** — Alle verfuegbaren Slash-Commands
- **.claude/skills/** — Architektur-Patterns, Styling, i18n, Security
- **docs/requirements/** — Feature-Anforderungen und Qualitaetsberichte

## License

MIT
