# Check Architecture Command

Verifies that a feature follows architecture patterns and best practices.

## Usage

```
<feature-name>
```

Example: `product`

## Checks Performed

### 1. Container/Presentational Pattern

✅ **Container Components:**
- Located in `containers/` directory
- Have OnPush Change Detection
- Inject Store and/or Services
- Have event handler methods (`on[Action]()`)
- NO template method calls (only event handlers)

✅ **Presentational Components:**
- Located in `components/` directory
- Have OnPush Change Detection
- Use `input()` / `input.required()` only
- Use `output()` only
- NO Store injection
- NO Service injection
- NO Business logic
- **Template: NUR computed() / signal() Reads** — KEINE Methoden-Aufrufe!
  - `hasError()`, `getErrorMessage()`, `getLabel()` etc. sind VERBOTEN
  - Stattdessen: `errors` computed Signal (Form-Fehler), `labels` computed Signal etc.
  - Einzige Ausnahme: Event Handler in `(event)="onAction()"` Bindings

### 2. Service Layers

✅ **API Service:**
- Only HTTP calls (`getAll`, `getById`, `create`, `update`, `delete`)
- NO Business logic
- NO Validation (except DTO transformation)
- Uses async/await pattern
- Proper error handling

✅ **Business Service:**
- Business rules and validation
- Uses API Service for data
- NO direct HTTP calls
- Methods named: `validateAnd...()`, `confirm...()`, etc.

### 3. Signal Store

✅ **Store Pattern:**
- Uses `@ngrx/signals`
- State interface defined
- Computed signals for derived state
- Methods use Services (not direct HTTP)
- Methods named: `load...()`, `add...()`, `update...()`, etc.

### 4. Template Performance

✅ **Performance Rules:**
- All `@for` loops have `track` (NOT `$index`)
- Track uses unique ID (`track item.id`)
- **NUR computed() / signal() im Template** — KEINE Methoden-Aufrufe!
- NO calculations in templates (`{{ price * qty }}`)
- Use computed signals instead

✅ **Template Syntax:**
```html
<!-- ✅ GOOD — Signal/Computed reads -->
@for (user of users(); track user.id) { }
<div>{{ activeUsers().length }}</div>
@if (errors().email.required) { <mat-error>...</mat-error> }

<!-- ❌ BAD — Method calls -->
@for (user of users(); track $index) { }
<div>{{ getActiveUsers().length }}</div>
@if (hasError('email', 'required')) { <mat-error>...</mat-error> }
```

✅ **Form Error Pattern:**
```typescript
// ❌ VERBOTEN: Methode im Template
protected hasError(field: string, error: string): boolean { ... }

// ✅ PFLICHT: Computed Signal mit form.events
protected readonly errors = computed(() => {
  this.formEvents(); // toSignal(toObservable(form).pipe(switchMap(f => f.events)))
  return { email: { required: this.checkError(form, 'email', 'required') } };
});
```

### 5. File Structure

✅ **Separation:**
- Separate HTML files (no inline templates)
- Separate SCSS files (no inline styles)
- Test files (.spec.ts) for all components/services
- Models in separate files

### 6. Naming Conventions

✅ **Methods (from Naming Glossary):**
- Container: `on[Action]()` - `onCreate()`, `onDelete(id)`
- API Service: `getAll()`, `getById()`, `create()`, `update()`, `delete()`
- Business: `validateAndCreate()`, `confirmDelete()`, `calculate...()`
- Store: `loadUsers()`, `addUser()`, `updateUser()`, `removeUser()`

✅ **Computed Signals:**
- `filteredItems`, `sortedItems`, `selectedItem`
- `itemCount`, `hasItems`, `isLoading`, `canDelete`

### 7. TypeScript

✅ **Strict Mode:**
- No `any` types
- Proper interface definitions
- DTO types for API requests/responses

### 8. Dead Code (Ungenutzte Methoden / Properties)

✅ **Keine ungenutzten Methoden:**
- Jede `protected`/`public` Methode in Components muss im Template ODER in Tests genutzt werden
- Jede `private` Methode muss intern aufgerufen werden
- Durch Refactoring überflüssig gewordene Methoden MÜSSEN gelöscht werden
- Kein auskommentierter Code, kein `// deprecated`, kein `// unused`

