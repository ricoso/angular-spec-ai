# REQ-025: API-Driven Content (Cleanup hardcoded Mappings)

**Status:** Draft
**Priority:** High
**Type:** Functional (Cross-Cutting / Cleanup)
**Created:** 2026-04-27
**Author:** Rico Sonntag

---

## 1. Overview

### 1.1 Purpose
Cross-cutting cleanup requirement: the booking wizard must stop relying on
hardcoded frontend mappings (`AVAILABLE_SERVICES`, `SERVICE_HINTS`, branch-
address strings, callback option `'none'`) and instead consume content
delivered by the SoftNet API (proxied through the Spring Boot backend).
At the same time, six small UX adjustments are bundled in:

- (a) Notes textarea placeholder + hint reads "Bitte tragen Sie hier Ihre
  Nachricht an uns ein."
- (b) Services + sub-options come from `/branches/{id}/jobtypes`, no local
  `AVAILABLE_SERVICES` table any more.
- (c) Branch info banner shows full address: `<Street>, <ZIP> <City>` —
  driven by API fields, not concatenated strings.
- (d) Callback dropdown defaults to **"Nein"** with options `Ja` / `Nein`
  (no more "Keine Auswahl").
- (e) Per-service hint text is delivered by the API (`hint` field on each
  JobType), not from `notes-hints.constants.ts`.
- (f) When the user picks a rental car category, the notes page shows a
  price block with `Mietwagen-Kategorie: <Name> — <Price> <Currency>
  pro <Unit>` underneath the service hints — only when an API price is
  available.
- (g) The error-fallback screen (REQ-024) no longer renders a hardcoded
  phone block (`+49 211 1234567`). The whole `<aside class=
  "error-fallback__phone">` element is removed, including its i18n keys
  (`app.error.phoneIntro`, `app.error.phoneAriaLabel`) in all 5 languages
  and the `phoneNumber` / `phoneTelHref` computeds in the container.
  Rationale: there is no single dealership phone number — phone numbers
  are per-branch and live in SoftNet master data; rendering a static
  Düsseldorf number when the API is down is misleading.

The implementation deletes / drastically simplifies several frontend files
(`service.model.ts`, `notes-hints.constants.ts`) and extends three backend
DTOs (`JobTypeResponseDto`, `BranchResponseDto`, `RentCategoryResponseDto`).

### 1.2 Scope
**Included:**
- Removal of `AVAILABLE_SERVICES` constant + `ServiceType` union from
  `service.model.ts`; replacement by an API-typed `ApiJobType` model.
- Removal of `notes-hints.constants.ts` + the `SERVICE_HINTS` array.
- `JobTypeResponseDto` extension with `hint?: string`, `category?: string`,
  `iconUrl?: string` (and optional `categoryName`).
- `BranchResponseDto` extension: structured `address` object with
  `street`, `zip`, `city`, `country?`.
- `RentCategoryResponseDto` extension: optional `price`, `currency`,
  `priceUnit`.
- `BookingStore`: new `loadJobTypesForBranch` rxMethod, `jobTypes` state,
  `selectedRentCategoryPrice` computed.
- `CallbackOption` union changed from `'none' | 'yes'` to `'no' | 'yes'`,
  `DEFAULT_NOTES_EXTRAS.callbackOption = 'no'`.
- Backend `callbackRequested` mapping `'yes' → true`, `'no' → false`
  (explicit boolean, not undefined).
- New presentational `RentCategoryPriceBlockComponent` on the notes page.
- Notes textarea placeholder + hint text (i18n keys updated in DE + EN
  + UK + FR + AR).
- Removal of the phone block (`<aside class="error-fallback__phone">`)
  from `error-fallback-container.component.html`, deletion of the
  `phoneNumber` / `phoneTelHref` computeds + the `phone-*` SCSS rules,
  and removal of the `app.error.phoneIntro` / `app.error.phoneAriaLabel`
  i18n keys in all 5 languages.

**Excluded:**
- New SoftNet endpoints — only existing endpoints (`/branches`,
  `/branches/{id}/jobtypes`, `/branches/{id}/rentcategories`) are
  consumed.
- Pricing logic / actual checkout — only display.
- Master-data editor for SoftNet (out of scope, customer-managed).
- Migration of legacy bookings (not persisted in this app).

### 1.3 Related Requirements
- REQ-002-Markenauswahl: branch info banner format
- REQ-004-Serviceauswahl: service cards rendering (no longer hardcoded)
- REQ-005-Hinweisfenster: notes page (placeholder, hints, extras)
- REQ-017-Loading-Spinner: rent-category loading + component-level errors
- REQ-021-Multi-Branch-Setup: `setup.json`, `BranchesService`
- REQ-024-Error-Fallback-Screen: top-level fallback if API fails

---

## 2. User Story

**As a** customer using the booking wizard
**I want** to see the services, prices, hints and addresses that my chosen
branch actually offers, kept up-to-date by the dealership in SoftNet
**So that** the information I see is always correct without a frontend
release whenever the dealer changes a service text, a price or an address.

**Acceptance Criteria:**

- [ ] AC-1: Notes textarea on `/home/notes` shows placeholder text
  "Bitte tragen Sie hier Ihre Nachricht an uns ein." (DE) /
  "Please enter your message to us here." (EN) — i18n key
  `booking.notes.form.note.placeholder`.
- [ ] AC-2: Notes textarea shows the same text as `<mat-hint>` underneath
  the field (i18n key `booking.notes.form.note.hint`); existing char-
  counter hint stays on the right.
- [ ] AC-3: Frontend file `src/app/features/booking/models/service.model.ts`
  no longer exports `AVAILABLE_SERVICES`. The `ServiceDisplay` /
  `ServiceType` constants are removed; only `ApiJobType` /
  `ApiJobTypeOption` / `SelectedJobType` remain.
- [ ] AC-4: Frontend file `notes-hints.constants.ts` is deleted.
  `service-hints.component` derives its hints from `BookingStore.jobTypes`
  filtered by `selectedJobTypes`.
