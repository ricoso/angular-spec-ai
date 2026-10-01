# Qualitätsbericht: REQ-038-Statistik-Dashboard

**Generiert:** 2026-09-25 07:50 (Erstprüfung 24.09.2026, Fix-Runde und Deploy 25.09.2026)
**Feature:** Statistik-Dashboard der Statusseite (Tabs „Statistik | Bugs", Kapazität, Standorte, Servicegruppen, Zeiten, Buchungsstrecke mit Absprungszenarien)
**Gesamtscore:** 91/100 ✅ (Erstprüfung; nach der Fix-Runde nicht neu bewertet)
**Deploy:** Revision `gs-terminbuchungs-app--0000039`, Image `req-038-statistik-dashboard-202609250738`, alle fünf REQ-038-Schalter an (PO-Entscheidung, siehe unten)

> **Hinweis zur Prüfung:** REQ-038 ist ein **reines Backend-Feature** (Spring Boot 4.1 / Java 25). Die
> Statusseite ist serverseitig gerendert, einsprachig deutsch, ohne JavaScript und hinter Basic Auth.
> `src/` ist unverändert (AC-30); es gibt keinen `src/app/features/…`-Ordner, keinen NgRx-Store, keine
> Forms und keine Angular-Komponente. Wie bei REQ-030/REQ-037 wurden die Angular-zentrierten Checks auf
> die realen Artefakte übertragen (Mapping requirement.md §14.0): Controller = Container, zustandslose
> `*SectionRenderer` = Presentational, In-Memory-Stores = Feature Store, Scheduler = Data Loading.

---

## Übersicht

| Kategorie | Gewicht | Score | Status |
|-----------|---------|-------|--------|
| Architecture | 20 % | 89/100 | ⚠️ |
| Security | 20 % | 93/100 | ✅ |
| Quality | 20 % | 89/100 | ⚠️ |
| Feature Checks | 20 % | 90/100 | ✅ |
| E2E Testing | 10 % | 92/100 | ✅ |
| Documentation | 10 % | 94/100 | ✅ |
| **Gesamt (gewichtet)** | **100 %** | **90,8 → 91/100** | ✅ |

Rechnung: `0,20·89 + 0,20·93 + 0,20·89 + 0,20·90 + 0,10·92 + 0,10·94 = 17,8 + 18,6 + 17,8 + 18,0 + 9,2 + 9,4 = 90,8`.

**Akzeptanzkriterien:** 36/36 erfüllt, davon 2 mit ⚠️ (AC-14 Testlücke — in der Fix-Runde geschlossen; AC-20 dokumentierte Abweichung BR-9 Nr. 6).

---

## 📐 Architecture (20 %) — 89/100 ⚠️

| Check | Score | Kern |
|-------|-------|------|
| check-architecture (Backend-Äquivalent) | 81/100 ⚠️ | Request-Pfad ohne I/O (per Abhängigkeitsanalyse und TC-8 belegt), Unterdrückung an genau einer Stelle, 16 Beans mit genau einem `@Autowired`, REQ-030/037 unbeschädigt. Abzüge: Querabhängigkeit capacity → usage.insights und Paketzyklus über `CellHtml`/`esc`, Duplizierung (`resolveZone` ×8, `branchNames` ×4, MDC-Blöcke, Formatter), Fachlogik-Fallbacks und doppelte Texte in Renderern, Rechenarbeit im Request bei leerem Store, toter Code. |
| check-stores (Backend-Äquivalent) | 89/100 ⚠️ | `volatile` + `synchronized`, kein I/O, nur Scheduler/Seed befüllen. Abzüge: `null` bis zum ersten Aufbau (Controller fängt ab), Sicht und Historie nicht atomar gelesen, Startreihenfolge über drei Mechanismen. |
| check-routing (Backend-Äquivalent) | 96/100 ✅ | Alle Mappings ohne 3xx, Catch-all spiegelt keinen Pfad, Kill-Switch-Matrix §5.1 vollständig (TC-7 über 8 Zeilen), alle Pfade hinter der Basic-Auth-Chain. Abzug: Catch-all fing fehlgeschlagene Content-Negotiation ab (404 statt 406). |

**In der Fix-Runde behoben:** neutrales Paket `common/html` (`HtmlText`, `GermanFormat`) — `CapacitySectionRenderer` importiert nichts mehr aus `usage.insights`; `UsageZone`, `BranchNames`, `JobTrace`, `Sleeper` ersetzen die Kopien; Stores halten Sicht + Historie in **einem** `volatile` Snapshot (`snapshot()`); `InsightsScheduler.onStartup` mit `@Order(HIGHEST_PRECEDENCE)`; Catch-all antwortet 406 bei falschem `Accept` auf Literal-Pfaden, `GET /refresh` 404; doppelte Label-Quellen (LEVEL_*/REASON_*, SC_*_HINT, MATRIX_OTHER_BRANCHES, CELL_*) auf eine Quelle reduziert; toter Code (`CAPACITY_SCAN_RUNNING`, tote CSS-Regeln) entfernt.
**Offen:** Europe/Berlin in einigen Renderern fest statt `usage.zone`; Jitter-Logik nicht zusammengeführt.

---

## 🔒 Security (20 %) — 93/100 ✅

Bestätigt: `esc()` für jeden dynamischen Wert (inkl. Standortnamen, Blob-/KQL-Werte, Warnungen), Catch-all ohne Pfad-Reflexion, `style` nur numerisch, KQL ohne Nutzereingaben, alle neuen Pfade hinter `/internal/**` (401 auch für unbekannte Pfade), CSP byte-gleich, `USAGE_BOOKING` nur Whitelist-Felder, `checkInResource` nie gelesen, BR-7-KQL-Regeln wörtlich eingehalten, Blob nur Zähler, MDC-Marker `job.*` nicht per `X-Request-Id` fälschbar, Seed-Profil verweigert den Start bei gesetztem Storage-Account.

| Severity | Befund (Erstprüfung) | Status nach Fix-Runde |
|----------|----------------------|-----------------------|
| LOW | `ERRORS_KQL` prüfte `job.*` als Teilstring irgendwo in der Zeile — ein Kundenfeld (`car.model = "x - job.capacityprobe: y"`) hätte einen echten Fehler aus dem Bugs-Tab ausblenden können | ✅ behoben: `extract(@' - ([A-Za-z0-9_.-]{0,64}): SOFTNET_ERROR', …)` auf das erste (echte) Präfix, `!startswith_cs 'job.'`; Angriffsfall in TC-36 |
| LOW | Schalter uneinheitlich ausgewertet (`=1` → Sektion sichtbar, aber kein Scheduler) | ✅ behoben: Conditions über Spring-`Binder` (true/1/yes/on) |
| LOW | Summenregel BR-9 Nr. 4a: zwei ausgeblendete Zellen mit sichtbarer Summe exakt auflösbar (2 = 1+1, 8 = 4+4) | ✅ behoben: gemeinsame SUM/MARGIN-Regel mit Leak-Test (genau eine / Summe 1–4 / eindeutige Auflösung) |
| LOW | Aufbewahrung hing am Rollup-Cron (`-` schaltete auch Verdichtung/Purge ab) | ✅ behoben: eigener, nicht abschaltbarer Lauf 04:15 |
| LOW | `BOOKINGS_KQL` nicht auf Logger/Präfix verankert | ✅ behoben: verankert auf `' - job.usagebooking: USAGE_BOOKING '` |
| — | `USAGE_BOOKING` über `traceId` mit `BOOKING_CREATED` (Termin-ID) verknüpfbar | ✅ behoben (PO-Entscheidung „nur anonyme Daten, nie wer"): Zeile wird gepuffert und täglich 23:59:59 gebündelt, gemischt, unter `job.usagebooking` geschrieben (Format v2 mit `day`) |

**Restrisiken (offen benannt):** Hat ein Standort an einem Tag genau eine Buchung, lässt sich deren Statistikzeile über Standort + Tag in den Roh-Logs (≈ 30 Tage) weiterhin der einen `BOOKING_CREATED`-Zeile zuordnen — ohne Information, die nicht ohnehin in der Buchung steht. R-1 eines einzelnen „< 5"-Standorts rückrechenbar (BR-9 Nr. 6, akzeptiert). Basic Auth ohne Rate-Limit (Bestand).

---

## 📝 Quality (20 %) — 89/100 ⚠️

| Check | Score | Kern |
|-------|-------|------|
| check-eslint (übertragen) | 90/100 ✅ | `lint:fix` 0 Errors, 255 vorbestehende Warnings in `src/`, keine Datei geändert; REQ-038-Spec und Screenshot-Skript 0/0. javac `-Xlint:all`: 10 neue Warnungen aus REQ-038 (deprecated `JsonNode.fields()`, `serializationInclusion`, fehlende `serialVersionUID`), ungenutzte Imports. |
| check-typescript → Java-Typsicherheit | 84/100 ⚠️ | 0 rawtypes/unchecked. Abzüge: `Cell` ohne erzwungene Invariante, zwei „keine Daten"-Wege, toter/nur-Test-Code, Duplizierung. |
| check-performance | 88/100 ⚠️ | Render 0,9–2,3 ms, kein I/O im Request, nächtliche Last im Rahmen. Abzug: Seitengröße 130 KB statt 80–120 KB (§9). |
| check-styling | 95/100 ✅ | Hex nur in `:root`, 0 px im REQ-038-Block, BR-22-Präfixe, Media-Query-Reihenfolge korrekt; Kontraste nachgerechnet und identisch zu §11.3 (`--ink` auf heat-1…4 11,80/9,41/7,02/4,64, cap-free/-tight/-booked 12,04/11,81/11,04); Fokus, Touch-Ziele, caption/scope, Farbe nie alleiniger Träger. |

**In der Fix-Runde behoben:** `-Xlint:all` 29 → 19 Warnungen, **0 aus REQ-038**; `Cell` normalisiert sich im kompakten Konstruktor (zweite Schutzlinie für AC-20); ungenutzte Imports, vollqualifizierte `java.util.List`, `Optional.isEmpty()+get()` bereinigt; hardcodierte Captions/aria-Labels/Verbindungswörter in Konstanten.
**Offen (dokumentiert, §19):** Seitengröße ≈ 130 KB.

---

## 🌍 Feature Checks (20 %) — 90/100 ✅

| Check | Score | Kern |
|-------|-------|------|
| check-i18n (übertragen) | 88/100 ⚠️ | ngx-translate n/a (einsprachige Statusseite, §17). 190 Konstanten mit §17-Namen byte-gleich, 0 Umlaut-Ersatz. Abzüge: ≈ 30 Texte außerhalb von Konstanten, doppelte Textquellen — **in der Fix-Runde behoben**. |
| check-forms | 100/100 ✅ (n/a) | Keine Formulare; nur der vorbestehende CSS-only-Fehlerfilter des Bugs-Tabs. |
| check-code-language | 100/100 ✅ | Alle Bezeichner, CSS-Klassen, IDs, JSON-Keys, Env-Variablen englisch. |
| Requirement-Konformität | −6 | Begründete Abweichungen waren nur im Code dokumentiert — **in der Fix-Runde in requirement.md §19 (Abweichungen 15–22) nachgetragen**. |

---

## 🧪 Test Coverage

| Bereich | Wert | Ziel | Status |
|---------|------|------|--------|
| Backend Tests | **1328/1328 grün** (118 Suites, Basis vor REQ-038: 431) | alle grün | ✅ |
| Backend JaCoCo LINE gesamt | 94,6 % | 80 % | ✅ |
| `capacity` | 99,4 % | 80 % | ✅ |
| `usage/insights` | 98,8 % | 80 % | ✅ |
| `reporting` | 90,0 % (unberührte REQ-030-Klassen drücken den Wert) | 80 % | ✅ |
| `common/html` | 100 % | 80 % | ✅ |
| Frontend Jest (Statements / Branches / Functions / Lines) | 89,3 / 75,8 / 88,5 / 90,9 % — 736/736 | Thresholds | ✅ |

**Ehrliche Einordnung:** Der Frontend-Jest-Lauf sagt über REQ-038 nichts aus (kein Frontend-Code); er belegt nur, dass das Frontend unverändert grün ist.

---

## 🧪 E2E Testing (Playwright — Lokale Test-Suite) — 92/100 ✅

> **Einordnung:** REQ-038 ist ein reines Backend-Feature: eine serverseitig gerenderte, einsprachig
> deutsche Statusseite hinter Basic Auth (`/internal/report`, CSS inline, kein JavaScript). `src/` ist
> unverändert (AC-30). Die Angular-zentrierten Vorgaben von `check-e2e.md` (Klick-Navigation durch den
> Buchungs-Wizard, `workflow-booking-complete.spec.ts` erweitern, DE/EN-Umschaltung, `take-screenshots.js`)
> werden wie bei REQ-037 auf die realen Artefakte übertragen: Tab-Links statt Wizard-Schritte, eigenes
> Screenshot-Skript statt des generischen.

**Ausführung:** immer über den lokalen Runner (frischer Frontend-Build, frisches Backend im Spring-Profil
`e2e-seed`, Seed-Set `full`, beide Crons aus, alle REQ-038-Schalter an). Vor dem Lauf lauschte nichts auf
:3000 (`lsof -ti:3000 -sTCP:LISTEN` leer), danach wurde das Backend vom Runner gestoppt und der Port war
wieder frei. Das Backend-Log bestätigt `profile active: "e2e-seed"` und
`insights seeded — 94 day(s) 2026-06-22…2026-09-23 (bookings=true, funnel=true, dropoff=true)`.

**Playwright Test-Dateien:**

| Datei | Tests | Viewports | Status |
|-------|-------|-----------|--------|
| `playwright/REQ-038-status-tabs.spec.ts` (neu, 363 Zeilen) | 18 | 3 | ✅ **54/54 passed** |
| `playwright/REQ-037-usage-statistics.spec.ts` (Bestand, unverändert) | 12 | 3 | ✅ 36/36 passed |
| `playwright/REQ-030-report-status-button.spec.ts` (Bestand, unverändert) | 2 | 3 | ✅ 6/6 passed |
| **Gezielter Lauf** `npm run test:local -- REQ-038 REQ-037 REQ-030` | **32** | 3 | ✅ **96 passed, 0 failed, 0 skipped** (18,0 s) |

Die Spec deckt §13.5 vollständig ab (Abgleich unten), deshalb wurde sie **nicht** erweitert.
Produktivcode wurde nicht geändert.

### REQ-038 Test-Szenarien (18 Tests × Desktop/Tablet/Mobile)

| Block | Test | Bezug |
|-------|------|-------|
| Navigation (TC-41) | Vorbedingung: Seed geladen — S0–S4 und S6 ohne „Historie derzeit nicht verfügbar", `cap-table`, `rk-table`, `sg-matrix`, `st-heat`, `fn-steps`, `fn-dropoff` sichtbar | TC-41 „Given" |
| Navigation (TC-41) | Statistik ist Standard-Tab; Klick „Bugs" → `/internal/report/bugs`, `aria-current` Bugs, `.nr`/`.ai` sichtbar; `goBack()` → Statistik; Reload bleibt auf Bugs | TC-41, AC-1, BR-3 |
| Navigation (TC-41) | Link „Statistik" öffnet `/internal/report/statistics` mit denselben Sektionen; kein `[role^=tab]`, kein `form` | TC-41, BR-2 |
| Navigation (TC-42) | Titel „Statusseite – Statistik" bzw. „– Bugs", genau ein `h1` „Statusseite" | TC-42, AC-4 |
| Unterpfade (TC-42, E7) | `/internal/report/xyz` → 404 `text/html`, ohne `app-root`, Titel „Seite nicht gefunden", Links auf die Tabs, Pfad nicht gespiegelt | TC-42, E7 |
| Unterpfade (TC-42, E7) | `/internal/report/a/b` → dito | TC-42, E7 |
| Unterpfade (E8) | `/internal/report/xyz` ohne Credentials → 401 `realm="internal"`, nicht 404 | E8 |
| Unterpfade (E8) | `/internal/report/a/b` ohne Credentials → dito | E8 |
| A11y/Layout (TC-43) | Statistik: 0 `script`, Overflow ≤ 2 px, beide Tab-Links ≥ 44 px hoch | TC-43, AC-2, AC-26 |
| A11y/Layout (TC-43) | Bugs: dito | TC-43 |
| A11y/Layout (TC-43) | Statistik: per Tastatur fokussierter Tab-Link hat `:focus-visible` mit Outline/Box-Shadow | TC-43, AC-27 |
| A11y/Layout (TC-43) | Bugs: dito | TC-43 |
| A11y/Layout (TC-43) | jedes `.st-scroll` hat `role="region"`, `tabindex="0"`, nicht-leeres `aria-label` | TC-43 |
| A11y/Layout (TC-43) | jede Heatmap-, Matrix- und 10-Tage-Zelle trägt ihren Wert als Text | TC-43, BR-23 |
| JSON (TC-44) | `insights.json`: 401 ohne, 200 `application/json` mit Auth; kein `sessionId`, kein `checkInResource`, keine ausgeblendete Zelle mit Wert | TC-44, BR-24, BR-9 |
| JSON (TC-44) | `capacity.json`: dito | TC-44 |
| JSON (TC-44) | `insights.json` mit Seed: `bookingsEnabled`/`funnelEnabled`, Rangliste, Trichter, Impulse; `barWidth = 0` bei jeder Zeile ≠ `VALUE` | TC-44, BR-9 Nr. 5 |
| JSON (TC-44) | `capacity.json` mit Seed: `scanDay`, `last10ScanDays`, jede Zeile mit 10-Tage-Verlauf gleicher Länge | TC-44, N2 |

**Abgleich mit requirement.md §13.5:**

| TC | Forderung | Nachweis | Status |
|----|-----------|----------|--------|
| TC-41 | Tabs, Sektionen `.ib .cap .rk .sg .tm .us .fn .st-limits`, kein `.ai`/`.err`, Klick/Zurück/Reload | 4 Tests (Navigation) | ✅ |
| TC-42 | 404 `text/html` ohne `app-root` für beide Pfade; Titel beider Tabs | 2 + 1 Tests, E8 zusätzlich 2 | ✅ |
| TC-43 | `script` = 0, Overflow ≤ 2, Tab-Links ≥ 44 px, Fokusrahmen, `.st-scroll` beschriftet, Heatmap-Zellen mit Text | 6 Tests | ✅ |
| TC-44 | `insights.json`/`capacity.json` 401/200; REQ-037-Spec im selben Lauf grün (`.us-block == 2`, `.us-empty == 2`) | 4 Tests + REQ-037 12/12 je Viewport | ✅ |
| TC-45 | `git diff --stat main -- src/` leer; REQ-030-Spec grün | Diff 0 Zeilen, keine unversionierten Dateien unter `src/`; REQ-030 2/2 je Viewport | ✅ |

### Workflow-/Regressionstests

| Spec | Bezug | Ergebnis |
|------|-------|----------|
| `REQ-037-usage-statistics.spec.ts` | S5 bleibt die REQ-037-Sektion auf dem Statistik-Tab; Basic Auth, CSP, Leerzustand, `usage.json`, Booking-App lädt, Health grün | ✅ 12/12 je Viewport, unverändert |
| `REQ-030-report-status-button.spec.ts` | Status-Button der Angular-App (direkt sichtbar, im iframe nicht gerendert), TC-45 | ✅ 2/2 je Viewport, unverändert |
| `workflow-booking-complete.spec.ts` | Nicht erweitert: REQ-038 fügt dem Buchungs-Workflow keinen Schritt hinzu (Präzedenz REQ-030/REQ-037). Die Datei steht wie im HEAD unter `describe.skip` | n/a |

**Vollständiger Suite-Lauf von heute** (`npm run test:local` ohne Filter, 11:24, Seed-Set `full`,
Log gegengeprüft): **270 passed, 0 failed, 1218 skipped** — je Viewport 90 passed, davon REQ-038 18,
REQ-037 12, REQ-030 2. Die übrigen 58 grünen Tests je Viewport sind die Booking-, iframe-, CSP- und
Fehler-Specs (u. a. REQ-009 23, REQ-024 10, REQ-023 16, REQ-031 4). Nach diesem Lauf wurde nur noch
`scripts/test-local.sh` angepasst (`lsof … -sTCP:LISTEN`, damit das Screenshot-Skript sich nicht selbst
beendet); der gezielte Lauf oben lief bereits mit dieser Fassung.

> **Die 1218 übersprungenen Tests** sind vorbestehende `test.describe.skip`-Blöcke (u. a. REQ-001 bis
> REQ-008, REQ-010 bis REQ-015, Cart-Overlay, `workflow-booking-complete.spec.ts:87`), unverändert im HEAD.
> REQ-038 hat keinen Test übersprungen oder deaktiviert. Die vier bedingten `test.skip` der REQ-038-Spec
> (drei für `E2E_SEED_SET ≠ full`, einer für `thin`) greifen im Standardlauf mit `full` nicht.

### Viewports

| Viewport | REQ-038 | REQ-037 | REQ-030 | Gezielter Lauf | Voller Suite-Lauf |
|----------|---------|---------|---------|----------------|-------------------|
| Desktop (1280×720) | ✅ 18/18 | ✅ 12/12 | ✅ 2/2 | ✅ 32 passed | ✅ 90 passed |
| Tablet (768×1024) | ✅ 18/18 | ✅ 12/12 | ✅ 2/2 | ✅ 32 passed | ✅ 90 passed |
| Mobile (375×667) | ✅ 18/18 | ✅ 12/12 | ✅ 2/2 | ✅ 32 passed | ✅ 90 passed |

### Sprache

| Sprache | Status | Begründung |
|---------|--------|------------|
| DE | ✅ | Titel, `h1`, Tab-Beschriftungen („Statistik", „Bugs"), 404-Text und Hinweise werden wörtlich geprüft |
| EN | n/a | Die Statusseite ist bewusst einsprachig deutsch (§17): interne Betreiberseite ohne ngx-translate, ohne Sprachumschalter, keine Angular-Komponente |

### Nachlauf nach der Fix-Runde (25.09.2026)

Nach der Fix-Runde (siehe unten) lief die komplette Suite erneut über `npm run test:local`: **270 passed, 0 failed, 1218 skipped** (3,7 min), je Viewport 90 passed, davon REQ-038 18/18. Alle 13 Screenshots wurden danach mit `node playwright/take-screenshots-req038.js` neu erzeugt (alle drei Seed-Sets in einem Lauf).

### Screenshots

Alle 13 Dateien liegen in `./screenshots/` und stammen aus `node playwright/take-screenshots-req038.js`
(eigenes Skript, 8,7 KB; startet je Seed-Set ein frisches Backend über `scripts/test-local.sh serve` und
stoppt es danach). Keine Quelldatei unter `server/src/main`, `playwright/fixtures` oder die Spec ist
jünger als die Aufnahmen (11:31), deshalb wurde das Skript nicht erneut ausgeführt.

| Seed-Set | Dateien | Format |
|----------|---------|--------|
| `full` | `e2e-responsive-{desktop,tablet,mobile}.png` | ganze Seite, 1280/768/375 px breit |
| `full` | `e2e-bugs-{desktop,tablet,mobile}.png`, `e2e-not-found-desktop.png` | Viewport |
| `thin` | `e2e-thin-{desktop,tablet,mobile}.png` (Erfassung seit gestern, kleine Zahlen) | ganze Seite |
| `fail` | `e2e-fail-{desktop,tablet,mobile}.png` (letzter Scan fehlgeschlagen, Rollup ausstehend) | ganze Seite |

Stichprobe gesichtet: Mobile-Statistik zeigt befüllte Heatmaps mit Wert in jeder Zelle, Legende und
horizontal scrollbarer Terminzeit-Tabelle innerhalb der Region; die 404-Seite zeigt „Seite nicht gefunden"
mit Links „Zur Statistik" und „Zu den Bugs".

### Issues

- Keine fehlgeschlagenen Tests, keine Flaky-Wiederholungen.
- Die Zustände der Seed-Sets `thin` und `fail` (E1–E3: Scan fehlgeschlagen, Rollup ausstehend, dünne
  Daten) sind nur als Screenshots belegt, nicht per Assertion. Die Spec läuft ausschließlich mit `full`;
  fachlich prüfen das die JUnit-Tests TC-48–TC-51.
- Alternative Flows §5 (Kill-Switch-Matrix, Auslieferungszustand TC-47 mit 404 auf den JSON-Endpunkten)
  sind nicht E2E abgedeckt, weil das Profil `e2e-seed` alle Schalter einschaltet. Abgedeckt durch
  `ReportControllerTest` (TC-11/TC-13/TC-46) und `Req038DeliveryStateTest`.
- Die E2E-Tests laufen gegen Fixture-Stores, nicht gegen Azure: Erfassung (`USAGE_BOOKING`, Feld `hits`),
  nächtlicher KQL-Rollup und Kapazitäts-Scan gegen SoftNet sind erst nach dem Deploy Ende-zu-Ende prüfbar.
- 401 für `/statistics` und `/bugs` ohne Credentials wird nur im Unit-Test geprüft
  (`ReportControllerTest.refusesEveryPathWithoutCredentials`, parametrisiert über sechs Pfade); E2E prüft
  401 für die unbekannten Unterpfade und die JSON-Endpunkte, REQ-037 für `/internal/report`. Nicht in
  §13.5 gefordert, deshalb kein Abzug.

### Abzüge (−8)

- **−4** Degradationszustände E1–E3 (`thin`/`fail`) nur visuell über Screenshots belegt, kein Playwright-Lauf
  mit Assertions gegen diese Seed-Sets.
- **−2** Alternative Flows §5 (Schalter-Matrix, Auslieferungszustand) im E2E nicht abgedeckt, nur im Unit-Test.
- **−2** Der Datenpfad (Erfassung, Rollup, Scan) ist E2E nicht nachweisbar, weil lokal keine Azure-Anbindung
  besteht; der Seed belegt Rendering, Unterdrückung und Zugriffsschutz, nicht die Pipeline.

---

## 📄 Feature Documentation — 94/100 ✅

- `feature-documentation-de.md` und `feature-documentation-en.md` (gleiche Gliederung): Überblick S0–S7, Aufruf und Zugriff, Ablauf vom Kundenklick bis zur Zahl (inkl. Tagesflush 23:59:59 und Aufbewahrungslauf 04:15), alle Sektionen mit echten Screenshots, Zustände befüllt / zu wenig Daten / Scan fehlgeschlagen, Messgrenzen, Datenschutz in einfachen Worten (inkl. Restrisiken), Betrieb (Schalter, Gates, PO-Aktivierungsentscheidung, Env, Verifikation), bekannte Abweichungen.
- 13 echte Screenshots in `./screenshots/`, Mockup nur als Designreferenz.
- Abzüge: Hintergrundschritte ohne UI nur textlich belegt; breite Tabellen im Full-Page-Screenshot nur bis zur Scroll-Kante sichtbar.
- Nach der Fix-Runde aktualisiert: requirement.md (§1–§19 inkl. neuer Abweichungen 15–22 und Tabelle „Aktivierung"), Feature-Doku DE/EN, `docs/infrastructure/azure-deploy.md` §9.

---

## ✅ Akzeptanzkriterien (Acceptance Criteria)

**Ergebnis: 36/36 erfüllt (davon 2 mit ⚠️).**

| AC | Kurzbeschreibung | Status | Nachweis Code | Nachweis Test (Unit/Integration) | Nachweis E2E |
|----|------------------|--------|---------------|----------------------------------|--------------|
| AC-1 | `/internal/report` und `/internal/report/` antworten 200 und zeigen direkt Statistik; `/statistics` und `/bugs` per Deep-Link, Reload und Zurück erreichbar | ✅ | `reporting/ReportController.java:197-221` (report/statistics/bugs, kein Redirect) | `ReportControllerTest.AllSourcesEnabled#rendersTheStatisticsTabByDefault` (TC-1: beide Pfade, 200 text/html, kein `Location`); `#servesOneDocumentPerTab` (TC-2) | TC-41 „statistics is the default tab … back and reload keep the tab“; TC-41 „Statistik link opens /statistics“ |
| AC-2 | `nav.tabs` mit aria-label und genau 2 Links, `aria-current`; kein `role=tab*`, `<script>`, `<form>`, `<img>`; CSP byte-gleich | ✅ | `ReportHtmlGenerator.java:67,411-423` (navigation), `:380-404` (frame); `config/SecurityConfig.java:44` unverändert (nicht im Diff) | `ReportHtmlGeneratorTest.Frame#rendersTheStatisticsFrame` / `#rendersTheBugsFrame` (TC-12: exakter nav-String, genau 1× aria-current, kein script/form/img/role=tab); `ReportControllerTest#needsNothingTheContentSecurityPolicyForbids` (TC-5: CSP == `EXPECTED_CSP` auf beiden Tabs und der 404-Seite, keine Client-Hint-Header) | TC-41 (`[role^=tab]` = 0, `form` = 0); TC-43 (`script` = 0, `nav.tabs a` = 2) |
| AC-3 | Bugs-Tab zeigt das Fragment byte-gleich bzw. `.nr` plus Meta-Zeile; Statistik-Tab ohne `.ai`/`.ov`/`.err`/`.nr` | ✅ | `ReportController.java:218-221,469-471`; `ReportHtmlGenerator.java:108,263-266` | `ReportControllerTest#servesOneDocumentPerTab` (`<main id="content">`+Fragment+`</main>`, Meta-Zeile), `#showsTheMissingReportHintOnTheBugsTabOnly` (TC-3), `#rendersTheStatisticsTabByDefault` (TC-1); `ReportHtmlGeneratorTest#embedsTheFragmentByteIdentical` (inkl. CSS-Filter) | TC-41 (`BUGS_SELECTOR` = 0 auf Statistik; `.nr`/`.ai` auf Bugs sichtbar) |
| AC-4 | `<title>` je Tab, genau eine H1 „Statusseite“, `main#content` ohne section-Wrapper; „Inhalt“ listet genau die gerenderten Sektionen | ✅ | `ReportHtmlGenerator.java:380-404`; `StatisticsPageRenderer.java:138-209` | `ReportControllerTest#framesBothTabs` (TC-4: `anchorsOf == tocLinks`), `StandaloneCases#framesBothTabsForRowThree`; `StatisticsPageRendererTest#listsOnlyRenderedSectionsInTheToc`, `#rendersNoTocWithoutAnySection` | TC-42 „each tab has its own title and exactly one h1“ |
| AC-5 | Unbekannte Unterpfade: 404 text/html mit Tab-Links, nie Angular-Shell; ohne Login 401 | ✅ | `ReportController.java:293-296`; `ReportHtmlGenerator.java:276-289` (Pfad wird nie ausgegeben) | `ReportControllerTest#answersUnknownSubPathsWith404` (TC-6: xyz, a/b, statistik; kein `<app-root`, kein Pfad im Body), `#refusesEveryPathWithoutCredentials` (401 + realm), `StandaloneCases#prefersLiteralMappingsOverTheCatchAll`; `ReportHtmlGeneratorTest#rendersTheNotFoundPage` | TC-42: 404 × 2 (xyz, a/b); E8: 401 × 2 |
| AC-6 | Kill-Switch-Matrix, alle 8 Zeilen von §5.1 | ✅ | `ReportController.java:343-375` (Tab ⇔ usage ∨ insights ∨ capacity; Trichter nur mit usage), `:384-403`; `StatisticsPageRenderer.java:138-175`; `usage/insights/InsightsSchedulerCondition.java:33-38` | `ReportControllerTest#followsTheKillSwitchMatrix` (TC-7, alle 8 Zeilen: Navigation, Inhalt `/internal/report`, Status von `/statistics`, insights/capacity/usage.json, Sektionsliste und TOC, Navigation auf `/bugs`, nie „nicht verf“; Zeile 5 ohne `.ib/.rk/.sg/.tm/.st-empty`; Zeilen 6–8 mit funnel=true als „beliebig“); `UsageDisabled#rendersTheBugsContentWithoutNavigation` (TC-11); `StandaloneCases#servesTheFunnelOnlyView` / `#servesTheBookingsOnlyView` (TC-46); `StatisticsPageRendererTest` Zeilen 2/5/7 mit echten Renderern; `Req038DeliveryStateTest.DeliveryState#rendersRowOneOfTheKillSwitchMatrix` (Zeile 1, echter Kontext) | nur Zeile 4 (Seed): TC-41 alle 8 Sektionen sichtbar |
| AC-7 | Nur In-Memory-Snapshots; kein Log Analytics, Blob oder SoftNet im Request; < 200 ms | ✅ | `ReportController.java:384-449` (liest nur UsageStatsStore/InsightsStore/CapacityStore; Konstruktor ohne Clients oder Scheduler) | `ReportControllerTest.AllSourcesEnabled#readsOnlyInMemorySnapshots` (TC-8: 20 GETs über alle 8 Pfade → `verifyNoInteractions` auf LogAnalyticsClient, InsightsBlobClient, CapacityBlobClient, SoftnetApiClient, Aggregator, UsageBlobClient); `UsageEnabled#readsOnlyTheSnapshotWithoutAnyBackendQuery` | — (200 ms nicht gemessen; §9 legt `verify(never())` als Nachweis fest, Begründung in `ReportControllerTest.java:800-805`) |
| AC-8 | Nachtscan per Cron plus Jitter 0–300 s, sequenziell, alphabetisch, 1 s Pause, nur `getSnaps` mit Wishlist §12.9, Ergebnis in `capacity-daily.json` | ✅ | `capacity/CapacityScanScheduler.java:84,93,96,205-209,271,615-621`; `capacity/CapacityProbe.java:118-146`; `application.yml:87-92` | `CapacityScanSchedulerTest#scansAllBranchesAlphabeticallyWithOneGroupsCall` (TC-34: Reihenfolge, 2 × 1 s, 1× getGroups, nie getGroup), `#waitsAtMostTheMaximumJitter`, `#exposesTheGlossaryConstants`, `#isWiredBySpringOnlyWhenEnabled`; `CapacityProbeTest#sendsExactlyTheSpecifiedWishlist` (TC-31, `verifyNoInteractions(snapsService, snapCache)`); `CapacityBlobClientTest` (TC-24); Default-Cron in `Req038DeliveryStateTest#shipsEveryNewSwitchOff` | — (Crons im E2E aus) |
| AC-9 | S1: Textstufen, 10-Tage-Raster, „an x von n Tagen frei“ mit Einordnung, Sortierung, Beschriftung relativ zum Abruftag, K-2 nur als Gesamtzahl | ✅ | `capacity/CapacityViewModelBuilder.java:142,227-243`; `capacity/CapacitySectionRenderer.java:223ff`; Neuaufbau 00:05 `CapacityScanScheduler.java:247` | `CapacitySectionRendererTest#rendersAllFiveLevelsAsPills`, `#rendersTheTenDayGridOldestFirst` (TT.MM., älteste links, 50 Zellen je mit Text), `#rendersSummaryAndClassification`, `#keepsTheViewModelOrder`, `#rendersTheSlotTotalWithoutSuppression` (kein cap-slots/-mini/-spark), `#omitsTheSlotColumnWithoutTheSwitch`, `#rendersNoteAndLegend` (TC-37); `CapacityViewModelTest#labelsTheFreeLevelRelativeToToday` (TC-54), `#labelsTheOtherLevels`, `#mapsCellsAndClassifiesTheTenDayWindow` (TC-58), `#sortsRowsPerBr26` (TC-59); `CapacityProbeTest#countsDistinctSlotsOnTheTargetDayOnly` (TC-32); `CapacityScanSchedulerTest#relabelsAtMidnightWithoutIo` | TC-43: jede `td.cap-day` mit Text; TC-44: capacity.json `last10` je Zeile |
| AC-10 | Fehlgeschlagener Standort „nicht messbar“ mit Grund, nie 0; Fallback-Scan mit `.st-warn`, Partial-Hinweis; I-1 bis I-3 entfallen | ✅ | `CapacityViewModelBuilder` (Fallback), `CapacitySectionRenderer`; `reporting/BriefingCalculator.java:106ff` (BR-14) | `CapacitySectionRendererTest#failedBranchIsNeverShownAsZero`, `#rendersTheFallbackScanWithDateAndWarning`, `#rendersPartialFallbackAndAllBranchesFootnote`; `CapacityViewModelTest#fallsBackToTheLastCompleteScan`, `#warnsAboutAPartialScan` (TC-51a); `CapacityProbeTest` TC-33 (7 Fehlerbilder, nie NONE/0); `BriefingCalculatorTest#dropsTheCapacityImpulsesWhileAFallbackScanIsShown` (TC-30); `ReportHtmlGeneratorTest#namesTheFailedScanAndTheFallback` | — |
| AC-11 | Fehler aus Scan und Rollup erscheinen nicht im Bugs-Tab | ✅ | `reporting/ReportAggregator.java:66-74` (`!contains_cs '- job.capacityprobe:'` / `'- job.insightsrollup:'`); Log-Muster `application.yml:100`; MDC in `CapacityScanScheduler.java:216-237` | `ReportAggregatorTest#errorsQueryExcludesTheBackgroundJobPrefixes`, `#excludesExactlyTheMarkersTheJobsSet`, `#jobErrorsNeverReachTheErrorListButCustomerErrorsAlwaysDo` (TC-36; der Mock wertet `!contains_cs` echt aus); `RequestIdFilterTest` TC-36 (Header `job.*` → UUID); `InsightsSchedulerTest#runsUnderTheJobTraceId` | — |
| AC-12 | Höchstens ein Scan je Kalendertag über alle Replicas (Claim per ETag), Abbruch nach 20 min; `refresh` startet weder Scan noch Rollup | ✅ | `CapacityScanScheduler.java:102,105,263,394-445`; `ReportController.java:311-335` (keine Abhängigkeit zu Scan oder Rollup) | `CapacityScanSchedulerTest#skipsAFreshForeignClaim`, `#takesOverAnOrphanedClaim`, `#skipsADayThatIsAlreadyScanned` (COMPLETE/PARTIAL/FAILED), `#reReadsAfterAClaimConflict` (412), `#stopsAtTheTwentyMinuteLimit` (TC-34); `ReportControllerTest#refreshTriggersNeitherScanNorRollup` (TC-10) | — |
| AC-13 | Eine `USAGE_BOOKING`-Zeile nach erfolgreicher Buchung, nur Whitelist-Felder; Logger-Fehler ändert nie die Antwort; aus → keine Zeile | ✅ | `appointments/AppointmentsService.java:106,139-150` (eigenes try/catch); `usage/insights/UsageEventLogger.java:69,120-122` | `AppointmentsServiceTest#createAppointment_insightsEnabled_writesExactlyOneLineAfterBookingCreated`, `#…insightsDisabled_writesNoUsageBookingLine` (TC-14a), `#…softnetFails_writesNoUsageBookingLine` (TC-14b), `#…usageLoggerThrows_keepsResponseAndWarnsWithoutValues` (TC-14c); `UsageEventLoggerTest#writesExactlyTheWhitelistedFieldsInFixedOrder` (TC-13), TC-15, TC-16; `Req038DeliveryStateTest#writesHitsAndUsageBookingWithEverySwitchOn` / `#leavesNoReq038TraceOfASearchAndABooking` | — |
| AC-14 | Rollup 03:45, höchstens 3 KQL, Nachholen bis 25 Tage, nie vor Erfassungsbeginn oder in verdichtete Monate, `If-Match` mit max. 3 Versuchen | ⚠️ | `usage/insights/InsightsScheduler.java:87,90,244,333-345,405-425`; `InsightsBlobClient.java:107` | `InsightsSchedulerTest#catchesUpTheGapsButNeverBeforeTheCollectingStart`, `#twoRunsProduceAnIdenticalFile`, `#neverQueriesACompactedMonth` (TC-22), `#mergesOverTheOtherReplicasStateAfterAConflict`, `#givesUpAfterThreeConflicts`, `#onlyTheFunnelRunsExactlyOneQuery` (TC-46); `InsightsAggregatorTest#theDropoffQueryReplacesTheFunnelQuery` (≤ 3 Abfragen); `InsightsBlobClientTest#savesWithIfMatchAndMapsA412ToAConflict`. **Testlücke:** die 25-Tage-Grenze (`CATCH_UP_DAYS`) wird nirgends als Grenzfall geprüft | — |
| AC-15 | S2: Rangliste Vormonat; unter 5 Buchungen in `tbody.rk-low` ohne Rang; monoton | ✅ | `usage/insights/InsightsViewModelBranches.java:89`; `BranchRankingSectionRenderer.java:111-176` | `InsightsViewModelTest#ranksDescendingWithTiesByName`, `#rankMoves`, `#belowThreshold`, `#monotoneWithRelativeBars`, `#jsonCarriesNoHiddenValue` (TC-29/TC-57); `BranchRankingSectionRendererTest#rendersARankedRow`, `#rendersEveryRankChange`, `#rendersTheBelowThresholdGroupLast`, `#zeroBranchShowsZeros`, `#headNamesApproximationsAndOptionalColumns` | TC-41: `.rk table.rk-table`; TC-44: `barWidth = 0` bei verdeckten Zeilen |
| AC-16 | S3: G-1 als Balkenliste, G-2/G-3 als Heatmap Vormonat mit Prozent und Farbstufe; eigene Zeile erst ab 20 Buchungen | ✅ | `InsightsViewModelBranches.java:373-376`; `ServiceGroupSectionRenderer.java:83ff` | `ServiceGroupSectionRendererTest#rendersGroupBars`, `#rendersTheMatrixTable`, `#rendersHeatCells` (TC-53), `#rendersTheCollectiveRow`; `InsightsViewModelTest#columns`, `#ownRowsAndCells`, `#collectiveRowWithThreeBranches`, `#collectiveRowWithTwoBranches`, `#hiddenCellsHaveNoHeat` (TC-26); `ServiceGroupClassifierTest` (TC-20) | TC-41: `.sg table.sg-matrix`; TC-43: jede Matrixzelle mit Text |
| AC-17 | S4: T-2 Kachel, T-1 7×3, T-3 Mo–Sa×5, T-5 Balken; Monat und Jahr untereinander, ohne Umschalter | ✅ | `TimingSectionRenderer.java:99ff`; `InsightsViewModelPeriods.java` | `TimingSectionRendererTest#rendersSectionFrame` (2× `st-grid`), `#rendersTheOffHoursTile`, `#rendersBookedWhenHeatCells` (Mo–So × 3), `#rendersSlotTableWithLegendAndWithoutEmptySunday`, `#rendersLeadTimeBars`; `InsightsViewModelPeriodsTest` TC-27/TC-53 | TC-41: `.tm table.st-heat` |
| AC-18 | S0: Impulse I-1 bis I-4 nach BR-21; ohne Datenbasis Leertext | ✅ | `BriefingCalculator.java:106ff`; `BriefingSectionRenderer.java:37,75` | `BriefingCalculatorTest#namesThePromoCandidatesOfTheLowerThird`, `#skipsAMixedBranch`, `#namesTheBottlenecksOnly`, `#namesTheTopThreeAndTheOffHoursShare`, `#yieldsAnEmptyBriefingWithoutAnyDataBasis` (TC-30), TC-54 (Titel); `BriefingSectionRendererTest#rendersTheEmptyStateWithoutAnyImpulse`, `#rendersOneTilePerImpulse` | TC-44: `briefing.impulses.length > 0`; TC-41: `.ib` |
| AC-19 | S6: globaler 5-Stufen-Trichter, Ausstieg je Geräteklasse, „Sonstige“ unter 20; aus → keine Sektion und keine Funnel-KQL | ✅ | `FunnelSectionRenderer.java:132ff`; `InsightsViewModelFunnel.java`; `InsightsAggregator.java:140` | `FunnelSectionRendererTest#rendersTheFiveStages`, `#rendersTheDropoutTableWithMergedColumn`, `#onlyAllColumnWithoutOwnClasses` (TC-52), `#switchedOffRendersNothing`; `InsightsViewModelFunnelTest` TC-52 (1)–(4) mit `assertRowsAddUp`; `InsightsAggregatorTest#cumulatesTheFunnelFromMaxStepRows` (TC-19), `#onlyBookingsRunsExactlyTwoQueries` (keine Funnel-KQL) | TC-41: `.fn ol.fn-steps` |
| AC-20 | Zellunterdrückung in HTML und JSON inkl. Sekundärunterdrückung; auch mittelbar nie ein verdeckter Wert | ⚠️ | `usage/insights/InsightsViewModel.java:64-79`; `InsightsViewModelSuppression.java`; `CellHtml.java` | `InsightsViewModelTest` TC-25 (5 Tests), TC-26, TC-56 (`#repairColumn`, `#offHoursColumn`, `#hiddenSumOneToFour`); `InsightsViewModelSuppressionTest#randomClosedMonthsNeverRevealAHiddenCell`, `#randomPeriodsNeverKeepASingleHiddenCell` (Eigenschaftstests); `CellHtmlTest#hiddenCellNeverLeaksNumberHeatOrWidth` (TC-57); `ReportControllerTest#servesTheInsightsViewAsJson` (TC-9: unterdrückte Zelle exakt ohne Wert). **Dokumentierte Abweichung:** BR-9 Nr. 6 / §19 Nr. 5, R-1 „< 5“ bleibt rückrechenbar | TC-44: `hiddenCellsWithValues(json) == []` für beide JSONs |
| AC-21 | Standortwerte nur für abgeschlossene Monate; keine Kombination Standort × Stunde/Tag/Gerät/Funnel; keine Marke, Modell, Kunde, SB, Wiederkehr | ✅ | Strukturell: `BranchCounts.java:18-24`, `BranchRankingRow.java:24-35`, `InsightsViewModel.java:36-52` ohne solche Felder; `FUNNEL_KQL`/`DROPOFF_KQL` ohne Standort-Extraktion | `InsightsViewModelTest#noClosedMonthYet`, `#januaryHasNoYearToPreviousMonth`, `#partialClosedMonth` (TC-28); `UsageEventLoggerTest#writesExactlyTheWhitelistedFieldsInFixedOrder` (TC-13: kein brand/model/sessionId/snapId); `InsightsAggregatorTest#theBookingQueriesCarryNoSessionAndNoDevice`, `#neverExposesTheFullTuple` (BR-30) | TC-44: JSON ohne `sessionId`/`checkInResource` |
| AC-22 | String-Test über alle KQL-Konstanten (kein join/traceId, nie sessionId + branchId; 3a/3b ohne URL und ohne `Log_s` nach `project`) | ✅ | `usage/insights/InsightsAggregator.java:107-217` | `InsightsAggregatorTest#noQueryCombinesSessionIdWithABranchOrJoins` (alle 4 Konstanten: kein join/traceId/dcount, nie SessionId zusammen mit BranchId bzw. `([0-9a-f]{24})`, ContainerAppName-Filter), `#theSessionQueriesNeverExposeAUrlOrTheRawLine` (3a/3b: keine Capture-Gruppe, kein `"url":"(`, kein `Url`, genau ein `project`, danach kein `Log_s`) (TC-17) | — |
| AC-23 | Vor Erfassungsbeginn „—“ mit „Erfassung seit …“, nie 0 | ✅ | `CellHtml.java` (Leerzustand), alle Renderer | `InsightsViewModelTest#neverCollected`, `#closedMonthWithoutData`; `InsightsViewModelPeriodsTest` (fehlende Maps → „—“); `ServiceGroupSectionRendererTest` / `TimingSectionRendererTest#periodWithoutDataShowsTheEmptyState`; `FunnelSectionRendererTest#stagesWithoutDataShowTheEmptyState`; `LimitsSectionRendererTest#rendersTheDashForAMissingDate`; `CellHtmlTest#emptyStateNamesTheCollectionStart`; `InsightsBlobClientTest#missingCountersStayNull` | — |
| AC-24 | `usage-daily.json` unverändert; neue Blobs nur Zähler, Enums, Tage, Scan-Zeiten; `branchDays` am 8. verdichtet; Retention 24 Monate auch bei abgeschalteten Schaltern | ✅ | `UsageBlobClient`/`DailyUsage` nicht im Diff; `InsightsRetentionJob.java:62,103,119,192,244`; Purge im Scan-Ergebnis-Write | `InsightsRetentionJobTest#doesNotCompactBeforeTheEighth`, `#compactsTheClosedMonthOnTheEighth`, `#purgesEverythingBeyondTheRetention`, `#compactsAndPurgesOnItsOwnWhenTheSwitchesAreOff`, `#touchesOnlyTheInsightsBlobOverTheWire` (TC-23); `InsightsBlobClientTest#roundTripsTheDocumentedExample`, `CapacityBlobClientTest#roundTripsTheSpecExample` (TC-24); `CapacityScanSchedulerTest#purgesScansBeyondTheRetention`; `Req038DeliveryStateTest#createsNeitherJobScheduler` (Retention-Bean existiert auch bei Schaltern aus); `UsageBlobClientTest` unverändert | — |
| AC-25 | `insights.json`/`capacity.json` hinter Basic Auth, dasselbe unterdrückte View-Model wie die Seite; Quelle aus → 404; ohne Login 401 | ✅ | `ReportController.java:250-285` (serialisiert dieselben `currentInsights`/`currentCapacity` wie die Seite) | `ReportControllerTest#servesTheInsightsViewAsJson`, `#servesTheCapacityViewAsJson` (TC-9), `#refusesEveryPathWithoutCredentials`, `#followsTheKillSwitchMatrix` (404-Spalten), `#servesTheFunnelOnlyView` (TC-46); `Req038DeliveryStateTest#answersTheNewJsonEndpointsWith404` | TC-44 × 4 (401 ohne, 200 mit Login, Seed-Inhalte) |
| AC-26 | WCAG 2.1 AA: aktiver Tab mit 0.2em-Unterstrich und fett, Touch-Ziel 2.75em, `:focus-visible`, Heatmaps als Tabellen, Werte als Text, Kontrast ≥ 4.5:1 | ✅ | CSS in `ReportHtmlGenerator.java:677-681` (Tokens), `:763-767` (Tabs), `:808-809` (`.st-heat td{color:var(--ink)}`); `CellHtml.java:315-319` | `ReportHtmlGeneratorTest#stylesTheActiveTabNotByColourAlone`, `#definesTheNewTokensInRoot`; `CellHtmlTest.assertMarkupContract` in allen Renderer-Tests (caption je Tabelle, scope je th); `ServiceGroupSectionRendererTest#rendersGroupBars` (role=img, aria-label, Wert als Text). Kontrast nicht automatisiert, aber nachgerechnet: `--ink` auf heat-1…4 = 11,80 / 9,41 / 7,02 / 4,64:1, auf cap-free/-tight/-booked = 12,04 / 11,81 / 11,04:1, deckungsgleich mit §11.3 | TC-43: Tab-Links ≥ 44 px, sichtbarer Tastatur-Fokus (beide Tabs), Text in jeder Heat-, Matrix- und Verlaufszelle |
| AC-27 | Kein horizontales Scrollen bei 375/768/1280 px; breite Tabellen in Scroll-Container mit `role`, `tabindex`, `aria-label` | ✅ | `CellHtml.java:319`; CSS `ReportHtmlGenerator.java:790-803` | `CellHtmlTest#scrollRegionCarriesItsAttributes`; `assertMarkupContract` in allen Renderer-Tests | TC-43 „no horizontal page scrolling“ (Statistik und Bugs, Überlauf ≤ 2 px) in 3 Viewports (`playwright-backend.config.ts:28-45`); TC-43 „every scroll container is a labelled, focusable region“ |
| AC-28 | REQ-037-Verträge bleiben: `.us-block`/`.us-empty` je 2, REQ-037-Spec unverändert grün, `usage.json` und `refresh` unverändert | ✅ | `StatisticsPageRenderer.java:166-168` (S5 unverändert); `playwright/REQ-037-usage-statistics.spec.ts` nicht im Diff | `ReportControllerTest.UsageEnabled#servesTheUsageAggregateAsJson`, `#refreshUpdatesTheStatisticsWithoutTheLlmToggle`, `RefreshThrottle#throttlesTheSecondRefreshWithoutQueryingTheBackend`, `#refreshTriggersNeitherScanNorRollup`; `UsageSectionRendererTest` unverändert; `StatFormatTest` (TC-39); Renderer-Tests „no .us-* class“ | REQ-037-Spec 12 × 3 grün im selben Lauf |
| AC-29 | Einschränkung als Text neben K-1/K-2/R-4; S7 immer am Ende mit „Erfassung seit …“ je aktiver Quelle | ✅ | `StatisticsPageRenderer.java:173-174`; `LimitsSectionRenderer.java:123ff` | `CapacitySectionRendererTest#rendersTheSlotTotalWithoutSuppression` („Referenz-Leistung, Näherung“), `#rendersNoteAndLegend`; `BranchRankingSectionRendererTest#headNamesApproximationsAndOptionalColumns`; `LimitsSectionRendererTest#namesTheCollectionStartOfEveryActiveSource`, `#showsOnlyTheUsageSourceForTheDefaultDeployment`; TC-7 (`st-limits` in den Zeilen 1–7 stets zuletzt) | TC-41: `section.st-limits` |
| AC-30 | Unter `src/` keine Änderung; Frontend-Button öffnet weiter `/internal/report` | ✅ | `git status --short -- src/` leer; `git diff --stat main -- src/` leer; `location-selection-container.component.ts:46-48` (`window.open('/internal/report', …)`) | — | REQ-030-Spec 2 × 3 grün (TC-45) |
| AC-31 | Ohne Env-Override alle neuen Schalter `false`: §5.1 Zeile 1, kein `USAGE_BOOKING`, kein `hits`, keine KQL 1–3, kein Scan, JSON 404 | ✅ | `application.yml:67-92`; Beans mit `@ConditionalOnProperty` bzw. `InsightsSchedulerCondition` | `Req038DeliveryStateTest.DeliveryState` (echter `BackendApplication`-Kontext, abgeschottete Umgebung): `#shipsEveryNewSwitchOff`, `#createsNeitherJobScheduler`, `#rendersRowOneOfTheKillSwitchMatrix`, `#answersTheNewJsonEndpointsWith404`, `#leavesNoReq038TraceOfASearchAndABooking`, `#runsNoJobAtTheNightlySlots` (22:30 und 03:45 simuliert: keine KQL, kein SoftNet, kein Blob-load/save) (TC-47); `PropertiesTest#req038Properties_bindTheShippedDefaultsOfApplicationYml` (TC-40) | — (E2E läuft bewusst mit allen Schaltern an) |
| AC-32 | Ohne Storage oder bei 403/Netzfehler: kein Scan, alle Seiten 200, S1–S4 zeigen „Historie derzeit nicht verfügbar“, nie 5xx | ✅ | `CapacityScanScheduler.java:207-209,394-445`; `ReportController.java:416-449` | `CapacityScanSchedulerTest#doesNotScanWithoutBlobStorage` (TC-35), `#skipsTheScanWhenTheClaimReadFails` (403), `#skipsTheScanWhenTheClaimCannotBeWritten`, `#showsTheHistoryHintUntilALoadSucceeds` (TC-48); `InsightsSchedulerTest#theHistoryWarningDisappearsAfterALaterSuccessfulLoad`, `#withoutStorageTheViewCarriesTheWarningOrKeepsASeededView`; `ReportControllerTest.StandaloneCases#namesTheMissingHistoryInsteadOfNumbers` (TC-48: `.st-warn` je Sektion S1–S4, JSON-`warnings`), `#rendersWithoutBranchNames` | TC-41-Vorbedingung (Seed statt Fallback) |
| AC-33 | Jede Sitzung mit MaxStep < 5 erhält genau ein Szenario nach Priorität; Σ Szenarien = erreicht(1) − erreicht(5); `booked` ⇔ MaxStep 5 | ✅ | `InsightsAggregator.java:169-217` (`case(...)` in Katalog-Reihenfolge), `:488-494` (booked ⇔ 5), `:539-542` (Plausibilitätsprüfung mit WARN) | `DropoffClassificationTest#theKqlCaseFollowsTheCatalogueOrder` (liest die echte Konstante aus), `#classifiesTheFixtureSessions` (a)–(g), `#classifiesTheRemainingCatalogueRows`, `#foldsTheClassifiedSessionsIntoAConsistentDay` (Σ über den echten Aggregator), `#discardsAnInconsistentBookedRow` (TC-60); `InsightsAggregatorTest#foldsDropoffRowsIntoTheTwoMargins` (TC-62) | TC-41-Vorbedingung: `section.fn-dropoff` sichtbar (nur vorhanden, nicht inhaltlich) |
| AC-34 | `hits` nur bei `POST /api/v1/snaps` mit Status < 400 und allen drei Schaltern an; sonst byte-gleiche Access-Zeile | ✅ | `common/filter/LoggingFilter.java:35,42,84,151-160`; `snaps/SnapsController.java:34-35` | `LoggingFilterTest#appendsHitsAtTheEndOfASuccessfulSearchLine`, `#hitsLineIsTheReq037LinePlusOneTrailingField`, `#keepsTheReq037LineWithoutAllThreeSwitches`, `#omitsHitsForFailedSearches`, `#omitsHitsWhenTheChainThrows`, `#omitsHitsForEveryOtherRequest`, `#ignoresForeignAttributeValues` (TC-61/BR-29); `SnapsControllerTest` TC-61 (a)–(e); `Req038DeliveryStateTest#leavesNoReq038TraceOfASearchAndABooking` (Regex = `ACCESS_PATTERN_WITH_DEVICE`), `#writesHitsAndUsageBookingWithEverySwitchOn` | — |
| AC-35 | `usage.dropoff.enabled=false`: kein `.fn-dropoff`, kein `hits`, kein `dropoff` im Blob, Abfrage `FUNNEL_KQL`; vor Erfassungsbeginn „—“ | ✅ | `ReportController.java:363-365,426-433`; `InsightsScheduler.java:418-422`; `FunnelSectionRenderer.java` | `FunnelSectionRendererTest#noDropoffSubsectionWhileSwitchedOff`, `#emptyDropoffShowsTheEmptyState`; `InsightsSchedulerTest#usesTheFunnelVariantWhileTheDropoffIsOff`, `#usesTheDropoffVariantOnlyFromItsCollectingStart` (TC-62); `InsightsViewModelFunnelTest#switchedOffOrNeverStarted`, `#missingDropoffIsNoData` (TC-63); `ReportControllerTest#rebuildsAViewWithDropoffDataWhileDropoffIsOff`; `LimitsSectionRendererTest#omitsTheDropOffScenariosWhileSwitchedOff`; `LoggingFilterTest#keepsTheReq037LineWithoutAllThreeSwitches` | — |
| AC-36 | Dropoff-Tabellen nach BR-9: 1–4 → „< 5“, Sekundär „k. A.“, Anteile ab 20, „Sonstige“, Leerzustand `.st-empty` | ✅ | `InsightsViewModelFunnel.java` (`MIN_DROPOFFS_DEVICE`, `InsightsViewModel.java:79`); `FunnelSectionRenderer.java` | `InsightsViewModelFunnelTest#suppressionAndSumRow`, `#deviceColumns`, `#noRecomputableCell`, `#rowOrder`, `#quietRowAndHints` (TC-64); `InsightsViewModelSuppressionTest#randomDropoffTablesAreProtected`; `FunnelSectionRendererTest#rendersTheStageTableWithSuppression` (nie „•“), `#rendersTheDeviceTable`, `#emptyDropoffShowsTheEmptyState` | TC-44: `hiddenCellsWithValues` (deckt `dropoff` im JSON mit ab) |

**Einordnung der zwei ⚠️:**
1. **AC-14:** Die 25-Tage-Grenze des Nachholens war ungetestet — **in der Fix-Runde geschlossen** (`InsightsSchedulerTest#catchesUpAtMostTwentyFiveDays`).
2. **AC-20:** R-1 „< 5" bleibt bewusst ohne komplementäre Unterdrückung (BR-9 Nr. 6, §19 Nr. 5) — Kenntnisnahme durch den DSB (G-DSB).

---

## 🚀 Deploy und Aktivierung (25.09.2026)

| Punkt | Wert |
|-------|------|
| Image | `lhindmobility.azurecr.io/gs-terminbuchungs-app:req-038-statistik-dashboard-202609250738` (ACR-Run `dn1y`, 4:13 min) |
| Revision | `gs-terminbuchungs-app--0000039`, Healthy, 100 % Traffic; `0000038` automatisch deaktiviert (Single-Revision-Modus) |
| Schalter | `USAGE_INSIGHTS_ENABLED`, `USAGE_FUNNEL_ENABLED`, `USAGE_DROPOFF_ENABLED`, `CAPACITY_SCAN_ENABLED`, `CAPACITY_SHOW_SLOT_COUNT` = `true`; `*_COLLECTING_SINCE` leer → Erfassung ab 26.09.2026 (Aktivierungstag ist Teiltag) |
| Smoke Produktion | Health 200, Angular-App ausgeliefert, `/internal/report`, `/statistics`, `/xyz`, `insights.json` ohne Auth 401; Start-Log: „REQ-038 capacity scan active", „REQ-038 usage insights active (bookings=true, funnel=true, dropoff=true)", Blob-Zugriff per Managed Identity ok (beide Dateien noch nicht vorhanden = Erstlauf), keine Fehler |
| Erste Läufe | Kapazitäts-Scan 25.09. 22:30; Buchungs-Flush 23:59:59; Insights-Rollup 26.09. 03:45; Aufbewahrungslauf 04:15 |

**PO-Entscheidung (24.09.2026):** Alle fünf Schalter wurden beim Deploy eingeschaltet, **bevor** die Gates S-1 (SoftNet-Spike/Abstimmung soft-nrg), S-2 (Wishlist-Rückgabe), G-DSB (DSB-Freigabe der Auswertung je `sessionId`) und G-VVT (VVT/Datenschutzerklärung) formal abgeschlossen sind. Begründung: ausschließlich anonyme Aggregate, Buchungszeile von der Buchung entkoppelt. **Offene Nacharbeiten:** DSB-Kenntnisnahme, VVT und Datenschutzerklärung ergänzen, soft-nrg über den nächtlichen Scan (≈ 55 Calls) informieren. Rücknahme jederzeit per Schalter (`azure-deploy.md` §9.5).

---

## Zusammenfassung

### Bestanden (✅)
- Security 93, Feature 90, E2E 92, Documentation 94; Gesamt 91 ≥ 90.
- 36/36 Akzeptanzkriterien; Backend 1328/1328, Frontend 736/736, E2E 270/270 in drei Viewports; Smoke im Auslieferungszustand und mit allen Schaltern an.
- Deploy in Produktion verifiziert.

### Warnungen (⚠️)
- Architecture 89 und Quality 89 (Erstprüfung) — die Befunde sind in der Fix-Runde weitgehend behoben; eine Neubewertung steht aus.
- Seitengröße ≈ 130 KB statt 80–120 KB.
- Datenpfad (Erfassung, Rollup, Scan) erst nach den ersten Nachtläufen in Azure Ende-zu-Ende nachweisbar.

### Fehler (❌)
- Keine.

---

## Empfehlung

**Status:** ✅ Ready for PR (Score 91 ≥ 90, 0 Fehler, 36/36 ACs) — deployt als Revision `0000039`.

**Nächste Schritte:**
1. Nach den ersten Nachtläufen (26.09.) prüfen: `capacity.json` (Scan-Tag, `state`), `insights.json` (`asOf`), KQL auf `USAGE_BOOKING` (Felder v2 mit `day`, keine Zusatzfelder) — Befehle in `azure-deploy.md` §9.4.
2. Gates nachziehen: DSB-Kenntnisnahme, VVT/Datenschutzerklärung, Info an soft-nrg.
3. Commit/PR durch den User (Änderungen sind lokal, nicht committed; REQ-037 ist noch nicht in `main`).

---

## Changelog

| Datum | Score | Änderungen |
|-------|-------|------------|
| 2026-09-24 | 91/100 | Erstprüfung /check-all (6 Kategorien + AC-Matrix 36/36). E2E 270 passed / 0 failed, REQ-038 18 × 3 Viewports. |
| 2026-09-25 | 91/100 (nicht neu bewertet) | Fix-Runde: Entkopplung `USAGE_BOOKING` (PO-Entscheidung „nur anonyme Daten"), einheitliche Schalter-Semantik, eigener Aufbewahrungslauf, verankerte `ERRORS_KQL`/`BOOKINGS_KQL`, 406 für falsches `Accept`, atomare Stores, erweiterte Summenregel, `Cell`-Invariante, Paket `common/html`, Konsolidierung, 0 neue Compiler-Warnungen. Step 6/6.5 erneut grün (Backend 1328, Jest 736, E2E 270). Deploy Revision `0000039` mit allen fünf Schaltern an. |
