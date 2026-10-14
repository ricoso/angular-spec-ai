# REQ-042: Bugfixing & UI-Improvements (Standortauswahl + Buchungsstrecke)

**Status:** Implementiert (lokal verifiziert 2026-10-14), Deploy auf PROD in Vorbereitung
**Priority:** High (enthält einen Buchungs-Blocker in Produktion)
**Type:** Bugfixing + UI-Improvement
**Created:** 2026-10-14
**Author:** Agent, im Auftrag des Nutzers (rico.sonntag@lhind.dlh.de)
**Baut auf:** REQ-041 (Rückrufaktion) — Revision 0000047 war der Ausgangsstand

> **Zweck dieses Requirements:** Nachvollziehbar festhalten, welche Fehler nach dem REQ-041-Deploy
> gefunden und behoben wurden und welche UI-Verbesserungen an der Standortauswahl entstanden sind.
> Es bündelt mehrere kleine, zusammenhängende Änderungen an **einer** Seite (Standortauswahl) plus
> **einen** produktionskritischen Buchungs-Bugfix. Kein Mockup — es sind Fehlerkorrekturen und
> inkrementelle UI-Anpassungen an bestehenden Komponenten.

---

## 1. Overview

Nach dem Live-Gang der Rückrufaktion (REQ-041) sind beim Nachtesten mehrere Punkte aufgefallen:
ein echter Buchungs-Blocker für Nutzfahrzeuge, mehrere Verbesserungswünsche an der Standortauswahl
und zusätzliche Fehler, die ein tiefes Review der REQ-041-Änderungen zutage gefördert hat. Alle
Punkte sind in diesem Requirement dokumentiert und umgesetzt.

**Deployment-Hinweis:** Bis auf den Mietwagen-/Rückruf-Fix (Backend, Teil von REQ-041) sind alle
Änderungen **reines Frontend**. Es sind **keine** neuen Azure-Secrets oder setup.json-Änderungen
nötig — die wurden bereits mit REQ-041 (Revision 0000047) gesetzt.

---

## 2. Bugfixes

### BUG-1 — Nutzfahrzeuge: keine Termine ohne Mietwagen (Produktions-Blocker)

**Symptom (Nutzer-Meldung):** „Wenn ich aktuell keinen Mietwagen anwähle bei Nutzfahrzeugen,
bekomme ich keinen Termin in Essen."

**Ursache:** Das Frontend sendet den Marken-**Slug** statt des echten Namens an SoftNet. In
`booking-api.service.ts` wird die Marken-`id` als Slug erzeugt
(`name.toLowerCase().replace(/\s+/g, '-')`), also `"Volkswagen Nutzfahrzeuge"` →
`"volkswagen-nutzfahrzeuge"`. Dieser Slug ging als `brandName` in die Snaps-Anfrage. SoftNet kennt
nur `"Volkswagen Nutzfahrzeuge"` (mit Leerzeichen) und antwortet mit **HTTP 400 → 0 Termine**.

**Warum nur ohne Mietwagen:** Mit Mietwagen ersetzt das Backend (`SnapsService.resolveRentalBrand`)
die Marke durch einen gültigen Namen und überschreibt so den kaputten Slug. Ohne Mietwagen geht der
Slug ungefiltert an SoftNet. Einwort-Marken (`volkswagen`) brechen nicht, weil SoftNet Groß-/
Kleinschreibung toleriert — nur das Leerzeichen→Bindestrich kippt. Betroffen sind alle Mehrwort-
Marken; aktuell genau **„Volkswagen Nutzfahrzeuge"**, an allen Standorten.

**Fix:** `booking.store.ts` (`loadAppointments` + `loadWorkshopCalendar`) löst den Slug über die
geladene Markenliste (`store.brands()`) zum echten Anzeige-Namen auf, bevor er an SoftNet geht.
Die Buchung selbst (`createAppointment`) ist nicht betroffen — sie sendet nur die `snapId`.

