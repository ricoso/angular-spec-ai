# Selector-Mapping: Material → Bootstrap

Referenz für die Anpassung von Playwright-E2E-Specs, die noch Material-Selektoren nutzen.

## Häufigste Pattern

| Material-Selektor (alt) | Bootstrap-Selektor (neu) | Hinweis |
|---|---|---|
| `.mat-mdc-form-field` | `.form-floating` | Wrapper |
| `.mat-mdc-form-field-error` | `.invalid-feedback` | Pflicht: Form-Validation-Anzeige |
| `mat-error` | `.invalid-feedback` | dito |
| `.mat-mdc-select-value-text` | `select.form-select` (value über `.value`) | Select-Anzeige |
| `[matInput]` | `input.form-control` | Standard-Input |
| `mat-form-field-flex` (CSS) | `.form-floating` (CSS) | Container-Layout |
| `mat-checkbox` (Tag) | `input.form-check-input[type="checkbox"]` | + `<label class="form-check-label">` |
| `.mat-mdc-checkbox-input` | `input.form-check-input` | im Tag-Mode |
| `mat-slide-toggle` | `input.form-check-input.form-switch[type="checkbox"][role="switch"]` | + Bootstrap-Switch-CSS |
| `mat-radio-group` | `.pill-group` (mit `<input type="radio">` + `<label>`) | bei UI-Migration auf Pills |
| `mat-icon` (Tag) | `i.bi[class*="bi-"]` | Bootstrap-Icons |
| `mat-icon` (Selektor "settings") | `i.bi.bi-gear` | konkrete Icon-Map siehe unten |
| `mat-card` | `.card` (mit `.card-body` innen) | Standard-Card |
| `.mat-mdc-card-content` | `.card-body` | dito |
| `mat-card-pickable` | `.card.card-pickable` | mit `.selected` State |
| `mat-flat-button[color="primary"]` | `button.btn.btn-primary` | Primary |
| `mat-stroked-button` | `button.btn.btn-outline-secondary` | Outlined |
| `mat-icon-button` | `button.btn.btn-link.p-0` | Icon-only |
| `mat-button` | `button.btn` | je nach Style mit btn-* |
| `mat-fab` | `.cart-fab` ODER `.a11y-fab` | Floating Buttons |
| `.mat-badge-content` | `.badge` ODER absolut positionierter `<span class="badge">` | Cart-Counter |
| `mat-progress-spinner` / `mat-spinner` | `.spinner-border` (Bootstrap) | Loading |
| `mat-menu` (Cart, A11y) | Floating-Overlay-Panel + `.cart-fab` / `.a11y-fab` | Header-Menü ersetzt |
| `mat-datepicker` (Toggle) | `input[ngbDatepicker]` + `.btn.btn-outline-secondary` mit `bi-calendar` | siehe ng-bootstrap |
| `mat-calendar` | `ngb-datepicker` | Inline-Calendar |
| `.mat-calendar-body-today` | `.bg-primary` (NgbDatepicker) | Today-Marker |
| `.mat-mdc-form-field-bottom-align` | `.invalid-feedback` (eigene Position) | Hint/Error-Bereich |
| `mat-chip-set` | `.d-flex.flex-wrap.gap-2` | Chip-Container |
| `mat-chip` | `.badge` ODER eigenes Chip-Pattern | Chip-Style |

## Icon-Mapping (Material → Bootstrap-Icons)

| Material (text in mat-icon) | Bootstrap-Icon-Klasse |
|---|---|
| `arrow_back` | `bi-arrow-left` |
| `arrow_forward` | `bi-arrow-right` |
| `arrow_upward` | `bi-arrow-up` |
| `arrow_downward` | `bi-arrow-down` |
| `check` | `bi-check-lg` |
| `check_circle` / `check_circle_outline` | `bi-check-circle-fill` / `bi-check-circle` |
| `close` / `cancel` | `bi-x-lg` |
| `error_outline` / `error` | `bi-exclamation-circle` / `bi-exclamation-triangle-fill` |
| `info_outline` / `info` | `bi-info-circle` |
| `warning` | `bi-exclamation-triangle-fill` |
| `refresh` | `bi-arrow-clockwise` |
| `search` | `bi-search` |
| `expand_more` / `keyboard_arrow_down` | `bi-chevron-down` |
| `expand_less` / `keyboard_arrow_up` | `bi-chevron-up` |
| `chevron_left` / `keyboard_arrow_left` | `bi-chevron-left` |
| `chevron_right` / `keyboard_arrow_right` | `bi-chevron-right` |
| `location_on` | `bi-geo-alt-fill` |
| `directions_car` / `drive_eta` | `bi-car-front-fill` |
| `tag` | `bi-tag-fill` |
| `build` | `bi-tools` |
| `event` | `bi-calendar-event` |
| `clock` / `access_time` | `bi-clock` |
| `info` | `bi-info-circle-fill` |
| `accessibility_new` | `bi-universal-access` |
| `shopping_cart` / `shopping_bag` | `bi-bag-fill` |
| `phone` / `call` | `bi-telephone-fill` |
| `email` / `mail` | `bi-envelope-fill` |
| `person` / `person_outline` | `bi-person-fill` / `bi-person` |
| `lock` / `lock_outline` | `bi-lock-fill` / `bi-lock` |
| `home` | `bi-house-fill` |
| `settings` | `bi-gear-fill` |
| `wb_sunny` / `sunrise` | `bi-sunrise` |
| `bedtime` / `sunset` | `bi-sunset` |
| `today` | `bi-calendar-week` |
| `history` | `bi-clock-history` |
| `add` / `plus` | `bi-plus-lg` |
| `remove` / `minus` | `bi-dash-lg` |

