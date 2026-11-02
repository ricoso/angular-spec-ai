# REQ-040: FIN zwingend bei Räderwechsel mit eingelagerten Reifen

**Status:** Umgesetzt (Frontend-only, Nutzerentscheid 2026-10-13) — siehe O-3
**Priority:** High
**Type:** Functional (Formularlogik, bedingtes Pflichtfeld)
**Created:** 2026-10-13
**Scope:** Kontakt-/Fahrzeugformular der Buchungsstrecke; betrifft das FIN-Feld in beiden Einstiegen (normale OTV und Reifenwechsel-/Boxenstopp-Shortcut)
**Supersedes (teilweise):** [REQ-039](../REQ-039-Responsive-Wizard/requirement.md) BR-9 / Abschnitt 18 „freiwillige FIN" — die FIN ist nicht mehr generell freiwillig, sondern **bedingt zwingend** (siehe BR-1).

---

## 1. Overview

### 1.1 Purpose

Wenn ein Kunde den Service **„Räderwechsel – meine Räder sind eingelagert"** bucht, muss das Autohaus das Fahrzeug eindeutig identifizieren können, um die **eingelagerten Räder** der richtigen Buchung/dem richtigen Fahrzeug zuzuordnen. Dafür wird die **FIN** (Fahrzeug-Identifizierungsnummer) benötigt. Diese Zuordnung ist nur mit der FIN zuverlässig möglich; das Kennzeichen allein reicht nicht (Kennzeichenwechsel, Saisonkennzeichen, Halterwechsel).

REQ-039 hatte die FIN generell als **freiwilliges** Feld eingeführt (immer sichtbar, optional). REQ-040 macht sie **bedingt zwingend**: Nur wenn der Service mit eingelagerten Reifen gewählt ist, wird die FIN abgefragt — und dann ist sie **Pflicht**.

### 1.2 Problem

- Bei „meine Räder sind eingelagert" fehlt dem Autohaus ohne FIN die eindeutige Fahrzeugzuordnung zu den eingelagerten Rädern.
- Die FIN wird aktuell (REQ-039) immer angezeigt, aber nie erzwungen — bei eingelagerten Reifen kann die Buchung also ohne FIN abgeschickt werden (`UNKNOWN`), was die Zuordnung verhindert.

### 1.3 Scope

**Included:**
- Die FIN wird **nur** abgefragt, wenn die aktuelle Service-Auswahl die Operation **„Räderwechsel – meine Räder sind eingelagert"** (`stored-tires`) enthält.
- Ist dieser Service gewählt, ist die FIN ein **Pflichtfeld** (genau 17 alphanumerische Zeichen; Submit ohne gültige FIN nicht möglich).
- Gilt in **beiden** Einstiegen: normale OTV (`variant: default`) **und** Reifenwechsel-/Boxenstopp-Shortcut (`variant: tireChange`, Deep-Link).
- Ist der Service **nicht** gewählt, wird die FIN **nicht** angezeigt und **nicht** abgefragt.

**Excluded:**
- Keine Änderung an anderen Fahrzeug-/Kundenfeldern, an der Terminlogik, der Mietwagen-Logik oder am Backend-FIN-Mapping (`car.VIN`, `normalizeVin`) außer der Pflicht-/Sichtbarkeitssteuerung.
- Keine neue SoftNet-Operation; die Erkennung nutzt die bestehende Operation (`stored-tires`).
- Keine Änderung am FIN-Format (bleibt 17 alphanumerisch) oder an der Normalisierung (trim + Großbuchstaben, front und back).

---

## 2. User Story & Acceptance Criteria

> **Als** Autohaus **möchte ich**, dass die FIN bei „Räderwechsel – meine Räder sind eingelagert" verpflichtend eingegeben wird, **damit** ich die eingelagerten Räder eindeutig dem Fahrzeug zuordnen kann.

