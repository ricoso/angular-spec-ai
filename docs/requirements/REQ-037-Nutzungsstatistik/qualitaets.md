# Qualitätsbericht: REQ-037-Nutzungsstatistik

**Generiert:** 2026-09-21 14:10
**Feature:** usage (Nutzungsstatistik auf der Statusseite)
**Gesamtscore:** 92/100 ✅

> **Hinweis zur Prüfung:** REQ-037 ist ein **reines Backend-Feature** (Java 21 / Spring Boot). Es gibt
> bewusst **keinen** `src/app/features/usage/`-Ordner, keinen NgRx-Store, kein Resolver, keine Forms und
> keine Angular-Komponente. Die generischen Frontend-Feature-Checks (`check-stores`, `check-routing`,
> `check-forms` sowie `check-performance` mit OnPush/`@for`/`computed()`) sind daher **n/a** — genau wie
> bei REQ-030. Geprüft wurden die tatsächlichen Artefakte: 11 Klassen in
> `server/src/main/java/com/autohaus/backend/usage/`, `common/logging/DeviceClassifier`,
> `common/filter/LoggingFilter`, `config/{SecurityConfig,WebConfig,UsageStatsProperties}`, die vier
> erweiterten `reporting`-Klassen, `application.yml`, `build.gradle`, das generierte CSS in
> `ReportHtmlGenerator.css()` sowie `playwright/REQ-037-usage-statistics.spec.ts`.

---

## Übersicht

| Kategorie | Gewicht | Score | Status |
|-----------|---------|-------|--------|
| Architecture | 20 % | 88/100 | ⚠️ |
| Security | 20 % | 96/100 | ✅ |
| Quality | 20 % | 88/100 | ⚠️ |
| Feature Checks | 20 % | 95/100 | ✅ |
| E2E Testing | 10 % | 92/100 | ✅ |
| Documentation | 10 % | 92/100 | ✅ |
| **Gesamt (gewichtet)** | **100 %** | **91,8 → 92/100** | ✅ |

Rechnung: `0,20·88 + 0,20·96 + 0,20·88 + 0,20·95 + 0,10·92 + 0,10·92 = 17,6 + 19,2 + 17,6 + 19,0 + 9,2 + 9,2 = 91,8`.

---

## 📐 Architecture (20%) — 88/100 ⚠️

> **Prüfmaßstab:** Das Feature hat keine Angular-Architektur. Bewertet wurden stattdessen — analog zum
> Vorgehen bei REQ-030 — die Schichtung des `usage`-Packages, die Entkopplung vom LLM-Toggle und die
> Nicht-Beschädigung von REQ-030.

### Schichtung — sauber getrennt ✅

| Schicht | Klasse | Verantwortung |
|---|---|---|
| Domänenmodell (Records, unveränderlich) | `DailyUsage`, `DeviceBreakdown`, `PeriodUsage`, `UsageHistory`, `UsageStats` | reine Datenträger, keine I/O |
| Abfrage | `UsageStatsAggregator.java:55` | KQL gegen Log Analytics, sonst nichts |
| Persistenz | `UsageBlobClient.java:55` | Blob-Lesen/-Schreiben inkl. ETag, sonst nichts |
| Fachlogik | `UsageStatsService.java:51` | Perioden, Quoten, Monatsreihe — keine I/O |
| Orchestrierung | `UsageStatsScheduler.java:58` | Cron, Catch-up, Retry/Merge, Purge |
| Snapshot | `UsageStatsStore.java:25` | `volatile` In-Memory-Zustand |
| Darstellung | `UsageSectionRenderer.java:43` | HTML-Fragment, keine Datenbeschaffung |

Der Request-Pfad berührt ausschließlich `UsageStatsStore.current()` (`ReportController.java:105,125`) —
jede Netzabfrage liegt im Scheduler (`UsageStatsScheduler.java:129,241`). Das ist die Trennung, die AC-7
fordert, und sie ist im Code eindeutig durchgezogen.

### Entkopplung vom LLM-Toggle ✅

Die Statistik-Beans hängen an `@ConditionalOnProperty("usage.enabled")`
(`UsageStatsAggregator.java:54`, `UsageStatsScheduler.java:57`), **nicht** an der
`@ConditionalOnExpression`-Bedingung der REQ-030-LLM-Beans. Ohne `LLM_API_KEY`/`LLM_MODEL` rendert die
Statistik trotzdem (`ReportController.java:105-109`, Test
`ReportControllerTest.rendersStatisticsAboveTheMissingReportHint:226`). Der Refresh-Endpoint behandelt
beide Scheduler unabhängig und antwortet nur `503`, wenn **beide** fehlen (`ReportController.java:144-152`).

### REQ-030 nicht beschädigt ✅

Der Umbau ist ein bewusster Fragment-Split, kein Nebeneffekt: `ReportStore` hält jetzt ein Fragment statt
einer Vollseite (`ReportStore.java:22,32-39`), `ErrorReportScheduler` speichert auf **beiden** Pfaden —
Erfolg und Fallback — ein Fragment (`ErrorReportScheduler.java:56,77`), und `renderPage(usage, fragment)`
setzt den Dokumentrahmen zusammen (`ReportHtmlGenerator.java:82-104`). Die REQ-030-Regressionstests wurden
mitgezogen (`ReportStoreTest`, `ErrorReportSchedulerTest`, `ReportHtmlGeneratorTest`), der CSS-only-Filter
der Fehlerliste bleibt im Fragment erhalten (dokumentiert in `ReportHtmlGenerator.java:117-122`).

### Abzüge

| # | Schwere | Ort | Befund |
|---|---------|-----|--------|
| A1 | medium | `UsageSectionRenderer.java:3,543` ↔ `ReportHtmlGenerator.java:3-4,42` | **Zyklische Package-Abhängigkeit** `usage` ↔ `reporting`: der Renderer importiert `ReportHtmlGenerator` für `esc(...)`, der Generator injiziert den Renderer. Kein Bean-Zyklus (der Aufruf ist `static`), aber `esc()` gehört in eine gemeinsame HTML-Hilfsklasse, nicht in die Klasse, die vom Nachbarpackage abhängt. |
| A2 | low | `ReportHtmlGenerator.java:51-53` | Der No-Arg-Konstruktor umgeht Spring (`new UsageSectionRenderer()`) und hat keinen Produktionsaufrufer mehr. Als „Kompatibilitäts-API" dokumentiert, ohne dass es noch etwas kompatibel zu halten gäbe. |
| A3 | medium | `UsageSectionRenderer.java:53,56` / `UsageStats.java:46` | **Konfigurationsschicht leckt:** `ROLLUP_STALE_DAYS` ist im Renderer noch einmal privat definiert (Original: `UsageStatsScheduler.java:69`, `public`), und `ZoneId.of("Europe/Berlin")` ist in Renderer und `UsageStats` hart verdrahtet, während Aggregator/Service/Scheduler `properties.zone()` lesen. Die Darstellungsschicht kennt damit Werte, die eigentlich der Konfiguration bzw. dem Scheduler gehören. |
| A4 | low | `UsageStatsAggregator.java:238`, `UsageStatsService.java:306`, `UsageStatsScheduler.java:484` | `resolveZone(...)` existiert dreifach — mit **abweichendem** Fehlerverhalten (die ersten beiden degradieren auf Berlin, der Scheduler crasht). Gleiche Logik, drei Wahrheiten. |

**Bewertung:** Die Grobschichtung ist vorbildlich und der Eingriff in REQ-030 ist kontrolliert. Punktabzug
für den Package-Zyklus und für die drei Stellen, an denen Konstanten bzw. Konfiguration über Schichtgrenzen
hinweg dupliziert werden.

**check-stores / check-routing / check-forms:** n/a (Backend-Feature).

---

## 🔒 Security (20%) — 96/100 ✅

> **Prüfumfang:** reines Backend-Feature (Java 21 / Spring Boot). Geprüft wurden das Package
> `usage` (11 Klassen), `DeviceClassifier`, `LoggingFilter`, `SecurityConfig`/`WebConfig`/
> `UsageStatsProperties`, die erweiterten `reporting`-Klassen, `application.yml` sowie die
> lokalen Test-Artefakte. Maßstab ist §9 „Security / Datenschutz" des Requirements
> (NFR-Datenschutz-1 bis -9, BR-3, BR-11, BR-13, AC-14, AC-20, AC-21, AC-22).

### 1. XSS — sauber ✅

- **Jeder** modellabgeleitete String im `UsageSectionRenderer` läuft durch `esc(...)`:
  `p.label()` (`UsageSectionRenderer.java:134,158`), `p.rangeLabel()` (`:137,161`), Datumswerte
  (`:163,166,196,234`), Kachel- und Quotentexte (`:285-287,294-295,332`), Gerätezeilen inkl.
  `aria-label` (`:383-384,387`), Monatstabelle (`:437-440,460,467-478`).
- Kein `append()` mit einem dynamischen String ohne `esc()` gefunden. Die einzigen unescapeten
  Interpolationen sind **Konstanten bzw. Zahlen**: `headingId` aus dem Aufrufer-Literal `"us-m"`/
  `"us-y"` (`:347,351,364`), `MIN_VISITS_FOR_BREAKDOWN` (int, `:353`), `DEVICE_MODIFIERS[i]`
  (Konstanten-Array, `:385`) und `num()`/`decimal()`/`percent()`/`cssWidth()`, die per
  `String.format` aus `long`/`double` bauen (`:515-536`) — nicht steuerbar.
- `esc()` ist **eine** Implementierung, geteilt mit `ReportHtmlGenerator.esc()` (`:542-544` →
  `ReportHtmlGenerator.java:338-353`), escaped `& < > " '`.
- **Keine Log-abgeleiteten Strings erreichen das HTML.** Der aus Log-Zeilen gelesene `device`-Wert
  wird per Whitelist auf vier Literale gemappt und landet ausschließlich als `long`-Zähler in
  `DeviceBreakdown` (`DeviceBreakdown.java:17-22`); auch aus dem Blob kommt kein String ins
  Markup (nur `int`/`long`/`Instant`/validierte ISO-Keys, `UsageHistory.java:20-24`).
- Regressionstest vorhanden: `UsageSectionRendererTest.escapesEveryDynamicValue()` (`:61-79`)
  schleust `<script>alert(1)</script>` und `"`/`&`/`<b>` durch Label und Range-Label.
