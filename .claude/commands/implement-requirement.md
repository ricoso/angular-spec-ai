# Implement Requirement Command

Implementiert ein Requirement basierend auf der Spezifikation.

> **Board-Integration:** Branch-Erstellung, Status-Updates und REQUIREMENTS.md-Sync
> werden vom Kanban Board automatisch erledigt. Dieser Workflow fokussiert sich auf die
> reine Implementierung.

> **Branch-Regel:** Es wird KEIN separater `feat/`-Branch erstellt.
> Die Implementierung erfolgt direkt auf dem `req/<REQ-ID>`-Branch,
> der beim `/create-requirement` angelegt wurde.

> **Kein Auto-Commit:** Änderungen werden NICHT automatisch committed oder gepusht.
> Der Benutzer prüft lokal und entscheidet selbst über Commit + Push.

> **VERBINDLICH (REQ-023):** Smoke-Tests und E2E-Tests sind PFLICHTBESTANDTEIL jeder Feature-Implementierung.
> Ohne grünen Smoke-Test UND grünen `npm run e2e:backend`-Run ist die Implementierung **NICHT abgeschlossen**.
> Step 6.5 ist ein BLOCKER — bei FAIL darf Step 7 nicht starten und es darf NICHT committed werden.

## Usage

```
/implement-requirement $ARGUMENTS
```

**$ARGUMENTS** = `<REQ-ID-Name>` (z.B. `REQ-001-Header`)

---

## VERBINDLICHE REGELN

> **JEDER Step ist PFLICHT. Kein Step darf übersprungen werden.**
> **Bei Verstoß: Implementierung ist UNGÜLTIG.**

### Gate-System

| Step | Gate |
|------|------|
| Step 1 | Requirement gelesen, Feature-Name + Sprache extrahiert |
| **Step 2** | **ALLE 5 Skills gelesen, Code-Sprache bestätigt** |
| Step 3 | Code implementiert |
| Step 4 | Styling angewendet |
| Step 5 | Tests geschrieben + >80% |
| **Step 6** | **lint + type-check + tests (3 parallele Agents)** |
| **Step 6.5** | **Smoke-Test + E2E (`npm run e2e:backend`) GRUEN — VERBINDLICH** |
| **Step 7** | **`/check-all` Score >= 90 + qualitaets.md generiert** |
| Step 8 | Implementierung abgeschlossen, Änderungen lokal bereit |

---

## Workflow

### Pre-Check: Dev Container Umgebung

> **Empfehlung:** Dieses Projekt sollte im Dev Container entwickelt werden.

**Pruefe ob im Dev Container:**
- Pruefe ob Umgebungsvariable `REMOTE_CONTAINERS` oder `CODESPACES` gesetzt ist
- ODER ob Datei `/.dockerenv` existiert

```bash
if [ -z "$REMOTE_CONTAINERS" ] && [ -z "$CODESPACES" ] && [ ! -f "/.dockerenv" ]; then
  echo "WARNUNG: Du arbeitest NICHT in einem Dev Container."
  echo "Empfehlung: VS Code → 'Dev Containers: Reopen in Container'"
fi
```

> Weiter mit Step 1 (kein Blocker, nur Hinweis).

---

### Step 1: Requirement lesen

1. Lese `docs/requirements/$ARGUMENTS/requirement.md`
2. Extrahiere:
   - Section 10: Data Model (Interfaces)
   - Section 11: UI/UX (Components)
   - Section 14: Implementation (File Structure)
   - Section 16: Naming Glossary (Methodennamen)
3. **Extrahiere den Feature-Namen** (z.B. `booking`, `user-management`)

**GATE 1:** Requirement gelesen, Feature-Name bestimmt

---

### Step 2: Skills lesen (ALLE PFLICHT!)

> **NICHT "bei Bedarf" — ALLE Skills MUESSEN gelesen werden!**
> **Kein Code darf generiert werden bevor ALLE 5 Skills gelesen wurden!**

**Lese ALLE 5 Skills in dieser Reihenfolge:**

