# Check All Command (Parallel Agent-System)

Orchestriert 6 spezialisierte Agents zur Ausführung aller 13 Checks (2-Phasen).

## Usage

```
$ARGUMENTS = <feature-name>
```

Example: `user-management`

---

## WICHTIG: Agent-Orchestrierung

Du bist der **Orchestrator**. Führe die folgenden Schritte EXAKT aus:

### Schritt 1a: Dev Server starten

Prüfe ob der Dev Server bereits läuft. Falls nicht, starte ihn:

```bash
curl -s http://localhost:4200 > /dev/null 2>&1 || (npm start &)
timeout 30 bash -c 'until curl -s http://localhost:4200 > /dev/null; do sleep 1; done'
```

---

### Schritt 1b: Starte 4 Agents PARALLEL (Phase 1 — Statische Checks)

Verwende das `Task`-Tool mit `subagent_type: "general-purpose"` und starte **ALLE 4 Agents in EINEM Tool-Aufruf** (parallel):

#### Agent 1: Architecture Checks
```
Prompt: "Analysiere das Feature '$ARGUMENTS' und führe folgende Architektur-Checks durch:

1. **check-architecture**: Container/Presentational Pattern
   - Prüfe: 1 Route = 1 Container + Presentational Children
   - Container: inject(Store), OnPush, Event Handler (onXxx())
   - Presentational: input(), output() only, KEIN Store
   - Template: NUR computed()/signal() Reads — KEINE Methoden-Aufrufe (außer Event Handler)
   - Form-Fehler: errors computed Signal statt hasError() Methode
   - Dead Code: KEINE ungenutzten Methoden/Properties — nicht im Template + nicht intern aufgerufen = LÖSCHEN!
   - API-Payloads: unbekannte optionale Angaben WEGLASSEN (undefined / Key nicht setzen) — KEINE Platzhalter ('UNKNOWN', 'N/A', '', erfundene Defaults). Ausnahme: SoftNet-Pflichtfeld `model` nie weglassen (Rückfall `AppointmentsService.MODEL_UNKNOWN`). Zusätzlich Backend prüfen: server/src/main/java/com/autohaus/backend/{appointments,cars,customers,snaps} (Details: check-architecture.md, Punkt 9)

2. **check-stores**: NgRx Signal Store Pattern
   - Prüfe: withState, withComputed, withMethods, withHooks
   - State: items[], loading, error
   - KEIN onInit für Feature-Daten (Resolver!)

3. **check-routing**: Routing Patterns
   - Prüfe: Lazy Loading, Route Resolver, Functional Guards
   - ResolveFn<void>, input() für Route Params

Suche nach Dateien in src/app/features/$ARGUMENTS/**/*.ts

Gib für jeden Check zurück:
- Score (0-100)
- Liste der gefundenen Issues mit Datei:Zeile
- Kategorie-Gesamtscore

Format:
ARCHITECTURE_RESULT:
check-architecture: <score>/100 [✅|⚠️|❌]
  - <issue1>
check-stores: <score>/100 [✅|⚠️|❌]
  - <issue1>
check-routing: <score>/100 [✅|⚠️|❌]
  - <issue1>
CATEGORY_SCORE: <avg>/100"
```

#### Agent 2: Quality Checks
```
Prompt: "Analysiere das Feature '$ARGUMENTS' und führe folgende Quality-Checks durch:

1. **check-eslint**: ESLint Rules
   - Import Order (Angular → Third Party → Local)
   - Naming Conventions (camelCase, PascalCase)
   - Unused imports

2. **check-typescript**: Type Safety
   - KEIN any Type (nutze unknown)
   - Explicit Return Types
   - Interfaces in models/
   - KEINE ungenutzten Methoden/Properties (Dead Code) — nicht referenziert = LÖSCHEN!

3. **check-performance**: Performance
   - OnPush bei ALLEN Components
   - @for mit track item.id
   - NUR computed()/signal() im Template — KEINE Methoden-Aufrufe!
   - hasError()/getErrorMessage() als Methode ist VERBOTEN → errors computed Signal
   - Einzige Ausnahme: Event Handler (click)="onAction()"

4. **check-styling**: SCSS & Accessibility
   - em/rem statt px
   - BEM Naming
   - WCAG 2.1 AA Compliance
   - Focus-Styles, Kontrast
   - Buttons: IMMER mat-flat-button (filled), KEIN mat-stroked-button

Suche nach Dateien in src/app/features/$ARGUMENTS/**/*

Gib für jeden Check zurück:
- Score (0-100)
- Liste der gefundenen Issues mit Datei:Zeile
- Kategorie-Gesamtscore

Format:
QUALITY_RESULT:
check-eslint: <score>/100 [✅|⚠️|❌]
  - <issue1>
check-typescript: <score>/100 [✅|⚠️|❌]
  - <issue1>
check-performance: <score>/100 [✅|⚠️|❌]
  - <issue1>
check-styling: <score>/100 [✅|⚠️|❌]
  - <issue1>
CATEGORY_SCORE: <avg>/100"
```

