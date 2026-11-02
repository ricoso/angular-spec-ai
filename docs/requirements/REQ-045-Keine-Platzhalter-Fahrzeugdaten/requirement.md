# REQ-045: Keine Platzhalter für unbekannte Fahrzeugdaten an SoftNet

**Status:** Am 02.11.2026 deployt und nach rund 11 Minuten Live-Betrieb zurückgerollt, **nicht live**. SoftNet verlangt `model` an einigen Standorten als Pflichtfeld („Mandatory field model not set“, siehe 5.3). FIN und Erstzulassung wegzulassen funktioniert. **Nutzerentscheid 02.11.2026: `model` wird nie weggelassen**, ohne bekanntes Modell geht wie bisher `"Unknown"`. **Seit 09:48 live (Revision `0000059`, siehe 5.4).**
**Priority:** High (Beschwerden der SoftNet-Mitarbeiter; Risiko für jede Buchung, solange 5.2 offen ist)
**Type:** Verhaltensänderung (Backend)
**Created:** 2026-10-30
**Author:** Agent, im Auftrag des Nutzers (rico.sonntag@lhind.dlh.de)
**Ändert das Verhalten aus:** REQ-009, REQ-018, REQ-039, REQ-040 (dort als „unverändert“ festgeschrieben). Diese Requirements bleiben unverändert.

> **Zweck dieses Requirements:** Sind Fahrzeugmodell, Fahrgestellnummer (FIN/VIN) oder Modelltyp
> nicht bekannt, dürfen die Felder nicht mit Platzhaltern wie „UNKNOWN“ befüllt werden. Sie werden
> weggelassen. Die SoftNet-Mitarbeiter beschweren sich über die Platzhalter: SoftNet übernimmt die
> Werte in den Fahrzeugstamm. Auch Platzhalter, die von außen hereinkommen, werden entfernt.
> Kein Mockup: Es ändert sich nur der Payload an SoftNet.

---

## 1. Overview

| Eintrag | Inhalt |
|---|---|
| Wo | Backend, `AppointmentsService.patchSnapWithBookingData` (Buchung `POST /api/v1/appointments`) |
| Was | `VIN`, `firstRegMonth` und `firstRegYear` werden nur gesendet, wenn eine echte Angabe vorliegt, sonst fehlt der Key im Payload an SoftNet. `model` wird immer gesendet: Kundenwert, sonst echtes Modell aus dem Snap, sonst `"Unknown"` (Pflichtfeld, siehe 5.3/5.4) |
| Wann betroffen | Praktisch jede Buchung: Das Formular fragt die FIN nur bei eingelagerten Reifen und im Rückruf-Modus ab (REQ-040/041), und es gibt weder ein Modell- noch ein Erstzulassungsfeld |
| Frontend | Keine Änderung. `vin`, `model`, `firstRegMonth` und `firstRegYear` bleiben dort `undefined`, wenn sie unbekannt sind |

**Deployment-Hinweis:** Nur das Backend ändert sich. Es gibt keine neuen Secrets, Umgebungsvariablen
oder setup.json-Änderungen.

---

## 2. Änderung

### 2.1 Vorher und nachher je Feld (`wishlist.car` im Payload an SoftNet)

| Feld | Vorher (Angabe unbekannt) | Nachher |
|---|---|---|
| `VIN` | `"UNKNOWN"` | Key fehlt |
| `model` | `"Unknown"` (oder das Modell, das SoftNet schon im Snap mitbrachte) | **Nie weggelassen** (Nutzerentscheid 02.11.2026, siehe 5.3): Kundenwert, sonst echtes Modell aus dem Snap, sonst `"Unknown"`. Neu: Auch ein leeres oder Platzhalter-Modell aus dem Snap wird zu `"Unknown"` |
| `firstRegMonth` | `"01"` (erfundener Default) | Key fehlt |
| `firstRegYear` | `"2020"` (erfundener Default) | Key fehlt |
| `brand` | `""`, wenn der Snap keine Marke trägt | **Unverändert** (siehe 6) |
| `license`, `currentMileage` | getrimmtes Kennzeichen, Kilometerstand | Unverändert |