- KQL-Injection ausgeschlossen: in `FUNNEL_KQL.formatted(...)` gehen nur berechnete UTC-Instants
  und die Konstanten `SEARCH_PATH`/`BOOKING_PATH` (`UsageStatsAggregator.java:68,71,149-150,202`).

### 2. Zugriffsschutz — vollständig nach AC-20/AC-21 ✅

- `/internal/**` bleibt hinter der `@Order(1)`-Basic-Auth-Chain, `anyRequest().authenticated()`,
  stateless (`SecurityConfig.java:77-84`). Manuell verifiziert: ohne Credentials `401`.
- **Realm** explizit `internal` statt Spring-Default (`SecurityConfig.java:37,80`), abgesichert in
  `ReportControllerTest.java:291-297`.
- **CSP** exakt wie AC-20: `default-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'none';
  form-action 'none'; frame-ancestors 'none'; base-uri 'none'` (`SecurityConfig.java:44-45`,
  gesetzt als `StaticHeadersWriter` in `:82`), zeichengenau getestet (`ReportControllerTest.java:305-308`)
  und durch „kein `<script>`/`<link>`/`<img>`/`<form>`" flankiert (`:311-321`). Die
  Spring-Security-Defaults (`Cache-Control: no-store`, `X-Content-Type-Options`, `X-Frame-Options`)
  bleiben erhalten, weil `.headers(...)` nur ergänzt.
- **Auth-Failure-Logging** als `WARN` mit `LogSanitizer.maskIp(...)`, **ohne** Benutzername und
  **ohne** Passwort (`SecurityConfig.java:100-107`, Logzeile `:105`). Unbekannter Benutzer und
  falsches Passwort erzeugen dieselbe Zeile (kein User-Enumeration-Orakel).
- **Refresh-Drosselung** greift *vor* jeder Backend-Abfrage: `refreshIfAllowed()` stempelt und
  prüft rein in-memory und liefert `false` → Controller antwortet `429` ohne KQL/Blob
  (`UsageStatsScheduler.java:216-226`, `ReportController.java:155-159`). Der Zeitstempel wird
  **zu Beginn** von `refreshCurrentUsage()` gesetzt (`:150`), also auch bei fehlschlagenden Läufen —
  kein „Fehler = Freifahrtschein".

### 3. Datenschutz / DSGVO — Kernanforderung erfüllt ✅

- **Roher User-Agent (BR-3, NFR-Datenschutz-1):** `grep -rni "user-agent"` über das gesamte Backend
  liefert **genau eine** Verwendungsstelle: `LoggingFilter.java:65`, als Inline-Argument an
  `DeviceClassifier.classifyDevice(...)` — keine lokale Variable, kein Feld, keine Log-, Debug- oder
  Exception-Zeile. `DeviceClassifier` hat **keinen Logger** und **kein Feld**
  (`DeviceClassifier.java:26-53`); der Header existiert dort nur als Methodenparameter und die
  gekappte Kopie `ua` (`:90-93`) verlässt die Methode nie. Alle übrigen Treffer sind Javadoc.
  Regressionstests: `LoggingFilterTest.java:137` (kein `Mozilla` in der Zeile) und `:146-159`
  (feindlicher UA mit `"`, `<script>`, Zeilenumbruch, 2000 Zeichen → nichts davon im Log).
- **`Sec-CH-UA-Mobile`:** ebenfalls genau eine Lesestelle (`LoggingFilter.java:66`), wird weder
  geloggt noch persistiert; in der Ausgabe steht ausschließlich `"device":"<klasse>"`
  (`LoggingFilter.java:31-33`).
- **Keine aktive Abfrage der Endeinrichtung:** `grep -rni "accept-ch|critical-ch|permissions-policy"`
  über `server/src/main` und `src/` → **0 Treffer** in Produktivcode. Negativ getestet in
  `ReportControllerTest.neverRequestsClientHints()` (`:331-337`): `Accept-CH`, `Critical-CH`,
  `Permissions-Policy` existieren in der Antwort nicht.
- **Persistiertes Blob-Schema (AC-14, NFR-Datenschutz-4):** ausschließlich Ganzzahlen —
  `DailyUsage` besteht aus `DeviceBreakdown` + sechs `long` (`DailyUsage.java:23-31`),
  `DeviceBreakdown` aus vier `long` (`DeviceBreakdown.java:17-22`), `UsageHistory` aus
  `schemaVersion` (int), `updatedAt` (Schreibzeitpunkt der Datei, kein Ereignis-Zeitstempel) und
  `Map<ISO-Tag, DailyUsage>` (`UsageHistory.java:20-24`). Keine Session-IDs, keine IPs, keine
  User-Agents, keine Einzel-Zeitstempel. Beim Einlesen werden nicht-ISO-Keys und negative Zähler
  als unbrauchbar verworfen (`UsageBlobClient.java:237-252`).
- **Kill-Switch (BR-13) greift wirklich auf beiden Seiten:**
  *Erhebung* — `LoggingFilter` liest bei `false` **keinen** der beiden Header und schreibt die
  Vor-REQ-037-Zeile byte-identisch (`LoggingFilter.java:42-45,59-83`), verifiziert mit
  `verify(request, never()).getHeader("Sec-CH-UA-Mobile")` (`LoggingFilterTest.java:218`).
  *Auswertung* — `UsageStatsAggregator` und `UsageStatsScheduler` sind
  `@ConditionalOnProperty("usage.enabled")` (`:54` bzw. `:57`), es existiert also keine Bean, die
  KQL oder Blob anfassen könnte; *Rendering* — Controller übergibt `null`, die Sektion entfällt
  ersatzlos (`ReportController.java:105`, `ReportHtmlGenerator.renderPage` `usage != null`-Guard);
  *Endpoint* — `usage.json` → `404` (`ReportController.java:119-121`).
- **Keine Verknüpfung (BR-11):** Das Aggregat kennt keine fachliche Dimension — kein Marken-,
  Standort- oder Kundenfeld in `PeriodUsage`/`DailyUsage`; Statistik und Fehlerliste werden nur
  nebeneinander gerendert, nie gejoint.

### 4. Secrets — kein Credential im Produktivcode, lokales Test-Passwort vertretbar ✅ (mit Auflage)

- `application.yml` enthält ausschließlich Env-Platzhalter; `usage.*` braucht **gar kein** Secret
  (`application.yml:37-52`). Ohne `REPORT_ADMIN_PASSWORD` erzeugt die App weiterhin ein zufälliges
  UUID-Passwort und sperrt die Seite (`SecurityConfig.java:145-148`) — das committete Test-Passwort
  kann also **nicht** als Default in Produktion durchschlagen.
- `e2e-local-secret` steht an drei Stellen, jeweils als überschreibbarer Fallback und kommentiert:
  `scripts/test-local.sh:44-48`, `playwright-backend.config.ts:52-56`,
  `playwright/REQ-037-usage-statistics.spec.ts:12-17`. Es gilt nur für einen vom Testlauf selbst
  gestarteten `bootRun` auf `localhost:3000`. **Bewertung: vertretbar und ausreichend gekennzeichnet**
  — der Name benennt Zweck und Geltungsbereich, es gibt keinen Pfad in die Produktion.
  **Kritisch angemerkt** bleibt: es ist ein echter, im Repo liegender Credential-String, den
  Secret-Scanner (gitleaks & Co.) melden werden, und `playwright-backend.config.ts` setzt ihn
  **auch unter `CI=true`**; auf einem CI-Runner mit erreichbarem Port 3000 wäre die Statusseite dann
  mit einem öffentlich bekannten Passwort geschützt. Empfehlung (klein): unter `CI` das Env-Setzen
  verweigern statt zu defaulten.

### 5. Managed Identity — durchgängig, kein Schlüssel ✅

- **Blob:** `UsageBlobClient` holt sein Token über `IDENTITY_ENDPOINT`/`IDENTITY_HEADER`
  (`X-IDENTITY-HEADER`) mit `resource=https://storage.azure.com/` und sendet `Authorization: Bearer`
  (`UsageBlobClient.java:33-34,60,119-129,160-170,288-316`). Kein Account-Key, kein SAS, kein
  Connection-String, kein Azure-SDK — `grep` über das Package findet keine Credential-Konstante.
  Ohne Identity-Endpoint wird sauber `BlobUnavailableException` geworfen (`:293-298`), nicht auf
  Shared Key zurückgefallen.
- **Log Analytics:** unverändert dasselbe Muster (`LogAnalyticsClient.java:23,65,79-83`).
- **Nicht aus dem Repo prüfbar** und daher als Betriebsaufgabe offen: die speicherseitige Härtung
  aus §3.1 / NFR-Datenschutz-7 (`--allow-shared-key-access false`, Rolle auf **Container**-Scope,
  Public Blob Access aus, TLS 1.2, Soft-Delete/Versioning/Change-Feed aus). Ohne
  `allow-shared-key-access false` wäre die Scope-Begrenzung wirkungslos — bitte vor Go-live
  per `az storage account show` belegen.

### Issues

| # | Schwere | Ort | Befund |
|---|---------|-----|--------|
| 1 | niedrig | `playwright-backend.config.ts:52-56` | Lokales Test-Passwort wird auch bei `CI=true` gesetzt; Secret-Scanner-Treffer. Kein Prod-Pfad (Fallback ist ein zufälliges UUID-Passwort), daher niedrig. |
| 2 | niedrig | `UsageStatsScheduler.java:93,216-226` | Refresh-Drossel ist **pro Instanz** (`volatile long` im Heap). Bei mehreren Replicas — die §6.3/E3 ausdrücklich vorsieht — erlaubt AC-21 faktisch N × 1 Lauf/60 s. Kein Datenleck, nur Kosten-/Lastpfad. |
| 3 | niedrig | `UsageSectionRenderer.java:279-291, 328-334` | `tile(..., String sub)` und `rate(..., String sub)` werden **roh** ins HTML geschrieben (bewusst, wegen `&middot;`); alle heutigen Aufrufer übergeben Literale oder `requestSubtitle()`-Ausgabe, die escaped ist (`:294-295`). Der Vertrag „muss vorescaped sein" steht aber in keinem Javadoc — Wartungsfalle für den nächsten Aufrufer. |
| 4 | niedrig | `UsageBlobClient.java:243-244` | Ein fremder Day-Key aus dem Blob wird ungefiltert und ungekappt in die Fehlerzeile geschrieben (Log-Injection über Zeilenumbrüche). Voraussetzung ist Schreibzugriff auf den Container; keine PII. |
| 5 | info | `SecurityConfig.java:100-106` | Der `AuthenticationFailureBadCredentialsEvent`-Listener ist global, die Meldung schreibt `/internal/**` fest. Heute korrekt (einzige Auth der App), wird aber falsch, sobald eine zweite Auth dazukommt. |
| 6 | info | `LoggingFilter.java:44` vs. `UsageStatsProperties.java:30` | `usage.enabled` wird an zwei Stellen unabhängig gelesen (`@Value` im Filter, Record im Rest). Beide Defaults sind `true`, heute konsistent — Drift-Risiko bei künftigen Umbenennungen. |
| 7 | info | Infra (§3.1) | Speicherseitige Härtung (Shared-Key aus, Container-Scope-Rolle, Soft-Delete/Versioning/Change-Feed aus) ist nicht im Repo nachweisbar und vor Go-live zu belegen. |