#### Agent 3: Security Check
```
Prompt: "Analysiere das Feature '$ARGUMENTS' und führe einen vollständigen Security-Check durch:

**check-security**: Security Audit

1. XSS Prevention
   - KEIN [innerHTML] ohne DomSanitizer
   - KEIN bypassSecurityTrustHtml() mit User-Input
   - KEINE eval() oder Function()

2. Authentication & Authorization
   - JWT in HttpOnly Cookies (NICHT localStorage!)
   - Route Guards für geschützte Routes
   - Role-Based Access Control

3. Sensitive Data
   - KEINE Passwörter/Tokens in localStorage
   - KEINE sensiblen Daten in URL-Parametern
   - KEINE console.log() mit sensiblen Daten
   - KEINE Credentials im Source Code

4. HTTP Security
   - HTTPS only
   - CSRF Token Handling

5. Input Validation
   - Client-Side Validators vorhanden
   - Sanitization für HTML-Content

Suche nach Dateien in src/app/features/$ARGUMENTS/**/*

Gib zurück:
- Score (0-100)
- Liste der gefundenen Security Issues mit Severity (CRITICAL/HIGH/MEDIUM/LOW)
- Datei:Zeile für jedes Issue

Format:
SECURITY_RESULT:
check-security: <score>/100 [✅|⚠️|❌]
  - [CRITICAL] <issue1> (datei:zeile)
  - [HIGH] <issue2> (datei:zeile)
  - [MEDIUM] <issue3> (datei:zeile)
CATEGORY_SCORE: <score>/100"
```

#### Agent 4: Feature Checks (i18n, Forms, Code Language)
```
Prompt: "Analysiere das Feature '$ARGUMENTS' und führe folgende Feature-Checks durch:

1. **check-i18n**: Internationalization
   - Feature-Alias PFLICHT: `protected readonly <feature> = i18nKeys.<path>` (sprechend!)
   - VERBOTEN: `protected readonly t = i18nKeys` — generisch, nicht erlaubt!
   - Templates: `{{ featureAlias.form.field.label | translate }}` (kein String-Literal, kein `t.`)
   - KEINE hardcoded Strings in Templates
   - Nested Key-Struktur: `form.email.error.required` (kein flaches `validation.required`)
   - Alle 5 Sprachen in translations.ts: DE + EN + UK + FR + AR

2. **check-forms**: Reactive Forms
   - Reactive Forms (FormGroup, FormControl)
   - Typed Forms mit Generics
   - Validators im Component
   - KEIN ngModel
   - Form-Fehler als computed Signal (KEINE hasError()/getErrorMessage() Methoden im Template!)
   - Pattern: toSignal(form.events) → errors computed → errors().field.error im Template
   - Optionale Felder: leer → weglassen (undefined), KEIN Platzhalter ('UNKNOWN', 'N/A', '') im Submit-Payload

3. **check-code-language**: Code Language
   - Code-Sprache ist FIXIERT auf Englisch (unabhängig von REQ-Sprache!)
   - ALLE Variablen, Methoden, Klassen, CSS-Klassen MÜSSEN Englisch sein
   - Korrekt: onSubmit(), loadUsers(), isLoading, User
   - FALSCH: beimAbsenden(), ladeBenutzer(), istLaden, Benutzer

Suche nach Dateien in src/app/features/$ARGUMENTS/**/*

Gib für jeden Check zurück:
- Score (0-100)
- Liste der gefundenen Issues mit Datei:Zeile
- Kategorie-Gesamtscore

Format:
FEATURE_RESULT:
check-i18n: <score>/100 [✅|⚠️|❌]
  - <issue1>
check-forms: <score>/100 [✅|⚠️|❌]
  - <issue1>
check-code-language: <score>/100 [✅|⚠️|❌]
  - <issue1>
CATEGORY_SCORE: <avg>/100"
```