- [ ] AC-5: Service cards on `/home/services` are rendered from the
  `BookingStore.jobTypes` signal (loaded via `loadJobTypesForBranch`).
  Names + descriptions come straight from the API response — no local
  i18n key lookup for the service title.
- [ ] AC-6: `BookingStore` exposes a new `loadJobTypesForBranch(branchId)`
  rxMethod that calls `GET /api/v1/branches/{branchId}/jobtypes` once per
  branch selection and caches the result in state until branch changes.
- [ ] AC-7: Variant detection (`selectedVariant` computed) checks
  `category === 'tireChange'` (or equivalent stable API marker) on the
  selected JobType — never compares against the legacy string
  `'tire-change'`.
- [ ] AC-8: Branch info banner on `/home/brands` reads
  `<Branch-Name> — <Street>, <ZIP> <City>`. Each segment comes from
  `BranchConfig.address.{street, zip, city}` populated by the API.
- [ ] AC-9: When the API does not deliver a `zip` or `city`, the banner
  falls back to `<Branch-Name> — <Street>` (no trailing comma, no
  empty placeholder).
- [ ] AC-10: Callback `<mat-select>` shows two options only: "Ja" and
  "Nein". The "Keine Auswahl" option is removed.
- [ ] AC-11: On entering `/home/notes` for the first time the callback
  dropdown is preselected with **"Nein"** (`callbackOption: 'no'`).
- [ ] AC-12: `CallbackOption` type union is `'no' | 'yes'`. The legacy
  value `'none'` no longer exists in the codebase or in tests.
- [ ] AC-13: Backend submits `callbackRequested: true` when the user
  picked `'yes'` and `callbackRequested: false` when the user picked
  `'no'` — never `undefined`.
- [ ] AC-14: Service hints displayed on `/home/notes` are taken from the
  `hint` field of the API JobType entry. If `hint` is empty/null, the
  hint card is omitted (no fallback i18n text).
- [ ] AC-15: When a rent category is selected on `/home/notes`, a price
  block is rendered below the service hints with the format
  `Mietwagen-Kategorie: <Name> — <Price> <Currency> pro <Unit>`.
  Example: `Mietwagen-Kategorie: Kompakt — 49,00 € pro Tag`.
- [ ] AC-16: When `selectedRentCategoryId` is null, the price block is
  not rendered (display: hidden, no DOM placeholder).
- [ ] AC-17: When the API does not deliver a `price` for the selected
  rent category, the price block is not rendered (silent feature flag,
  no error).
- [ ] AC-18: Backend `JobTypeResponseDto` carries `hint?: String`,
  `category?: String`, `categoryName?: String`, `iconUrl?: String`.
- [ ] AC-19: Backend `BranchResponseDto.address` is a structured object
  `{ street, zip, city, country? }` (not a flat string).
- [ ] AC-20: Backend `RentCategoryResponseDto` carries `price?: BigDecimal`,
  `currency?: String` (ISO-4217), `priceUnit?: String` (`day`, `hour`).
- [ ] AC-21: i18n keys `booking.notes.callback.none` are removed in
  DE / EN / UK / FR / AR. New key `booking.notes.callback.no` is added
  with locale-correct values.
- [ ] AC-22: WCAG 2.1 AA: textarea hint and price block are linked via
  `aria-describedby`; price block has `role="status"` and a screen-reader
  text "Mietwagen-Kategorie ausgewählt".
- [ ] AC-23: Coverage > 80 % for changed files (`booking.store.ts`,
  `notes-form.component`, `notes-extras-form.component`,
  `service-hints.component`, new `rent-category-price-block.component`,
  backend services).

---

## 3. Preconditions

### 3.1 System
- Spring Boot backend reachable at `${apiUrl}` (`/api/v1`).
- `setup.json` loaded successfully (REQ-021), branch is resolvable.
- SoftNet API reachable for `getJobTypes`, `getRentCategories`,
  `getGroups`.

### 3.2 User
- User has selected a branch (deep-link `?branchId=…` or header switcher).
- User has progressed at least to the brand-selection page.

### 3.3 Data
- SoftNet master data contains, for the chosen branch:
  - JobTypes with `id`, `name`, `description`, optional `hint`,
    `category`, `iconUrl`, list of operations (sub-options).
  - Branch with `street`, `zip`, `city`.
  - Rent categories (when applicable) with `price`, `currency`,
    `priceUnit`.
- If SoftNet does not yet deliver `hint` / `price` / `iconUrl`, the
  backend forwards the missing field as `null` and the frontend hides
  the dependent UI block (graceful degradation).

---

## 4. Main Flow

**Step 1: Branch selection (REQ-021/REQ-002)**
- **User:** Opens `/home?branchId=duesseldorf-werstener` (or picks branch
  via header switcher).
- **System:** `BranchSelectionResolver` calls
  `GET /api/v1/branches/duesseldorf-werstener`. Backend responds with
  structured `address: { street, zip, city }`.
- **Expected:** `BookingStore.selectedBranch.address` is fully
  populated.

**Step 2: Brand page renders banner**
- **User:** Navigates to `/home/brands`.
- **System:** Brand-selection container reads `selectedBranch()` from
  store, computes `formattedBranchAddress()` =
  `${street}, ${zip} ${city}`. Banner reads
  `Filiale: <Name> — <Street>, <ZIP> <City>`.

**Step 3: User picks a brand → JobTypes preload**
- **User:** Clicks a brand card.
- **System:** `BrandSelectionContainer.onBrandSelect` calls
  `bookingStore.setBrand(brand)` and triggers
  `bookingStore.loadJobTypesForBranch(branchId)`.
  Backend forwards SoftNet response, enriches each entry with `hint`,
  `category`, `iconUrl`. Store caches the array in `jobTypes`.
- **Expected:** `BookingStore.jobTypes()` is populated, no spinner on the
  next page.

**Step 4: Service page renders dynamic cards**
- **User:** Lands on `/home/services`.
- **System:** Service container reads `bookingStore.jobTypes()`. Cards
  are rendered with `name`, `description`, `iconUrl` (or fallback Material
  icon if `iconUrl` is null). Sub-options come from the same API entry.