| # | Skill | Datei | Warum |
|---|-------|-------|-------|
| 1 | **Code Language** | `.claude/skills/code-language.md` | **Bestimmt Sprache ALLER Variablen, Methoden, Klassen, CSS-Klassen** |
| 2 | **Architecture** | `.claude/skills/angular-architecture.md` | Container/Presentational, Store Pattern, Deployment-Typ |
| 3 | **i18n** | `.claude/skills/i18n-typings.md` | UI-Sprachen (DE/EN/etc.), Translation Pattern |
| 4 | **Routing** | `.claude/skills/routing-patterns.md` | Resolver, Guards, Lazy Loading, HashLocation |
| 5 | **Forms** | `.claude/skills/forms.md` | Reactive Forms (auch wenn Feature keine Forms hat → bestätigen!) |

**Nach dem Lesen — Bestätige explizit:**

```
SKILL-CHECK BESTÄTIGT:
- Code-Sprache: [Englisch/Deutsch] (aus code-language.md)
- UI-Sprachen: [DE, EN] (aus i18n-typings.md)
- Deployment: [Click-Dummy/Production] (aus angular-architecture.md)
- Routing: [HashLocation/Standard] (aus routing-patterns.md)
- Forms: [Ja/Nein — relevant für dieses Feature]
```

> **STOP wenn Code-Sprache nicht bestätigt wurde!**

**GATE 2:** ALLE 5 Skills gelesen, Code-Sprache + UI-Sprachen + Deployment bestätigt

---

### Step 3: Implementieren

**Verwende die Code-Sprache aus Step 2 für ALLE Dateien!**

> **MOCKUP-PFLICHT:** Vor der Implementierung MUSS der UI-Mockup gelesen und pixelgenau umgesetzt werden!

**3a: Mockup lesen und analysieren:**

1. Öffne `docs/requirements/$ARGUMENTS/mockup.html` im Browser oder lese den HTML/CSS-Code
2. Analysiere **exakt**:
   - Layout-Struktur (Reihenfolge, Abstände, Ausrichtung)
   - Farben, Schriftgrößen, Icons
   - Komponentenaufteilung (welche Elemente sind eigene Components)
   - Responsive Verhalten (falls im Mockup erkennbar)
3. **Die Implementierung MUSS sich 1:1 an den Mockup halten!**
   - Keine eigenmächtigen Layout-Änderungen
   - Keine fehlenden Elemente
   - Keine hinzugefügten Elemente die nicht im Mockup sind
   - Gleiche visuelle Hierarchie und Anordnung

> **STOP wenn kein Mockup vorhanden!** Warnung ausgeben und User fragen ob ohne Mockup fortgefahren werden soll.

Reihenfolge:
1. **Models** (`models/*.model.ts`)
2. **Store** (`stores/*.store.ts`)
3. **Services** (`services/*-api.service.ts`)
4. **Container Component** (`*-container.component.ts` + `.html` + `.scss`)
5. **Presentational Components** (`components/*.component.ts` + `.html` + `.scss`)
6. **i18n** (Translations in ALLEN konfigurierten UI-Sprachen)
7. **Routes** (Feature-Routes + app.routes.ts)
8. **Resolver** (`resolvers/*.resolver.ts`)

**Regeln aus Skills beachten:**
- Container: `inject(Store)`, `OnPush`, Event Handler (`onXxx()`)
- Presentational: `input()`, `output()` only, KEIN Store
- Store: `withState`, `withComputed`, `withMethods`, `providedIn: 'root'`
- KEIN `onInit` im Store für Feature-Daten → Resolver!
- KEIN `ngOnInit` in Component für Data Loading → Resolver!
- Separate `.html` + `.scss` Dateien (KEINE inline templates/styles!)
- `@for` mit `track item.id` (NICHT `$index`)

**GATE 3:** Alle Dateien implementiert

---

### Step 4: Styling

- IMMER `src/styles/_variables.scss` verwenden
- KEINE hardcoded Farben!
- Mobile-First responsive
- em/rem statt px
- BEM Naming
- WCAG 2.1 AA