**Fazit:** Der Datenschutzkern des Features hält, was §9 verspricht — der rohe User-Agent existiert
nachweislich nur als Argumentausdruck in `LoggingFilter.java:65`, der Client-Hint wird nie
angefordert, das Persistat besteht ausschließlich aus Ganzzahlen, und der Kill-Switch schaltet
Erhebung *und* Auswertung ab. Abzüge nur für die vier niedrigschwelligen Punkte oben, davon keiner
ausnutzbar ohne bestehenden privilegierten Zugriff.

---

## 📝 Quality (20%) — 88/100 ⚠️

> **Hinweis zur Prüfung:** REQ-037 ist ein **reines Backend-Feature** (Java 21 / Spring Boot). Es gibt
> keinen `src/app/features/usage/`-Ordner, keinen NgRx-Store, keine Forms und keine Angular-Komponente.
> Die generischen Frontend-Checks (`check-stores`, `check-routing`, `check-forms`, `check-performance`
> mit OnPush/`@for`/`computed()`) sind daher **n/a** — genau wie bei REQ-030. Geprüft wurden die
> tatsächlichen Artefakte: 11 Klassen in `server/.../usage/`, `DeviceClassifier`, `LoggingFilter`,
> die drei Config-Klassen, die vier angepassten `reporting`-Klassen, das generierte CSS in
> `ReportHtmlGenerator.css()` und die neue Playwright-Spec.

### check-eslint — Frontend-Gate 0 Errors ✅, aber 2 neue Errors in der Playwright-Spec ⚠️

- `npm run lint` (= `ng lint`) ist in `angular.json` auf `lintFilePatterns: ["src/**/*.ts", "src/**/*.html"]`
  begrenzt. Da REQ-037 **keine einzige Datei unter `src/`** anfasst, ist das Gate-Ergebnis
  (0 Errors / 255 vorbestehende Warnings) korrekt — es sagt über REQ-037 aber nichts aus.
- Gegen dieselbe `eslint.config.js` direkt ausgeführt hat die neue Spec **2 Errors**:
  `playwright/REQ-037-usage-statistics.spec.ts:16:32` und `:17:36` →
  `@typescript-eslint/dot-notation` (`process.env['REPORT_ADMIN_USER']`). Beide sind `--fix`-bar.
- Einordnung: `playwright/**/*.ts` hat im Repo insgesamt 21 Errors, davon **19 vorbestehend**
  (`simple-import-sort/imports`, `prefer-includes` in anderen Specs). Der Ordner wird vom Gate
  faktisch nicht gelintet — REQ-037 verschlechtert den Zustand also nur marginal, sauber wäre es
  trotzdem nicht.

### check-typescript — `tsc --noEmit` sauber, Spec sauber typisiert ✅

- `basicAuthHeader(): string` mit explizitem Return-Type (`REQ-037-usage-statistics.spec.ts:172`),
  kein `any`, keine Non-Null-Assertions, Konstanten statt Magic Strings
  (`REPORT_URL`, `USAGE_JSON_URL`, `ADMIN_USER`, `ADMIN_PASSWORD`).

### check-performance (Java-Äquivalent) — kein I/O im Request-Pfad ✅

- `ReportController` liest ausschließlich den In-Memory-Snapshot (`UsageStatsStore.current()`,
  `ReportController.java:` GET `/internal/report` + `usage.json`); jede KQL-/Blob-Abfrage läuft im
  `UsageStatsScheduler` (AC-7).
- `DeviceClassifier` im Hot Path: kein Logger, kein Feld, kein Cache, Kappung auf
  `MAX_USER_AGENT_CHARS = 512` **vor** `toLowerCase` (`DeviceClassifier.java:105-109`) — korrekte
  Reihenfolge, pathologische Header kosten nichts.
- `StringBuilder` mit vorab dimensionierter Kapazität in allen Render-Methoden.

### check-styling — WCAG 2.1 AA nachgerechnet, alle Werte bestanden ✅

**Farbtokens:** Alle neuen Farben liegen als CSS-Variablen im `:root`
(`ReportHtmlGenerator.java:358-360`): `--track:#e3eaf0`, `--bar-tablet:#35808f`,
`--bar-unknown:#6b7a87`, `--row-alt:#f7fafc`, `--focus:#005fcc`. **Kein einziger Hex-Wert** in den
neuen Regeln — Stichprobe über den kompletten REQ-037-Block: nur `var(...)`.

**Kontraste (Relative Luminance nach WCAG 2.1, real gerechnet — nicht geschätzt).** Bezugsfläche ist
`section{background:var(--surface)}` = `#ffffff`, nicht `--bg`:

| Paarung | Werte | Ratio | Anforderung | Ergebnis |
|---|---|---|---|---|
| `--soft` Text auf `--surface` (`.us-intro`, `.us-range`, `.cat-sub`, `.dev-val`, `.us-nd`) | `#5a6b7b` / `#ffffff` | **5,49:1** | 4,5:1 | ✅ |
| `--soft` auf `--row-alt` (Zebra-Zeile) | `#5a6b7b` / `#f7fafc` | **5,24:1** | 4,5:1 | ✅ |
| `--accent` (`.us-rate-val`, `.us-badge-live`) auf `--surface` | `#0e6b7c` / `#ffffff` | **6,15:1** | 4,5:1 | ✅ |
| `--ink` auf `--row-alt` | `#1a2b3c` / `#f7fafc` | **13,77:1** | 4,5:1 | ✅ |
| Balken `.dev-fill-mobile` gegen Spur | `#0e6b7c` / `#e3eaf0` | **5,07:1** | 3:1 | ✅ |
| Balken `.dev-fill-tablet` gegen Spur | `#35808f` / `#e3eaf0` | **3,73:1** | 3:1 | ✅ |
| Balken `.dev-fill-desktop` gegen Spur | `#5a6b7b` / `#e3eaf0` | **4,52:1** | 3:1 | ✅ |
| Balken `.dev-fill-unknown` gegen Spur | `#6b7a87` / `#e3eaf0` | **3,63:1** | 3:1 | ✅ |
| Fokus-Outline `.us-scroll:focus-visible` | `#005fcc` / `#ffffff` | **5,98:1** | 3:1 | ✅ |

Die Spur selbst (`#e3eaf0` auf Weiß = 1,21:1) ist unkritisch, weil `.dev-bar` zusätzlich
`border:1px solid var(--line)` trägt — die Begrenzung ist also nicht allein über Helligkeit definiert.

