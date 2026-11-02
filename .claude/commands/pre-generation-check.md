# Pre-Generation Check Command

**Command:** `/pre-generation-check`

**Description:** Validiert generierte Code-Dateien BEVOR sie committed werden.

---

## Wann ausführen?

- ✅ Nach `/implement-requirement`
- ✅ Nach manueller Code-Generierung
- ✅ Vor jedem Commit

---

## Checks

### 1. ❌ Keine Inline Templates

```bash
# Suche nach inline templates
grep -r "template:\s*\`" src/app/features/ --include="*.ts"
grep -r "template:\s*'" src/app/features/ --include="*.ts"
```

**Fix:** Separate `.html` Datei erstellen, `templateUrl` verwenden.

### 2. ❌ Keine Inline Styles

```bash
# Suche nach inline styles
grep -r "styles:\s*\[" src/app/features/ --include="*.ts"
```

**Fix:** Separate `.scss` Datei erstellen, `styleUrls` verwenden.

### 3. ❌ Kein onInit für Feature-Daten (Store)

```bash
# Suche nach onInit in Feature Stores
grep -rn "onInit" src/app/features/**/*.store.ts
```

**Erlaubt NUR in:**
- `src/app/core/stores/` (App-Config, Auth, Feature Flags)

**Fix:** Route Resolver verwenden.

### 4. ❌ Kein ngOnInit für Data Loading (Component)

```bash
# Suche nach ngOnInit mit Store/Service-Aufrufen
grep -rn "ngOnInit" src/app/features/**/*.component.ts -A 5 | grep -E "(load|fetch|get|init)"
```

**Patterns die VERBOTEN sind:**
```typescript
// ❌ VERBOTEN
ngOnInit(): void {
  this.store.loadUsers();      // ❌ Store-Methode
  this.service.fetchData();    // ❌ Service-Methode
  this.loadInitialData();      // ❌ Data Loading Methode
}
```

**Erlaubt in ngOnInit:**
```typescript
// ✅ ERLAUBT
ngOnInit(): void {
  this.setupEventListener();   // ✅ Event Listener
  this.initializeTimer();      // ✅ Timer
  // Route params werden via input() gelesen, nicht ngOnInit
}
```

**Fix:** Route Resolver verwenden:
```typescript
// resolver.ts
export const featureResolver: ResolveFn<void> = () => {
  inject(FeatureStore).loadData();
  return of(void 0);
};

// routes.ts
{ path: 'feature', component: FeatureComponent, resolve: { _: featureResolver } }
```

### 5. ❌ Kein track $index

```bash
# Suche nach track $index
grep -rn "track \$index" src/app/features/ --include="*.html"
```

**Fix:** `track item.id` verwenden (unique identifier).

### 6. ❌ Keine Methoden im Template

```bash
# Suche nach method() calls (außer Event Handler)
grep -rn "{{ .*() }}" src/app/features/ --include="*.html" | grep -v "()"
```

**Fix:** `computed()` Signal erstellen.

### 7. ✅ Resolver vorhanden

```bash
# Prüfe ob Resolver existiert für jedes Feature
ls src/app/features/*/resolvers/*.resolver.ts
```

**Fix:** Resolver erstellen für Data Loading.

### 8. ❌ Keine Platzhalterwerte für unbekannte Daten (API-/SoftNet-Payloads)

```bash
# Frontend
grep -rnE "['\"](UNKNOWN|Unknown|N/A|n/a|TBD)['\"]" src/app/features --include="*.ts" | grep -v "\.spec\.ts"
# Backend (SoftNet-Payloads)
grep -rnE '"(UNKNOWN|Unknown|N/A|n/a|TBD)"' server/src/main/java/com/autohaus/backend/appointments server/src/main/java/com/autohaus/backend/cars server/src/main/java/com/autohaus/backend/customers server/src/main/java/com/autohaus/backend/snaps --include="*.java"
```

**Fix:** Feld weglassen (Frontend: `undefined`, Backend: Key nicht setzen bzw. `SoftnetPayload.putIfKnown`). Regel aus CLAUDE.md: SoftNet-Mitarbeiter beschweren sich über Platzhalter wie `UNKNOWN`. **Ausnahme:** Das SoftNet-Pflichtfeld `model` nie weglassen, `AppointmentsService.MODEL_UNKNOWN` ist der erlaubte Treffer (Nutzerentscheid 02.11.2026, REQ-045 5.3). Details: `.claude/skills/spring-backend.md`.

---

## Output Format

```
🔍 Pre-Generation Check

✅ Keine Inline Templates gefunden
✅ Keine Inline Styles gefunden
❌ onInit in Feature Store gefunden:
   - src/app/features/user/store/user.store.ts:45
   - Fix: Route Resolver verwenden!
❌ ngOnInit Data Loading in Component gefunden:
   - src/app/features/user/user-container.component.ts:28
   - Code: this.store.loadUsers()
   - Fix: Route Resolver verwenden!
✅ Kein track $index gefunden
⚠️ Methoden im Template gefunden:
   - src/app/features/user/components/user-list.component.html:12
   - Fix: computed() Signal erstellen
✅ Resolver vorhanden für alle Features
✅ Keine Platzhalterwerte in API-/SoftNet-Payloads

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 Ergebnis: 5/8 Checks bestanden

❌ Blockierende Fehler:
   1. onInit in Feature Store → MUSS gefixt werden!
   2. ngOnInit Data Loading in Component → MUSS gefixt werden!

⚠️ Warnungen:
   1. Methoden im Template → Sollte gefixt werden
```

---

## Integration

### Als Git Pre-Commit Hook

```bash
# .husky/pre-commit
npm run lint:fix
claude /pre-generation-check
```

### Nach Implementierung

```bash
# Nach /implement-requirement
/pre-generation-check
/check-all <feature>
```

---

## Auto-Fix Optionen

Einige Issues können automatisch gefixt werden:

```bash
# ESLint Auto-Fix
npm run lint:fix

# Performance Fix (track, computed)
/fix-performance <feature>
```

**Manuelle Fixes erforderlich:**
- Inline Templates → Separate Dateien
- onInit im Store → Resolver Pattern
- ngOnInit in Component → Resolver Pattern
- Business Logic Placement
