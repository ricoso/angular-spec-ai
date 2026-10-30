# REQ-043: Bugfixing + KPI (Kennzeichen-Limit, OTV-Vermerk, Anfragen statt Besuche)

**Status:** Implementiert und auf PROD deployt. B-1, B-2, K-1 (erste Fassung) und K-2 am
2026-10-29 mit Image `req043-202610290935` (Revision `gs-terminbuchungs-app--0000055`); K-1
(überarbeitet) und K-3 am selben Tag mit Image `req043k3-202610291131` (Revision
`gs-terminbuchungs-app--0000056`) nachgezogen
**Priority:** High (B-1 verhindert Buchungen in Produktion)
**Type:** Bugfixing + KPI-Anpassung Statistikseite
**Created:** 2026-10-29
**Author:** Agent, im Auftrag des Nutzers (rico.sonntag@lhind.dlh.de)
**Baut auf:** REQ-039 (Kennzeichen-Regel BR-8), REQ-037/REQ-038 (Statistikseite), Revision 0000054

> **Zweck dieses Requirements:** Nachvollziehbar festhalten, welcher Produktionsfehler beim
> Kennzeichen gefunden und behoben wurde, welcher Kundenwunsch für das Hinweisfeld umgesetzt ist
> und wie sich die Kennzahlen der Statistikseite geändert haben. Kein Mockup: Es sind eine
> Fehlerkorrektur und kleine Anpassungen an bestehenden Komponenten.

---

## 1. Overview

| ID | Art | Kurz |
|---|---|---|
| B-1 | Bugfix | Kennzeichen höchstens 20 Zeichen, sonst lehnt SoftNet die Buchung ab |
| B-2 | Kundenwunsch | Räderwechsel-Buchungen bekommen am Ende des Hinweisfelds „Buchung erfolgte über OTV“ |
| K-1 | KPI | Statistik: Kachel „Anfragen“ (Terminsuch-Aufrufe) statt „Besuche“ |
| K-2 | KPI | Statistik: Sektion „Auf einen Blick: Handlungsimpulse“ ausgeblendet |
| K-3 | KPI | Statistik: jede Kachel teilt ihre große Zahl in „ok · Fehler“ auf |

**Deployment-Hinweis:** Frontend und Backend ändern sich. Es gibt **keine** neuen Secrets,
Umgebungsvariablen oder setup.json-Änderungen.

---

## 2. Bugfixes

### B-1: Kennzeichen länger als 20 Zeichen, Buchung scheitert (Produktion)

**Symptom (Fehlermeldung vom 28.10.2026, Volkswagen Zentrum Essen, Trace
`c88f6ab8-1fd4-439e-a733-8e91c2da818a`):** SoftNet antwortet auf `POST /api/planning/appointments`
mit HTTP 500 und `InvalidParameterError`:
`.car.licenseNr should NOT be longer than 20 characters`. Die Kundin sieht nur eine allgemeine
Fehlermeldung und erfährt nicht, dass das Kennzeichen-Feld die Ursache ist.

**Ursache:**
- Wir senden das Kennzeichen als `wishlist.car.license`. SoftNet speichert es intern als
  `licenseNr` mit `maxLength 20`.
- Die SoftNet-OpenAPI-Spec dokumentiert für `license` dagegen „max 250 char“. Deshalb ist die
  Grenze bisher nicht aufgefallen.
- Bei uns galt nur: „nicht leer“ (Frontend `required` + `pattern(/\S/)`, Backend `@NotBlank`,
  REQ-039 BR-8). Eine Längenprüfung gab es weder im Frontend noch im Backend, der Text ging
  unverändert an SoftNet.
- SoftNet meldet den Eingabefehler als **500** statt 400. Das Backend macht daraus einen 502, das
  Frontend zeigt den generischen Buchungsfehler.

**Beleg (Log Analytics, 30 Tage bis 29.10.2026):** 3 Fälle mit Kennzeichen-Feldern von 51, 22
und 40 Zeichen (Länge aus der PII-Redaction `***(n)`, der Inhalt ist nicht geloggt). In **allen
drei** Sitzungen gab es danach keine erfolgreiche Buchung mehr. Es waren also 3 verlorene
Buchungen. Vermutete Ursache: Autofill (z. B. E-Mail-Adresse) oder Freitext im Kennzeichen-Feld.

**Fix:**
- **Frontend:**
  - `Validators.maxLength(20)` am Kennzeichen (`carinformation-container`).
  - Natives `maxlength="20"` am Input, damit das Tippen bei 20 Zeichen stoppt.
  - Neue Fehlermeldung `licensePlate.error.tooLong` in allen 5 UI-Sprachen.
  - Das Kennzeichen wird beim Absenden getrimmt.
  - Konstante: `LICENSE_PLATE_MAX_LENGTH` in `customer.model.ts`.