---

### Schritt 2: Sammle Phase 1 Ergebnisse

Warte auf alle 4 Agents und sammle deren Ergebnisse.

---

### Schritt 3: Phase 2 — Agent 5 (E2E Testing via Playwright MCP)

> **NACH Phase 1!** Statische Checks sollten OK sein bevor E2E startet.

Starte Agent 5 mit `subagent_type: "general-purpose"`:

#### Agent 5: E2E Testing (Lokaler Playwright)
```
Prompt: "Führe E2E-Tests für das Feature '$ARGUMENTS' durch via lokaler Playwright Test-Suite.

Folge den Anweisungen in .claude/commands/check-e2e.md:

1. Ermittle REQ-ID aus Feature-Name
2. Lese docs/requirements/<REQ-ID>/requirement.md (Sections 4, 5, 6, 13)
3. Prüfe/erstelle/erweitere Playwright Test-Dateien:
   - playwright/REQ-XXX-*.spec.ts (Feature-Tests)
   - playwright/workflow-*.spec.ts (Gesamtworkflow)
   - playwright/helpers/*.helpers.ts (Shared Helpers)
4. Führe Tests aus: npx playwright test --project=chromium-desktop
5. **PFLICHT: Ergebnisse in qualitaets.md eintragen** (Schritt 7 aus check-e2e.md):
   - Aktualisiere docs/requirements/<REQ-ID>/qualitaets.md
   - Sektion '## 🧪 E2E Testing (Playwright — Lokale Test-Suite)' mit:
     - Playwright Test-Dateien Tabelle
     - Test-Szenarien Aufschlüsselung
     - Workflow-Tests Aufschlüsselung
     - Viewports Ergebnisse
     - Issues
6. **PFLICHT: Screenshots für Dokumentation erstellen** (Schritt 6 aus check-e2e.md):
   - Screenshot-Config in `playwright/take-screenshots.js` erweitern (falls neues Feature)
   - Screenshots erstellen: `node playwright/take-screenshots.js <REQ-ID>`
   - Ergebnis: `docs/requirements/<REQ-ID>/screenshots/e2e-responsive-{desktop,tablet,mobile}.png`
   - **KEIN MCP nötig!** Das Script nutzt Playwright API direkt.
   - ⛔ Agent 6 (Documentation) KANN NICHT ohne Screenshots arbeiten!

Gib zurück im Format:
E2E_RESULT:
check-e2e: <score>/100 [✅|⚠️|❌]
Scenarios: X/Y passed
Responsive: [desktop ✅❌, tablet ✅❌, mobile ✅❌]
Language: [DE ✅❌, EN ✅❌]
Accessibility: ✅❌
qualitaets.md: ✅ aktualisiert
Issues:
  - <issue1>
CATEGORY_SCORE: <score>/100"
```

---

### Schritt 4: Phase 3 — Agent 6 (Documentation)

> **NACH Agent 5!** Agent 6 nutzt Screenshots von Agent 5.

Starte Agent 6 mit `subagent_type: "general-purpose"`:

#### Agent 6: Documentation
```
Prompt: "Generiere Feature-Dokumentation für '$ARGUMENTS' in allen Doc-Sprachen (DE, EN).

Folge den Anweisungen in .claude/commands/check-documentation.md:

1. Lese Template: docs/requirements/DOKU-TEMPLATE.md
2. Lese Requirement: docs/requirements/<REQ-ID>/requirement.md (Section 1, 2, 4, 11, 14)
3. **PFLICHT: Prüfe ob Screenshots existieren** in docs/requirements/<REQ-ID>/screenshots/:
   - e2e-responsive-desktop.png, e2e-responsive-tablet.png, e2e-responsive-mobile.png
   - Falls Screenshots FEHLEN: Erstelle sie via `node playwright/take-screenshots.js <REQ-ID>`
   - ⛔ **KEINE Dokumentation ohne echte Screenshots generieren!**
   - ⛔ **KEINE Mockups oder Platzhalter-Bilder referenzieren!**
4. Für jede Doc-Sprache (DE, EN):
   a. Setze Sprache via localStorage: localStorage.setItem('app-language', '<lang>')
   b. Erstelle sprachspezifische Screenshots: doc-overview-<lang>.png, doc-step-XX-<lang>.png
   c. Generiere feature-documentation-<lang>.md aus Template
5. Prüfe Qualität: Main Flow vollständig, **Screenshots vorhanden (Dateien auf Disk prüfen!)**, Sprache konsistent

Gib zurück im Format:
DOCUMENTATION_RESULT:
check-documentation: <score>/100 [✅|⚠️|❌]
DE: ✅❌  |  EN: ✅❌
Main Flow Steps: X/Y documented
Screenshots: X vorhanden
Issues:
  - <issue1>
CATEGORY_SCORE: <score>/100"
```

---

### Schritt 5: Berechne Gesamtscore

Gewichtung:
| Kategorie | Gewichtung |
|-----------|------------|
| Architecture | 20% |
| Security | 20% |
| Quality | 20% |
| Feature (i18n, Forms, Language) | 20% |
| E2E Testing | 10% |
| Documentation | 10% |

---

### Schritt 5b: Akzeptanzkriterien prüfen (PFLICHT!)

> ⛔ **BLOCKER! Kein "Ready for PR" ohne 100% AC-Erfüllung!**

1. **Ermittle REQ-ID** aus dem Feature-Namen (z.B. `booking` → `REQ-004-Serviceauswahl`)
2. **Lese requirement.md Section 2** ("Acceptance Criteria") — extrahiere ALLE ACs
3. **Prüfe JEDES AC** gegen Code + Tests:
   - Ist das AC im Code implementiert? (Datei:Zeile als Nachweis)
   - Wird das AC durch einen E2E-Test (TC-X) abgedeckt?
   - Wird das AC durch einen Unit-Test abgedeckt?
4. **Erstelle AC-Matrix:**

```
| AC | Beschreibung | Status | Nachweis (Test / Code) |
|----|-------------|--------|------------------------|
| AC-1 | [Text aus requirement.md] | ✅ Erfüllt / ❌ Nicht erfüllt | [TC-X (E2E), datei.ts] |
| AC-2 | ... | ... | ... |
```

5. **Ergebnis:** X/Y Akzeptanzkriterien erfüllt

> ⛔ **STOP bei nicht erfüllten ACs!** Alle ACs MÜSSEN erfüllt sein für "Ready for PR".
> Falls ACs fehlen → Issues auflisten und Status auf ❌ setzen.

---

### Schritt 6: Erstelle konsolidierten Report

Gib den Report in folgendem Format aus:

**UND generiere automatisch die `qualitaets.md`:**

```
docs/requirements/$REQ_ID/qualitaets.md
```

Nutze das Template aus `docs/requirements/QUALITAETS-TEMPLATE.md` und fülle alle Werte aus.

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔍 FULL CHECK: $ARGUMENTS (6 AGENTS)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📐 ARCHITECTURE (3 checks)
   [status] check-architecture: XX/100
   [status] check-stores: XX/100
   [status] check-routing: XX/100

🔒 SECURITY
   [status] check-security: XX/100

📝 QUALITY (4 checks)
   [status] check-eslint: XX/100
   [status] check-typescript: XX/100
   [status] check-performance: XX/100
   [status] check-styling: XX/100

🌍 FEATURE CHECKS (3 checks)
   [status] check-i18n: XX/100
   [status] check-forms: XX/100
   [status] check-code-language: XX/100

🧪 E2E TESTING (Playwright)
   [status] check-e2e: XX/100
   Scenarios: X/Y passed
   Responsive: [desktop ✅, tablet ✅, mobile ✅]