**GATE 4:** Styling angewendet

---

### Step 5: Tests schreiben

- Jest Unit Tests für Store, Services, Components
- Ziel: >80% Coverage
- Tests ausführen und bestätigen:

```bash
npm run test:coverage
```

**GATE 5:** Tests geschrieben + alle bestanden

---

### Step 6: Technische Prüfung (PFLICHT! BLOCKER! PARALLEL AGENTS!)

> **ALLE 4 Prüfungen MUESSEN ausgeführt werden!**
> **ALLE 4 als parallele Agents starten** (4 Tool-Aufrufe in EINER Nachricht)!
> **Bei Fehler: Fixen und erneut ALLE 4 parallel als Agents ausführen bis GRUEN!**

**4 parallele Agents starten (EINE Nachricht, 4 Tool-Aufrufe):**

| # | Agent Description | Command | Prüft |
|---|-------------------|---------|-------|
| 1 | `"Run lint:fix"` | `npm run lint:fix` | ESLint Regeln + Auto-Fix |
| 2 | `"Run type-check"` | `npm run type-check` | TypeScript Typen |
| 3 | `"Run test:coverage"` | `npm run test:coverage` | Jest Tests + Coverage >80% |
| 4 | `"Run backend tests"` | `cd server && ./gradlew test` | Spring Boot Backend Tests |

> **WICHTIG:** Die 4 Agents sind voneinander UNABHÄNGIG und MUESSEN parallel gestartet werden!

**Ergebnis dokumentieren (erst wenn ALLE 4 Agents fertig):**
```
TECHNISCHE PRÜFUNG (4 parallele Agents):
- lint:fix       → [PASS / FAIL + Fehler]
- type-check     → [PASS / FAIL + Fehler]
- test:coverage  → [PASS (XX%) / FAIL + fehlgeschlagene Tests]
- backend:test   → [PASS (XX%) / FAIL + fehlgeschlagene Tests]
```

> **STOP bei FAIL!** Erst fixen, dann erneut ALLE 4 parallel als Agents prüfen.
> **Step 6.5 darf NICHT starten wenn Step 6 nicht GRUEN ist!**

**GATE 6:** lint + type-check + tests

---

### Step 6.5: Smoke-Test + E2E (PFLICHT! BLOCKER! VERBINDLICH!)

> **Smoke-Tests und E2E-Tests sind verpflichtender Bestandteil jeder Feature-Implementierung.**
> **Ohne grünen Smoke-Test UND grünen E2E-Run ist die Implementierung NICHT abgeschlossen.**
> **Festgelegt durch REQ-023 — gilt rueckwirkend fuer alle zukuenftigen Features.**

**6.5a — Smoke-Test (ca. 30 s):**

Verifiziert, dass die App ueberhaupt startet, Backend gesund ist und das Angular-Bundle ausgeliefert wird:

```bash
# Frontend Build
npm run build

# Backend in Hintergrund starten
(cd server && ./gradlew bootRun) &
BACKEND_PID=$!

# Bis zu 60 s auf Health warten
for i in $(seq 1 60); do
  if curl -sf http://localhost:3000/api/v1/health >/dev/null; then break; fi
  sleep 1
done

# Health-Check + Root-Page-Check
curl -sf http://localhost:3000/api/v1/health | grep -q '"status":"healthy"' \
  || (echo "SMOKE FAIL: Health-Check"; kill $BACKEND_PID; exit 1)
curl -sf http://localhost:3000/ | grep -q '<app-root' \
  || (echo "SMOKE FAIL: Angular-Root nicht ausgeliefert"; kill $BACKEND_PID; exit 1)

kill $BACKEND_PID
echo "SMOKE PASS"
```

**6.5b — E2E (full, ca. 5–10 min):**

```bash
npm run e2e:backend
```

Erwartung: alle Specs grün in Desktop + Tablet + Mobile.

**Ergebnis dokumentieren:**