- **Expected:** No `AVAILABLE_SERVICES` import, all data reactive.

**Step 5: User selects services → variant detection**
- **User:** Toggles JobType cards on/off.
- **System:** `selectedVariant` computed scans
  `selectedJobTypes().some(jt => jt.category === 'tireChange')`.
- **Expected:** When a tireChange JobType is selected, the store reports
  variant `tireChange`; otherwise `default`.

**Step 6: User opens notes page**
- **User:** Clicks "Weiter" on services page.
- **System:** Notes container loads. The textarea shows placeholder
  + hint "Bitte tragen Sie hier Ihre Nachricht an uns ein." Service
  hints are derived from `selectedJobTypes` × `jobTypes` and rendered
  one card per JobType that has a non-empty `hint` string. Callback
  dropdown is preselected with "Nein".

**Step 7: User selects a rent category → price block appears**
- **User:** Picks "Kompakt" in the rent category dropdown.
- **System:** Store updates `selectedRentCategoryId`. New computed
  `selectedRentCategoryPrice` returns
  `{ name: 'Kompakt', price: 49.00, currency: 'EUR', priceUnit: 'day' }`.
  `RentCategoryPriceBlockComponent` renders
  `Mietwagen-Kategorie: Kompakt — 49,00 € pro Tag`.

**Step 8: User submits booking**
- **User:** Completes the wizard, hits "Buchen".
- **System:** `submitBooking` builds the SoftNet payload with
  `callbackRequested: extras.callbackOption === 'yes'` (boolean),
  `rentCategoryId: <id>`, JobType-Operation IDs from API.
- **Expected:** Backend receives correct API IDs, no legacy strings.

---

## 5. Alternative Flows

### 5.1 Alt Flow A: User does not pick a rent category
**Trigger:** `selectedRentCategoryId() === null` on the notes page.
**Flow:**
1. `selectedRentCategoryPrice` computed returns `null`.
2. `RentCategoryPriceBlockComponent` `*@if` guard is false.
3. No DOM is rendered for the price block — the layout below the hints
   collapses naturally.

### 5.2 Alt Flow B: SoftNet delivers no `hint` for a JobType
**Trigger:** `jobType.hint` is `null` or empty string.
**Flow:**
1. `service-hints.component` filter excludes the JobType from
   `visibleHints`.
2. If no JobType has a hint, the whole "Wichtige Hinweise"-section header
   is hidden (no empty box).

### 5.3 Alt Flow C: Backend delivers no `zip` / `city` on the branch
**Trigger:** Branch master data in SoftNet is incomplete.
**Flow:**
1. `BranchResponseDto.address` has `zip = null` or `city = null`.
2. Frontend `formattedBranchAddress()` falls back: if both missing →
   only `street`; if only `zip` missing → `<street>, <city>`; if only
   `city` missing → `<street>, <zip>`.
3. Banner is rendered without empty separators ("--, ").

### 5.4 Alt Flow D: SoftNet delivers no `price` on a rent category
**Trigger:** Master data missing.
**Flow:**
1. `RentCategoryResponseDto.price` is `null`.
2. `selectedRentCategoryPrice` computed returns `null` even when an ID
   is selected.
3. Notes page hides the price block — booking submit still works
   (rentCategoryId still goes through).

### 5.5 Alt Flow E: User changes branch mid-wizard
**Trigger:** Branch switcher opened, new branch picked.
**Flow:**
1. `selectBranch(newBranch)` resets `selectedBrand`, `selectedJobTypes`,
   `selectedAppointment`, `notesExtras`.
2. `loadJobTypesForBranch(newBranchId)` is triggered (cache invalidated
   by branch change).
3. User is redirected back to `/home/brands`.

---

## 6. Exception Flows

### 6.1 Exception E1: `GET /branches/{id}/jobtypes` returns 5xx
**Trigger:** Backend or SoftNet outage when entering `/home/services`.
**Flow:**
1. `loadJobTypesForBranch` rxMethod catches the error.
2. Store sets `jobTypesError` (component-level error per REQ-017).
3. Service container shows the standard component-level error block with
   "Nochmals versuchen"-Button (REQ-017 pattern). No top-level fallback
   takes over (REQ-024 only triggers on app-bootstrapping failures).

### 6.2 Exception E2: `BranchResponseDto` missing `address.street`
**Trigger:** Bad master data.
**Flow:**
1. Backend logs warning `Branch <id> has no street address — falling
   back to branch name`.
2. DTO returns `address: { street: branch.name, zip: null, city: null }`.
3. Frontend banner shows `Filiale: <Name> — <Name>` (cosmetically odd
   but no crash).

### 6.3 Exception E3: User selects rent category, then SoftNet master
data changes mid-session
**Trigger:** Reload of rent categories returns a list without the
previously selected ID.
**Flow:**
1. `selectedRentCategoryPrice` returns `null` because the category is no
   longer in `rentCategories()`.
2. Frontend resets `selectedRentCategoryId = null`, re-renders the
   dropdown without selection.
3. User is asked to pick again (no error, just a state reset).

### 6.4 Exception E4: API delivers an unknown `priceUnit`
**Trigger:** SoftNet ships `"week"` instead of expected `"day"`.
**Flow:**
1. Frontend `formatPriceUnit(unit)` falls back to the i18n key
   `booking.notes.rentCategoryPrice.unitDay` if no mapping exists.
2. Console warning is logged in dev builds. Production: silent fallback.

---

## 7. Postconditions

### 7.1 Success
- `BookingStore.jobTypes` cached for current branch.
- `BookingStore.selectedJobTypes` carries API IDs (UUIDs from SoftNet),
  no legacy `'inspection'` / `'tire-change'` literals.
- `notesExtras.callbackOption ∈ { 'no', 'yes' }`.
- Submit payload contains `callbackRequested: true|false`,
  `rentCategoryId: <uuid>|undefined`, JobType operation IDs.
- Branch banner shows full address from API.

### 7.2 Failure
- No partial state — store keeps last good `jobTypes` snapshot until
  reload succeeds.
- Component-level error block visible, retry button restores normal flow.
- No legacy `'none'` value can sneak into the booking submission.