```typescript
// ❌ BAD — isInvalid() durch errors computed ersetzt, aber nicht gelöscht
export class UserFormComponent {
  protected readonly errors = computed(() => { ... });
  protected isInvalid(): boolean { ... }  // DEAD CODE → LÖSCHEN!
}

// ✅ GOOD — Nur was gebraucht wird
export class UserFormComponent {
  protected readonly errors = computed(() => { ... });
}
```

### 9. API-Payloads ohne Platzhalter

✅ **Unbekannte optionale Angaben werden weggelassen** (Regel aus CLAUDE.md, Details in `angular-architecture.md` und `spring-backend.md`):
- Frontend (API Service, Store-Mapping): leer/unbekannt → `undefined`, KEIN `'UNKNOWN'`, `'N/A'`, `''`, `0` oder erfundener Default
- Backend (Payload-Bau für SoftNet, z. B. `AppointmentsService`, `CarsService`): Key wird nicht gesetzt statt Platzhalter; eingehende Platzhalter werden entfernt (`SoftnetPayload.putIfKnown`)
- Test vorhanden: unbekannte Angabe → Key fehlt im Payload

```bash
# Frontend
grep -rnE "['\"](UNKNOWN|Unknown|N/A|n/a|TBD)['\"]" src/app/features --include='*.ts' | grep -v '\.spec\.ts'
# Backend (SoftNet-Payloads)
grep -rnE '"(UNKNOWN|Unknown|N/A|n/a|TBD)"' server/src/main/java/com/autohaus/backend/appointments server/src/main/java/com/autohaus/backend/cars server/src/main/java/com/autohaus/backend/customers server/src/main/java/com/autohaus/backend/snaps --include='*.java'
```

❌ Jeder Treffer ist ein Issue. Ausnahmen: Werte ohne Bezug zu Payload-Daten (z. B. Fehlercodes wie `CriticalErrorCode`, Statistik-Enums) und `AppointmentsService.MODEL_UNKNOWN` (`"Unknown"`): `model` ist bei SoftNet standortabhängig Pflicht und darf nie fehlen (Nutzerentscheid 02.11.2026, REQ-045 5.3). Umgekehrt ist ein weggelassenes `model` ein Issue.

## Output Format

```
🔍 Checking architecture for: product

✅ Container/Presentational Pattern
   ✅ product-container: OnPush, Store injection
   ✅ product-list: No Store, Input/Output only
   ✅ product-card: No Store, Input/Output only

✅ Service Layers
   ✅ product-api.service: HTTP only, no business logic
   ✅ product-business.service: Uses API service

✅ Signal Store
   ✅ Product.store: Uses services, no direct HTTP
   ✅ Computed signals: filteredProducts, selectedProduct

⚠️  Template Performance
   ❌ product-list.component.html:12
      - Missing track in @for loop
      - Fix: Add `track product.id`
   
   ❌ product-container.component.html:8
      - Method call in template: {{ getActiveProducts() }}
      - Fix: Create computed signal

✅ File Structure
   ✅ All components have separate HTML/SCSS
   ✅ All files have .spec.ts tests

✅ Naming Conventions
   ✅ All methods follow naming glossary
   ✅ Computed signals properly named

⚠️  TypeScript
   ❌ product.model.ts:15
      - Using `any` type
      - Fix: Define proper interface

✅ API-Payloads
   ✅ Keine Platzhalter (UNKNOWN/N/A/''), unbekannte Angaben werden weggelassen

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 Score: 85/100

❌ 3 issues found
⚠️  Recommendations:
   1. Add trackBy to @for loops
   2. Replace method calls with computed signals
   3. Remove `any` types
```

## Auto-Fix

Some issues can be auto-fixed:

```
/fix-performance product
```

This will:
- Add trackBy to @for loops
- Convert methods to computed signals
- Add OnPush if missing

## Manual Fixes Required

- Business logic placement
- Service layer separation
- Type definitions

## Best Practices

Run this check:
- ✅ After creating new feature
- ✅ Before committing code
- ✅ During code review
- ✅ After refactoring

## Integration

Add to git hooks:

```json
// package.json
{
  "scripts": {
    "check:arch": "claude /check-architecture",
    "pre-commit": "npm run check:arch"
  }
}
```