```
SMOKE + E2E:
- Smoke-Test         → [PASS / FAIL + Fehlerausgabe]
- E2E (Desktop)      → [PASS (XX/XX) / FAIL + Spec-Namen]
- E2E (Tablet)       → [PASS (XX/XX) / FAIL + Spec-Namen]
- E2E (Mobile)       → [PASS (XX/XX) / FAIL + Spec-Namen]
```

> **STOP bei FAIL!**
> - Smoke-FAIL → Container-Setup, Health-Endpoint oder Build defekt → fix, Step 6 + 6.5 erneut
> - E2E-FAIL → echte Regression oder Flake. **Flake-Fix ist Pflicht** (kein „retry & ignore")
> **Step 7 darf NICHT starten wenn Step 6.5 nicht GRUEN ist!**
> **Es darf NICHT committed werden bei FAIL!**

**Bei neuen Features ohne dedizierten E2E-Test:**
- Eine neue Spec `playwright/<REQ-ID>-<feature>.spec.ts` MUSS angelegt werden, die mindestens den Happy-Path des Features validiert (Filiale → … → Submit).
- Ziel: User-Flow-Abdeckung ≥ 95 % (Vorgabe REQ-023).

**GATE 6.5:** Smoke-Test grün + E2E grün in allen 3 Viewports

---

### Step 7: Quality Checks (PFLICHT! BLOCKER!)

> **NICHT UEBERSPRINGEN! /check-all ist PFLICHT vor jedem Commit!**

**Feature-Name aus Step 1 verwenden:**

```
/check-all <feature-name>
```

Dies führt 13 Checks aus (11 statisch + E2E + Documentation).

**Quality Gate:**
- Ziel: Score >= 90/100
- Generiert: `docs/requirements/$ARGUMENTS/qualitaets.md`
- Generiert: `feature-documentation-de.md` + `feature-documentation-en.md`

**Bei Score < 90:**
1. Issues aus dem Report lesen
2. Fixen
3. **Step 6 erneut ausführen** (lint + type-check + tests)
4. **Step 7 erneut ausführen** (/check-all)
5. Wiederholen bis Score >= 90

> **STOP bei Score < 90!** Commit ist NICHT erlaubt!

**GATE 7:** Score >= 90/100 + qualitaets.md generiert

---

### Step 8: Fertigmeldung (KEIN Commit/Push!)

**Erst nach bestandenem Quality Gate (Step 7)!**

> **WICHTIG: Änderungen werden NICHT committed oder gepusht!**
> Der Benutzer prüft die Änderungen lokal und entscheidet selbst über Commit + Push.

**Ausgabe:**

```
IMPLEMENTIERUNG ABGESCHLOSSEN: $ARGUMENTS
- Feature: <Feature-Name>
- Quality Score: XX/100
- Test Coverage: XX%
- Alle Änderungen sind LOKAL (nicht committed)

Nächste Schritte (manuell):
  git add .
  git commit -m "feat($ARGUMENTS): implement <Feature-Name>"
  git push -u origin HEAD
```

**GATE 8:** Implementierung abgeschlossen, Änderungen lokal bereit zur Prüfung

---

## Checkliste (ALLE Punkte PFLICHT!)

- [ ] Step 1: Requirement gelesen
- [ ] **Step 2: ALLE 5 Skills gelesen (code-language, architecture, i18n, routing, forms)**
- [ ] **Step 2: Code-Sprache bestätigt**
- [ ] **Step 3: Mockup gelesen und analysiert (`mockup.html`)**
- [ ] **Step 3: Implementierung hält sich 1:1 an den Mockup**
- [ ] Step 3: Models definiert
- [ ] Step 3: Store mit `withState`, `withComputed`, `withMethods`
- [ ] Step 3: Container Component mit `OnPush`
- [ ] Step 3: Presentational Components mit `input()`/`output()`
- [ ] Step 3: i18n in ALLEN UI-Sprachen
- [ ] Step 3: Resolver für Data Loading
- [ ] Step 4: Styling aus `_variables.scss`, Mobile-First, WCAG 2.1 AA
- [ ] Step 5: Tests >80%
- [ ] **Step 6: `npm run lint:fix`**
- [ ] **Step 6: `npm run type-check`**
- [ ] **Step 6: `npm run test:coverage`**
- [ ] **Step 6: `cd server && ./gradlew test` (Backend-Tests)**
- [ ] **Step 6.5: Smoke-Test (Frontend-Build + Backend-Health-Check) PASS**
- [ ] **Step 6.5: `npm run e2e:backend` GRUEN in Desktop + Tablet + Mobile (PFLICHT, REQ-023)**
- [ ] **Step 6.5: Bei neuem Feature ohne Spec → neue `playwright/<REQ-ID>-*.spec.ts` angelegt (≥ Happy-Path)**
- [ ] **Step 7: `/check-all` ausgeführt (Score >= 90/100)**
- [ ] **Step 7: `qualitaets.md` generiert**
- [ ] **Step 7: E2E Tests bestanden (check-e2e)**
- [ ] **Step 7: Feature-Dokumentation in DE + EN generiert (check-documentation)**
- [ ] Step 8: Fertigmeldung ausgegeben (Änderungen LOKAL, kein Commit/Push)

---

## Backend-Architektur & Deployment

### Architektur

```
Browser → Angular App → Spring Boot Backend (:3000) → SoftNet API
```

- **Spring Boot Backend** (`server/`): Middleware/Proxy zwischen Angular und SoftNet API
- **Frontend API-Services**: Machen HTTP-Calls an `/api/v1/...` (KEINE statischen Mock-Daten!)
- **Backend**: Ruft echte SoftNet API auf, transformiert Responses fuer das Frontend
- **Store bleibt unveraendert**: Ruft weiterhin die API-Services auf

### Deployment-Modi (gleicher Code, andere Env-Werte)

| Modus | SoftNet-Instanz | Env-Quelle | Wo |
|-------|----------------|-----------|-----|
| **Dev** | Integration (`int-api.soft-nrg.com`) | `application.yml` / `.env`-Datei (lokal) | Lokal + GitHub Actions |
| **Prod** | Production (`api.soft-nrg.com`) | GitLab Secrets Store → K8s Env Vars | Kubernetes Pod |

> **Prod-Deployment:** Die Env-Werte werden NICHT als Datei deployed.
> Stattdessen injiziert GitLab CI/CD die Secrets als Environment Variables
> in den Kubernetes Pod (via `CI/CD > Settings > Variables`).
> Spring Boot liest sowohl `application.yml` als auch `System.getenv()`.

### E2E-Tests

| Command | Beschreibung | Wann |
|---------|-------------|------|
| `npm run e2e:backend` | Angular Build + Spring Boot Backend + Playwright | GitHub Actions (Dev-Werte) |
| `npm run e2e:backend` | Gleicher Command | Im Pod (Prod-Werte) |

> **PFLICHT:** E2E-Tests laufen IMMER mit Backend — kein Click-Dummy-Modus mehr!
> Frontend-Services machen IMMER echte HTTP-Calls zum Backend.

### Commands

```bash
# Frontend
npm start              # Angular Dev Server (:4200, braucht Backend auf :3000)
npm test               # Jest Watch
npm run test:coverage  # Coverage Report
npm run lint:fix       # ESLint Auto-fix
npm run type-check     # TypeScript Check

# Backend
cd server && ./gradlew bootRun   # Spring Boot Server (:3000)
cd server && ./gradlew test      # Backend Tests
cd server && ./gradlew test jacocoTestReport  # Backend Coverage

# E2E (IMMER mit Backend!)
npm run e2e:backend    # Build + Spring Boot Backend + Playwright (3 Viewports)
```

---

## Referenzen

- Requirement: `docs/requirements/$ARGUMENTS/requirement.md`
- Skills: `.claude/skills/*.md` (ALLE 5 lesen!)
- Styling: `src/styles/_variables.scss`
- Quality Template: `docs/requirements/QUALITAETS-TEMPLATE.md`
- Backend: `server/` (Spring Boot, `application.yml` fuer Konfiguration)