---

## 8. Business Rules

- **BR-1:** All service master data (name, description, hint, sub-
  options) is owned by SoftNet. Frontend renders raw API strings, no
  i18n lookup table for service names. Translations live in SoftNet.
- **BR-2:** Default callback selection is **"Nein"** — there is no
  "no choice" state any more (UX decision).
- **BR-3:** Variant detection (`tireChange`) uses the API category
  marker. If SoftNet renames the category, only the constant
  `TIRE_CHANGE_CATEGORY` (defined once) needs to be updated.
- **BR-4:** German postal codes are always 5 digits (BR validated by
  SoftNet, frontend just renders).
- **BR-5:** Rent-category price is shown only when
  `price !== null && currency !== null`. Missing currency or unit hides
  the block (no `?` placeholders).
- **BR-6:** Currency is rendered with the user's current UI locale
  (Intl.NumberFormat). DE: `49,00 €`, EN: `€49.00`.
- **BR-7:** When SoftNet `hint` contains HTML, it must be sanitised by
  Angular's default template binding (no `[innerHTML]`). Plain text only.
- **BR-8:** The error-fallback screen MUST NOT show any phone number.
  Phone numbers are branch-specific master data in SoftNet and a single
  hardcoded fallback number is misleading when the user might be
  trying to reach a different branch. The retry button is the only
  primary action on the screen.

---

## 9. Non-Functional Requirements

### Performance
- JobTypes are cached per branch in the store: max 1 HTTP call per
  branch per session.
- Rent categories: 1 call on `/home/notes` mount, cached until branch
  change.
- Branch detail call piggy-backs on existing `loadBranches` /
  `loadBranch` (no extra call).
- All API responses < 500 ms (95th percentile, SoftNet INT system).

### Security
- HTTPS only (proxied through backend).
- API JobType strings are rendered through Angular template binding,
  never via `[innerHTML]` (XSS).
- No PII in JobType / rent-category responses → no extra masking.

### Usability
- Mobile-first; price block wraps on < 48em.
- WCAG 2.1 AA: `aria-describedby` links textarea to hint, price block
  uses `role="status"` and `aria-live="polite"`.
- Browser support: latest 2 versions (Chrome, Firefox, Edge, Safari).

### Maintainability
- Removing `AVAILABLE_SERVICES` removes ~120 lines of static data and
  ~14 i18n keys per language.
- Adding a new service in production = adding it in SoftNet, no FE/BE
  release.

---

## 10. Data Model

```typescript
// === REMOVED (legacy, will be deleted) ===
// export type ServiceType = 'inspection' | 'tuv' | 'brake-fluid'
//   | 'tire-change' | 'actions-checks' | 'repair' | 'bodywork';
// export interface ServiceDisplay { id; titleKey; descriptionKey; svgIcon; options[]; }
// export const AVAILABLE_SERVICES: ServiceDisplay[] = [...]
// export const SERVICE_HINTS: ServiceHint[] = [...]
// export type CallbackOption = 'none' | 'yes';

// === NEW (API-typed) ===

/** A JobType as delivered by the backend (raw SoftNet master data). */
export interface ApiJobType {
  id: string;                     // SoftNet UUID
  name: string;                   // already localised by SoftNet
  description?: string;
  hint?: string;                  // shown on /home/notes — plain text
  category?: string;              // stable marker, e.g. 'tireChange'
  categoryName?: string;          // optional human label
  iconUrl?: string;               // absolute URL or asset path
  options?: ApiJobTypeOption[];   // sub-checkbox options
}

export interface ApiJobTypeOption {
  id: string;                     // SoftNet operation ID
  label: string;                  // already localised
  description?: string;
}

/** A user selection — only IDs, not display strings. */
export interface SelectedJobType {
  jobTypeId: string;
  selectedOptionIds: string[];
}

/** Callback dropdown — 'no' is the default. */
export type CallbackOption = 'no' | 'yes';

export interface NotesExtras {
  appointmentPreference: AppointmentPreference;
  callbackOption: CallbackOption;
}

export const DEFAULT_NOTES_EXTRAS: NotesExtras = {
  appointmentPreference: 'anytime',
  callbackOption: 'no',
};

/** Structured branch address (replaces flat string). */
export interface BranchAddressFull {
  street: string;
  zip: string | null;
  city: string | null;
  country?: string;               // ISO-3166 alpha-2, defaults to 'DE'
}

export interface BranchConfig {
  branchId: string;
  name: string;
  address: BranchAddressFull;
  brands: string[];
}

/** Rent category extended with price metadata. */
export interface RentCategory {
  id: string;
  groupId: string;
  name: string;
  description: string;
  brands: string[];
  branches: string[];
  price?: number;                 // numeric, nullable when SoftNet has no price
  currency?: string;              // ISO-4217, e.g. 'EUR'
  priceUnit?: 'day' | 'hour';     // mapped to i18n key
}

/** Computed from store. Renders the price block. */
export interface SelectedRentCategoryPrice {
  name: string;
  price: number;
  currency: string;
  priceUnit: 'day' | 'hour';
}
```

```java
// === Backend DTO changes ===

public record JobTypeResponseDto(
    String id,
    String name,
    String description,
    String hint,            // NEW — nullable
    String category,        // NEW — nullable, stable marker
    String categoryName,    // NEW — nullable, human label
    String iconUrl,         // NEW — nullable
    List<OperationDto> operations
) {
    public record OperationDto(String id, String name) {}
}

public record BranchResponseDto(
    String id,
    String name,
    BranchAddressDto address,    // NEW — structured (was: String description)
    List<String> brands
) {
    public record BranchAddressDto(String street, String zip, String city, String country) {}
}

public record RentCategoryResponseDto(
    String id,
    String groupId,
    String name,
    String description,
    List<String> brands,
    List<String> branches,
    BigDecimal price,        // NEW — nullable
    String currency,         // NEW — nullable, ISO-4217
    String priceUnit         // NEW — nullable, "day" | "hour"
) {}
```

---

## 11. UI/UX

### Layout — Notes page (`/home/notes`)