**Beweis (PROD + lokal gegen SoftNet):**
| gesendeter `brandName` | Ergebnis |
|---|---|
| `volkswagen-nutzfahrzeuge` (Slug) | HTTP 400, 0 Termine |
| `Volkswagen Nutzfahrzeuge` (echter Name) | HTTP 200, 15–18 Tage Termine |

### BUG-2 — Rückruf-Modus: „Zurück" auf Hinweise öffnet den ausgeblendeten Service-Schritt

**Ursache:** `notes-container.onBack()` navigierte immer zu `/home/services`. Im Rückruf-Modus wird
dort der Default-Katalog geladen und überschreibt die automatisch gesetzte Rückruf-Operation → die
Buchung ginge mit `variant=recall` (Rückruf-Abrechnung), aber einer Default-Operation raus
(SoftNet-Fehler oder Fehlabrechnung auf dem Rückruf-Client).

**Fix:** `onBack()` navigiert im Rückruf-Modus zu `/home/brand`. Zusätzlich sperrt ein neuer
`recallBlocksServicesGuard` die Service-Route im Rückruf-Modus (gegen Direktaufruf/History).

### BUG-3 — Pflicht-FIN akzeptierte reine Leerzeichen

**Ursache:** Bei Pflicht-FIN (eingelagerte Räder REQ-040 oder Rückruf REQ-041) nutzte das Feld
`Validators.required` + den optionalen Validator. Eine FIN aus nur Leerzeichen (`"   "`) bestand
beide (required sieht einen nicht-leeren String, der optionale Validator trimmt und wertet leer als
„nicht angegeben") → die Buchung ging ohne FIN (`UNKNOWN`) raus.

**Fix:** Neuer `requiredVinValidator()` — leere/whitespace-only Eingabe ergibt `{ required: true }`,
ein gegebener Wert muss ein gültiger 17-stelliger alphanumerischer FIN sein.

### BUG-4 — Rückruf-Modus: Mietwagen-Angebot aus der falschen Group

**Ursache:** `RentCategoriesController` reichte nur `variant=tireChange` durch, alles andere wurde
`default`. Im Rückruf-Modus kamen die Mietwagen-Kategorien damit aus der Default-Group, während
Snaps und Buchung die Rückruf-Group nutzen.

**Fix:** `variant=recall` wird jetzt durchgereicht. Live-Befund INT: die Rückruf-Group hat an allen
27 Standorten **0** Mietwagen-Kategorien → der Hinweise-Schritt zeigt korrekt „kein Mietwagen
verfügbar" statt eines Schalters, der ins Leere führt.

---

## 3. UI-Improvements (Standortauswahl)

### UI-1 — Standort-Namen sichtbar

Die Karten zeigten bisher nur die Adresse; der Name steckte nur im `aria-label`. Jetzt steht der
**Standort-Name** als Überschrift auf jeder Karte, die Adresse als Untertitel darunter.

### UI-2 — Städtesuche

Suchfeld „Standort nach Stadt suchen" über der Liste, filtert live (case-insensitiv) nach Stadt
**oder** Standortname. Ohne Treffer: Hinweis „Kein Standort gefunden." Barrierefrei (Label,
`aria-label`), in allen 5 UI-Sprachen (DE/EN/UK/FR/AR). Funktioniert auf **beiden** Seiten: der
globalen Standortauswahl **und** der Rückruf-Deep-Link-Seite.

### UI-3 — Leverkusen ausgeblendet

Die beiden Leverkusener Standorte werden in der Standortauswahl nicht mehr angezeigt
(`HIDDEN_CITIES`, Frontend-Filter). Hintergrund: Leverkusen bucht laut GS-Website über einen anderen
Anbieter. Hart entfernt (Backend/setup.json) wurde bewusst **nicht** — ein Deep-Link funktioniert
technisch weiter. 25 statt 27 Standorte sichtbar.

### UI-4 — Rückrufaktion als Karte (blau) auf der Test-Seite

