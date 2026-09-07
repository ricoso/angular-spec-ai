# REQ-035 — Radwechsel-Terminsuche reparieren + Boxenstopp-Samstage bevorzugen

## Überblick

Kunden, die einen Räderwechsel (Boxenstopp) buchen, sehen bei der Datums-Suche im Werkstattkalender **keine Termine** (SoftNet lehnt die Anfrage mit 400 ab), und die Termin-Schnellauswahl bevorzugt nicht die Boxenstopp-Samstage, an denen SoftNet exklusiv Radwechsel-Slots vergibt.

## Problem (Ist-Zustand, per Prod-Logs + Live-Test am 31.08.2026 verifiziert)

- **P1:** Der Werkstattkalender (Datums-Suche) sendet kein `variant`-Feld → Backend nutzt die Default-Group statt der tireChange-Group → SoftNet 400 `"missing operations in list"` → UI zeigt „keine Termine". Live-Beweis: identischer Request ohne variant → 400; mit `variant:'tireChange'` → 18 Tage Slots.
- **P2:** Die Frontend-Service-Keys `bring-own-tires`, `stored-tires`, `store-tires` — **drei verschiedene Services** — matchen die Operation-Namen der tireChange-Groups nicht (dort: „Räderwechsel - ich bringe meine Räder mit." etc.); der Mapper liefert eine leere Liste → SoftNet 400 `"Missing operations list!"`.
- **P3:** Die Schnellauswahl behandelt tireChange wie default und zeigt die 4 frühesten Tage — Boxenstopp-Samstage (z. B. 10.10.2026: 5 Slots 06–10 Uhr, keine Default-Slots) gehen unter.

## User Stories

- **US-1:** Als Kunde im Radwechsel-Flow möchte ich im Werkstattkalender ein Datum wählen und die dort verfügbaren Slots sehen, damit ich gezielt an einem Wunschtag buchen kann.
- **US-2:** Als Kunde möchte ich zwischen „Räder mitbringen", „Räder sind eingelagert" und „Räder einlagern lassen" wählen können und dafür jeweils den korrekten SoftNet-Service gebucht bekommen.
- **US-3:** Als Kunde im Radwechsel-Flow möchte ich in der Schnellauswahl bevorzugt die nächsten Boxenstopp-Samstage angeboten bekommen, damit ich die dafür reservierten Slots nutze.

## Akzeptanzkriterien

- **AC-1:** Datums-Suche im Werkstattkalender liefert im tireChange-Flow Slots (kein 400); die Snaps-Anfrage geht an die tireChange-Group der Filiale.
- **AC-2:** Die drei Radwechsel-Keys mappen auf drei **verschiedene** Operations der tireChange-Group (Referenz VW Zentrum Essen, Group `690cbc4c9e5d1205e2f21b25`):
  - `bring-own-tires` → „Räderwechsel - ich bringe meine Räder mit."
  - `stored-tires` → „Räderwechsel - meine Räder sind eingelagert"
  - `store-tires` → „Meine angelieferten Räder sollen bitte eingelagert werden."
- **AC-3:** In Default-Groups (Naming „Radwechsel ohne/mit Einlagerung") funktionieren die Keys weiterhin.
- **AC-4 (Schnellauswahl):** Bei `variant='tireChange'` werden die **nächsten 3 Samstage** mit freien Slots zuerst angezeigt, danach mit den frühesten Werktagsterminen auf 4 Vorschläge aufgefüllt; ohne verfügbare Samstage reine Werktagsliste. Bei `variant='default'` bleibt das Verhalten **exakt wie bisher**.
- **AC-5:** Eine leere Operations-Liste wird nicht mehr an SoftNet gesendet, sondern als klarer 400 mit aussagekräftiger Message vom eigenen Backend beantwortet.

## Abgleich der Operation-Namen über alle Autohäuser (31.08.2026, Prod)

Alle 27 Filialen haben eine tireChange-Group; **25 von 27** liefern exakt dieselben 4 Operation-Namen (siehe AC-2 plus „Hol & Bringservice"). Weitere Benennungsvarianten existieren nicht.

**Separater Befund (Ticket an SoftNet/Setup, nicht Teil dieses REQ):** Audi Zentrum Leverkusen (Group `692efcc9d6f69fcab895e50b`) und VW Zentrum Leverkusen (Group `692efccdfa906b4a962c1c7b`) → SoftNet 403 `AccessContextError` „Client does not have access to group". Der Radwechsel-Flow ist dort unabhängig vom Code kaputt.

## Out of Scope

- Änderungen an SoftNet-Konfiguration/Groups (inkl. Leverkusen-Freischaltung).
- UI-Redesign der Schnellauswahl (nur Auswahl-Logik).
- Der Kennzeichen-Befund aus REQ-034-Analyse (SoftNet-seitige Kürzung, liegt beim Dienstleister).