Bei bekannter Angabe ändert sich nichts: Die FIN geht getrimmt und in Großbuchstaben, Modell und
Erstzulassung getrimmt.

### 2.2 Umsetzung

- Neue Hilfsklasse `com.autohaus.backend.softnet.SoftnetPayload`:
  - `isPlaceholder(String)`: `true` für `null`, leer oder nur Leerzeichen sowie für die Platzhalter
    `unknown`, `n/a`, `na`, `null`, `undefined`, `tbd`, `unbekannt` und `-` (getrimmt, ohne
    Groß-/Kleinschreibung).
  - `putIfKnown(ObjectNode, Feld, Wert)`: setzt den getrimmten Wert nur bei einer echten Angabe, sonst
    wird der Key **entfernt**. Das entfernt auch einen Platzhalter, der aus dem Snap geerbt wurde,
    weil das Auto-Objekt per `deepCopy()` aus dem Snap entsteht.
- `AppointmentsService.normalizeVin` liefert bei leer oder Platzhalter `null` (sonst getrimmt und
  groß). Die Konstante `VIN_UNKNOWN` entfällt.
- `model`: Wert der Buchung, sonst ein echtes Modell aus dem Snap, sonst kein Key.

### 2.3 Unverändert, aber geprüft

- **Frontend:** `booking.store.ts` und `appointment-api.service.ts` senden `undefined`, nie einen Platzhalter.
- **`CarsService`** (`POST /api/v1/cars`, vom Frontend nicht genutzt): Die FIN ist dort Pflicht
  (`@NotBlank`, 17 Zeichen). Es gibt keinen Platzhalter.
- **`LogSanitizer`:** Bei fehlender FIN im Log-Objekt gibt es nichts zu maskieren, kein Fehler. Die
  Zeile `BOOKING_CREATED` zeigt dann kein `VIN`-Feld mehr.

---

## 3. Verhältnis zu bisherigen Requirements

| Requirement | Aussage dort | Jetzt |
|---|---|---|
| REQ-009, REQ-018 | `VIN: dto.vin \|\| "UNKNOWN"`, Defaults `01` und `2020`, `model … \|\| "Unknown"` | ersetzt |
| REQ-039 (BR-9) | „ohne Angabe bleibt es bei `UNKNOWN`“ | ersetzt |
| REQ-040 (BR-6, O-3) | „Backend unverändert (leere FIN → `UNKNOWN`)“ | ersetzt |
| REQ-015 (Bugfix 13.04.2026) | SoftNet lehnte ein leeres `model` ab („Mandatory field model not set“); daraus entstand `'Unknown'` | Risiko, siehe 6 |

---

## 4. Betroffene Dateien

**Backend:**
- `server/src/main/java/com/autohaus/backend/softnet/SoftnetPayload.java` (neu)
- `server/src/main/java/com/autohaus/backend/appointments/AppointmentsService.java`: `normalizeVin`, `patchSnapWithBookingData`, Konstante `VIN_UNKNOWN` entfernt

**Tests:**
- `server/src/test/java/com/autohaus/backend/softnet/SoftnetPayloadTest.java` (neu)
- `server/src/test/java/com/autohaus/backend/appointments/AppointmentsServiceTest.java`

---

## 5. Tests & Verifikation

### 5.1 Automatisierte Tests

- **JUnit (`./gradlew test`):** 1.458 von 1.458 grün (vorher 1.410). Neu: `SoftnetPayloadTest` (42, davon die
  meisten als Parametertests über alle Platzhalter-Schreibweisen) und 6 Tests in `AppointmentsServiceTest`:
  ohne Angabe fehlen die vier Keys, leere und Platzhalter-Werte werden weggelassen, echte Werte gehen
  getrimmt durch, geerbte Platzhalter aus dem Snap werden entfernt, ein echtes Snap-Modell bleibt bei
  leerem Modell der Buchung, das Modell der Buchung hat Vorrang. Der Test zu `normalizeVin` erwartet jetzt `null`.
  `SoftnetPayload` ist zu 100 % durch Tests abgedeckt (Zeilen und Zweige). Die Coverage-Konfiguration
  wurde nicht angefasst.