📄 DOCUMENTATION
   [status] check-documentation: XX/100
   DE: ✅❌  |  EN: ✅❌

✅ AKZEPTANZKRITERIEN
   X/Y erfüllt [✅|❌]
   [AC-Matrix Tabelle]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 GESAMT: XX/100

[Falls Issues vorhanden:]
⚠️ X Warnings:
   - datei:zeile - Issue description

❌ X Errors:
   - datei:zeile - Issue description

[Status basierend auf Score:]
✅ Ready for PR          (Score >= 90, keine ❌, ALLE ACs erfüllt)
⚠️ Review empfohlen      (Score 70-89)
❌ Nicht bereit          (Score < 70 oder kritische Fehler oder ACs nicht erfüllt)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

### Schritt 7: Generiere qualitaets.md

**WICHTIG:** Nach dem Konsolidieren der Ergebnisse:

1. **Ermittle REQ-ID** aus dem Feature-Namen:
   - Feature `header` → suche in `docs/requirements/REQ-*-Header/`
   - Feature `user-management` → suche in `docs/requirements/REQ-*-UserManagement/`

2. **Lese das Template:** `docs/requirements/QUALITAETS-TEMPLATE.md`

3. **Erstelle/Aktualisiere:** `docs/requirements/REQ-XXX-Name/qualitaets.md`
   - Fülle ALLE Scores und Status aus den Agent-Ergebnissen
   - Setze Datum und Uhrzeit
   - Liste alle gefundenen Issues mit Datei:Zeile
   - Berechne Empfehlung basierend auf Gesamtscore
   - **Akzeptanzkriterien Sektion:** AC-Matrix aus Schritt 5b eintragen (PFLICHT!)
   - **E2E Testing Sektion:** Agent 5 hat die E2E-Ergebnisse bereits eingetragen (Schritt 7 aus check-e2e.md).
     Falls nicht: Manuell eintragen mit Playwright Test-Dateien, Szenarien-Aufschlüsselung, Viewports, Issues.
   - Fülle Documentation Sektionen

4. **Aktualisiere Changelog:**
   - Falls die Datei bereits existiert: Füge neuen Eintrag hinzu
   - Falls neu: Erstelle initialen Eintrag

5. **Bestätige:**
```
📄 qualitaets.md erstellt/aktualisiert:
   docs/requirements/REQ-XXX-Name/qualitaets.md
📄 Feature-Dokumentation generiert:
   docs/requirements/REQ-XXX-Name/feature-documentation-de.md
   docs/requirements/REQ-XXX-Name/feature-documentation-en.md
```

---

### Schritt 8: Dev Server stoppen

```bash
kill $(lsof -t -i:4200) 2>/dev/null || true
```

---

## Status-Bedeutung

| Status | Score | Symbol |
|--------|-------|--------|
| Bestanden | 90-100 | ✅ |
| Warnungen | 70-89 | ⚠️ |
| Fehler | <70 | ❌ |

---

## Fehlerbehandlung

Falls ein Agent fehlschlägt:
1. Dokumentiere den Fehler im Report
2. Zeige "N/A" für den fehlgeschlagenen Check
3. Berechne Gesamtscore ohne fehlgeschlagene Kategorie
4. Empfehle manuelle Prüfung

Falls Playwright MCP nicht verfügbar:
1. E2E Tests laufen trotzdem via `npx playwright test` (KEIN MCP nötig!)
2. Screenshots via `node playwright/take-screenshots.js` erstellen (KEIN MCP nötig!)
3. Documentation Agent nutzt die erstellten Screenshots
4. ⛔ **Phase 2+3 NIEMALS überspringen!** Beide sind MCP-unabhängig.

---

## Einzelne Checks

Für granulare Kontrolle können einzelne Checks separat ausgeführt werden:

```
/check-architecture <feature>
/check-security <feature>
/check-eslint <feature>
/check-e2e <feature>
/check-documentation <feature>
... etc.
```

## Gruppierte Checks

```
/check-arch <feature>     # Architecture + Stores + Routing
/check-quality <feature>  # ESLint + TypeScript + Performance + Styling
```