```
┌──────────────────────────────────────────────────────────────┐
│  Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung      │
│  Möchten Sie uns noch etwas zu Ihrer Buchung mitteilen?     │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Bitte tragen Sie hier Ihre Nachricht an uns ein.    │   │
│  │                                                      │   │
│  └──────────────────────────────────────────────────────┘   │
│  Bitte tragen Sie hier Ihre Nachricht an uns ein. 0/1000    │
│                                                              │
│  Mietwagen-Kategorie  [ Kein Mietwagen        ▼ ]           │
│  Terminpräferenz      [ Jederzeit             ▼ ]           │
│  Rückruf              [ Nein                  ▼ ]           │
│                                                              │
│  Wichtige Hinweise zu Ihren ausgewählten Services           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ <Hint-Text aus API JobType.hint>                    │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Mietwagen-Kategorie: Kompakt — 49,00 € pro Tag             │
│                                                              │
│  [ Zurück ]                                  [ Weiter ]     │
└──────────────────────────────────────────────────────────────┘
```

### Component Map

| Component | Source | Behaviour change |
|-----------|--------|-------------------|
| `BrandSelectionContainerComponent` | REQ-002 | Banner reads structured address |
| `ServiceSelectionContainerComponent` | REQ-004 | Cards rendered from `BookingStore.jobTypes()` |
| `ServiceCardComponent` | REQ-004 | Receives `ApiJobType` instead of `ServiceDisplay`; renders `iconUrl` w/ fallback |
| `NotesFormComponent` | REQ-005 | Textarea gains placeholder + hint |
| `NotesExtrasFormComponent` | REQ-005 | Callback dropdown: Ja / Nein, default "Nein" |
| `ServiceHintsComponent` | REQ-005 | Reads hints from store, not constants |
| `RentCategoryPriceBlockComponent` | NEW | Renders price block when computed signal is non-null |

### Form Fields

| Field | Type | Required | Validation | i18n key |
|-------|------|----------|------------|----------|
| Notes textarea | textarea | No | maxLength 1000 | `booking.notes.form.note.placeholder` (placeholder) + `…hint` (hint) |
| Mietwagen-Kategorie | select | No | from API list | `booking.notes.rentCategory.label` |
| Rückruf | select | Yes (default "Nein") | `'no' \| 'yes'` | `booking.notes.callback.label` |

---

## 12. API Specification

### 12.1 GET `/api/v1/branches`

**Response 200:**
```json
{
  "data": [
    {
      "id": "duesseldorf-werstener",
      "name": "Düsseldorf-Werstener Str.",
      "address": {
        "street": "Werstener Str. 1",
        "zip": "40591",
        "city": "Düsseldorf",
        "country": "DE"
      },
      "brands": ["Volkswagen", "Audi"]
    }
  ]
}
```

### 12.2 GET `/api/v1/branches/{branchId}/jobtypes`

**Response 200:**
```json
{
  "data": [
    {
      "id": "jt-uuid-001",
      "name": "Inspektion",
      "description": "Hauptinspektion gemäß Herstellervorgabe",
      "hint": "Bitte bringen Sie Ihr Serviceheft mit. Ein Ersatzfahrzeug kann auf Anfrage bereitgestellt werden.",
      "category": "inspection",
      "categoryName": "Inspektion & Wartung",
      "iconUrl": "https://cdn.softnet.example/icons/inspection.svg",
      "operations": [
        { "id": "op-001", "name": "Hauptinspektion" },
        { "id": "op-002", "name": "Ölwechsel" }
      ]
    },
    {
      "id": "jt-uuid-004",
      "name": "Reifenwechsel",
      "description": "Sommer-/Winterreifen-Service",
      "hint": "Für Einlagerung bitte Reifenanzahl + -größe vorab durchgeben.",
      "category": "tireChange",
      "iconUrl": null,
      "operations": [...]
    }
  ]
}
```

### 12.3 GET `/api/v1/branches/{branchId}/rentcategories`

**Response 200:**
```json
{
  "data": [
    {
      "id": "rc-001",
      "groupId": "default",
      "name": "Kompakt",
      "description": "VW Polo o.ä.",
      "brands": ["Volkswagen"],
      "branches": ["duesseldorf-werstener"],
      "price": 49.00,
      "currency": "EUR",
      "priceUnit": "day"
    }
  ]
}
```

**Error (404 / 5xx):** standard error envelope from REQ-017 — handled
component-level by retry button.

---

## 13. Test Cases

### TC-1 (a): Notes textarea placeholder + hint
- **Given:** User on `/home/notes`.
- **When:** Page renders.
- **Then:** Textarea `placeholder` = "Bitte tragen Sie hier Ihre
  Nachricht an uns ein.", `<mat-hint>` text = same string,
  char-counter on right.

### TC-2 (b): Service cards loaded from API
- **Given:** Branch selected, JobTypes endpoint returns 3 entries.
- **When:** User opens `/home/services`.
- **Then:** 3 cards rendered with `name` from API, `iconUrl` rendered
  via `<img>`. No `AVAILABLE_SERVICES` symbol exists in compiled JS.

### TC-3 (b): tireChange variant detection via category
- **Given:** JobTypes API returns one JobType with `category: 'tireChange'`.
- **When:** User toggles that card on.
- **Then:** `BookingStore.selectedVariant() === 'tireChange'`. No
  string comparison against `'tire-change'` in code.

### TC-4 (c): Branch banner full address
- **Given:** Branch `address = { street: 'Werstener Str. 1', zip:
  '40591', city: 'Düsseldorf' }`.
- **When:** `/home/brands` loads.
- **Then:** Banner reads "Filiale: Düsseldorf-Werstener Str. — Werstener
  Str. 1, 40591 Düsseldorf".

### TC-5 (c): Branch banner partial address
- **Given:** `zip = null`, `city = null`.
- **When:** `/home/brands` loads.
- **Then:** Banner reads "Filiale: <Name> — Werstener Str. 1" (no
  trailing comma).