- **E2E (`npm run test:local -- --grep-invert "Callback=Ja wird als callbackRequested"`, 3 Viewports,
  frisches Backend mit der Änderung):** 325 bestanden, 0 fehlgeschlagen, Laufzeit 5,7 Minuten. Zum
  Vergleich: 328 bei REQ-044. Die Differenz von 3 sind die drei Viewports des ausgeschlossenen Tests
  REQ-021 TC-3 (siehe 5.2). Alle anderen Tests beantworten die Buchung lokal (`route.fulfill`) oder
  lösen keine aus. Die Buchung selbst ist damit **nicht** gegen SoftNet getestet.

### 5.2 Verifikation gegen SoftNet: nicht durchgeführt

Vorgesehen war eine echte Buchung ohne FIN, Modell und Erstzulassung gegen das INT-System. Sie
wurde **nicht** ausgelöst:

- Das lokale Backend spricht nicht mit den `int-`Hosts. Im TLS-Handshake-Log (SNI) des lokalen
  Backends standen `api.soft-nrg.com` und `auth.soft-nrg.com`. Das sind dieselben Hosts, die auch
  die Container App in Produktion nutzt (`SOFTNET_BASE_URL`, `SOFTNET_AUTH_URL`).
- Laut SoftNet-OpenAPI ist `https://api.soft-nrg.com` das **Produktivsystem** und
  `https://int-api.soft-nrg.com` das Integrationssystem. Das lokale Backend nutzt also das
  Produktivsystem. Der Nutzer hat am 02.11.2026 bestätigt, dass eine Buchung von hier echt ist.
  Testbuchungen sind nur einzeln erlaubt (höchstens ein bis zwei) und immer mit dem Hinweistext
  „lhind, Testbuchung, bitte stornieren“. Das Autohaus storniert sie dann.
- Deshalb wurde keine Probe-Buchung an SoftNet geschickt, und der E2E-Test REQ-021 TC-3 (lässt
  `POST /api/v1/appointments` echt an SoftNet durch) wurde beim Regressionslauf ausgeschlossen.

**Folge:** Es ist ungeprüft, ob SoftNet eine Buchung annimmt, bei der `VIN`, `model`, `firstRegMonth`
und `firstRegYear` fehlen.

**Nutzerentscheid 02.11.2026:** Deploy ohne Vorabprüfung bei SoftNet („einfach die Felder
rauslassen“). Geprüft wird über die ersten echten Kundenbuchungen nach dem Deploy (Logzeilen
`SOFTNET_ERROR`, „Mandatory field“). Lehnt SoftNet ab, wird auf die vorherige Revision zurückgerollt.

### 5.3 Ergebnis des Deploys am 02.11.2026: Rollback

| Zeit (MEZ) | Ereignis |
|---|---|
| 09:19 | Deploy Image `req044-045-202611020914` (REQ-044 + REQ-045), Revision `0000057` |
| 09:23 | `0000057` übernimmt 100 % Traffic |
| 09:26 | Testbuchung über die API: VW Zentrum Düsseldorf, 16.12.2026 10:30, Kunde „Testbuchung LHIND“, Hinweistext „lhind, Testbuchung, bitte stornieren“, Termin-ID `6ae8491e099a927a6cb56bb5`. **Angenommen** (HTTP 200, Status „Geplant“), ohne FIN, Modell und Erstzulassung |
| 09:24–09:33 | Echte Buchungen an Volkswagen Essen-Kray, Volkswagen Zentrum Wuppertal und Volkswagen Düsseldorf-Benrath **abgelehnt**: `400 ValidationError` „Save to database validation errors“, Detail `{"message":"Mandatory field model not set","fieldname":"model"}` |
| 09:32 | Rollback auf Image `req043k3-202610291131` (Revision `0000058`), um 09:34 live |