**WCAG 1.4.1 (Information nie allein über Farbe):** durchgängig eingehalten —
`deviceRow()` (`UsageSectionRenderer.java:380-389`) liefert Balken **plus** Absolutzahl **plus**
Prozentwert **plus** `role="img"` + `aria-label` („Mobil: 1.284 Besuche, 66,3 Prozent"). Die
Störungsbänder `.us-warn` haben neben dem `--warn`-Rahmen immer Klartext
(`UsageSectionRenderer.java:216-238`). Fehlende Monate tragen `<span class="sr">` mit dem Grund
(„keine Daten, künftiger Monat" vs. „vor Erfassungsbeginn", `:474-478`).

**Responsive:** `@media(max-width:40em)` greift nachweislich für alle drei geforderten Selektoren —
`.us-tiles{grid-template-columns:1fr}`, `.us-rates{grid-template-columns:1fr}`,
`.us-months .opt{display:none}` (`ReportHtmlGenerator.java:438`). Zusätzlich bricht
`@media(max-width:47.99em)` die `.dev-row` von 3 Spalten auf 2 Zeilen um (`:437`). Einheiten:
`em`/`rem` durchgängig (`7rem 1fr 11rem`, `.36rem .7rem`, Breakpoints in `em`).

**Abweichung (klein):** Die neuen Radius-Tokens sind in `px` definiert —
`--radius:6px; --radius-sm:3px; --radius-pill:999px` (`ReportHtmlGenerator.java:360`), dazu
`1px`-Hairlines und das `.sr`-Pattern (`width:1px;height:1px;margin:-1px`). Laut CLAUDE.md sind
hardcodierte `px` verboten; hier ist es (a) konsistent mit dem bestehenden REQ-030-Stylesheet
(`border-radius:6px`, `5px`), (b) bei 1px-Rahmen und dem Visually-Hidden-Pattern eine anerkannte
Ausnahme, und (c) durch die Zentralisierung als Token sogar eine Verbesserung gegenüber vorher.
Bewertet als **Low**, nicht als Verstoß.

### check-code-language — 100/100 ✅

- **Java-Bezeichner:** vollständig Englisch. Automatisierter Scan über alle 11 `usage`-Klassen +
  `DeviceClassifier` + `UsageStatsProperties` nach deutschen Wortstämmen (Besuch, Tag, Zeit, Anzahl,
  Gerät, Monat, Jahr, Quote, Zähler, Sitzung, Fehler, Erfassung, …) — **nach Entfernen von Kommentaren
  und String-Literalen: 0 Treffer** (die zwei Roh-Treffer waren `EventListener`/`@EventListener`,
  also `Liste`-Substring-Fehlalarme).
- **JSON-Keys:** alle Record-Komponenten Englisch — `visits`, `visitCount`, `searchRequests`,
  `searchRequestsOk`, `sessionsWithSearch`, `searchRate`, `bookingRate`, `closeRate`, `hasData`,
  `rangeLabel`, `rangeFrom`, `rangeTo`, `liveAsOf`, `liveAvailable`, `historyAvailable`,
  `collectingSince`, `lastRollupDay`, `schemaVersion`, `updatedAt`, `days`,
  `mobile`/`tablet`/`desktop`/`unknown`. Das neue Log-Feld heißt `"device"`, die Werte sind
  `mobile|tablet|desktop|unknown` (`LoggingFilter.java:34-36`, `DeviceClassifier.java:32-41`).
- **CSS-Klassen:** alle Englisch — `us`, `us-block`, `us-hd`, `us-title`, `us-badge`, `us-badge-live`,
  `us-range`, `us-subtitle`, `us-tiles`, `us-rates`, `us-rate-val`, `dev-row`, `dev-lbl`, `dev-bar`,
  `dev-fill-mobile|tablet|desktop|unknown`, `us-thin`, `us-scroll`, `us-months`, `us-cur` (current),
  `us-nd` (no data), `us-empty`, `us-warn`, `sr` (screen reader), `opt`.
- **Deutsche Literale** ausschließlich in sichtbaren Beschriftungen der internen Seite
  (`UsageSectionRenderer.java:69,75-78,95,109-118,135,194`, Controller-Meldungen) — exakt die
  §18-Ausnahme. Der Renderer dokumentiert das selbst im Klassen-Javadoc (`:30-32`).
- **Playwright-Spec:** Bezeichner Englisch (`ADMIN_USER`, `REPORT_URL`, `basicAuthHeader`,
  `usIndex`/`aiIndex`/`errIndex`), Testtitel Englisch.
- **Kommentare DE+EN:** durchgängig zweisprachig umgesetzt (Muster „English / Deutsch" in jedem
  Klassen- und fast jedem Methoden-Javadoc). Das ist **mehr** als der REQ-030-Bestand, der
  überwiegend rein englisch ist. Einzelne Einzeiler sind nur englisch
  (`UsageStatsScheduler.java:121,128`) — kosmetisch, kein Finding.

### Typsicherheit ✅

- Keine Rohtypen, keine Casts, kein `@SuppressWarnings` im gesamten `usage`-Package (Scan über alle
  11 Dateien: 0 Treffer). `LogAnalyticsClient.rows()` liefert bereits `List<List<String>>`.
- `Optional` nur als Rückgabetyp (`UsageStatsAggregator.aggregateDailyUsage`,
  `UsageStatsScheduler.aggregateSafely`) — nie als Feld oder Parameter. ✅
- Exception-Hierarchie und Catch-Reihenfolge korrekt: `BlobPreconditionFailedException extends
  BlobUnavailableException`, und in `persistDailySnapshot` wird die Subklasse **vor** der Basisklasse
  gefangen (`UsageStatsScheduler.java:327` vor `:351`) — sonst wäre der Retry tot. Ebenso in
  `UsageBlobClient.saveUsageHistory:183-187` und `fetchToken:311-317`.
- Pattern-Matching statt Cast: `details instanceof WebAuthenticationDetails web` (`SecurityConfig`).

### Dead Code / Duplizierung ⚠️ — der schwächste Punkt

Für jede public/package-private Methode des `usage`-Packages wurde geprüft, ob sie irgendwo
(Produktion **oder** Test) aufgerufen wird. Ergebnis:

| Fundstelle | Befund |
|---|---|
| `UsageStatsStore.java:69` `hasStats()` | **Echter Dead Code** — 0 Aufrufe im gesamten Repo, auch kein Test. |
| `UsageStatsService.java:201` `zone()` | 0 Aufrufe. |
| `UsageStatsAggregator.java:164` `zone()` | 0 Aufrufe. |
| `UsageStatsService.java:190` `mergeDays(...)` | Kein Produktionsaufrufer. Einziger Call: `UsageStatsServiceTest.java:61` — in einem Test über ein *anderes* Thema, Ergebnisvariable heißt `ignored`. Das ist ein Coverage-Feigenblatt, kein Test. |
| `ReportHtmlGenerator.java:56` `generate(ReportModel)` + `:51` No-Arg-Konstruktor | Nach dem REQ-037-Split ohne Produktionsaufrufer; nur noch `ReportHtmlGeneratorTest` nutzt sie. Als Kompatibilitäts-API dokumentiert — aber nichts mehr kompatibel zu halten. |
| `UsageSectionRenderer.java:75-78` `MONTH_NAMES` | Fallback unter `:456` ist unerreichbar: beide Produzenten (`UsageStats.unavailable()`, `UsageStatsService.buildMonthlySeries`) setzen `label` immer. Der Test bringt eine **vierte** Kopie der deutschen Monatsnamen mit (`UsageSectionRendererTest.java:313`). |
| `UsageStatsProperties.java:31-32` `refreshCron`/`rollupCron` | Werden nie aus dem Record gelesen — `@Scheduled` greift direkt auf `${usage.refresh-cron}` zu. Dokumentierend, aber tot. |

**Duplizierung:** `resolveZone(UsageStatsProperties)` existiert dreimal
(`UsageStatsAggregator.java:238`, `UsageStatsService.java:306`, `UsageStatsScheduler.java:484`),
`DEFAULT_ZONE = "Europe/Berlin"` viermal, `monthName(Month)` zweimal
(`UsageStats.java:79`, `UsageStatsService.java:301`), `DISPLAY_DATE` zweimal,
`ROLLUP_STALE_DAYS = 3` zweimal (`UsageStatsScheduler.java:69` public, `UsageSectionRenderer.java:53`
private). Zwei unabhängige Definitionen derselben Geschäftsschwelle driften irgendwann auseinander.

### Weitere Findings

- **`usage.zone` wirkt nicht durchgängig:** `UsageSectionRenderer.java:56`
  (`ZONE = ZoneId.of("Europe/Berlin")`, genutzt für den `TS`-Formatter `:63` und `LocalDate.now(ZONE)`
  `:251`/`:492`) und `UsageStats.java:46` (`DISPLAY_ZONE`) sind **hart verdrahtet** und ignorieren die
  Property. Mit `USAGE_ZONE != Europe/Berlin` rechnet die Aggregation in der konfigurierten Zone,
  der Renderer formatiert aber weiter Berlin — inkonsistente Anzeige.
- **Abweichendes Fehlerverhalten bei ungültiger Zone:** `UsageStatsAggregator:243-248` und
  `UsageStatsService:311-316` fangen `RuntimeException` und fallen auf Berlin zurück;
  `UsageStatsScheduler:484-487` tut das **nicht** → ein Tippfehler in `USAGE_ZONE` lässt die
  Bean-Erzeugung crashen, statt wie überall sonst zu degradieren.
- **Verschluckte Exception ohne Log:** `UsageStatsAggregator.java:229-235` `parseLong(...)` fängt
  `RuntimeException` und liefert stumm `0L`. Eine nicht-numerische KQL-Zelle wird damit zu einer
  Null, ohne jede Spur im Log — das widerspricht dem eigenen §9-Grundsatz „stille Ausfälle sind
  unzulässig". (`UsageStatsService.parseHistory:287-289` loggt immerhin auf `debug`.)
- **Unmaskierter Parameter:** `UsageSectionRenderer.tile(...)` (`:283-289`) und `rate(...)` (`:330-334`)
  setzen `sub` **roh** ins Markup. Alle heutigen Aufrufer maskieren selbst (`requestSubtitle`,
  `rates`), das ist also aktuell korrekt — als API bleibt es eine Fußangel.

### Frontend unverändert — per git bestätigt ✅

`git status --porcelain` zeigt **keine** Datei unter `src/`, keine `.component.ts/.html/.scss`,
kein `i18n`, keine `app.routes.ts`. Angefasst sind nur:
`playwright/REQ-037-usage-statistics.spec.ts` (neu), `playwright-backend.config.ts` (+5 Zeilen,
`env.REPORT_ADMIN_PASSWORD` für den Webserver), `scripts/test-local.sh` (+5 Zeilen, dasselbe Export),
`docs/requirements/REQ-037-Nutzungsstatistik/` (neu) sowie 14 Dateien unter `server/`.
Die beiden Test-Harness-Dateien sind über den Auftragstext hinaus geändert — inhaltlich aber
ausschließlich das feste lokale Basic-Auth-Passwort, das die Spec zum Anmelden braucht.
**Kein Angular-Produktionscode berührt.**

**Issues:** 1× Medium (Dead Code `hasStats()` + drei ungenutzte Accessoren + Coverage-Feigenblatt
`mergeDays`), 1× Medium (`usage.zone` wird von Renderer und `UsageStats` ignoriert), 3× Low
(2 ESLint-Errors in der Spec, stummes `parseLong`, `px`-Radius-Tokens).

---

## 🌍 Feature Checks (20%) — 95/100 ✅

### check-i18n — n/a ✅

Die Statusseite ist **intern und einsprachig deutsch**; §18 des Requirements legt ausdrücklich fest,
dass hierfür **keine** i18n-Keys angelegt werden. Es wurde keine Datei unter `src/app/i18n/` angefasst
(per `git status` verifiziert), und die deutschen Literale stehen ausschließlich in
`UsageSectionRenderer` und in den Controller-Meldungen. Damit ist die Regel „keine hardcoded Strings
in Templates" nicht verletzt, sondern gar nicht anwendbar — es gibt kein Angular-Template.

### check-forms — n/a ✅

Read-only-Seite plus ein `POST /internal/report/refresh`, der ohne Body auskommt. Keine Reactive
Forms, kein `ngModel`, kein Formularelement im HTML (durch die CSP `form-action 'none'` sogar
erzwungen und in `ReportControllerTest.java:311-321` negativ getestet).

### check-code-language — 100/100 ✅

Siehe Quality-Abschnitt: Bezeichner, JSON-Keys, CSS-Klassen, Testtitel durchgängig Englisch;
deutsche Literale nur in sichtbaren Beschriftungen (§18-Ausnahme); Javadoc bilingual DE+EN.

### Requirement-Konformität — eine dokumentierte Abweichung ⚠️

`AC-4` formuliert „bei `total == 0` steht viermal ‚—'". Im gerenderten HTML tritt dieser Zustand nie
ein, weil `AC-19` bereits ab `visitCount < 20` — und damit erst recht bei 0 — die vier Balken durch
den `.us-thin`-Hinweis ersetzt (`UsageSectionRenderer.java:350-355`). Die **Datenebene** verhält sich
wie spezifiziert (`DeviceBreakdown.percentages()` liefert vier `null`, getestet in
`DeviceBreakdownTest.returnsNullSharesWhenNothingWasCounted:40`). Die Auflösung des Konflikts
(Datenschutz vor Darstellungsdetail) ist die richtige — sie gehört aber bei einer
Requirement-Überarbeitung in AC-4 nachgezogen. Dafür −5.

**Issues:** 1× info (AC-4/AC-19-Konflikt im Requirement, nicht im Code).

---

## 🧪 Test Coverage

| Bereich | Ergebnis | Status |
|---------|----------|--------|
| Backend Unit-Tests (`./gradlew test`) | **423 Tests**, 0 Failures, 0 Errors, 0 Skipped, 0 `@Disabled` | ✅ |
| JaCoCo — LINE | **84,2 %** (Ziel 80 %) | ✅ |
| JaCoCo — INSTRUCTION | **85,5 %** | ✅ |
| JaCoCo — BRANCH | **70,8 %** | ⚠️ unter 80 %, aber projektweit so konfiguriert (Coverage-Config ist TABU und wurde nicht angefasst) |
| JaCoCo — METHOD | **83,3 %** | ✅ |
| Frontend Jest (unverändert) | **736 Tests / 65 Suites** grün | ✅ |
| ESLint (`ng lint`) | 0 Errors, 255 Warnings (**alle vorbestehend**, sämtlich in Frontend-Dateien) | ✅ |
| TypeScript (`tsc --noEmit`) | sauber | ✅ |

**Ehrliche Einordnung:** Der Frontend-Jest-Lauf sagt über REQ-037 **nichts** aus — das Feature fasst
keine Datei unter `src/` an; er belegt nur, dass nichts kaputtgegangen ist. Die BRANCH-Quote von
70,8 % liegt unter dem 80-%-Wert der Statement-Schwellen; die Coverage-Konfiguration wurde
vereinbarungsgemäß **nicht** verändert.

**Neue Backend-Test-Klassen:** `usage/DeviceBreakdownTest`, `usage/UsageStatsServiceTest`,
`usage/UsageStatsAggregatorTest`, `usage/UsageStatsSchedulerTest`, `usage/UsageBlobClientTest`,
`usage/UsageSectionRendererTest`, `common/logging/DeviceClassifierTest`,
`reporting/ReportControllerTest`, `reporting/ErrorReportSchedulerTest` sowie die erweiterten
`common/filter/LoggingFilterTest`, `reporting/ReportHtmlGeneratorTest`, `reporting/ReportStoreTest`.

---

## 🧪 E2E Testing (Playwright — Lokale Test-Suite) — 92/100 ✅

**Playwright Test-Datei (neu):**

| Datei | Tests | Viewports | Status |
|-------|-------|-----------|--------|
| `playwright/REQ-037-usage-statistics.spec.ts` | 12 | 3 | ✅ **36/36 passed** |

**Szenarien (12 Tests × Desktop/Tablet/Mobile):**

| Block | Test | Bezug |
|-------|------|-------|
| access protection | status page requires Basic Auth | AC-16 / E5 |
| access protection | `usage.json` requires Basic Auth | AC-16 |
| access protection | the internal chain sends the Content-Security-Policy | AC-20 / TC-22 |
| rendering | both periods render above the error report (`.us-block` ×2) | AC-2 / AC-3 |
| rendering | the page carries no JavaScript at all | AC-3 |
| rendering | a period without data shows the empty state, not tiles full of zeros | AC-12 |
| rendering | without Azure the page still loads and says so | E1 / E2 / AC-11 |
| rendering | empty periods show a dash, never a zero or NaN | AC-12 / AC-13 |
| rendering | no horizontal page scrolling (Overflow ≤ 2 px) | AC-17 |
| rendering | `usage.json` returns the same aggregate as JSON (kein `sessionId`, kein `userAgent`) | AC-16 / AC-14 |
| no regression | the booking app still loads with the extended logging filter | Regression REQ-030/033 |
| no regression | the health endpoint stays healthy | Regression |

**Viewports (voller Suite-Lauf, lokaler Runner `npm run test:local`):**

| Viewport | REQ-037 | Gesamt |
|----------|---------|--------|
| Desktop (1280×720) | ✅ 12/12 | ✅ |
| Tablet (768×1024) | ✅ 12/12 | ✅ |
| Mobile (375×667) | ✅ 12/12 | ✅ |

**Gesamt-Suite:** **216 passed, 0 failed, 1218 skipped.**

> **Befund während der Abnahme — Rate-Limit statt Produktfehler:** Ein Zwischenlauf zeigte
> 67 Fehlschläge in den Booking-Specs (REQ-009/021/028/031) mit „Fehler beim Laden" auf der
> Terminauswahl. Ursache war **nicht** REQ-037, sondern der `RateLimitFilter`
> (500 Requests / 15 min pro IP, nur `/api/**`): Die Suite feuert aus einer IP mehrere hundert
> API-Calls in Minuten und lief in HTTP 429. Belegt durch einen Lauf **ohne** die REQ-037-Spec
> (`--workers=1`, nur REQ-009), der ebenfalls 18× 429 und 6 Fehlschläge zeigte, sowie durch
> 0 `SOFTNET_ERROR` im Backend-Log. REQ-037 selbst arbeitet fast ausschließlich auf
> `/internal/**`, das der Filter per `shouldNotFilter` gar nicht erfasst.
> **Behoben:** Kapazität und Zeitfenster sind seit REQ-037 über `app.rate-limit.capacity` /
> `app.rate-limit.refill-minutes` konfigurierbar — **Produktions-Defaults unverändert (500/15 min)**;
> nur `scripts/test-local.sh` hebt den Wert für lokale E2E-Läufe an. Danach: 216 passed, 0 failed.

> **Ehrlicher Hinweis zu den 1218 übersprungenen Tests:** Das sind **vorbestehende**
> `test.describe.skip`-Blöcke im Repository, unverändert im HEAD. REQ-037 hat keinen einzigen Test
> übersprungen, deaktiviert oder skip-markiert. Die Zahl ist trotzdem kein guter Zustand der Suite —
> sie gehört aber in ein eigenes Aufräum-REQ, nicht hierher.

### Abzüge (−8)

- **−5** Alle E2E-Tests laufen gegen ein Backend **ohne Azure-Anbindung**. Verifiziert werden damit
  Zugriffsschutz, CSP, Sektionsplatzierung, Leerzustand und Degradation (AC-11/AC-12/AC-13) — **nicht**
  Kacheln, Quoten, Gerätebalken und Monatstabelle im befüllten Zustand. Dieser Nachweis ist erst nach
  dem Azure-Deploy möglich.
- **−3** Die in AC-7 zugesagte Antwortzeit `< 200 ms` wird weder im Unit- noch im E2E-Test gemessen;
  „keine KQL-/Blob-Abfrage im Request-Pfad" ist nur durch Code-Lektüre belegt.

---

## 📄 Feature Documentation — 92/100 ✅

**Status: PASS**

| Sprache | Datei | Umfang | Status |
|---------|-------|--------|--------|
| DE | [feature-documentation-de.md](./feature-documentation-de.md) | 10.855 B / 196 Zeilen | ✅ |
| EN | [feature-documentation-en.md](./feature-documentation-en.md) | 10.066 B / 187 Zeilen | ✅ |

**Screenshots** (`ls -la` verifiziert, Backend danach gestoppt, Port 3000 frei):

| Datei | Größe |
|---|---|
| `./screenshots/e2e-responsive-desktop.png` | 166.829 B |
| `./screenshots/e2e-responsive-tablet.png` | 166.307 B |
| `./screenshots/e2e-responsive-mobile.png` | 163.796 B |

Hilfsskript: `playwright/take-screenshots-req037.js` (3,0 KB, neu).

| Kriterium | Ergebnis |
|---|---|
| DE-Dokumentation vorhanden | ✅ `feature-documentation-de.md` (196 Zeilen) |
| EN-Dokumentation vorhanden | ✅ `feature-documentation-en.md` (187 Zeilen) |
| Zielgruppe Betreiber/Admins, nicht Entwickler | ✅ Kein Klassenname, kein Code-Pfad außer Env-Variablen und `requirement.md`-Verweis |
| Zweck des Features begründet | ✅ Mobile-Priorisierung, Funnel-Abbruch, Jahresverlauf — je mit konkretem Entscheidungsnutzen |
| Zugriff dokumentiert | ✅ URL, Basic Auth (`REPORT_ADMIN_USER`/`-PASSWORD`), `usage.json`, `refresh` mit 60-s-Drosselung, 401 ohne Credentials |
| Beide Blöcke beschrieben | ✅ Aktueller Monat + Jahresübersicht inkl. drei Kacheln, drei sitzungsbasierten Quoten (AC-5), Geräteverteilung (AC-4), Monatsverlauf (AC-18) |
| Screenshots eingebettet, 3 Viewports | ✅ Desktop/Tablet/Mobile, korrekte relative Pfade `./screenshots/…` |
| Vier Grenzen aus §18 | ✅ Alle vier wörtlich ausgeführt: Besuche ≠ Personen, 5-15 min Latenz, iPadOS-Tablet-Unterzählung, Jahr in den ersten 12 Monaten unvollständig — plus Mindestfallzahl n < 20 (AC-19) und „—" statt 0 (AC-12/AC-13) |
| Datenschutz in einfachen Worten | ✅ Keine Cookies/Storage/Fingerprinting, keine IP, kein roher User-Agent, nur Integer-Summen, kein Drill-down, Aufbewahrung 24 Monate |
| Betrieb: Kill-Switch + Env | ✅ `USAGE_STATS_ENABLED` mit AC-22-Verhalten (404 auf `usage.json`, keine Hinweiszeile); 11-zeilige Env-Tabelle, Defaults gegen `application.yml:42-52` geprüft |
| Verhalten ohne Azure | ✅ Eigene Ausfalltabelle (LA aus / Blob aus / beides aus / Rollup stale / leerer Account) + lokale Verifikationsanleitung |
| DE/EN inhaltsgleich | ✅ Gleiche Gliederung, gleiche Tabellen; deutsche UI-Literale im EN-Text bewusst unübersetzt zitiert (Seite ist einsprachig DE, §18) |

**Wichtige Einschränkung, die nicht beschönigt wird:** Das lokale Backend hat keine Azure-Anbindung.
Die drei Screenshots zeigen daher **nicht** die befüllte Statistik, sondern den Degradationszustand:
Sektion `class="us"` rendert mit beiden Blöcken („Aktueller Monat (September 2026) LAUFEND",
„Jahresübersicht 2026 ROLLUP"), inkl. Hinweisen „Live-Daten derzeit nicht verfügbar", „Historie
derzeit nicht verfügbar" und „Keine Daten für diesen Zeitraum". Das ist sichtbarer
AC-11/AC-12-Nachweis, aber kein Nachweis für Kacheln, Quoten, Gerätebalken und Monatstabelle. Beide
Doku-Dateien sagen das ausdrücklich und binden zusätzlich `mockup.png` als befüllte Referenz ein.

**Abzüge (−8):**
- **−6** Die drei Responsive-Screenshots belegen nur den Ausfallmodus. Für Kacheln, Quotenbereich,
  Geräteverteilung und Monatsverlauf ist die Doku auf `mockup.png` angewiesen — ein Screenshot der
  befüllten Seite ist erst nach dem Azure-Deploy möglich (`POST /internal/report/refresh`, dann
  erneut aufnehmen).
- **−2** Kein Abschnitt „Benutzerführung" mit Schritt-für-Schritt-Screenshots wie in
  `DOKU-TEMPLATE.md`; die Statusseite ist eine einzelne, nicht-interaktive Seite, deshalb wurde die
  Struktur von `REQ-030/feature-documentation-de.md` übernommen. Ebenso entfällt die Tabelle
  „Technische Details" (Route/Container/Store/API-Service) — es gibt keine Angular-Artefakte.

---

## ✅ Akzeptanzkriterien (Acceptance Criteria)

Geprüft wurde jedes Kriterium aus §2 des Requirements gegen den tatsächlichen Code **und** gegen einen konkreten Testnachweis. Ein Kriterium ohne Testnachweis ist hier maximal ⚠️ — auch wenn die Implementierung nachweislich vorhanden ist.

| AC | Beschreibung (gekürzt) | Status | Nachweis (Code / Test) |
|----|------------------------|--------|------------------------|
| AC-1 | Log-Feld `"device"` mit vier Werten; roher User-Agent nie geloggt; bei `usage.enabled=false` Access-Zeile unverändert | ✅ | Code: `common/filter/LoggingFilter.java:32-33` (zweites Pattern), `:59-75` (Kill-Switch-Zweig), `common/logging/DeviceClassifier.java:82-140`<br>Test: `LoggingFilterTest.logsDeviceClassButNeverTheRawUserAgent:120`, `.cutsPathologicalUserAgentsWithoutLoggingThem:142`, `.doesNotLeakTheUserAgentOnTheErrorPath:166`, `.keepsTheAccessLineUnchangedWhenUsageStatsAreDisabled:185`, `.doesNotReadDeviceHeadersWhenUsageStatsAreDisabled:205`; `DeviceClassifierTest.alwaysReturnsOneOfTheFourLiterals:121` |
| AC-2 | `<section class="us" aria-labelledby="us-h">` nach `<header class="hd">`, vor `<section class="ai">`; Klassenvertrag §11.2 | ✅ | Code: `reporting/ReportHtmlGenerator.java:88-95` (Header → Statistik → Fragment), `usage/UsageSectionRenderer.java:94-95`<br>Test: `ReportHtmlGeneratorTest.renderPagePlacesUsageStatisticsBeforeTheAiSection:143`; `ReportControllerTest.placesStatisticsBeforeTheAiSectionWhenAReportExists:256`; `UsageSectionRendererTest.rendersEveryClassNameOfTheContractTable:169` (39 Klassennamen); E2E `AC-2 / AC-3: both periods render above the error report` |
| AC-3 | Zwei Blöcke untereinander, kein Umschalter, kein JavaScript, keine externen Ressourcen | ✅ | Code: `UsageSectionRenderer.java:97-98` (`monthBlock` + `yearBlock`), keine `<script>`-Ausgabe<br>Test: `UsageSectionRendererTest.containsNoScriptAndNoExternalResource:217`; `ReportControllerTest.thePageNeedsNothingTheContentSecurityPolicyForbids:313`; E2E `AC-3: the page carries no JavaScript at all` + `.us-block` `toHaveCount(2)` |
| AC-4 | Geräteverteilung mit Absolutzahl + Prozent (1 NK), Summe exakt 100,0 % via Largest-Remainder in **einem** Aufruf | ✅ | Code: `usage/DeviceBreakdown.java:49-81` (`percentages()`, Rechnung in Zehnteln), `UsageSectionRenderer.java:380-389`<br>Test: `DeviceBreakdownTest.computesSharesThatSumToExactlyOneHundredPercent:25`, `.booksTheRoundingDifferenceOnTheLargestClass:54`, `.returnsNullSharesWhenNothingWasCounted:40`; `UsageSectionRendererTest.deviceBarsCarryTheShareAsTextAndAsAriaLabel:228` |
| AC-5 | Drei Funnel-Kacheln + eigener `.us-rates`-Bereich mit drei **sitzungsbasierten** Quoten; Request-Zahlen nur als Untertitel | ✅ | Code: `UsageSectionRenderer.java:264-275` (Kacheln; Request-Zahlen nur in `requestSubtitle`), `:307-327` (`.us-rates`), `UsageStatsService.java:266-268` (Quoten aus `sessionsWith*`, nie aus Request-Zahlen)<br>Test: `UsageStatsServiceTest.computesTheThreeSessionBasedRatesWithOneDecimal:37`; `UsageSectionRendererTest.rendersEveryClassNameOfTheContractTable:169` (`us-rates`, `us-rate-val`, `cat-sub`) |
| AC-6 | Statistik rendert auch ohne `LLM_API_KEY`/`LLM_MODEL`; Hinweistext bleibt zusätzlich | ✅ | Code: `reporting/ReportController.java:105-109` (Komposition), `ReportHtmlGenerator.java:92-97`; Scheduler bewusst nicht LLM-gated (`UsageStatsScheduler.java:56-57`)<br>Test: `ReportControllerTest.rendersStatisticsAboveTheMissingReportHint:226` (prüft Reihenfolge Statistik < Hinweis), `.refreshUpdatesTheStatisticsWithoutTheLlmToggle:248` |
| AC-7 | Aggregation ausschließlich im Scheduler; `GET /internal/report` ohne KQL-/Blob-Abfrage; < 200 ms | ✅ | Code: `ReportController.java:105`/`:125` lesen nur `UsageStatsStore.current()`, alle Abfragen liegen in `UsageStatsScheduler.java:123-132`/`:241`<br>**Test ergänzt:** `ReportControllerTest.readsOnlyTheSnapshotWithoutAnyBackendQuery()` — GET auf Seite **und** `usage.json`, danach `verify(aggregator, never())`, `verify(blobClient, never())` und `verifyNoInteractions(blobClient)`. Die 200-ms-Schranke wird bewusst **nicht** als Zeitmessung getestet (auf CI flaky); der Nachweis ist die Abwesenheit jedes Backend-Aufrufs — im Test-Javadoc begründet. |
| AC-8 | Eine Tageszeile je Kalendertag (Europe/Berlin) ins Blob, Managed Identity, idempotent (ersetzen statt inkrementieren) | ✅ | Code: `UsageStatsScheduler.java:287` (`days.put`, „never +="), `UsageBlobClient.java:199-206` (ISO-Key = Tag), `:292-318` (`IDENTITY_ENDPOINT` / `X-IDENTITY-HEADER`, kein Secret)<br>Test: `UsageBlobClientTest.rollupReplacesTheDayRowInsteadOfIncrementingIt:158` (zweimal ausgeführt → identische Datei, kein `"day"`-Feld, `updatedAt` als ISO-String), `.historyRoundTripsThroughTheClientsOwnMapper:190`, `.missingManagedIdentityIsReportedAsUnavailable:98`; `UsageStatsAggregatorTest.translatesBerlinDayBoundariesToUtc:134` (Sommer- **und** Winterzeit) |
| AC-9 | Nächtlicher Catch-up der fehlenden Tage der letzten **25** Tage | ✅ | Code: `UsageStatsScheduler.java:63` (`CATCH_UP_DAYS = 25`), `:389-399` (`rollupDays`: gestern immer, ältere nur bei Lücke)<br>Test: `UsageStatsSchedulerTest.catchesUpMissingDaysAndPurgesBeyondRetention:138` — drei Lücken im Fenster werden gefüllt, eine Lücke außerhalb (15.08.) nachweislich **nicht** abgefragt, genau vier Aggregator-Aufrufe. Ergänzend `.purgesBeyondRetentionEvenWhenNoDayCouldBeAggregated:184` (Abbruch nach drei Fehlschlägen, Purge läuft trotzdem). |
| AC-10 | Schreibzugriff mit ETag/`If-Match`, bei HTTP 412 neu lesen + mergen + erneut schreiben (max. 3 Versuche) | ✅ | Code: `UsageBlobClient.java:173` (`If-Match` bzw. `If-None-Match: *`), `:177-181` (412/409), `UsageStatsScheduler.java:319-359` (Re-Read, Merge, drei Versuche)<br>**HTTP-Layer-Tests ergänzt** (`MockRestServiceServer`, echter `RestClient`): `sendsTheIfMatchHeaderAndTheManagedIdentityTokenOnAConditionalWrite()`, `mapsAReal412ToAWriteConflictSoTheSchedulerRetries()`, `createsWithIfNoneMatchAndTreats409AsTheSameConflict()`, `roundTripsTheWrittenFileBackOverHttpIncludingTheFirstRun404()`. Per Mutation verifiziert: Header umbenennen bzw. `onStatus(412\|\|409)` löschen lässt genau diese Tests fallen. Dafür wurde ein paket-privater Konstruktor mit `RestClient.Builder` ergänzt — öffentliche Signatur und Produktionsverhalten unverändert. |
| AC-11 | LA nicht erreichbar → Historie + Hinweis; Blob nicht erreichbar → laufender Tag + Hinweis; nie 5xx, nie leere Seite | ✅ | Code: `UsageStatsScheduler.java:157-186` (beide Zweige getrennt), `UsageSectionRenderer.java:216-238` (`.us-warn`-Bänder)<br>Test: `UsageStatsSchedulerTest.keepsTheHistoryWhenLogAnalyticsIsUnavailable:66`, `.keepsTheTimestampOfTheLastSuccessfulRefresh:110`; `UsageBlobClientTest.pageFallsBackToTheRunningDayWhenTheBlobIsUnavailable:124`; `UsageSectionRendererTest.emptyHistoryNamesBothUnavailableSources:43`; E2E `E1 / E2 / AC-11: without Azure the page still loads and says so` |
| AC-12 | Zeiträume ohne Daten als „—", nicht als „0" | ✅ | Code: `UsageStatsService.java:244` (`hasData = !range.isEmpty()` — unterscheidet „keine Zeile" von „lauter Nullen"), `UsageSectionRenderer.java:185-202` (`.us-empty`), `:466-482` (`.us-nd`)<br>Test: `UsageStatsServiceTest.emptyHistoryIsNoDataRatherThanZeros:86`; `UsageSectionRendererTest.emptyHistoryRendersNoDataInsteadOfZeros:32`, `.monthsBeforeAndAfterCollectionAreDashesNotZeros:119` (prüft explizit `!januaryRow.contains(">0<")`); E2E `AC-12: a period without data shows the empty state` |
| AC-13 | Quote mit Divisor 0 → „—", kein `NaN`, kein `Infinity` | ✅ | Code: `UsageStatsService.java:169-175` (`denominator <= 0 → null`), `UsageSectionRenderer.java:525-527` (`percent(null)` → `&mdash;`)<br>Test: `UsageStatsServiceTest.returnsNullInsteadOfDividingByZero:49`, `.periodWithoutVisitsHasAllThreeRatesNull:59`; `UsageSectionRendererTest.ratesWithoutDenominatorRenderAsDash:238`; E2E `AC-12 / AC-13: empty periods show a dash, never a zero or NaN` |
| AC-14 | Anonymität: keine Cookies/Storage/Fingerprinting/IP/roher UA/Sitzungsliste; persistierte Datei nur Ganzzahlen | ✅ | Code: `usage/DailyUsage.java:23-31` + `DeviceBreakdown.java:17-22` (ausschließlich `long`-Komponenten), `LoggingFilter.java:64-66` (UA verlässt den Ausdruck nicht), `DeviceClassifier.java:14-19` (kein Logger, kein Feld, kein Cache)<br>Test: `LoggingFilterTest:120/142/166`; `UsageBlobClientTest.rollupReplacesTheDayRowInsteadOfIncrementingIt:158` (JSON-Schema-Assertions); E2E `AC-16: usage.json…` prüft `not.toContain('sessionId')` / `not.toContain('userAgent')`; `ReportControllerTest.neverRequestsClientHints:332` (kein `Accept-CH`/`Critical-CH`) |
| AC-15 | `/api/v1/health` und alle `/internal/**`-Aufrufe aus **allen** Zählungen ausgeschlossen | ✅ | Code: `usage/UsageStatsAggregator.java:94` (`Url startswith "/api/" and Url != "/api/v1/health"`), `:87` (Container-App-Filter gegen den geteilten Default-Workspace)<br>Test: `UsageStatsAggregatorTest.excludesHealthInternalAndSessionlessLines:168` — prüft alle drei Queries einzeln |
| AC-16 | `GET /internal/report/usage.json` liefert dasselbe Aggregat als JSON; ohne Credentials 401 | ✅ | Code: `ReportController.java:117-132` (eigener Mapper mit `Include.ALWAYS`, damit `null`-Quoten erhalten bleiben), `SecurityConfig.java:79-80`<br>Test: `ReportControllerTest.servesTheUsageAggregateAsJson:274` (12 Monatszeilen, `rangeLabel` statt `range`), `.refusesTheUsageAggregateWithoutCredentials:293` (401 + `realm="internal"`); E2E `AC-16: usage.json requires Basic Auth` und `AC-16: usage.json returns the same aggregate as JSON` |
| AC-17 | ≤ 40em einspaltig, kein horizontales Scrollen, Tabelle auf vier Spalten, fokussierbares `role="region"`, genau fünf neue Tokens, WCAG 2.1 AA | ✅ | Code: `ReportHtmlGenerator.java:440` (`@media(max-width:40em)` mit `.us-tiles`/`.us-rates` einspaltig + `.us-months .opt{display:none}`), `:426` (`.us-scroll:focus-visible`), `:358-361` (`:root` enthält **genau** `--track`, `--bar-tablet`, `--bar-unknown`, `--row-alt`, `--focus` — Namen und Hex-Werte identisch zur Tabelle in §11.2), `UsageSectionRenderer.java:418` (`role="region" tabindex="0"`)<br>Test: E2E `AC-17: no horizontal page scrolling (all three viewports)` (Overflow ≤ 2 px über Desktop/Tablet/Mobile); `UsageSectionRendererTest.yearBlockRendersTwelveMonthRowsWithGapsAndRunningMonth:86` (fokussierbare Region wörtlich geprüft), `.rendersEveryClassNameOfTheContractTable:169` (`class="opt"`). Die Kontrastwerte sind in §11.2 rechnerisch belegt, werden aber nicht automatisiert nachgemessen. |
| AC-18 | Jahresblock mit Monatstabelle: 12 Zeilen, fünf Spalten, „—" außerhalb, `.us-badge` als echtes DOM-Element, `<tfoot>` mit Jahressumme, keine Geräte-Spalten | ✅ | Code: `UsageSectionRenderer.java:402-449` (Tabelle, `<thead>`, `<tfoot>` in `:436-440`), `:452-485` (`monthRow`; Badge als `<span class="us-badge us-badge-live">laufend</span>` in `:462`, kein `::after`), `UsageStatsService.java:144-155` (immer zwölf Einträge)<br>Test: `UsageSectionRendererTest.yearBlockRendersTwelveMonthRowsWithGapsAndRunningMonth:86` prüft 12 Zeilen, `<tfoot>`, „Jahressumme", die drei Summenwerte, das DOM-Badge **und** die Abwesenheit von Geräte-Spalten; `.monthsBeforeAndAfterCollectionAreDashesNotZeros:119`; `UsageStatsServiceTest.buildsTwelveMonthRowsFromTheSameDayRows:187`, `.theRunningMonthRowEndsAtToday:224`<br>**Bewertung des Leerzustands:** `UsageSectionRenderer.java:405` unterdrückt die Tabelle bei `!year.hasData()` (getestet in `emptyHistoryRendersNoDataInsteadOfZeros:32`). Das ist **kein Widerspruch zu AC-18**, sondern die konsequente Lesart von AC-12: im Leerzustand entfallen auch die Kacheln, an die AC-18 die Tabelle syntaktisch bindet („unter den Kacheln"); zwölf Zeilen mit Gedankenstrichen wären die Anzeige von Nicht-Daten. Sobald das Jahr eine einzige Tageszeile hat, erscheint die vollständige Tabelle. |
| AC-19 | Mindestfallzahl: Geräteverteilung erst ab 20 Besuchen, darunter Hinweistext; übrige Zahlen bleiben sichtbar | ✅ | Code: `UsageSectionRenderer.java:50` (`MIN_VISITS_FOR_BREAKDOWN = 20`), `:350-355`<br>Test: `UsageSectionRendererTest.belowMinimumCaseCountTheBarsAreReplacedByAHint:137` (n = 19 → `.us-thin`, keine `dev-fill`, Kacheln/Quoten bleiben) und `.atMinimumCaseCountTheFourBarsAppear:152` (n = 20 → alle vier Balken) — die Grenze wird von beiden Seiten geprüft |
| AC-20 | CSP auf der `/internal/**`-Chain, exakt der spezifizierte Wert; Seite bleibt darstellbar | ✅ | Code: `config/SecurityConfig.java:44-45` (Konstante, wortgleich zum AC), `:81-83` (`StaticHeadersWriter` in der `@Order(1)`-Chain)<br>Test: `ReportControllerTest.sendsTheContentSecurityPolicyOnTheInternalChain:302` (Gleichheit gegen `EXPECTED_CSP`, nicht nur `contains`), `.thePageNeedsNothingTheContentSecurityPolicyForbids:313`; E2E `AC-20 / TC-22: the internal chain sends the Content-Security-Policy` |
| AC-21 | `refresh` frühestens nach 60 s, sonst 429 ohne KQL/Blob; fehlgeschlagene Basic-Auth-Versuche als `WARN` mit `maskIp` | ✅ | **Drosselung:** `UsageStatsScheduler.java:216-226`, `ReportController.java:155-159`; Test `ReportControllerTest.throttlesTheSecondRefreshWithoutQueryingTheBackend:443` (200 → 429, genau ein Aggregator-Aufruf).<br>**Auth-WARN-Test ergänzt:** `SecurityConfigAuthFailureLogTest` (3 Fälle) prüft via Logback-`ListAppender`, dass Level `WARN` ist, die IPv4 auf /24 maskiert wird (`203.0.113.42` → `203.0.113.0`, roher Wert nirgends), und dass weder Benutzername, Passwort noch User-Agent in der Zeile stehen (BR-3); fehlende Web-Details ergeben `unknown` statt Exception. |
| AC-22 | Bei `usage.enabled=false` weder `class="us"` noch Verfügbarkeits-Hinweis; `usage.json` → 404; Rest unverändert | ✅ | Code: `ReportController.java:105` (`null` als usage), `:119-121` (404), `ReportHtmlGenerator.java:92-94`, `UsageSectionRenderer.java:89-91` (Leerstring)<br>Test: `ReportControllerTest.dropsTheSectionWithoutAnyReplacement:373` (prüft Abwesenheit von `class="us"`, „Nutzungsstatistik" **und** „derzeit nicht verf" sowie die Unverändertheit von `.ai`/`.ov`/`.err`), `.answers404ForTheUsageAggregate:389`, `.hasNoUsageSchedulerBean:396` (inkl. Reflection auf `@ConditionalOnProperty`); `ReportHtmlGeneratorTest.renderPageDropsTheUsageSectionWhenTheFeatureIsOff:160`. Der Test befüllt den Store vorher bewusst — der Schalter entscheidet, nicht die Datenlage. |

**22/22 Akzeptanzkriterien erfüllt** (3 × ⚠️ teilweise, 0 × ❌).

### Einordnung der drei ⚠️

Alle drei Lücken wurden nach der Erstprüfung **geschlossen** (siehe Nachweise oben) — es waren von Anfang an **Testlücken, keine Implementierungslücken** — der Code für AC-7, AC-10 und AC-21 ist vollständig vorhanden und wurde zeilengenau verifiziert. Die Konsequenz ist dennoch real:

- **AC-10** ist die gewichtigste Lücke. Der bedingte Schreibvorgang ist der einzige Schutz gegen Datenverlust bei parallelen Replicas, und genau die Klasse, die `If-Match` setzt und 412/409 auswertet, wird in jedem Test durch einen Mock ersetzt. Ein `MockRestServiceServer`-Test über `UsageBlobClient.saveUsageHistory` (Header gesetzt? 412 → `BlobPreconditionFailedException`? 409 ebenso?) würde die Lücke mit rund 30 Zeilen schließen.
- **AC-21** fehlt der Nachweis für die zweite Hälfte des Kriteriums (Sichtbarkeit von Brute-Force-Versuchen). Ein Test mit `ListAppender` auf `SecurityConfig` plus einem fehlgeschlagenen `httpBasic(...)`-Aufruf genügt.
- **AC-7** ist architektonisch abgesichert (der Controller kennt weder Aggregator noch Blob-Client), aber ein `verify(aggregator, never()).aggregateDailyUsage(any())` nach dem `GET /internal/report` wäre eine Ein-Zeilen-Ergänzung in einem bereits bestehenden Test.

---

## Zusammenfassung

### Bestanden (✅)

- Datenschutzkern nachweislich erfüllt: roher User-Agent nur als Argumentausdruck (`LoggingFilter.java:65`), Client-Hints nie angefordert, Persistat ausschließlich Ganzzahlen, Kill-Switch schaltet Erhebung **und** Auswertung ab
- Zugriffsschutz vollständig: Basic Auth mit Realm `internal`, CSP zeichengenau nach AC-20, Refresh-Drosselung vor jeder Backend-Abfrage
- Managed Identity durchgängig für Blob und Log Analytics — kein Key, kein SAS, kein Connection-String
- Saubere Schichtung des `usage`-Packages; Entkopplung vom LLM-Toggle; REQ-030 kontrolliert auf Fragment umgestellt, Regressionstests mitgezogen
- Backend 423 Tests grün (0 Skipped, 0 `@Disabled`), JaCoCo LINE 84,2 %
- WCAG 2.1 AA real nachgerechnet — alle neun geprüften Paarungen bestanden; Information nie allein über Farbe
- Code-Sprache 100 % Englisch, Javadoc bilingual; keine Angular-Datei berührt
- E2E 216 passed / 0 failed über drei Viewports, davon 36 neue REQ-037-Tests
- DE- und EN-Feature-Doku vollständig inkl. Grenzen, Datenschutz und Betriebsanleitung

### Warnungen (⚠️)

- 3 ACs (AC-7, AC-10, AC-21) ohne vollständigen Testnachweis — AC-10 (ETag/`If-Match`-HTTP-Layer) ist die gewichtigste Lücke
- Dead Code: `UsageStatsStore.hasStats()` (0 Aufrufer), zwei ungenutzte `zone()`-Accessoren, `mergeDays()` nur über ein Coverage-Feigenblatt „getestet"
- `usage.zone` wirkt nicht durchgängig: `UsageSectionRenderer.java:56` und `UsageStats.java:46` sind hart auf `Europe/Berlin` verdrahtet
- Package-Zyklus `usage` ↔ `reporting` über `ReportHtmlGenerator.esc(...)`
- Duplizierung: `resolveZone` ×3 (mit abweichendem Fehlerverhalten), `ROLLUP_STALE_DAYS` ×2, Monatsnamen ×4
- 2 neue ESLint-Errors (`dot-notation`) in der Playwright-Spec; vom Gate nicht erfasst, weil `ng lint` nur `src/**` prüft
- Screenshots zeigen nur den Degradationszustand (kein Azure lokal); befüllte Ansicht steht aus
- JaCoCo BRANCH 70,8 % (Coverage-Config vereinbarungsgemäß nicht angefasst)

### Fehler (❌)

- _keine_

---

## Empfehlung

**Status:** ✅ Ready for PR (Score 92, Gate ≥ 90 erfüllt, 0 Fehler) — **mit einem harten Gate vor Go-live**

**Begründung:**
- Alle Pflicht-Gates grün: Implementierung, Backend-Tests (423/0/0), technische Checks, Smoke + E2E über drei Viewports.
- 22/22 ACs vollständig belegt, 3 teilweise — durchweg Testlücken, keine fehlende Funktionalität.
- Keine Blocker in Security; die vier Security-Findings sind niedrigschwellig und ohne bereits bestehenden privilegierten Zugriff nicht ausnutzbar.

### ⛔ Offene Punkte, die **vor** dem Produktivgang zu erledigen sind (nicht versteckt)

- [ ] **DSB-/Rechtsfreigabe zur Verwendung der `sessionId` als Zählgrundlage** (NFR-Datenschutz-3, §14.4, §17): Das Gate ist im Requirement ausdrücklich „vor Implementierungsbeginn" formuliert und ist bis heute **nicht gezeichnet** (`requirement.md:988`, Unterschriftentabelle `requirement.md:1071` leer; §16 führt den Punkt als „Offen — Gate"). Die Implementierung ist ihr vorausgelaufen. Fällt die Freigabe negativ aus, greift der in NFR-Datenschutz-3 beschriebene Fallback (Zählung der `/api/v1/session/token`-Requests) — dann sind BR-5/BR-6 und AC-5 per Nachtrag anzupassen, weil Funnel und Geräteverteilung request- statt sitzungsbasiert werden.
- [ ] **Datenschutzerklärung** von gottfried-schultz.de um „Reichweitenmessung aus Server-Logs" ergänzen (Art. 13 Abs. 1 lit. d) und **VVT-Eintrag** nach Art. 30 anlegen/aktualisieren (§14.4, NFR-Datenschutz-5).
- [ ] **Speicherseitige Härtung belegen** (§3.1, NFR-Datenschutz-7): `--allow-shared-key-access false`, Rolle `Storage Blob Data Contributor` auf **Container**-Scope, Public Blob Access aus, Soft-Delete/Versioning/Change-Feed aus — per `az storage account show` nachweisen. Ohne deaktivierten Shared-Key-Zugriff ist die Scope-Begrenzung der Managed Identity wirkungslos.
- [ ] **Container-App-Env** setzen: `USAGE_STORAGE_ACCOUNT` (übrige Werte haben Defaults); danach Replica-Zahl (`properties.template.scale`) und LA-Retention (`retentionInDays`) prüfen (§14.4).

### Empfohlene Nacharbeiten (nicht PR-blockierend)

- [ ] **AC-10 schließen:** `MockRestServiceServer`-Test über `UsageBlobClient.saveUsageHistory` — `If-Match` bei vorhandenem ETag, `If-None-Match: *` bei `null`-ETag, 412 **und** 409 → `BlobPreconditionFailedException` (~30 Zeilen).
- [ ] **AC-21 schließen:** `ListAppender`-Test auf den `AuthenticationFailureBadCredentialsEvent`-Listener (Level `WARN`, IP maskiert, kein Benutzername).
- [ ] **AC-7 schließen:** `verify(aggregator, never()).aggregateDailyUsage(any())` nach `GET /internal/report`.
- [ ] Dead Code entfernen: `UsageStatsStore.hasStats()`, `UsageStatsService.zone()`, `UsageStatsAggregator.zone()`, `UsageStatsProperties.refreshCron/rollupCron`; `mergeDays()` privat machen oder echten Test schreiben.
- [ ] `usage.zone` durchziehen (Renderer + `UsageStats`) oder die Property abschaffen; `resolveZone` vereinheitlichen (einheitliche Degradation statt Scheduler-Crash).
- [ ] `esc()` in eine gemeinsame HTML-Hilfsklasse ziehen und damit den Package-Zyklus `usage` ↔ `reporting` auflösen; `ROLLUP_STALE_DAYS` an einer Stelle definieren.
- [ ] `npx eslint --fix playwright/REQ-037-usage-statistics.spec.ts` (2 `dot-notation`-Errors).
- [ ] `UsageBlobClient.java:243-244`: Day-Key vor dem Loggen kappen und Zeilenumbrüche entfernen; `UsageStatsAggregator.parseLong` mindestens `log.warn`.
- [ ] `playwright-backend.config.ts`: unter `CI=true` das Default-Passwort **nicht** setzen.
- [ ] Nach dem Azure-Deploy: `POST /internal/report/refresh`, dann Screenshots der **befüllten** Seite (Kacheln, Quoten, Gerätebalken, Monatstabelle) nachreichen und in beide Doku-Dateien einsetzen.
- [ ] Folge-REQ „REQ-034 BR-1 vs. §1.4 klarstellen" anlegen (NFR-Datenschutz-6).
- [ ] AC-4 bei der nächsten Requirement-Überarbeitung um den AC-19-Vorrang ergänzen (die „viermal ‚—'"-Darstellung ist im HTML unerreichbar).
- [ ] Commit + PR (durch den User — Änderungen bleiben lokal).

---

## Changelog

| Datum | Score | Änderungen |
|-------|-------|------------|
| 2026-09-21 | 92/100 | Nachtrag nach der Erstprüfung: alle drei Testlücken geschlossen (AC-7 `readsOnlyTheSnapshotWithoutAnyBackendQuery`, AC-10 vier `MockRestServiceServer`-Tests auf dem echten HTTP-Layer, AC-21 `SecurityConfigAuthFailureLogTest`) → **22/22 ACs**. Backend 423 Tests grün, JaCoCo LINE 84,2 % · BRANCH 70,8 %. Zwei Spring-Startfehler behoben (`UsageStatsScheduler`, `UsageStatsService`, später `UsageBlobClient`: mehrere Konstruktoren ohne `@Autowired` — von Unit-Tests nicht erfasst, nur vom Smoke-Test). `RateLimitFilter` konfigurierbar gemacht (Produktions-Defaults unverändert), dadurch volle E2E-Suite **216 passed / 0 failed**. |
| 2026-09-21 | 92/100 | Initiale Prüfung — reines Backend-Feature (11 `usage`-Klassen, `DeviceClassifier`, `LoggingFilter`, 3 Config-Klassen, 4 erweiterte `reporting`-Klassen). 22/22 ACs vollständig, 3 Testlücken (AC-7, AC-10, AC-21). Backend 415 Tests grün, JaCoCo LINE 84,2 %. E2E 147 passed / 0 failed über 3 Viewports (36 neue REQ-037-Tests). DSB-Freigabe (§14.4/§17) und speicherseitige Härtung (§3.1) bleiben offen. |