### TC-6 (d): Callback dropdown options + default
- **Given:** First entry to `/home/notes`.
- **When:** Page renders.
- **Then:** Dropdown contains exactly two options "Ja" and "Nein".
  "Nein" is preselected. `notesExtras.callbackOption === 'no'`.

### TC-7 (d): Callback maps to backend boolean
- **Given:** User picks "Ja", submits booking.
- **When:** `submitBooking` POSTs `/api/v1/appointments`.
- **Then:** Payload contains `"callbackRequested": true`.
- **And given:** User picks "Nein". **Then:** payload contains
  `"callbackRequested": false`.

### TC-8 (e): Service hints from API
- **Given:** Selected JobType has `hint = 'Bitte bringen Sie ihr
  Serviceheft mit.'`.
- **When:** User opens `/home/notes`.
- **Then:** A hint card with that exact text is rendered. No i18n
  translation lookup happens.

### TC-9 (e): Empty hint hides card
- **Given:** Selected JobType has `hint = null`.
- **When:** Notes page renders.
- **Then:** No hint card for that JobType. If no JobType has a hint,
  the section heading is also hidden.

### TC-10 (f): Rent-category price block visible
- **Given:** User selects "Kompakt" (price 49.00, currency EUR, unit
  day).
- **When:** Notes page re-renders.
- **Then:** Below service hints, "Mietwagen-Kategorie: Kompakt —
  49,00 € pro Tag" is visible.

### TC-11 (f): Price block hidden when no selection
- **Given:** `selectedRentCategoryId === null`.
- **When:** Notes page renders.
- **Then:** No price block in DOM.

### TC-12 (f): Price block hidden when API has no price
- **Given:** Selected category has `price = null`.
- **When:** Notes page renders.
- **Then:** No price block, no error.

### TC-13: JobTypes API failure → component-level error
- **Given:** `GET /branches/{id}/jobtypes` returns 500.
- **When:** User navigates to `/home/services`.
- **Then:** Component-level error block visible (REQ-017 pattern), retry
  button calls `loadJobTypesForBranch` again.

### TC-14: Branch switch invalidates JobTypes cache
- **Given:** User has loaded JobTypes for branch A.
- **When:** User picks branch B in the switcher.
- **Then:** `loadJobTypesForBranch('B')` is called, store replaces
  `jobTypes` array, `selectedJobTypes` reset to `[]`.

### TC-15: Booking submit carries API IDs
- **Given:** User selected JobType with API id `jt-uuid-004`,
  operation `op-005`, rent category `rc-001`, callback "Ja".
- **When:** Submit.
- **Then:** Backend receives `jobTypeIds: ['jt-uuid-004']`,
  `operationIds: ['op-005']`, `rentCategoryId: 'rc-001'`,
  `callbackRequested: true`.

### TC-16: A11y — textarea aria-describedby
- **Given:** Notes form rendered.
- **When:** Inspect DOM.
- **Then:** Textarea has `aria-describedby="notes-hint notes-counter"`,
  hint span has `id="notes-hint"`.

### TC-17 (g): Error-Fallback has no phone block
- **Given:** Critical error triggered (e.g. backend 500 on bootstrap).
- **When:** User is auto-navigated to `/app-error`.
- **Then:** `<aside class="error-fallback__phone">` does NOT exist in
  the DOM, no `tel:` link is rendered, no element with class
  `error-fallback__phone-link` exists. The retry button is the only
  primary action visible.

### TC-18 (g): Removed phone i18n keys not used anywhere
- **Given:** Codebase after this REQ.
- **When:** Searching for `app.error.phoneIntro` or
  `app.error.phoneAriaLabel`.
- **Then:** Zero matches in `src/`, zero matches in `playwright/`.
  Translations object no longer carries those keys in any of the 5
  locales.

---

## 14. Implementation

### 14.1 Backend (Spring Boot, Java 21)

**`server/src/main/java/com/autohaus/backend/jobtypes/JobTypesService.java`**
- Extend mapping in `getJobTypesByBranch`: read `hint`, `category`,
  `categoryName`, `iconUrl` from SoftNet `JsonNode`.
- Replace name-based tire-change detection (`TIRE_CHANGE_NAME_FRAGMENT`)
  with a category-based marker emitted to the FE.
- Update `JobTypeResponseDto` accordingly.

**`server/src/main/java/com/autohaus/backend/branches/BranchesService.java`**
- Replace flat `address: String` with structured `BranchAddressDto`.
- Source order: SoftNet `/users/{id}` (when scope allows) → fallback to
  `setup.json` extension `addressFull: { street, zip, city, country }`.
- Update `BranchResponseDto` accordingly. Handle null fields gracefully.

**`server/src/main/java/com/autohaus/backend/rentcategories/RentCategoriesService.java`**
- Map SoftNet `price`, `currency`, `priceUnit` (or equivalents) onto
  `RentCategoryResponseDto`. Pass through nulls when missing.

**`server/src/main/java/com/autohaus/backend/appointments/AppointmentsService.java`**
- Existing logic already maps `Boolean.TRUE.equals(dto.callbackRequested())`
  — fine. New: ensure `callbackRequested` is set explicitly in the FE
  payload (true|false), never null.

**`server/src/main/resources/setup.json`**
- Extend each branch entry with `addressFull: { street, zip, city,
  country }` if SoftNet does not yet supply it. (Documented as fallback
  source until SoftNet master data catches up.)

### 14.2 Frontend (Angular 21)

**Files removed / drastically simplified:**
- `src/app/features/booking/models/service.model.ts` — keep only
  `ApiJobType`, `ApiJobTypeOption`, `SelectedJobType` types.
- `src/app/features/booking/components/notes/notes-hints.constants.ts`
  — DELETED.

**Files updated:**
- `src/app/features/booking/models/notes-extras.model.ts` —
  `CallbackOption = 'no' | 'yes'`, default `'no'`.
- `src/app/features/booking/models/branch-config.model.ts` — `address`
  becomes `BranchAddressFull` object with nullable `zip` / `city`.
- `src/app/features/booking/models/rent-category.model.ts` — add
  `price?`, `currency?`, `priceUnit?`.
