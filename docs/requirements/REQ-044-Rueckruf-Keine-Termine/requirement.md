# REQ-044: Rückrufaktion — Hinweis „Lieferengpass“ statt „Keine Termine verfügbar.“

**Status:** Implementiert und lokal getestet, noch nicht deployt
**Priority:** Medium (Kundenkommunikation während der laufenden Rückrufaktion)
**Type:** Textänderung (Frontend, i18n)
**Created:** 2026-10-30
**Author:** Agent, im Auftrag des Nutzers (rico.sonntag@lhind.dlh.de)
**Erweitert:** REQ-041 (Rückrufaktion). REQ-041 bleibt unverändert.

> **Zweck dieses Requirements:** Findet ein Kunde in der Rückrufaktion keinen Termin, soll er
> erfahren, warum: Die Schraube ist nicht lieferbar. Bisher sah er den allgemeinen Hinweis „Keine
> Termine verfügbar.“ mit der Aufforderung, Standort oder Service zu wechseln. Den Service kann er
> im Rückruf-Modus aber gar nicht wählen. Kein Mockup: Es ändern sich nur zwei Textzeilen in einer
> bestehenden Ansicht.

---

## 1. Overview

| Eintrag | Inhalt |
|---|---|
| Wo | Terminauswahl (`/home/appointment`), Leerzustand der Datumsleiste |
| Wann | Nur im Rückruf-Modus (`isRecallMode()`), wenn die Terminsuche keinen Slot liefert |
| Was | Zwei neue Textzeilen statt der allgemeinen: Titel (fett) und Hinweis |
| Was bleibt | Der Text im Normalmodus, die Schaltfläche „Neu laden“, der Link „Weitere freie Termine“ |
| Backend | Keine Änderung |

**Deployment-Hinweis:** Nur das Frontend ändert sich. Es gibt **keine** neuen Secrets,
Umgebungsvariablen oder setup.json-Änderungen.

---

## 2. Änderung

### 2.1 Wortlaut

Der deutsche Text stammt vom Nutzer (diktiert; „Schaube“ ist „Schraube“, so heißt die Operation
der Rückrufaktion: „Rückrufaktion Lenkungsgetriebe Schraube“). Die anderen Sprachen sind
Übersetzungen des Agenten.

| Sprache | Titel (`booking.recall.noAppointments`) | Hinweis (`booking.recall.noAppointmentsHint`) |
|---|---|---|
| DE | Aufgrund von Lieferengpässen der Schraube sind aktuell in diesem Standort keine Termine verfügbar. | Bitte versuchen Sie es zu einem späteren Zeitpunkt oder versuchen Sie es in einem unserer anderen Standorte. |
| EN | Due to supply shortages of the screw, no appointments are currently available at this location. | Please try again later or try one of our other locations. |
| UK | Через перебої з постачанням гвинта наразі в цьому місці немає доступних візитів. | Будь ласка, спробуйте пізніше або виберіть одне з наших інших місць. |
| FR | En raison de difficultés d'approvisionnement de la vis, aucun rendez-vous n'est actuellement disponible sur ce site. | Veuillez réessayer plus tard ou essayer l'un de nos autres sites. |
| AR | بسبب نقص في توريد المسمار، لا توجد مواعيد متاحة حاليًا في هذا الموقع. | يرجى المحاولة لاحقًا أو اختيار أحد مواقعنا الأخرى. |

UK und AR sind noch nicht von einer Muttersprachlerin oder einem Muttersprachler gegengelesen.
Aktuell bietet die Sprachauswahl nur Deutsch an (`AVAILABLE_LANGUAGES`), die anderen Texte sind
für die spätere Freischaltung vorhanden.

### 2.2 Umsetzung

- `AppointmentSelectionContainerComponent` liest `store.isRecallMode` und wählt über das
  `computed()`-Signal `noAppointmentsKeys` die i18n-Keys für Titel und Hinweis.
- Das Template bindet nur noch `noAppointmentsKeys().title` und `.hint`. Texte stehen weiterhin
  ausschließlich in `translations.ts`.
- Neues Interface `NoAppointmentsKeys` in `models/appointment.model.ts`.

### 2.3 Wann der Text erscheint

