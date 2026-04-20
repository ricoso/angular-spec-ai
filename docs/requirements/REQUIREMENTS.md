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
| REQ-011 | Serviceauswahl-Erweitert | ✔️ Implemented | High | REQ-004 | Erweiterte Serviceauswahl mit zusätzlichen Optionen |
| REQ-012 | Hinweisseite-Erweitert | ✔️ Implemented | High | REQ-005 | Erweiterte Hinweisseite |
| REQ-013 | Branch-Marke-Tausch | ✔️ Implemented | High | REQ-002, REQ-003 | Marke/Standort-Tausch und UI-Verbesserungen |
| REQ-014 | NestJS-Backend | 📝 Draft | Critical | REQ-001–REQ-010 | NestJS Backend als Middleware/Proxy zur SoftNet API |
| REQ-015 | API-Data-Integration | 🚧 In Progress | High | REQ-014 | Integration der echten API-Daten ins Frontend |
| REQ-016 | User-Lookup | 📝 Draft | High | REQ-009, REQ-014 | User-Abfrage per Email/Kennzeichen mit Formular-Prefill |
| REQ-017 | Loading-Spinner | 📝 Draft | High | REQ-002, REQ-003 | Globale Loading-Spinner-Nachrüstung für alle Wizard-Seiten mit API-Calls |
| REQ-018 | Spring-Boot-Migration | 📝 Draft | Critical | REQ-014 | NestJS Backend durch Spring Boot (Gradle + Spring Security) ersetzen — API 1:1 kompatibel |
| REQ-019 | Deployment | 📝 Draft | High | REQ-018 | Production Deployment mit Docker/Kubernetes |

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
                                                                │
                                                                └──► REQ-016-User-Lookup (erweitert REQ-009)

REQ-011-Serviceauswahl-Erweitert (erweitert REQ-004)
REQ-012-Hinweisseite-Erweitert (erweitert REQ-005)
REQ-013-Branch-Marke-Tausch (erweitert REQ-002/REQ-003)

REQ-017-Loading-Spinner (Cross-Cutting, Referenz: REQ-002/REQ-003)

REQ-014-NestJS-Backend ──► REQ-015-API-Data-Integration
                    ──► REQ-018-Spring-Boot-Migration ──► REQ-019-Deployment
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
| REQ-010 | [REQ-010-Buchungsübersicht](./REQ-010-Buchungsübersicht/requirement.md) |
| REQ-011 | [REQ-011-Serviceauswahl-Erweitert](./REQ-011-Serviceauswahl-Erweitert/requirement.md) |
| REQ-012 | [REQ-012-Hinweisseite-Erweitert](./REQ-012-Hinweisseite-Erweitert/requirement.md) |
| REQ-013 | [REQ-013-Branch-Marke-Tausch](./REQ-013-Branch-Marke-Tausch/requirement.md) |
| REQ-014 | [REQ-014-NestJS-Backend](./REQ-014-NestJS-Backend/requirement.md) |
| REQ-015 | [REQ-015-API-Data-Integration](./REQ-015-API-Data-Integration/requirement.md) |
| REQ-016 | [REQ-016-User-Lookup](./REQ-016-User-Lookup/requirement.md) |
| REQ-017 | [REQ-017-Loading-Spinner](./REQ-017-Loading-Spinner/requirement.md) |
| REQ-018 | [REQ-018-Spring-Boot-Migration](./REQ-018-Spring-Boot-Migration/requirement.md) |
| REQ-019 | [REQ-019-Deployment](./REQ-019-Deployment/requirement.md) |

---

## Statistics

| Status         | Count  |
| -------------- | ------ |
| 📝 Draft | 5 |
| 🔍 In Review | 1 |
| ✅ Approved | 1 |
| 🚧 In Progress | 1 |
| ✔️ Implemented | 12 |
| **Total** | **19** |


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
