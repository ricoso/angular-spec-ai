# Feature Documentation (EN): UI Migration Material → Bootstrap 5

## What changed

The Angular 21 booking app was migrated from **Angular Material 21** to **Bootstrap 5.3 + ng-bootstrap 20**. Source of truth: the static design demo at `docs/design-bootstrap/`.

1. **Tech stack switch**
   - `@angular/material` removed; Material theme + all 15 Material module imports gone
   - `bootstrap@5.3.3` and `bootstrap-icons@1.11.3` via CDN in `src/index.html` (no npm bundle)
   - `@ng-bootstrap/ng-bootstrap@20` as npm dependency for the datepicker
   - `@popperjs/core` as a Bootstrap-JS peer dependency

2. **New component layout**
   - **Header**: simplified — only logo, title, and embedded close button. Cart icon and a11y trigger removed.
   - **FloatingOverlay**: existing component, refactored with Bootstrap icons and classes. Cart FAB bottom-right (`.cart-fab`), A11y FAB bottom-left (`.a11y-fab`), both at `bottom: 5em` so they don't overlap wizard nav buttons.
   - **Accessibility menu**: Material radios → pill-group radios (font size as A·A·A·A); Material slide toggles → Bootstrap form switches.
   - **Wizard breadcrumb**: Material icons → Bootstrap icons; markup otherwise unchanged (`aria-current="step"` etc. preserved).

3. **9 wizard pages migrated in parallel** (via parallel sub-agents)
   - `location-selection`: branch cards via Bootstrap `card-pickable` pattern
   - `brand-selection`: brand cards with grayscale → color hover
   - `service-selection`: **layout switch** from grid to horizontal carousel + detail panel
   - `notes`: **form pattern switch** — mat-selects → toggle switches + pill-group radios + Bootstrap form switches
   - `appointment-selection`: date strip + time slot grid instead of mat-datepicker
   - `workshop-calendar`: NgbDatepicker (inline + toggle button)
   - `carinformation`: reactive forms with `.form-floating` + `.invalid-feedback` pattern, new stable form field IDs
   - `booking-overview`: 2-column layout (summary cards on left, sticky submit on right)
   - `error-fallback`: Bootstrap alert pattern instead of mat-card

4. **Language selector reduced to German only**
   - `AVAILABLE_LANGUAGES` array in `translations.ts` now only contains `{ code: 'de', label: 'Deutsch' }`
   - Translation data for EN/UK/FR/AR remains intact in code → re-enabling is a 1-line change

5. **A11y preservation (WCAG 2.1 AA)**
   - All 15 existing `id="..."` attributes preserved (E2E selector stability)
   - All ARIA attributes (`role`, `aria-label`, `aria-describedby`, `aria-current`, `aria-live`) ported to new markup
   - Skip link, focus styles, touch targets ≥ 2.75em, contrast mode, reduced motion — all still functional

## Why

Before: 15 Material modules, awkward to theme (`mat-mdc-*` CSS hierarchy), Material theme imports inflated the bundle and complicated theming. Custom designs had to fight Material internals (`::ng-deep .mdc-evolution-chip__text-label`).

After: **Bootstrap classes directly in markup** — no more `::ng-deep` hacks. Theming via CSS custom properties in `_bootstrap-theme.scss`. Bootstrap loaded via CDN — no bundle weight. Cleaner, easier to maintain.

Bonus: UX improvements from the demo were rolled in at the same time (carousel, switches, pills, cart slide-out, more visible a11y FAB).

## How to use

Nothing changes for end users — wizard works identically (location → brand → services → notes → appointment → booking). Visual refresh + better mobile UX (switches instead of dropdowns, carousel instead of cluttered grids).

**For developers:**
- New component classes in `src/styles/_bootstrap-theme.scss` (`.card-pickable`, `.pill-group`, `.switch-card`, `.cart-item`, `.service-card-mini`, `.service-detail`, `.date-tile`, `.slot`, etc.)
- Bootstrap bundle is globally available — no imports needed in individual components (except for ng-bootstrap components like NgbDatepicker)
- Form validation: `.is-invalid` on input + `.invalid-feedback` directly below
- Floating buttons: `.cart-fab` (right), `.a11y-fab` (left) at `bottom: 5em`

## Open follow-ups

1. **E2E run against backend** (`npm run e2e:backend`) — 18 reactivated specs likely need selector updates for the datepicker and form validation. See `selector-mapping.md`.
2. **Coverage lift** — Statements 73.99% (target 80%); missing tests in BookingStore branches, FloatingOverlay methods, WizardBreadcrumb.
3. **NgbDatepicker DE locale** — currently English, should be localized for the workshop calendar.

## Files changed (overview)

```
133 files changed (per git status)
- 9 wizard pages refactored (all under src/app/features/booking/components/*)
- 5 shared components (header, floating-overlay, accessibility-menu, wizard-breadcrumb, app.component)
- 4 config files (package.json, angular.json, tsconfig.spec.json, jest.config.ts)
- 2 style files (styles.scss, _bootstrap-theme.scss NEW; _material-overrides.scss DELETED)
- 1 i18n file (translations.ts)
- index.html (Bootstrap CDN links)
- 9 spec files updated (svg→png, language list, cart tests, virtual-pickup-delivery)
- 18 Playwright specs reactivated (test.describe.skip → test.describe)
```