- **Ursache:** `model` ist bei SoftNet **standortabhängig** ein Pflichtfeld. VW Zentrum Düsseldorf nimmt
  Buchungen ohne Modell an, Essen-Kray, Wuppertal und Benrath nicht. Eine einzelne Testbuchung an
  einem Standort beweist deshalb nichts für die anderen.
- **FIN und Erstzulassung:** SoftNet listet in der Fehlerantwort alle fehlenden Pflichtfelder auf, dort
  stand nur `model`. Das Weglassen von `VIN`, `firstRegMonth` und `firstRegYear` wurde akzeptiert.
- **Auswirkung:** Auf `0000057` liefen 84 Buchungsanfragen, davon scheiterten 73; betroffen
  waren 6 Sitzungen (anonym gezählt, viele Wiederholversuche). 11 Sitzungen buchten erfolgreich. Die
  Statistikpuffer beider beendeten Revisionen wurden beim Herunterfahren geschrieben (64 bzw. 11
  Zeilen), es gingen keine Statistikdaten verloren.
- **Folge für REQ-045 (Nutzerentscheid 02.11.2026):** `model` wird nie weggelassen. Die Strecke fragt
  nur Autohaus und Marke ab, ein Modell gibt es nicht. Ohne bekanntes Modell geht wie bisher `"Unknown"`
  (`AppointmentsService.MODEL_UNKNOWN`). Weggelassen werden nur `VIN` und die Erstzulassung. Nach dem
  Rollback liefen auf `0000058` alle Buchungen wieder durch (5 von 5 bis 09:38).

### 5.4 Korrektur nach dem Rollback

- `AppointmentsService.bookingModel(dtoModel, snapModel)`: Kundenwert, sonst echtes Modell aus dem
  Snap, sonst `MODEL_UNKNOWN` (`"Unknown"`). Nie leer, nie weggelassen. `VIN`, `firstRegMonth` und
  `firstRegYear` bleiben über `SoftnetPayload.putIfKnown` weggelassen.
- Tests: `AppointmentsServiceTest` 41/41 (neu `bookingModel_isNeverEmpty_customerThenSnapThenFallback`;
  die Weglass-Tests prüfen jetzt `model == "Unknown"`), `SoftnetPayloadTest` 42/42.
- Neu-Deploy am 02.11.2026 um 09:47: Image `req044-045b-202611020942` (Build `dn2g`), Revision `0000059`,
  ab 09:48 mit 100 % Traffic. Darin auch REQ-044 (Rückruftext, `chunk-TJT6DMI3.js`).
- Zweite und letzte Testbuchung um 09:48 an **Volkswagen Essen-Kray**, also dem Standort mit den meisten
  Ablehnungen: Kunde „Testbuchung LHIND“, `rico.sonntag@lhind.dlh.de`, `015158931878`, Hinweistext
  „lhind, Testbuchung, bitte stornieren“, `model: "Unknown"`, ohne FIN und Erstzulassung. **Angenommen**
  (HTTP 200, „Geplant“, 15.12.2026 11:00, Termin-ID `6ae84e7a23b79c2826601a85`).
---

## 6. Risiken und offene Punkte

- **Pflichtfeld-Risiko (REQ-015).** Am 13.04.2026 lehnte SoftNet ein leeres `model` als „Mandatory
  field model not set“ ab. Das Fallback `'Unknown'` entstand daraus. Ob SoftNet ein **fehlendes**
  Feld akzeptiert, ist unbekannt. Lehnt SoftNet ab, scheitert **jede** Buchung, weil das Frontend
  kein Modellfeld hat. Vor dem Deploy klären:
  1. **Lesend prüfen, ohne Buchung:** `GET /api/planning/fields?scope=car&mandatory=true`
     (SoftNet-OpenAPI `api.planning.fields.query`: „Fields for user or car that are needed for
     successful registration“). Die Antwort listet die Pflichtfelder des Mandanten. Steht `model`,
     `VIN`, `firstRegMonth` oder `firstRegYear` darin, darf das Feld nicht fehlen. Der Aufruf ändert
     nichts in SoftNet und ist auf jedem System unkritisch.
  2. SoftNet fragen, ob `model`, `VIN`, `firstRegMonth` und `firstRegYear` optional sind.
  3. Eine markierte Testbuchung fahren (höchstens ein bis zwei, Hinweistext „lhind, Testbuchung,
     bitte stornieren“). Am 02.11.2026 durchgeführt, siehe 5.3. Weil Pflichtfelder je Standort
     verschieden sind, belegt sie nur den gebuchten Standort.

  **Ergebnis (02.11.2026):** Das Risiko ist eingetreten, `model` ist standortabhängig Pflicht (5.3).
  4. Nach dem Deploy die Logzeilen `SOFTNET_ERROR` beobachten (Alert `alert-softnet-error-logs` besteht). Rollback siehe unten.