Auf der globalen Standortauswahl bekommt jeder Standort — analog zur gelben
„Radwechsel+ (Boxenstopp)"-Karte — eine zusätzliche **blaue „Rückrufaktion"-Karte**. So ist pro
Standort auf einen Blick sichtbar, welche Applikationen/Varianten es gibt (normal · Boxenstopp ·
Rückrufaktion). Ein Klick startet denselben Rückruf-Flow wie der Deep-Link (FIN wird dann im
Kundendaten-Schritt eingegeben). Die **Kunden-Rückrufseite** (Deep-Link `?recall=true`) zeigt
**keine** Zusatzkarten — nur echte Standorte (unverändert aus REQ-041).

> **Offener Punkt:** Die Rückruf-Karte ist (wie der Boxenstopp) für jeden sichtbar. Falls sie nur
> intern/zum Testen gewünscht ist, kann sie hinter einen Schalter (z. B. URL-Parameter) gelegt
> werden. Entscheidung des Nutzers steht aus.

### UI-5 — Hinweis „Serviceauswahl entfällt" entfernt (aus REQ-041)

Der Zusatzhinweis im Rückruf-Banner wurde auf Wunsch entfernt; der Banner zeigt nur noch Badge,
Beschreibung und erkannte FIN. i18n-Key `booking.recall.serviceAutoSelected` in allen 5 Sprachen
gelöscht, Mockup aktualisiert.

---

## 4. Betroffene Dateien

**Frontend:**
- `src/app/features/booking/stores/booking.store.ts` — BUG-1 (brandName-Auflösung), RECALL_GROUP_ID ausgelagert
- `src/app/features/booking/models/recall.model.ts` — **neu**, geteilte `RECALL_GROUP_ID`
- `src/app/features/booking/validators/optional-vin.validator.ts` — BUG-3 (`requiredVinValidator`)
- `src/app/features/booking/components/carinformation/carinformation-container.component.ts` — BUG-3 (Validator-Tausch)
- `src/app/features/booking/components/notes/notes-container.component.ts` — BUG-2 (`onBack`)
- `src/app/features/booking/guards/recall.guard.ts` — BUG-2 (`recallBlocksServicesGuard`)
- `src/app/features/booking/booking.routes.ts` — BUG-2 (Guard auf services-Route)
- `src/app/features/booking/components/location-selection/location-selection-container.component.*` — UI-2, UI-3, UI-5
- `src/app/features/booking/components/location-selection/location-cards.component.*` — UI-1, UI-4
- `src/app/core/i18n/translations.ts` — UI-2 (Such-Keys), UI-5 (Key entfernt)

**Backend:**
- `server/.../rentcategories/RentCategoriesController.java` — BUG-4 (`variant=recall` durchreichen)

---

## 5. Tests & Verifikation

- **Jest:** 841/841 grün (neue Tests: brandName-Auflösung, Leverkusen-Filter, Städtesuche,
  Rückruf-Karte, `requiredVinValidator`, `recallBlocksServicesGuard`, Notes-Zurück im Rückruf-Modus).
- **Backend (Gradle):** 1381 Tests grün (neue RentCategories-Tests für `recall`).
- **Type-Check:** 0 Fehler. **ESLint:** 0 Fehler.
- **E2E (Playwright, 3 Viewports):** Regression grün; REQ-041-Suite inkl. Zurück-Schutz und
  Mietwagen-Hinweis grün.
- **Live gegen SoftNet (PROD + INT):** BUG-1 und BUG-4 reproduziert und nach dem Fix bestätigt.

---

## 6. Status / Wo stehen wir

- Alle Punkte **implementiert und lokal verifiziert**.
- **Ausgangsstand:** REQ-041, Azure Revision 0000047 (Rückrufaktion live, Recall-Fingerprint +
  setup.json mit recall-Variante gesetzt).
- **Deploy:** Image-Tag `req041plus-<Zeitstempel>`, Deploy auf PROD in Vorbereitung.
- **Offen:** Entscheidung zur Sichtbarkeit der Rückruf-Karte (UI-4); Übergabe der Rückruf-URL an GS
  (siehe `gs-rueckrufaktion-integration.md`); Umstellung der Kartennamen auf die offiziellen
  GS-Website-Namen (optional, Nutzer-Entscheidung offen).
