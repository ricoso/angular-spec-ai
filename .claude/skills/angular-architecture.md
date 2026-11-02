# Angular Architecture (COMPACT)

## Projekt-Vorgabe
- Typ: Production (Spring Boot Backend + SoftNet API)
- API Services: HTTP calls to Spring Boot Backend (:3000, /api/v1/*)
- Auth: Session Token (HMAC-SHA256)

**VORHER LESEN:** code-language.md, performance.md

---

## Pattern: Container/Presentational

**Regel:** 1 Route = 1 Container + N Presentational Children

---

## Layer Responsibilities

| Layer | Inject | Aufgabe |
|-------|--------|---------|
| **Container** | Store, Business Service | Event Handler, Combine Stores |
| **Presentational** | - | input/output, Display only |
| **API Service** | HttpClient | HTTP calls, return Promise<T>, DTO-Mapping ohne Platzhalter (siehe unten) |
| **Business Service** | API Service | Validation, Logik |
| **Feature Store** | API Service | Global Business Data |
| **Component Store** | - | Local UI State |

---

## Feature Store (Global Business Data)

```typescript
// user.store.ts
export const UserStore = signalStore(
  { providedIn: 'root' },  // Global Singleton

  withState<UserState>({ users: [], isLoading: false, error: null }),

  withComputed(({ users }) => ({
    hasUsers: computed(() => users().length > 0),
    userCount: computed(() => users().length)
  })),

  withMethods((store, userApi = inject(UserApiService)) => ({
    async loadUsers(): Promise<void> {
      patchState(store, { isLoading: true });
      try {
        const users = await userApi.getAll();
        patchState(store, { users, isLoading: false });
      } catch (error: unknown) {
        patchState(store, { error: 'Failed to load', isLoading: false });
      }
    },
    async addUser(user: User): Promise<void> {
      await userApi.create(user);
      patchState(store, { users: [...store.users(), user] });
    }
  }))
);
```

**Wann Feature Store:** Business Data (Users, Products, Orders), API Data, Shared across routes, Auth state

---

## Component Store (Local UI State)

```typescript
// product-list-ui.store.ts
export const ProductListUiStore = signalStore(
  // NO providedIn - local to component!

  withState({
    selectedTab: 'all' as 'all' | 'featured',
    sortBy: 'name' as 'name' | 'price',
    viewMode: 'grid' as 'grid' | 'list',
    currentPage: 1
  }),

  withMethods((store) => ({
    selectTab(tab: 'all' | 'featured'): void {
      patchState(store, { selectedTab: tab, currentPage: 1 });
    },
    setSortBy(sortBy: 'name' | 'price'): void {
      patchState(store, { sortBy });
    }
  }))
);
```

**Wann Component Store:** UI State (tabs, filters, sort, view mode), Component-specific, Destroyed with component

---

## Container: Feature + Component Store

```typescript
@Component({
  providers: [ProductListUiStore],  // Local!
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProductListContainerComponent {
  protected productStore = inject(ProductStore);
  protected uiStore = inject(ProductListUiStore);

  protected readonly filteredProducts = computed(() => {
    const tab = this.uiStore.selectedTab();
    return tab === 'featured'
      ? this.productStore.products().filter(p => p.featured)
      : this.productStore.products();
  });

  // NO ngOnInit for data loading - Resolver does this!
}
```

---

## Router Resolver (Data Loading)

```typescript
export const usersResolver: ResolveFn<void> = () => {
  const store = inject(UserStore);
  store.loadUsers();
  return;  // Return void!
};

// user.routes.ts
{ path: '', component: UserContainerComponent, resolve: { _: usersResolver } }
```

---

## Store onInit - NUR für globale Daten!

```typescript
// OK: Global Config
export const ConfigStore = signalStore(
  { providedIn: 'root' },
  withHooks({ onInit(store) { store.loadConfig(); } })  // OK
);

// FALSCH: Feature Data
export const UserStore = signalStore(
  withHooks({ onInit(store) { store.loadUsers(); } })  // FALSCH!
);
```

**onInit erlaubt für:** App-Config, Auth Session, Feature Flags
**onInit VERBOTEN für:** Users, Products, Orders (→ Resolver!)

---

## File Structure

```
feature/
├── containers/name-container/
│   ├── name-container.component.ts
│   └── name-container.component.html
├── components/
│   └── name-list/
│       ├── name-list.component.ts
│       └── name-list.store.ts      # Component Store
├── services/
│   ├── name-api.service.ts         # HTTP only
│   └── name-business.service.ts    # Logic
├── stores/
│   └── name.store.ts               # Feature Store
├── resolvers/
│   └── name.resolver.ts
└── name.routes.ts
```

---

## Rules

| Rule | Feature Store | Component Store |
|------|---------------|-----------------|
| providedIn | 'root' | Component providers |
| Scope | Global | Local |
| Data | Business | UI State |
| withHooks | Only global data | Never |
| Lifecycle | App lifetime | Component lifetime |

**MANDATORY:**
- OnPush everywhere
- Separate HTML + SCSS files
- `inject()` NUR als Class Property (Anfang der Klasse), NIEMALS in Methoden
- NO Store in Presentational
- NO HTTP in Store
- NO business logic in Container
- TrackBy in @for loops
- **NUR computed() / signal() im Template** — KEINE Methoden-Aufrufe!
  - Erlaubt: `{{ mySignal() }}`, `{{ myComputed() }}`, `(click)="onSave()"`
  - VERBOTEN: `{{ hasError('x') }}`, `{{ getLabel() }}`, `{{ calculate() }}`
  - Auch bei Forms: `hasError()` → `errors` computed Signal (siehe forms.md)

---

## API-Payloads: Unbekanntes weglassen (PFLICHT)

Leere oder unbekannte optionale Angaben (VIN/FIN, Erstzulassung, …) gehen NICHT mit Platzhalter, leerem String oder erfundenem Default an das Backend, sondern bleiben `undefined` (der Key fehlt im JSON). Das gilt auch für `model`: Das Frontend schickt es nur, wenn es bekannt ist. Den Rückfallwert für das SoftNet-Pflichtfeld `model` (`"Unknown"`) setzt allein das Backend, weil SoftNet Buchungen ohne Modell an manchen Standorten ablehnt (siehe `spring-backend.md`, Abschnitt „SoftNet-Payloads“, und REQ-045 5.3).

```typescript
// ✅ GOOD — empty = omitted
const vin = raw.vin?.trim().toUpperCase();
const vehicle = { licensePlate: raw.licensePlate.trim(), mileage: raw.mileage, ...(vin ? { vin } : {}) };

// ❌ BAD — placeholder / invented default
const vehicle = { licensePlate: raw.licensePlate, mileage: raw.mileage, vin: raw.vin || 'UNKNOWN', firstRegYear: '2020' };
```

---

## Unit Test Pattern (Mocked Template)

**Regel:** UI wird via Playwright E2E getestet. Unit Tests prüfen NUR Logik (Inputs, Outputs, Event Handler, Computed).

**Template mocken** mit `overrideComponent` → eliminiert Angular Material / Child Component Abhängigkeiten:

```typescript
describe('MyContainerComponent', () => {
  let component: MyContainerComponent;
  let fixture: ComponentFixture<MyContainerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyContainerComponent],
      providers: [/* Stores, mocked Services, provideRouter([]) etc. */]
    })
      .overrideComponent(MyContainerComponent, {
        set: { template: '<div class="mocked">Mocked Component</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(MyContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });
});
```

### Protected Member Access + Input/Output Testing

```typescript
// Protected access via typed cast
const exposed = component as unknown as { onSave: (data: MyData) => void };
exposed.onSave(testData);

// Inputs via setInput (NOT direct assignment)
fixture.componentRef.setInput('selectedItem', 'abc');
fixture.detectChanges();

// Outputs via subscribe + exposed handler
const spy = jest.fn();
component.itemSelected.subscribe(spy);
const exp = component as unknown as { onClick: (item: Item) => void };
exp.onClick(testItem);
expect(spy).toHaveBeenCalledWith(testItem.id);
```