- [ ] **AC-1:** Enthält die Service-Auswahl die Operation „Räderwechsel – meine Räder sind eingelagert" (`stored-tires`), wird im Fahrzeugformular das FIN-Feld angezeigt — mit Pflicht-Kennzeichnung (Stern) und ohne „freiwillig"-Hinweis.
- [ ] **AC-2:** Ist dieser Service gewählt, blockiert eine leere oder ungültige FIN den Submit; es erscheint die passende Fehlermeldung (leer: „FIN ist erforderlich"; Format/Länge wie REQ-039).
- [ ] **AC-3:** Ist dieser Service **nicht** gewählt, wird das FIN-Feld **nicht** angezeigt, nicht validiert und nicht mit der Buchung gesendet (Backend erhält wie bisher `UNKNOWN`).
- [ ] **AC-4:** AC-1 bis AC-3 gelten identisch in der normalen OTV (`variant: default`) **und** im Reifenwechsel-/Boxenstopp-Shortcut (`variant: tireChange`).
- [ ] **AC-5:** Wird der Service nach Eingabe einer FIN wieder abgewählt, wird das Feld ausgeblendet und der (jetzt irrelevante) FIN-Wert nicht gesendet; bei erneuter Auswahl ist die FIN wieder Pflicht und leer bzw. mit dem zuvor eingegebenen Wert vorbelegt (Implementierungsentscheid, siehe O-1).
- [ ] **AC-6:** Eine gültige, eingegebene FIN wird getrimmt, in Großbuchstaben normalisiert, mit der Buchung übermittelt und in der Übersicht angezeigt (unverändert ggü. REQ-039).
- [ ] **AC-7:** Die Pflicht-/Sichtbarkeitslogik hängt **allein** an der Service-Auswahl, nicht an Marke, Standort oder Mietwagen-Option.

---

## 3. Business Rules

- **BR-1 (bedingte Pflicht):** Die FIN ist **genau dann** ein sichtbares Pflichtfeld, wenn die aktuelle Service-Auswahl die Operation `stored-tires` („…meine Räder sind eingelagert") enthält. Andernfalls ist sie ausgeblendet und wird nicht abgefragt.
- **BR-2 (flussunabhängig):** BR-1 gilt unabhängig von der Variante — normale OTV (`default`) und Reifenwechsel-Shortcut (`tireChange`) verhalten sich gleich. Maßgeblich ist nur, **ob** der Service gewählt ist.
- **BR-3 (Format unverändert):** Eine eingegebene FIN muss genau 17 alphanumerische Zeichen haben (bestehender `vinValidator`-Kern); zusätzlich gilt bei `stored-tires` „required" (nicht leer).
- **BR-4 (Erkennung):** `stored-tires` wird über den **Namen der gewählten Operation** erkannt (im Live-Flow trägt `selectedServices()[].serviceId` die SoftNet-Operation-ID, nicht das `ServiceType`-Enum; `selectedOptionIds` ist leer). Der Name wird gegen die zwei kanonischen Substrings des Backend-`OperationIdMapper` geprüft: **`räder sind eingelagert`** (Default „Räderwechsel – meine Räder sind eingelagert") und **`radwechsel mit einlagerung`** (Boxenstopp). Ein naives `includes('eingelagert')` wäre falsch — es träfe den **anderen** Service `store-tires` („…angelieferten Räder sollen bitte eingelagert werden") mit. Die beiden Substrings decken beide Varianten ab und schließen `store-tires` aus. Umgesetzt als zentrale Modul-Konstante `STORED_TIRES_NAME_PATTERNS` im `BookingStore` (keine verstreuten String-Vergleiche).
- **BR-5 (keine stillen Daten):** Ist der Service nicht gewählt, wird **keine** FIN gesendet — auch wenn zuvor eine eingegeben und der Service dann abgewählt wurde (BR-5 verhindert „versteckte" Übermittlung).
- **BR-6 (Backend unverändert):** Das Backend-Verhalten (`car.VIN` = `normalizeVin(vin)`, leer → `UNKNOWN`) bleibt. REQ-040 ändert nur, **wann** das Frontend eine FIN sendet/erzwingt. Optional (O-3): serverseitige Pflichtprüfung, wenn eine `stored-tires`-Operation gebucht wird.

---

## 4. Main Flow

1. Kunde wählt Services. Enthält die Auswahl „Räderwechsel – meine Räder sind eingelagert", merkt sich der Store dies (`hasStoredTiresService`).
2. Im Kontakt-/Fahrzeugschritt zeigt das Fahrzeugformular das FIN-Feld **nur**, wenn `hasStoredTiresService` true ist — dann als Pflichtfeld mit Stern.
3. Beim Submit prüft das Formular die FIN (nicht leer + 17 alphanumerisch). Fehler blockiert die Navigation und zeigt die Meldung.
4. Mit gültiger FIN wird die Buchung inkl. normalisierter FIN übermittelt.

### 4.1 Alternative Flows
- **4a — Service nicht gewählt:** FIN-Feld nicht sichtbar, kein Validator, keine Übermittlung (AC-3).
- **4b — Service nach FIN-Eingabe abgewählt:** Feld verschwindet, FIN wird nicht gesendet (BR-5).
- **4c — Reifenwechsel-Shortcut (tireChange):** identisch zu Main Flow (BR-2), nur anderer Einstieg/Group.

---

## 5. Data Model

| Stelle | Änderung |
|---|---|
| `BookingStore` | Neuer Computed `hasStoredTiresService: boolean` — true, wenn eine gewählte Operation als `stored-tires` erkannt wird (BR-4). Join `selectedServices` ↔ `operations` über die Operation-ID, dann Namensabgleich gegen `STORED_TIRES_NAME_PATTERNS` (gleiches Muster wie `selectedServiceLabels`). |
| `VehicleFormComponent` | FIN-Feld bedingt rendern (`@if (storedTiresSelected())`); Validatoren dynamisch: `required` + bestehende 17-Zeichen/Alphanumerik-Prüfung, wenn sichtbar; sonst kein Validator. Input `storedTiresSelected` vom Container (aus dem Store). |
| `CarinformationContainer` | Reicht `store.hasStoredTiresService` an das Fahrzeugformular; beim Submit nur dann FIN erzwingen/senden. |
| Validator | `optionalVinValidator` bleibt für den Format-/Längen-Kern; für den Pflichtfall kommt `Validators.required` hinzu (oder ein `requiredVinValidator`, der leer als Fehler wertet). |

Keine neue Route, keine neue Persistenz, keine SoftNet-Vertragsänderung.

---

## 6. UI/UX

- Das FIN-Feld erscheint im Fahrzeugformular an gleicher Stelle wie bisher, aber **nur** bei gewähltem `stored-tires`-Service.
- Pflicht-Darstellung wie die übrigen Pflichtfelder: Label mit Stern (`*`), `aria-required="true"`, Fehler als `role="alert"` mit `aria-invalid`.
- Der „freiwillig"-Hilfetext (`vin.optionalHint`) entfällt im Pflichtfall; stattdessen ein kurzer Zweck-Hinweis (z. B. „Zur eindeutigen Zuordnung Ihrer eingelagerten Räder."), siehe i18n.
- Fehlertexte: leer → neuer Key `vin.error.required`; Format/Länge → bestehende Keys (`vin.error.invalidFormat`, `vin.error.length`).

---

## 7. i18n Keys (DE/EN, zusätzlich UK/FR/AR)

| Key | DE | EN |
|---|---|---|
| `booking.carinformation.form.vin.requiredHint` (**neu**) | Zur eindeutigen Zuordnung Ihrer eingelagerten Räder erforderlich. | Required so we can match your stored wheels to your vehicle. |
| `booking.carinformation.form.vin.error.required` (**existiert bereits**, aus REQ-039 vorprovisioniert) | Bitte geben Sie die FIN ein. | Please enter the VIN. |

Nur `vin.requiredHint` ist neu (in allen 5 Sprachen DE/EN/UK/FR/AR ergänzt). `vin.error.required` war bereits in REQ-039 angelegt und wird jetzt nur verdrahtet. Bestehende Keys bleiben: `vin.label`, `vin.placeholder`, `vin.error.invalidFormat`, `vin.error.invalidLength`. `vin.optionalHint` wird nur noch verwendet, falls das Feld jemals wieder optional auftaucht — sonst entfernbar (siehe O-4).

---

## 8. Test Cases

- **TC-1 (AC-1/AC-2):** Service `stored-tires` gewählt → FIN-Feld sichtbar, mit Stern; leerer Submit blockiert + „required"-Fehler; ungültige FIN (16 Zeichen) blockiert; gültige 17-Zeichen-FIN erlaubt Weiter.
- **TC-2 (AC-3):** Service nicht gewählt → FIN-Feld nicht im DOM, Submit ohne FIN möglich, Buchungs-Payload ohne echte FIN (`UNKNOWN`).
- **TC-3 (AC-4):** TC-1 identisch im `tireChange`-Deep-Link.
- **TC-4 (AC-5/BR-5):** FIN eingeben → Service abwählen → Feld weg, FIN nicht gesendet; Service erneut wählen → wieder Pflicht.
- **TC-5 (AC-6):** Gültige FIN wird getrimmt/großgeschrieben, in der Übersicht und im Payload korrekt.
- **TC-6 (AC-7):** Sichtbarkeit/Pflicht ändern sich nicht bei Wechsel von Marke/Standort/Mietwagen, solange die Service-Auswahl gleich bleibt.
- **TC-7 (E2E, 3 Viewports):** Happy-Path Räderwechsel-eingelagert inkl. Pflicht-FIN bis Übersicht.

---

## 9. Implementation (Reihenfolge)

1. `BookingStore.hasStoredTiresService` (Computed) + zentrale Erkennungs-Konstante/Util für `stored-tires` (BR-4).
2. `VehicleFormComponent`: bedingtes Rendern + dynamische Validatoren (required ∪ Format) über einen `storedTiresSelected`-Input; Fehler-Computeds erweitern.
3. `CarinformationContainer`: Input binden; Submit-Logik nur bei Bedarf FIN erzwingen/senden.
4. i18n (2 neue Keys in 5 Sprachen), feature-doc.
5. Tests (Store, VehicleForm, Container, E2E) + Gates (Step 6/6.5/7).

---

## 10. Dependencies

**Requires:**
- REQ-009 (Fahrzeugformular), REQ-004/REQ-011/REQ-025 (Serviceauswahl, Operations), REQ-035/REQ-036 (Reifenwechsel-/tireChange-Group, `OperationIdMapper`), REQ-039 (FIN-Feld, Validator, Normalisierung).

**Überholt:**
- REQ-039 „freiwillige FIN" (BR-9 / 18): Die FIN ist nicht mehr generell freiwillig, sondern bedingt zwingend. REQ-039 selbst wird **nicht** editiert (Historie); dieser Verweis dokumentiert die Ablösung.

---

## 11. Offene Punkte

- **O-1 (AC-5 Vorbelegung):** Bei Abwählen/Wiederwählen des Services — FIN-Wert verwerfen oder vorhalten? Vorschlag: im Store/Control vorhalten (bessere UX), aber nur senden, wenn sichtbar (BR-5). PO-Entscheid.
- **O-2 (Erkennung) — ENTSCHIEDEN (2026-10-13):** Namens-Match über die zwei kanonischen Substrings (`räder sind eingelagert`, `radwechsel mit einlagerung`), konsistent zum Backend-`OperationIdMapper`. Robuster wäre ein kanonisches Operation-Flag im Operations-/JobType-DTO — als **Folge-Ticket** vorgemerkt, nicht Teil von REQ-040.
- **O-3 (Server-Pflicht) — ENTSCHIEDEN (2026-10-13):** **Frontend-only.** Keine serverseitige Pflichtprüfung; das Backend bleibt unverändert (leere FIN → `UNKNOWN`). Eine serverseitige Erzwingung (400 statt stiller `UNKNOWN`) ist ein mögliches Folge-Ticket (Defense-in-Depth).
- **O-4 (Cleanup):** `vin.optionalHint` entfernen, falls die FIN nirgends mehr optional auftaucht.
- **O-5 (mehrere Räder-Operationen):** Verhalten klären, falls künftig weitere „eingelagert"-Varianten existieren — gilt die Pflicht für alle Operationen mit Einlagerungsbezug?