- **Backend:**
  - `@Size(max = 20)` auf `VehicleDto.licensePlate` als zweite Linie
    (`CreateAppointmentRequest.LICENSE_PLATE_MAX_LENGTH`). Ein zu langes Kennzeichen wird mit 400
    abgelehnt, bevor es an SoftNet geht.
  - Das Kennzeichen geht getrimmt an SoftNet.

20 Zeichen reichen für jedes deutsche Kennzeichen inklusive Trennzeichen und E/H-Kennung sowie
für ausländische Kennzeichen.

> Erweitert REQ-039 BR-8: „nicht leer **und höchstens 20 Zeichen**“. REQ-039 selbst bleibt
> unverändert.

### B-2: „Buchung erfolgte über OTV“ im Hinweisfeld bei Räderwechsel

**Wunsch (Kunde, 29.10.2026):** Bei jeder Räderwechsel-Buchung steht ganz am Ende des
Hinweisfelds `Buchung erfolgte über OTV`. Damit kann die Dispo Online-Buchungen besser
koordinieren.

**Regel – „Räderwechsel-Buchung“ ist:**
1. eine Buchung im Boxenstopp-Modus (`variant=tireChange`), **oder**
2. eine Buchung im normalen Ablauf mit mindestens einer Leistung aus der Kategorie
   „Räderwechsel“. Das ist der SoftNet-Tag mit `rank 3`, dieselbe Regel wie bei den
   Servicegruppen der Statistik. „Hol & Bringservice“ hängt an allen Tags und zählt nicht.

**Umsetzung:**
- **Frontend:** `BookingStore.hasTireChangeService` erkennt die Regel und sendet im Buchungs-Request
  das optionale Feld `tireChangeBooking: true`.
- **Backend:** `AppointmentsService` hängt `Buchung erfolgte über OTV` als **letzte Zeile** an
  `wishlist.note`, wenn `variant=tireChange` gilt oder `tireChangeBooking=true` gesendet wurde.
- **Reihenfolge im Hinweisfeld:** Mietwagen-Wunsch (REQ-039) → Kundennotiz → OTV-Vermerk.
- **2000-Zeichen-Limit (SoftNet):** Der Vermerk und der Mietwagen-Wunsch werden zuerst reserviert,
  gekürzt wird nur die Kundennotiz.
- **Fehlerfall:** Ein Fehlalarm ist unkritisch, der Vermerk ist für jede Buchung über die App
  inhaltlich wahr.

**Offen beim Kunden:** Die Fachabteilung prüft, wo der Vermerk im SoftNet-Planer bzw. in der Dispo
erscheint.

---

## 3. KPI-Änderungen (Statistikseite `/internal/report`)

### K-1: „Anfragen“ statt „Besuche“

**Wunsch:** Die erste Kachel der Nutzungsstatistik zeigt statt der Besuche die **Anfragen**. Daraus
ergeben sich die Terminsuchen und daraus die Buchungen.

**Definition (überarbeitet, gilt):**
- „Anfragen“ = jeder Terminsuch-Aufruf `POST /api/v1/snaps` am Kalendertag (Europe/Berlin),
  inklusive Fehler. Das ist die Zahl, die bis dahin klein unter der Kachel „Terminsuchen“ stand
  („39.248 Anfragen“ im Oktober). Sie wandert in die eigene Kachel und kommt unter „Terminsuchen“
  nicht mehr vor, damit „Anfragen“ nur noch einmal auf der Seite steht.
- Der Zähler existiert seit REQ-037. Die Monatstabelle zeigt ihn deshalb für **jeden** erfassten
  Monat, auch für September, ohne Nachberechnung.

**Erste Fassung (Revision 0000055, am selben Tag ersetzt):** Die Kachel zeigte alle
Server-Anfragen (API-Aufrufe + Datei-Abrufe, ≈ 78.800 pro Tag). Das war fachlich nicht gemeint:
Die Zahl ließ sich nicht in den Funnel einordnen, und „Anfragen“ stand doppelt auf der Seite. Die
Zähler `apiRequests`/`fileRequests` werden weiter erfasst und stehen in `usage.json`, auf der
Seite erscheinen sie nicht mehr.

**Anzeige:**
- Kachel **„Anfragen“** (`/snaps`): die große Zahl, darunter „x ok · y Fehler“ (K-3).
- Monatsverlauf: Spalte „Anfragen“ = Terminsuch-Aufrufe je Monat, Jahressumme im Fuß.
- Die Besuche bleiben dort, wo sie Nenner sind: Suchquote, Buchungsquote, Geräteverteilung und
  die Buchungsstrecke („Besuch (App-Aufruf)“).
- `LIMIT_REQUESTS` unter „Messgrenzen“ erklärt Anfragen und Fehler.

