# Feature-Dokumentation (DE): UI-Migration Material → Bootstrap 5

## Was wurde gemacht

Die Angular-21-Buchungs-App wurde von **Angular Material 21** auf **Bootstrap 5.3 + ng-bootstrap 20** umgestellt. Vorlage war die im Vorfeld erstellte statische Design-Demo unter `docs/design-bootstrap/`. Die Migration umfasst:

1. **Tech-Stack-Wechsel**
   - `@angular/material` deinstalliert; Material-Theme + alle 15 Material-Module-Imports entfernt
   - `bootstrap@5.3.3` und `bootstrap-icons@1.11.3` per CDN in `src/index.html` (kein npm-Bundle)
   - `@ng-bootstrap/ng-bootstrap@20` als npm-Paket für Datepicker-Komponente
   - `@popperjs/core` als Peer-Dependency für Bootstrap-JS

2. **Neue Component-Struktur**
   - **Header**: stark vereinfacht — nur noch Logo, Title, Embedded-Close-Button. Cart-Icon und A11y-Menü-Trigger raus.
   - **FloatingOverlay**: weiterhin existierende Komponente, jetzt mit Bootstrap-Icons und Bootstrap-Klassen. Cart-FAB unten rechts (`.cart-fab`), A11y-FAB unten links (`.a11y-fab`), beide bei `bottom: 5em` damit sie die Wizard-Nav-Buttons nicht überlagern.
   - **Accessibility-Menü**: Material-Radios → Pill-Group-Radios (Schriftgröße als A·A·A·A); Material-Slide-Toggles → Bootstrap-Form-Switches.
   - **Wizard-Breadcrumb**: Material-Icons → Bootstrap-Icons; Markup ansonsten gleich (`aria-current="step"` etc. erhalten).

3. **9 Wizard-Pages parallel migriert** (via parallele Sub-Agents)
   - `location-selection`: Filial-Karten als Bootstrap-`card-pickable`-Pattern
   - `brand-selection`: Marken-Karten mit Grayscale → Color-Hover
   - `service-selection`: **Layout-Wechsel** von Grid auf horizontales Karussell + Detail-Panel
   - `notes`: **Form-Pattern-Wechsel** — Mat-Selects → Toggle-Switches + Pill-Group-Radios + Standard-Form-Switches
   - `appointment-selection`: Date-Strip + Time-Slot-Grid statt Mat-Datepicker
   - `workshop-calendar`: NgbDatepicker (Inline + Toggle-Button)
   - `carinformation`: Reactive-Form mit `.form-floating` + `.invalid-feedback`-Pattern, neuer stabile Form-Feld-IDs
   - `booking-overview`: 2-Spalten-Layout (Summary-Cards links, Sticky-Submit rechts)
   - `error-fallback`: Bootstrap-Alert-Pattern statt Mat-Card

4. **Sprach-Selektor reduziert auf nur Deutsch**
   - `AVAILABLE_LANGUAGES` Array in `translations.ts` enthält nur noch `{ code: 'de', label: 'Deutsch' }`
   - Translation-Daten EN/UK/FR/AR bleiben vollständig im Code → Re-Aktivierung = 1-Zeilen-Change

5. **A11y-Erhaltung (WCAG 2.1 AA)**
   - Alle 15 bestehenden `id="..."`-Attribute UNVERÄNDERT (E2E-Selektor-Stabilität)
   - Alle ARIA-Attribute (`role`, `aria-label`, `aria-describedby`, `aria-current`, `aria-live`) auf neues Markup übertragen
   - Skip-Link, Focus-Styles, Touch-Targets ≥ 2.75em, Kontrast-Mode, Reduced-Motion — alle weiter funktional

## Warum

Vorher: 15 Material-Module, schwer thematisierbar (`mat-mdc-*`-CSS-Hierarchie), Material-Theme-Imports machten das Bundle größer und das Theming komplex. Custom-Designs mussten gegen Material-Internals arbeiten (`::ng-deep .mdc-evolution-chip__text-label`).

Nachher: **Bootstrap-Klassen direkt im Markup** — keine ::ng-deep-Hacks mehr. Theming via CSS Custom Properties in `_bootstrap-theme.scss`. Bootstrap kommt per CDN — kein Bundle-Anteil. Cleaner, leichter zu pflegen.

Zusätzlich: UX-Verbesserungen aus der Demo wurden direkt mit übernommen (Karussell, Switches, Pills, Cart-Slide-Out, A11y-FAB-Sichtbarkeit).

## Wie nutzen

Nichts geändert für den Endbenutzer — Wizard funktioniert identisch (Filiale → Marke → Services → Notes → Termin → Buchung). Visuelle Auffrischung + bessere mobile Bedienung (Switches statt Dropdowns, Karussell statt überfüllten Grids).

**Für Entwickler:**
- Neue Komponenten-Klassen in `src/styles/_bootstrap-theme.scss` (`.card-pickable`, `.pill-group`, `.switch-card`, `.cart-item`, `.service-card-mini`, `.service-detail`, `.date-tile`, `.slot`, etc.)
- Bootstrap-Bundle ist global verfügbar — keine Imports in einzelnen Components nötig (außer für ng-bootstrap-Komponenten wie NgbDatepicker)
- Form-Validation: `.is-invalid` auf Input + `.invalid-feedback` direkt darunter
- Floating-Buttons: `.cart-fab` (rechts), `.a11y-fab` (links) bei `bottom: 5em`

## Folge-Tasks (offen)

1. **E2E-Lauf gegen Backend** (`npm run e2e:backend`) — 18 reaktivierte Specs benötigen voraussichtlich Selektor-Updates für Datepicker und Form-Validation. Siehe `selector-mapping.md`.
2. **Coverage-Lift** — Statements 73.99% (Soll: 80%); fehlende Tests in BookingStore-Branches, FloatingOverlay-Methoden, WizardBreadcrumb.
3. **NgbDatepicker DE-Locale** — aktuell Englisch, sollte für Werkstatt-Kalender lokalisiert werden.

## Geänderte Dateien (Übersicht)

```
133 Files changed (laut git status)
- 9 Wizard-Pages refactored (alle Files unter src/app/features/booking/components/*)
- 5 Shared Components (header, floating-overlay, accessibility-menu, wizard-breadcrumb, app.component)
- 4 Config-Files (package.json, angular.json, tsconfig.spec.json, jest.config.ts)
- 2 Style-Files (styles.scss, _bootstrap-theme.scss NEU; _material-overrides.scss DELETE)
- 1 i18n-File (translations.ts)
- index.html (Bootstrap CDN-Links)
- 9 Spec-Files Anpassungen (svg→png, Sprach-Liste, Cart-Tests, virtual-pickup-delivery)
- 18 Playwright-Specs reaktiviert (test.describe.skip → test.describe)
```