- `src/app/features/booking/services/rent-category-api.service.ts` —
  forward new fields from DTO.
- `src/app/features/booking/services/branch-api.service.ts` (or
  equivalent) — parse structured `address`.
- `src/app/features/booking/services/job-type-api.service.ts` (NEW or
  rename from existing) — `getJobTypesForBranch(branchId)`.
- `src/app/features/booking/stores/booking.store.ts`:
  - state: `jobTypes: ApiJobType[]`, `selectedJobTypes: SelectedJobType[]`
    (replaces `services` / `selectedServices`).
  - rxMethod: `loadJobTypesForBranch`.
  - computed: `selectedVariant` checks `category === 'tireChange'`;
    `selectedRentCategoryPrice` returns
    `SelectedRentCategoryPrice | null`; `callbackRequested` returns
    boolean (`option === 'yes'`).
- `src/app/features/booking/components/notes/notes-form.component.html`
  — add `placeholder` + `<mat-hint align="start">` with new key.
- `src/app/features/booking/components/notes/notes-extras-form.component.{ts,html}`
  — drop `'none'` option, default `'no'`, rename label key.
- `src/app/features/booking/components/notes/service-hints.component.{ts,html}`
  — input `selectedJobTypes`, look up hints in store, render raw
  `hint` strings. Hide section when no hint available.
- `src/app/features/booking/components/notes/rent-category-price-block.component.{ts,html,scss}`
  — NEW presentational; renders only when `selectedRentCategoryPrice`
  computed is non-null.
- `src/app/features/booking/components/brand-selection/brand-selection-container.component.{ts,html}`
  — `formattedBranchAddress()` builds string from structured object
  with null-safe fallbacks.
- `src/app/features/booking/components/services/service-card.component.{ts,html}`
  — accept `ApiJobType`, render `iconUrl` (or Material fallback icon).
- `src/app/features/error-fallback/error-fallback-container.component.html`
  — remove the entire `<aside class="error-fallback__phone">` element
  (lines 38–47).
- `src/app/features/error-fallback/error-fallback-container.component.ts`
  — remove `phoneNumber` and `phoneTelHref` computeds; remove
  `BookingStore` injection if no longer needed for this component.
- `src/app/features/error-fallback/error-fallback-container.component.scss`
  — remove `&__phone`, `&__phone-icon`, `&__phone-text`, `&__phone-link`
  blocks.
- `src/app/features/error-fallback/error-fallback-container.component.spec.ts`
  — drop the test "should fall back to default phone when no branch
  selected"; ensure remaining specs still pass.
- `playwright/REQ-024-error-fallback.spec.ts` — remove `AC-10` spec
  (telephone link) and any phone-related assertions; renumber if
  necessary.
- `src/app/core/i18n/translations.ts` — see Section 17.

**Mapping helper (FE):**
```typescript
export function mapCallbackOptionToBoolean(opt: CallbackOption): boolean {
  return opt === 'yes';
}
```

### 14.3 Effort Estimate
- Backend (DTO + services + tests): ~6 h
- Frontend (store + 3 components + delete legacy + i18n): ~10 h
- Frontend (g) — phone-block removal in error-fallback + spec cleanup: ~0.5 h
- Tests (Jest + Spring Boot): ~6 h
- **Total: ~22.5 h**

---

## 15. Dependencies

**Requires:**
- REQ-021-Multi-Branch-Setup (`setup.json`, `BranchesService`)
- REQ-017-Loading-Spinner — points (i)+(j) provide rent-category
  loading + component-level error pattern.
- REQ-005-Hinweisfenster — base notes page layout, where placeholder /
  hint / extras live.
- REQ-024-Error-Fallback-Screen — top-level fallback when bootstrapping
  fails; this REQ uses component-level errors instead.

**Blocks:**
- (none — pure cleanup; future REQs benefit from the simpler model)

---

## 16. Naming Glossary

### Container / Component Methods
- `onSelectJobType(jobTypeId)` — toggle JobType selection
- `onConfirmJobTypeOptions(jobTypeId, optionIds[])` — confirm sub-options
- `onRentCategoryChange(id)` — select rent category
- `onCallbackOptionChange(option)` — `'no' | 'yes'`
- `formattedBranchAddress()` — null-safe address string builder

### API Services
- `JobTypeApiService.getJobTypesForBranch(branchId)`
- `BranchApiService.getBranches()` / `getBranch(id)` — returns
  structured address
- `RentCategoryApiService.getRentCategories(branchId)` — returns price
  fields

### Signal Store (`BookingStore`)
- `loadJobTypesForBranch(branchId)` — rxMethod, populates `jobTypes`
- `setBrand(brand)` — preserves JobTypes cache (only branch change
  invalidates)
- `toggleJobType(jobTypeId)` — replaces `toggleService`
- `confirmJobTypeOptions(jobTypeId, optionIds[])`
- `setNotesExtras(extras)` — extras with `callbackOption: 'no'|'yes'`
- `setSelectedRentCategoryId(id | null)`

### Computed Signals
- `selectedVariant` — `'default' | 'tireChange'`, derived from
  `category` field
- `selectedJobTypeHints` — `ApiJobType[]` filtered by `selectedJobTypes`
  with non-empty `hint`
- `selectedRentCategoryPrice` — `SelectedRentCategoryPrice | null`
- `callbackRequested` — `boolean`, `option === 'yes'`
- `formattedBranchAddress` — `string`

### Helpers
- `mapCallbackOptionToBoolean(option)` — `'yes' → true, 'no' → false`
- `formatRentCategoryPrice(price, currency, priceUnit, locale)` —
  `Intl.NumberFormat` wrapper
- `TIRE_CHANGE_CATEGORY = 'tireChange'` — single source of truth for
  variant detection

### Backend (Java)
- `BranchesService.toAddressDto(BranchSetup)` — null-safe mapping
- `JobTypesService.toJobTypeDto(JsonNode)` — extracts hint / category /
  icon
- `RentCategoriesService.toRentCategoryDto(JsonNode)` — extracts price /
  currency / priceUnit

