# Requirements Overview

**Project:** Autohaus GmbH Fahrzeugauswahl
**Version:** 1.0
**Last Updated:** 2026-03-09

---

## Status Legend

| Symbol | Status      |
| ------ | ----------- |
| 📝     | Draft       |
| 🔍     | In Review   |
| ✅     | Approved    |
| 🚧     | In Progress |
| ✔️     | Implemented |
| ❌     | Rejected    |

---

## Requirements List

| REQ-ID  | Name              | Status         | Priority | Dependencies                       | Description                                                                                                                                                                     |
| ------- | ----------------- | -------------- | -------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| REQ-001 | Header | ✔️ Implemented | High | - | Wiederverwendbarer Header mit Logo und Accessibility-Einstellungen (Font-Size, High-Contrast, Reduced-Motion) |
| REQ-002 | Markenauswahl | ✔️ Implemented | High | REQ-001 | Einstiegsseite Buchungswizard: Fahrzeugmarken-Auswahl (Audi, BMW, Mercedes-Benz, MINI, Volkswagen) |
| REQ-003 | Standortwahl | ✔️ Implemented | High | REQ-002 | Standortwahl basierend auf gewählter Fahrzeugmarke (Wizard-Schritt 2) |
| REQ-004 | Serviceauswahl | ✔️ Implemented | High | REQ-003 | Serviceauswahl mit Multi-Select, Radio-Varianten und Zusammenfassungsleiste (Wizard-Schritt 3) |
| REQ-005 | Hinweisfenster | ✔️ Implemented | High | REQ-004 | Optionale Buchungsnotiz + servicespezifische Hinweise (Wizard-Schritt 4) |
| REQ-006 | Terminauswahl | ✔️ Implemented | High | REQ-005 | Terminauswahl mit 4 Vorschlägen, Single-Select (Wizard-Schritt 5) |
| REQ-007 | WizardStateSync | ✔️ Implemented | High | REQ-002, REQ-003, REQ-004, REQ-005 | Cross-Cutting: Bei Rückwärtsnavigation im Wizard werden Store-Properties genullt, um UI-Flow und Store-State synchron zu halten. Verhindert unbeabsichtigte URL-Navigation. |
| REQ-008 | Werkstattkalender | ✔️ Implemented | High | REQ-006 | Werkstattkalender mit DatePicker und Uhrzeitslot-Auswahl (Wizard-Schritt 5b) |
| REQ-009 | carinformation | ✔️ Implemented | High | REQ-008 | Fahrzeugdaten und Kundendaten erfassen (Wizard-Schritt 6) |
| REQ-010 | Buchungsübersicht | ✔️ Implemented | High | REQ-009 | Letzte Seite des Buchungs-Wizards. Zeigt Übersicht aller Eingaben (Termin, Service, persönliche Daten, Preis inkl. MwSt.). Enthält "Jetzt anfragen"-Button statt Weiter-Button. |
| REQ-002-Marke | Standortauswahl nach Marke | 📝 Draft | High | REQ-002 | Standortauswahl nach Markenauswahl: User wählt Marke → sieht Autohaus-Standorte → wählt Standort. Statische Daten, kein Backend. |

---

## Dependency Graph

```
REQ-001-Header
    │
    └──► REQ-002-Markenauswahl ◄── REQ-007-WizardStateSync (Cross-Cutting)
              │
              └──► REQ-003-Standortwahl
                        │
                        └──► REQ-004-Serviceauswahl
                                  │
                                  └──► REQ-005-Hinweisfenster
                                            │
                                            └──► REQ-006-Terminauswahl
                                                      │
                                                      ├──► REQ-008-Werkstattkalender (5b)
                                                      │
                                                      └──► REQ-009-carinformation
                                                                │
                                                                └──► REQ-010-Buchungsübersicht
```

---

## Quick Links

| REQ-ID  | Requirement Document                                                    |
| ------- | ----------------------------------------------------------------------- |
| REQ-001 | [REQ-001-Header](./REQ-001-Header/requirement.md)                       |
| REQ-002 | [REQ-002-Markenauswahl](./REQ-002-Markenauswahl/requirement.md)         |
| REQ-003 | [REQ-003-Standortwahl](./REQ-003-Standortwahl/requirement.md)           |
| REQ-004 | [REQ-004-Serviceauswahl](./REQ-004-Serviceauswahl/requirement.md)       |
| REQ-005 | [REQ-005-Hinweisfenster](./REQ-005-Hinweisfenster/requirement.md)       |
| REQ-006 | [REQ-006-Terminauswahl](./REQ-006-Terminauswahl/requirement.md)         |
| REQ-007 | [REQ-007-WizardStateSync](./REQ-007-WizardStateSync/requirement.md)     |
| REQ-008 | [REQ-008-Werkstattkalender](./REQ-008-Werkstattkalender/requirement.md) |
| REQ-009 | [REQ-009-carinformation](./REQ-009-carinformation/requirement.md)       |
| REQ-010 | [REQ-010-Buchungsübersicht](./REQ-010-Buchungsübersicht/requirement.md) | ✔️ Implemented |
| REQ-002-Marke | [REQ-002-Marke](./REQ-002-Marke/requirement.md) |

---

## Statistics

| Status         | Count  |
| -------------- | ------ |
| 📝 Draft | 1 |
| 🔍 In Review | 1 |
| ✅ Approved | 1 |
| 🚧 In Progress | 1 |
| ✔️ Implemented | 12 |
| **Total** | **16** |


---

## Notes

### Design System

- Alle Pages verwenden helles Theme aus `src/styles/_variables.scss`
- Background: #f8f9fa (hell, freundlich)
- NICHT die dunklen Farben aus Screenshots übernehmen!

### Accessibility (PFLICHT)

- Jede Page bekommt den Header aus REQ-001
- Header enthält Accessibility-Controls (Font-Size, Contrast, Motion)
- WCAG 2.1 AA Konformität

### Bilingual

- UI immer DE + EN (i18n)
- Code-Sprache = Requirement-Sprache (hier: Deutsch)
