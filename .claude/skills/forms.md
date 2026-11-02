# Reactive Forms Skill

WANN NUTZEN: Bei ALLEN Form-Implementierungen (Login, Registration, User Edit, etc.)

---

## Reactive Forms Pattern

### Container Component (hat FormGroup)

```typescript
export class UserEditContainerComponent {
  private fb = inject(FormBuilder);

  protected readonly form = signal<FormGroup>(this.fb.group({
    firstName: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    age: [null, [Validators.required, Validators.min(18)]]
  }));

  protected async onSubmit(): Promise<void> {
    if (this.form().invalid) {
      this.form().markAllAsTouched();
      return;
    }

    await this.userBusiness.create(this.form().value);
  }
}
```

### Presentational Component (zeigt FormGroup)

> **REGEL:** KEINE Methoden im Template! Stattdessen `computed()` Signal für Fehler-State.
> `hasError()` / `getErrorMessage()` als Methode ist VERBOTEN — wird bei jedem Change Detection Cycle aufgerufen!

```typescript
export class UserFormComponent {
  public readonly form = input.required<FormGroup>();
  public readonly submit = output<void>();

  // Reactive: form.events tracken, damit computed() bei Änderungen aktualisiert
  private readonly formEvents = toSignal(
    toObservable(this.form).pipe(
      switchMap(form => form.events.pipe(startWith(null)))
    ),
    { initialValue: null }
  );

  // ✅ Computed Signal — cached, nur bei form.events neu berechnet
  protected readonly errors = computed(() => {
    this.formEvents(); // Trigger bei Form-Events
    const form = this.form();
    return {
      firstName: {
        required: this.checkError(form, 'firstName', 'required'),
        minlength: this.checkError(form, 'firstName', 'minlength'),
      },
      email: {
        required: this.checkError(form, 'email', 'required'),
        email: this.checkError(form, 'email', 'email'),
      },
    };
  });

  // Private Helper (NICHT im Template aufgerufen!)
  private checkError(form: FormGroup, field: string, error: string): boolean {
    const control = form.get(field);
    return !!(control?.hasError(error) && control.touched);
  }
}
```

**Template:**
```html
<form [formGroup]="form()" (ngSubmit)="submit.emit()">
  <!-- ✅ GOOD: computed signal read -->
  <input formControlName="email" />
  @if (errors().email.required) {
    <mat-error>{{ t.validation.required | translate }}</mat-error>
  }
  @if (errors().email.email) {
    <mat-error>{{ t.validation.invalidEmail | translate }}</mat-error>
  }
  <button type="submit" [disabled]="form().invalid">Save</button>
</form>
```

**VERBOTEN:**
```html
<!-- ❌ BAD: Methoden-Aufruf im Template -->
@if (hasError('email')) { ... }
{{ getErrorMessage('email') }}

<!-- ✅ GOOD: Computed Signal im Template -->
@if (errors().email.required) { ... }
```

---

## Optionale Felder: weglassen, nicht auffüllen

Ein leeres optionales Feld (z. B. FIN) geht NICHT als `''` oder Platzhalter (`'UNKNOWN'`, `'N/A'`) in den Submit-Payload. Beim Mapping: `trim()`, leer → `undefined` (der Key fehlt im JSON). Das Backend gibt nur echte Angaben an SoftNet weiter (siehe `spring-backend.md`, „SoftNet-Payloads").

```typescript
// ✅ GOOD
const vin = this.form().controls.vin.value.trim().toUpperCase();
this.store.submit({ ...base, ...(vin ? { vin } : {}) });

// ❌ BAD
this.store.submit({ ...base, vin: this.form().value.vin || 'UNKNOWN' });
```

---

## Custom Validators

```typescript
export class CustomValidators {
  static strongPassword(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = control.value;
      if (!value) return null;

      const valid = /[0-9]/.test(value) && /[A-Z]/.test(value) &&
                    /[a-z]/.test(value) && value.length >= 8;

      return valid ? null : { weakPassword: true };
    };
  }

  static matchFields(field1: string, field2: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value1 = control.get(field1)?.value;
      const value2 = control.get(field2)?.value;
      return value1 === value2 ? null : { fieldsMismatch: true };
    };
  }
}
```