- **Rückfallwert nur für `model`.** SoftNet lehnte das fehlende Modell ab (5.3). Der Nutzer hat
  entschieden, dass `model` immer gesendet wird, ohne bekanntes Modell als `"Unknown"`. Für jedes
  weitere Pflichtfeld gilt: nicht stillschweigend einen Platzhalter senden, sondern mit dem Nutzer klären.
- **Fahrzeug-Zuordnung in SoftNet.** Laut SoftNet-Spezifikation wird ein Fahrzeug über FIN und
  Kennzeichen wiedererkannt („If a car with the same VIN and license number exists for the user,
  the existing car will be used“). Ohne FIN (vorher `UNKNOWN`) kann sich die Zuordnung bei
  wiederkehrenden Kunden ändern, etwa ein neues statt des bestehenden Fahrzeugs. Mit SoftNet klären.
- **Bestehende Fahrzeuge in SoftNet.** Platzhalter, die schon im Fahrzeugstamm stehen (FIN
  „UNKNOWN“, Modell „Unknown“, Erstzulassung 01/2020), bleiben dort. Die Bereinigung ist Sache von SoftNet.
- **`brand ""` bleibt unverändert** (Nutzerentscheid offen). Der leere String entsteht nur, wenn der
  Snap keine Marke trägt. Er ist kein „UNKNOWN“, aber SoftNet könnte ihn als nicht gesetzt werten.
- **Erstzulassung war eine Erweiterung des Auftrags.** Der Nutzer nannte Modell, FIN und Modelltyp
  („Informationen wie …“). Die erfundenen Defaults `01` und `2020` folgen derselben Logik und werden
  deshalb ebenfalls weggelassen. Am 02.11.2026 vom Nutzer bestätigt: weglassen, wenn niemand sie
  eingibt. Das Frontend hat kein Eingabefeld für die Erstzulassung, die Felder fehlen also immer.
- **„Modelltyp“** gibt es im SoftNet-Schema nicht als eigenes Feld (`car` kennt `id`, `brand`, `model`,
  `license`, `VIN`, `firstRegYear`, `firstRegMonth`, `currentMileage`). Gemeint ist `model`.

---

## 7. Deployment

- **Zeitpunkt:** Jeder Neustart verwirft den Terminsuche-Cache im RAM (Kunden mitten in der Buchung
  erhalten 404 „Snap … not found in cache“) und den REQ-038-Buchungspuffer. Am besten zwischen
  05 und 06 Uhr: Die Last ist dann minimal (Ø 22 API-Aufrufe pro Stunde), und der Tagespuffer ist
  noch fast leer (siehe `docs/infrastructure/last-und-kapazitaetsprofil.md`, Anforderung A5).
- **Rollback:** Vorherige Revision `gs-terminbuchungs-app--0000056`. Das stellt das alte Verhalten
  (Platzhalter) wieder her, es gibt keine Datenänderung.
- **Durchgeführt am 02.11.2026:** Deploy 09:19 an einem Montagvormittag (Nutzerentscheid), Rollback
  09:32 per `az containerapp update --image …:req043k3-202610291131` (neue Revision `0000058`). Der
  Rollback dauerte rund zwei Minuten. Details in 5.3.