### K-3: Jede Kachel teilt ihre große Zahl in „ok · Fehler“ auf

**Wunsch:** Unter jeder großen Zahl steht, wie viele davon ok sind und wie viele Fehler hatten,
als Gesamtzahlen. ok + Fehler ergibt immer die große Zahl. Bis dahin stand unter „Terminsuchen“
und „Buchungen“ die Aufteilung einer **anderen** Zahl (der Anfragen).

**Definition:**

| Kachel | Große Zahl | ok | Fehler |
|---|---|---|---|
| Anfragen | Terminsuch-Aufrufe | Aufrufe mit Status < 400 | Aufrufe mit Status ≥ 400 |
| Terminsuchen | Sitzungen mit Terminsuche | mindestens eine Suche erfolgreich | **jede** Suche der Sitzung gescheitert |
| Buchungen | Sitzungen mit Buchungsversuch | mindestens eine Buchung erfolgreich | **jeder** Buchungsversuch gescheitert |

- Terminsuchen und Buchungen zählen **Kunden (Sitzungen)**, nicht Versuche. Wer nach einem Fehler
  erfolgreich bucht, zählt als ok. Wer 16-mal scheitert, zählt einmal als Fehler. „Fehler“ sind
  damit die Kunden, die wirklich hängen geblieben sind (Nutzerentscheid 29.10.2026).
- „ok“ ist der bisherige Zähler `sessionsWithSearch` bzw. `sessionsWithBooking`. Suchquote,
  Buchungsquote und Abschlussquote rechnen unverändert mit den ok-Sitzungen.
- Sitzungen ohne Session-ID zählen nicht.

**Größenordnung (PROD, Oktober bis 29.10.2026, Log Analytics):**

| Kachel | Große Zahl | ok | Fehler |
|---|---|---|---|
| Anfragen | 39.248 | 37.892 | 1.356 |
| Terminsuchen | 12.712 | 12.406 | 306 |
| Buchungen | 6.043 | 5.684 | 359 |

**Daten & Historie:**
- **Neue Zähler:** `DailyUsage.searchSessionsFailed` und `bookingSessionsFailed`, berechnet in der
  bestehenden Funnel-Abfrage (`failedSessions = countif(okHits == 0 and isnotempty(SessionId))`).
  Es gibt keine zusätzliche KQL-Abfrage.
- **Nachberechnung direkt nach dem Deploy:** `UsageStatsScheduler.backfillOnStartup()` rechnet
  Tageszeilen ohne die neuen Zähler sofort neu, nicht erst um 03:30. Um 03:30 wäre der älteste Tag
  schon aus der 30-Tage-Retention von Log Analytics gefallen.
  - Reichweite `BACKFILL_DAYS = 29`: Jeder Tag, der nach „jetzt minus 30 Tage“ begann, liegt noch
    vollständig in Log Analytics. Ein halb gelöschter Tag überschreibt also nie eine Zeile.
  - Fehlende Tage holt weiter nur das 25-Tage-Fenster aus REQ-037 AC-9 nach.
  - Später startet die Funktion nur, wenn eine solche Zeile existiert. Jeder weitere Start kostet
    also nur einen Blob-Lesezugriff.
- **Ergebnis:** Der Oktober ist vollständig. Für 22.–29.09.2026 sind die Fehler „nicht erfasst“,
  die Jahreskachel nennt das („Fehler erfasst ab 30.09.2026“).
- **Ohne Zähler:** Die Kachel zeigt nur die ok-Sitzungen und „Fehler nicht erfasst“, nie „0 Fehler“.
- **Datenschutz:** Es sind reine Zähler ohne Personenbezug, keine Session-ID wird gespeichert.

### K-2: „Auf einen Blick: Handlungsimpulse“ ausgeblendet

**Wunsch:** Die Sektion S0 (Kacheln „Engpass“, „Renner & Abendbuchungen“, freie Termine,
Werbe-Impuls) ist zu viel Information und wird ausgeblendet.

**Umsetzung:**
- `StatisticsPageRenderer.BRIEFING_VISIBLE = false`. Die Sektion und ihr Sprunglink entfallen.
- Berechnung (`BriefingCalculator`), Renderer und `insights.json` bleiben unverändert. Mit `true`
  ist die Sektion wieder da.

---

## 4. Betroffene Dateien

**Frontend:**
- `src/app/features/booking/models/customer.model.ts`: B-1, `LICENSE_PLATE_MAX_LENGTH`
- `src/app/features/booking/components/carinformation/carinformation-container.component.ts`: B-1, Validator
- `src/app/features/booking/components/carinformation/components/vehicle-form/vehicle-form.component.{ts,html}`: B-1, `maxlength` + Fehlermeldung
- `src/app/core/i18n/translations.ts`: B-1, `licensePlate.error.tooLong` (DE/EN/UK/FR/AR)
- `src/app/features/booking/stores/booking.store.ts`: B-1 (trim) + B-2 (`hasTireChangeService`, `tireChangeBooking`)
- `src/app/features/booking/services/appointment-api.service.ts`: B-2, Feld `tireChangeBooking`