### Models / Types
- `ApiJobType`, `ApiJobTypeOption`, `SelectedJobType`
- `BranchAddressFull`, `BranchConfig`
- `RentCategory`, `SelectedRentCategoryPrice`
- `CallbackOption = 'no' | 'yes'`

---

## 17. i18n Keys

Code language is **English** (variable / type names). UI is rendered in
DE + EN + UK + FR + AR. Service-specific texts (JobType name,
description, hint) are **master data in SoftNet** and not stored in
`translations.ts`.

### 17.1 Removed keys (all 5 languages)

```
booking.notes.callback.none           // "Keine Auswahl" / "No selection" / …
booking.notes.hints.inspection        // covered by API JobType.hint
booking.notes.hints.tuv
booking.notes.hints.brakeFluid
booking.notes.hints.tireChange
booking.notes.hints.actionsChecks
booking.notes.hints.repair
booking.notes.hints.bodywork
booking.services.inspection.title     // covered by API JobType.name
booking.services.inspection.description
booking.services.inspection.options.* (all sub-keys)
booking.services.tuv.*
booking.services.brakeFluid.*
booking.services.tireChange.*
booking.services.actionsChecks.*
booking.services.repair.*
booking.services.bodywork.*
app.error.phoneIntro                  // "Sie können auch direkt anrufen:" / "You can also call us directly:" / …
app.error.phoneAriaLabel              // "Autohaus anrufen unter" / "Call dealership at" / …
```

> **Note (g):** `app.error.phoneIntro` and `app.error.phoneAriaLabel` are
> removed in all 5 languages (DE / EN / UK / FR / AR). The
> error-fallback screen no longer renders a phone block — see Section 1.1
> point (g) and BR-8.

### 17.2 New / updated keys

#### DE (`de`)
```
booking.notes.form.note.placeholder = "Bitte tragen Sie hier Ihre Nachricht an uns ein."
booking.notes.form.note.hint        = "Bitte tragen Sie hier Ihre Nachricht an uns ein."
booking.notes.callback.no           = "Nein"
booking.notes.callback.yes          = "Ja"
booking.notes.rentCategoryPrice.label  = "Mietwagen-Kategorie"
booking.notes.rentCategoryPrice.unitDay  = "pro Tag"
booking.notes.rentCategoryPrice.unitHour = "pro Stunde"
booking.notes.rentCategoryPrice.srLabel  = "Mietwagen-Kategorie ausgewählt"
booking.branch.addressSeparator     = ", "        // optional helper
```

#### EN (`en`)
```
booking.notes.form.note.placeholder = "Please enter your message to us here."
booking.notes.form.note.hint        = "Please enter your message to us here."
booking.notes.callback.no           = "No"
booking.notes.callback.yes          = "Yes"
booking.notes.rentCategoryPrice.label    = "Rental car category"
booking.notes.rentCategoryPrice.unitDay  = "per day"
booking.notes.rentCategoryPrice.unitHour = "per hour"
booking.notes.rentCategoryPrice.srLabel  = "Rental car category selected"
```

#### UK (`uk`) — to be kept consistent
```
booking.notes.form.note.placeholder = "Будь ласка, введіть тут ваше повідомлення."
booking.notes.form.note.hint        = "Будь ласка, введіть тут ваше повідомлення."
booking.notes.callback.no           = "Ні"
booking.notes.callback.yes          = "Так"
booking.notes.rentCategoryPrice.label    = "Категорія орендованого авто"
booking.notes.rentCategoryPrice.unitDay  = "на день"
booking.notes.rentCategoryPrice.unitHour = "за годину"
booking.notes.rentCategoryPrice.srLabel  = "Категорію орендованого авто обрано"
```

#### FR (`fr`)
```
booking.notes.form.note.placeholder = "Veuillez saisir votre message ici."
booking.notes.form.note.hint        = "Veuillez saisir votre message ici."
booking.notes.callback.no           = "Non"
booking.notes.callback.yes          = "Oui"
booking.notes.rentCategoryPrice.label    = "Catégorie de voiture de location"
booking.notes.rentCategoryPrice.unitDay  = "par jour"
booking.notes.rentCategoryPrice.unitHour = "par heure"
booking.notes.rentCategoryPrice.srLabel  = "Catégorie de voiture de location sélectionnée"
```

#### AR (`ar`)
```
booking.notes.form.note.placeholder = "يرجى إدخال رسالتك إلينا هنا."
booking.notes.form.note.hint        = "يرجى إدخال رسالتك إلينا هنا."
booking.notes.callback.no           = "لا"
booking.notes.callback.yes          = "نعم"
booking.notes.rentCategoryPrice.label    = "فئة سيارة الإيجار"
booking.notes.rentCategoryPrice.unitDay  = "في اليوم"
booking.notes.rentCategoryPrice.unitHour = "في الساعة"
booking.notes.rentCategoryPrice.srLabel  = "تم اختيار فئة سيارة الإيجار"
```

### 17.3 Important notes on i18n

- `service.titleKey` / `service.descriptionKey` / per-service hint keys
  no longer exist after this REQ. Service master data lives in SoftNet
  and is rendered as-is. SoftNet is responsible for delivering the
  user-language strings (currently DE, master language is German).
- For UK / FR / AR users, JobType labels remain in DE until SoftNet
  exposes localised master data (out of scope for this REQ; documented
  as a follow-up). The container UI (titles, buttons, hints) stays
  fully translated.
- Currency is rendered with `Intl.NumberFormat(locale, { style:
  'currency', currency })` — no extra i18n key required.

---

## 18. Implementation Notes

**Bilingual code reminder:** comments in DE + EN, error messages in
English, JSDoc bilingual. See `.claude/skills/bilingual-code.md`.

**Migration order (suggested):**

1. Backend DTO extensions + services (additive, nullable) → ship.
2. Frontend models + store (consume new fields, keep legacy `'none'`
   temporarily as alias) → ship.
3. UI changes (placeholder, default `'no'`, price block, dynamic
   service cards) + delete legacy constants → ship.
4. Remove backend feature toggles for tire-change name-fragment
   detection.

This way no big-bang release is needed and the booking flow keeps
working at every step.