- Die Terminsuche (`POST /api/v1/snaps`, Variante `recall`) liefert eine leere Liste.
- Schlägt die Suche selbst fehl, bleibt es bei der allgemeinen Fehleransicht mit „Erneut versuchen“.
- Der Text nennt immer den Lieferengpass als Ursache, auch wenn die Werkstatt aus einem anderen
  Grund keinen freien Slot hat. Er gilt, solange die Rückrufaktion „Lenkungsgetriebe Schraube“ läuft
  und der Engpass besteht. Danach oder bei einer anderen Kampagne sind die zwei Keys anzupassen.

### 2.4 Beobachtung außerhalb dieses Requirements

Der Werkstattkalender (`/home/workshop-calendar`, Link „Weitere freie Termine“) hat keinen
Leerzustand. Ohne Slots zeigt er den Einleitungssatz („Wir haben folgende … Termine für Sie
gefunden“) und darunter nichts, im Normal- wie im Rückruf-Modus. Das bleibt unverändert und wäre
ein eigenes Requirement.

---

## 3. Betroffene Dateien

**Frontend:**
- `src/app/core/i18n/translations.ts`: `booking.recall.noAppointments` und `noAppointmentsHint` (DE/EN/UK/FR/AR)
- `src/app/features/booking/models/appointment.model.ts`: Interface `NoAppointmentsKeys`
- `src/app/features/booking/components/appointment-selection/appointment-selection-container.component.ts`: `isRecallMode`, `noAppointmentsKeys`
- `src/app/features/booking/components/appointment-selection/appointment-selection-container.component.html`: Leerzustand bindet `noAppointmentsKeys()`

**Tests:**
- `appointment-selection-container.component.spec.ts` (Jest): Key-Auswahl, deutscher Wortlaut, Texte in allen fünf Sprachen
- `playwright/REQ-044-recall-no-appointments.spec.ts` (neu)

---

## 4. Tests & Verifikation

- **Jest:** 858/858 grün (vorher 849), Coverage 92,9 % Statements, 84,2 % Branches. Neu: 9 Tests
  (4 zur Key-Auswahl und zum Wortlaut, 5 zur Vollständigkeit je Sprache).
- **Type-Check:** 0 Fehler. **ESLint:** 0 Fehler. Das Template hat zwei Warnungen mehr
  (`no-call-expression` für die zwei Signal-Aufrufe), wie bei allen Signalen in Templates.
- **E2E `npm run test:local -- REQ-044`:** 6 bestanden auf Desktop, Tablet und Mobil.
  - Rückruf-Modus: Deep-Link → Standort → Marke → Hinweise → Termin, die Terminsuche wird lokal mit
    „kein Termin“ beantwortet. Geprüft werden Titel und Hinweis im Wortlaut, die Variante `recall` in
    der Suchanfrage, und dass „Neu laden“ eine weitere Suche auslöst.
  - Normalmodus (Gegenprobe): bleibt bei „Keine Termine verfügbar.“ und enthält kein „Lieferengpässen“.
  - Es entsteht keine Buchung bei SoftNet, die Suche wird mit `route.fulfill` beantwortet.
- **Optischer Check** (Desktop, Tablet, Mobil 375 px): Der längere Text bricht sauber um. Auf dem
  Handy steht der Titel über drei Zeilen, darunter Hinweis, „Neu laden“ und „Weitere freie
  Termine“, nichts wird abgeschnitten oder von der Aktionsleiste verdeckt.
- **Gesamte Playwright-Suite (`npm run test:local`, 3 Viewports):** 328 bestanden, 0
  fehlgeschlagen, 1241 übersprungen durch bestehende `describe.skip`-Blöcke, Laufzeit 5,5 Minuten.
  Vorher 322, die Differenz sind die 6 neuen Tests.

---

## 5. Deployment, Risiken, offene Punkte

- **Deploy-Zeitpunkt:** Wie bei jedem Deploy startet der Container neu. Der REQ-038-Buchungspuffer
  im RAM geht verloren, und Kunden mitten in der Buchung erhalten beim Absenden einen Fehler
  (404 „Snap … not found in cache“), weil der Terminsuche-Cache im RAM der Instanz liegt. Am besten
  früh am Tag deployen.
- **Rollback:** Vorherige Revision, keine Datenänderung.
- **Offen:** Muttersprachliches Gegenlesen von UK und AR, sobald die Sprachauswahl freigeschaltet wird.
- **Offen:** Der Werkstattkalender zeigt ohne Slots nichts an (siehe 2.4).
