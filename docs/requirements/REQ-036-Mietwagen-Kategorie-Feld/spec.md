# REQ-036 — Mietwagen-Kategorie im korrekten SoftNet-Feld übertragen (`rentCategoryid`)

## Überblick

Die vom Kunden gewählte Mietwagen-Kategorie (z. B. „Kleinwagen") wird an SoftNet im **falschen Feld** übertragen: Wir senden die ID aus der `rentcategories`-Collection im Feld `wishlist.fleetCategoryid`, das laut OpenAPI-Spec eine ID aus der **fleetcategory**-Collection erwartet. Das nachfolgende System kann die ID dort nicht auflösen — die Mobilitäts-Option ist damit faktisch verloren, obwohl SoftNet die Buchung mit `200 OK` bestätigt.

## Problem (Ist-Zustand, verifiziert am 07.09.2026)

### P1 — Falsches Zielfeld

`WishlistResource` (SoftNet OpenAPI v1.4, verwendet in `POST /api/planning/snaps` und `POST /api/planning/appointments`) definiert vier Mobilitäts-Felder, von denen zwei auf **verschiedene Collections** verweisen:

| Feld | Typ | Spec-Beschreibung |
|---|---|---|
| `isFleet` | boolean | mandatory for rent appointments |
| `fleetType` | enum `char` \| `rentObject` \| `rentInternalCar` | mandatory for rent appointments |
| `fleetCategoryid` | string | mandatory for rent appointments, **id in fleetcategory collection** |
| `rentCategoryid` | string, min=max=**24** Zeichen | 24 char, **id in rentCategory resource**. If empty or missing, means any rent category |

Zwei getrennte Ressourcen mit eigenen Endpoints und Schemas:

- `GET /api/planning/rentcategories` → `RentCategoryResource` (id, groupid, name, description, **brand**, allowOtherBrands, **branches**)
- `GET /api/planning/fleetcategory` (+ `/{id}`) → `FleetCategoryResource` (id, groupid, name, description, image, **tags**, order, type, fixSpendTime, minSpendTime, maxSpendTime)

**Unser Code-Pfad:**

1. `RentCategoriesService.getRentCategories` → `SoftnetApiClient.getRentCategories` → `GET /api/planning/rentcategories` — wir halten also **rentCategory**-Objekte (belegt dadurch, dass unser DTO `brand` und `branches` mappt; diese Felder existieren nur im `RentCategoryResource`).
2. `AppointmentsService.patchSnapWithBookingData` schreibt diese ID nach `wishlist.fleetCategoryid`.

→ Eine rentCategory-ID landet in einem fleetCategory-Feld.

### P2 — Fehler bleibt unentdeckt

SoftNet validiert die Referenz nicht und antwortet mit `200 OK`. Der Fehler ist daher nur im nachfolgenden System sichtbar („keine Kennung davon").

**Belegfall:** Appointment `6a9aca6664eb1bc89cf0fb2b`, gebucht 04.09.2026 13:40:56 UTC, Gottfried Schultz Velbert, Mobilität „Kleinwagen" (rentCategory-ID `682ad3dc6969db66dd27f6c3`, Group `682ad3bd6969db66dd27ee56`) → SoftNet 200 OK, Kategorie im Zielsystem unbekannt.

### P3 — Erfolgspfad loggt die Mobilitäts-Felder nicht

`BOOKING_CREATED` (`AppointmentsService`, Erfolgspfad) loggt nur `wishlist.client` und `wishlist.car`. Die Geschwister-Felder `isFleet`, `fleetType`, `fleetCategoryid`/`rentCategoryid` fehlen. Folge: Bei erfolgreichen Buchungen ist nicht nachweisbar, ob und welche Mobilitäts-Option übertragen wurde — die Analyse dieses Falls musste über Redaktions-Längen und einen fremden Fehlerfall-Log geführt werden. Gleiche Lücke wie zuvor beim Kennzeichen (REQ-034-Analyse).

## User Stories

- **US-1:** Als Kunde möchte ich, dass meine gewählte Mietwagen-Kategorie im Werkstatt-System korrekt ankommt, damit bei meinem Termin tatsächlich ein Fahrzeug dieser Klasse bereitsteht.
- **US-2:** Als Service-Mitarbeiter möchte ich die gewünschte Mietwagen-Klasse im nachfolgenden System sehen, ohne beim Kunden nachfragen zu müssen.
- **US-3:** Als Entwickler/Betreiber möchte ich aus den Logs einer erfolgreichen Buchung erkennen können, ob und welche Mobilitäts-Option übertragen wurde, ohne auf Fehlerfälle oder Indizien angewiesen zu sein.

## Akzeptanzkriterien

- **AC-1:** Bei gesetzter `rentCategoryId` wird die ID in `wishlist.rentCategoryid` übertragen (nicht mehr in `fleetCategoryid`).
- **AC-2:** `wishlist.isFleet = true` und `wishlist.fleetType = "rentObject"` werden weiterhin gesetzt (laut Spec Pflicht für Miet-Termine; `fleetType`-Wert bleibt aus dem gültigen Enum).
- **AC-3:** `wishlist.fleetCategoryid` wird **nicht mehr gesendet** (gewählte Variante 1 — siehe „Entscheidung"). Wir besitzen keine gültige fleetCategory-ID, und eine falsche ID ist schlechter als keine.
- **AC-4:** Ohne gewählte Mobilitäts-Option bleibt der Request unverändert — keines der vier Felder wird gesetzt (Spec: „If empty or missing, means any rent category").
- **AC-5:** Die ID wird vor dem Senden auf **exakt 24 Zeichen** geprüft (Spec: `minLength`/`maxLength` = 24). Verstößt sie dagegen, wird sie nicht gesendet und eine WARN-Zeile mit Trace-ID geloggt — statt SoftNet einen ungültigen Wert zu schicken.
- **AC-6:** `BOOKING_CREATED` loggt zusätzlich die nicht-personenbezogenen Mobilitäts-Felder (`isFleet`, `fleetType`, `rentCategoryid`). Reine IDs und Booleans, keine PII — die Datensparsamkeit aus REQ-034 bleibt gewahrt.
- **AC-7:** Eine Buchung mit Mietwagen-Option ist nach dem Fix im Log eindeutig als solche erkennbar und die übertragene Kategorie-ID ist ablesbar.

## Entscheidung: Variante 1 — `fleetCategoryid` weglassen

> **`isFleet` + `fleetType` + `rentCategoryid` senden, `fleetCategoryid` weglassen.**

Ursprünglich war beabsichtigt, `fleetCategoryid` einfach mitzusenden („steht in der Spec als mandatory"). Das ist **mit unserem Zugang technisch nicht möglich** — Nachweis aus der OpenAPI-Spec v1.4:

| Endpoint | erforderlicher OAuth-Scope | für uns nutzbar? |
|---|---|---|
| `GET /api/planning/rentcategories` | `scope.api.planning.service` | **ja** — das ist unser Scope (`SoftnetAuthService:185`), Abruf funktioniert produktiv |
| `GET /api/planning/fleetcategory` (+ `/{id}`) | **`scope.api.planning.rent.service`** | **nein** — anderer Scope, den unser Token nicht führt |

Wir können fleetCategory-IDs also **gar nicht beschaffen**. Eine „mitgesendete" fleetCategory wäre zwangsläufig wieder ein geratener oder fremder Wert — genau der Fehler, der zu diesem REQ geführt hat.

**Inhaltlich passt der Endpoint auch nicht zu unserem Use-Case:** `fleetcategory` ist laut Spec-Beschreibung *„Available fleet categories for the current group and their conditions for reservation of appointments"* mit den Query-Parametern `startDate`, `endDate`, `spendTime`, `isFixed`, `type` — also Fuhrpark-Objekte mit Reservierungs-Zeitfenstern (SoftNets Vermietungs-Modul). Unser Fall ist ein Werkstatttermin mit Ersatzwagen-Wunsch; die passende Ressource dafür ist `rentcategories` — dort steht auch die Preisinformation, die der Kunde in der UI sieht („Kleinwagen ab 45,00 € brutto inkl. 150 km"). Das erklärt zudem, warum ausgerechnet `rentCategoryid` das weiche, optionale Feld ist („If empty or missing, means any rent category"), während die drei `fleet*`-Felder für echte Miet-Termine gelten.

Verworfene Alternativen:
- *Beide Felder senden:* Nicht umsetzbar — siehe Scope-Tabelle. Setzt Scope-Erweiterung durch den Dienstleister voraus (dann Folge-REQ).
- *`rentCategoryid` ergänzen, `fleetCategoryid` drin lassen:* Wir würden weiterhin eine nachweislich falsche ID mitsenden.

**Restrisiko:** Falls SoftNet `fleetCategoryid` für unsere Buchungen doch hart erzwingt, schlägt die Buchung mit einem Validierungsfehler fehl statt still die Kategorie zu verlieren. Das ist in Step 6.5 sofort sichtbar — ein lauter Fehler ist dem stillen Datenverlust vorzuziehen. Rückfallebene ist dann nicht Code, sondern die Scope-Erweiterung (siehe Rückfrage 2).

## Rückfrage an den Dienstleister (parallel, blockiert diese Umsetzung nicht)

1. Ist `wishlist.rentCategoryid` das korrekte Feld für eine Kategorie aus `GET /api/planning/rentcategories`?
2. `fleetCategoryid` ist als „mandatory for rent appointments" dokumentiert, die zugehörige Collection (`GET /api/planning/fleetcategory`) liegt aber hinter dem Scope `scope.api.planning.rent.service`, den unser Token nicht führt. **Gilt die Pflicht auch für Werkstatt-Termine mit Ersatzwagen-Wunsch — oder nur für echte Miet-Termine?** Falls wir das Feld liefern müssen: Bitte den Scope `scope.api.planning.rent.service` für unseren Client freischalten.
3. Bitte den gespeicherten Roh-Request zu Appointment `6a9aca6664eb1bc89cf0fb2b` (04.09.2026, 13:40:53–13:40:56 UTC) bereitstellen, zur Bestätigung des Ist-Zustands.

## Out of Scope

- Änderungen an der UI der Mobilitäts-Auswahl (Kategorien, Texte, Preise) — nur das Übertragungsfeld und das Logging ändern sich.
- Anbindung von `GET /api/planning/fleetcategory` — mit unserem Scope nicht abrufbar; erst nach Freischaltung von `scope.api.planning.rent.service` als Folge-REQ möglich.
- Nachträgliche Korrektur bereits gebuchter Termine mit falscher Feld-Zuordnung — fachliche Entscheidung, nicht Teil dieser Umsetzung.
- Die weiteren Log-Lücken im Erfolgspfad (`operations`, `checkInTime`, `note`) — nur die Mobilitäts-Felder aus AC-6.

## Betroffene Dateien (Erwartung)

| Datei | Änderung |
|---|---|
| `server/src/main/java/com/autohaus/backend/appointments/AppointmentsService.java` | `fleetCategoryid` → `rentCategoryid`, 24-Zeichen-Guard, `BOOKING_CREATED` um Mobilitäts-Felder erweitern |
| `server/src/test/java/com/autohaus/backend/appointments/AppointmentsServiceTest.java` | `createAppointment_withRentCategory_setsFleetFields` anpassen + Tests für Guard und Log-Inhalt |

## Verweise

- SoftNet OpenAPI v1.4 — `soft-netOpenAPI.html`, Schema `WishlistResource`; Spec-JSON extrahierbar aus dem eingebetteten `__redoc_state`-Objekt
- REQ-034 — Datensparsames Logging (Redaction-Format `***(Länge)`, `LogSanitizer`)
- Debug-Paket zum Belegfall: `debug-mobilitaet-6a9aca66.md` / `.json`