## Validation-Pattern

**Material:**
```html
<mat-form-field>
  <mat-label>E-Mail</mat-label>
  <input matInput type="email" formControlName="email">
  <mat-error *ngIf="form.get('email').invalid && form.get('email').touched">
    Pflichtfeld
  </mat-error>
</mat-form-field>
```

**Bootstrap (Migration):**
```html
<div class="form-floating mb-3">
  <input
    type="email"
    id="customer-email"
    class="form-control"
    [class.is-invalid]="form.get('email').invalid && form.get('email').touched"
    formControlName="email"
    aria-describedby="customer-email-error"
    placeholder="E-Mail">
  <label for="customer-email">E-Mail *</label>
  @if (form.get('email').invalid && form.get('email').touched) {
    <div class="invalid-feedback" id="customer-email-error">Pflichtfeld</div>
  }
</div>
```

## Stable Selectors (immer noch gültig)

Diese Selektoren bleiben über die Migration hinweg gleich und müssen in E2E-Specs **nicht** geändert werden:

| Selektor | Wo |
|---|---|
| `[role="banner"]` | Header |
| `[role="group"]` | Card-Grids (Filiale, Service) |
| `[role="region"]` | Booking-Overview-Tiles |
| `[role="alert"]` | Error-Fallback |
| `[role="status"]` | Loading-States, Banner |
| `[role="radio"]` | Service-Tiles, Time-Slots |
| `[role="radiogroup"]` | Filial-Liste, Pills |
| `[aria-current="step"]` | Wizard-Breadcrumb (aktiver Schritt) |
| `[aria-live="polite"]` / `"assertive"` | Status / Error-Banner |
| `#main-content` | Skip-Link-Target |
| `#appointment-title`, `#overview-title` | Page-Titles |
| `#error-fallback-title`, `#error-fallback-message` | Error-Fallback |
| `#font-size-label`, `#language-label` | A11y-Menü |
| `#notes-counter`, `#notes-hint` | Notes-Form |
| `#tile-title-appointment`, `#tile-title-data`, `#tile-title-extras`, `#tile-title-service` | Booking-Overview-Tiles |
| `#wk-title`, `#workshop-date-input` | Workshop-Calendar |

**Carinformation-Page neu ergänzte stabile IDs** (vorher gab's keine):
- `#customer-email`, `#customer-firstName`, `#customer-lastName`, `#customer-salutation`, `#customer-street`, `#customer-postalCode`, `#customer-city`, `#customer-mobilePhone`
- `#vehicle-licensePlate`, `#vehicle-mileage`
- `#privacy-consent`, `#carinformation-back-button`, `#carinformation-continue-button`
- Error-IDs: `#customer-email-error`, etc.

## E2E-Test-Anpassungs-Checkliste

Pro Spec:
1. Suche `mat-mdc-` / `mat-calendar` / `cdk-overlay` / `mat-form-field` / `mat-icon` / `mat-button` / `mat-select` / `mat-checkbox` / `mat-slide-toggle`
2. Mappe via Tabelle oben
3. Bei Datepicker-Specs: NgbDatepicker hat anderen Open/Close-Mechanismus — ggf. eigene `[ngbDatepicker]`-Selektoren ergänzen
4. Bei Modal/Dialog-Specs: Material-Dialog → ng-bootstrap NgbModal (Selektoren auf `.modal.show`)
5. Re-Run je Spec mit `--project=chromium-desktop` zur Diagnose