**Backend:**
- `server/.../appointments/dto/CreateAppointmentRequest.java`: B-1 (`@Size(max = 20)`) + B-2 (Feld + Kompat-Konstruktor)
- `server/.../appointments/AppointmentsService.java`: B-1 (trim) + B-2 (`OTV_MARKER`, `composeNote(…, otvMarker)`)
- `server/.../usage/DailyUsage.java`, `PeriodUsage.java`: K-1/K-3, Zähler, `hasAllCounters()`, `searchSessions()`/`bookingSessions()`
- `server/.../usage/UsageStatsAggregator.java`: K-1 `REQUESTS_KQL`, K-3 `failedSessions` in `FUNNEL_KQL`
- `server/.../usage/UsageStatsService.java`: K-1/K-3, Summe nur über erfasste Tage + `requestsFrom`/`sessionsFailedFrom`
- `server/.../usage/UsageStatsScheduler.java`: K-3, `BACKFILL_DAYS` + `backfillOnStartup()`
- `server/.../usage/UsageBlobClient.java`: K-1/K-3, Plausibilitätsprüfung erlaubt fehlende Zähler
- `server/.../usage/UsageSectionRenderer.java`: K-1/K-3, Kacheln + Monatsverlauf
- `server/.../reporting/LimitsSectionRenderer.java`: K-1/K-3, `LIMIT_REQUESTS`
- `server/.../reporting/StatisticsPageRenderer.java`: K-2, `BRIEFING_VISIBLE`

**Tests:** Jest (Store, Container, Vehicle-Form), JUnit (Appointments, Validation, Usage,
Reporting), Playwright `REQ-043-bugfix-kpi.spec.ts` (neu) sowie die Sektionslisten in
`REQ-038-status-tabs.spec.ts` (ohne S0).

---

## 5. Tests & Verifikation

- **Jest:** 849/849 grün, Coverage 92,9 % Statements.
- **Backend (Gradle):** 1403/1403 grün; nach K-1 (überarbeitet) und K-3: 1410/1410 grün.
- **Kacheln mit Daten:** Lokal fehlt Azure, die Statistik ist im E2E deshalb leer (REQ-037 AC-12).
  Die Kacheln mit ok/Fehler, Teilsummen und „nicht erfasst“ prüft `UsageSectionRendererTest`, die
  Nachberechnung `UsageStatsSchedulerTest`.
- **Type-Check:** 0 Fehler. **ESLint:** 0 Fehler.
- **E2E (Playwright, 3 Viewports, `npm run test:local`):** 322 bestanden, 0 fehlgeschlagen
  (1241 übersprungen durch bestehende `describe.skip`-Blöcke). Der neue Spec ist auf Desktop,
  Tablet und Mobil grün. Nach K-1 (überarbeitet) und K-3 erneut: 322 bestanden, 0 fehlgeschlagen.
- Die Buchung im neuen E2E-Spec wird lokal beantwortet (`route.fulfill`), es entsteht keine echte
  Buchung bei SoftNet.

---

## 6. Deployment, Risiken, offene Punkte

- **Deploy-Zeitpunkt:** Der REQ-038-Buchungspuffer liegt nur im RAM. Beim Revisionswechsel fehlen
  die Buchungsmerkmale des laufenden Tages in der Statistik. Am besten früh am Tag deployen.
- **Rollback:**
  - Neue Tageszeilen enthalten `apiRequests`/`fileRequests`. Eine Revision **vor** REQ-043 kann die
    Blob-Datei deshalb nicht lesen (unbekannte Felder → E4).
  - Sie zeigt dann nur den laufenden Tag und schreibt nichts, Datenverlust gibt es also keinen.
  - Nach dem erneuten Vorwärts-Deploy ist alles wieder da.
- **Nachberechnung auf PROD (Revision 0000056, 29.10.2026 11:39):** `usage rollup written: 29
  day(s)`. Damit sind 30.09.–28.10. mit allen Zählern neu geschrieben.
- **Rollback K-3:** Eine Revision vor K-3 kennt `searchSessionsFailed`/`bookingSessionsFailed`
  nicht und kann die Blob-Datei nicht lesen (E4, wie oben). Sie schreibt nichts, Datenverlust gibt
  es keinen.
- **Meldung an SoftNet** (über die Fachabteilung):
  - Die Spec nennt für `car.license` 250 Zeichen, tatsächlich gelten 20 (`licenseNr`).
  - Ein Eingabefehler sollte mit 400 statt 500 beantwortet werden.
