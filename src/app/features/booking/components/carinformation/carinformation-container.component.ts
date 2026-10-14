import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import type { TranslationKey } from '@core/i18n';
import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { CustomerInfo, VehicleInfo } from '../../models/customer.model';
import { BookingStore } from '../../stores/booking.store';
import { optionalVinValidator, requiredVinValidator } from '../../validators/optional-vin.validator';

import { CustomerFormComponent } from './components/customer-form/customer-form.component';
import { VehicleFormComponent } from './components/vehicle-form/vehicle-form.component';

@Component({
  selector: 'app-carinformation-container',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    WizardBreadcrumbComponent,
    CustomerFormComponent,
    VehicleFormComponent
  ],
  templateUrl: './carinformation-container.component.html',
  styleUrl: './carinformation-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CarinformationContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  protected readonly carinformation = i18nKeys.booking.carinformation;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 5;

  /** REQ-040: "Räder sind eingelagert" selected → the VIN field is shown and required. */
  protected readonly storedTiresSelected = computed(() => this.store.hasStoredTiresService());
  /** REQ-041: recall campaign mode → the VIN is prefilled from the URL and required. */
  protected readonly isRecallMode = computed(() => this.store.isRecallMode());
  /** REQ-040/REQ-041: the VIN field is shown + mandatory for stored tires OR a recall campaign. */
  protected readonly vinRequired = computed(() => this.storedTiresSelected() || this.isRecallMode());
  /** Context hint under the required VIN field. */
  protected readonly vinHintKey = computed<TranslationKey>(() =>
    this.isRecallMode() ? this.carinformation.form.vin.recallHint : this.carinformation.form.vin.requiredHint
  );

  protected readonly customerForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    salutation: ['mr' as 'mr' | 'ms', [Validators.required]],
    firstName: ['', [Validators.required, Validators.pattern(/^[a-zA-ZÀ-ÖØ-öø-ÿ\s-]+$/)]],
    lastName: ['', [Validators.required, Validators.pattern(/^[a-zA-ZÀ-ÖØ-öø-ÿ\s-]+$/)]],
    street: ['', [Validators.required]],
    postalCode: ['', [Validators.required, Validators.pattern(/^\d{5}$/)]],
    city: ['', [Validators.required, Validators.pattern(/^[a-zA-ZÀ-ÖØ-öø-ÿ\s-]+$/)]],
    mobilePhone: ['', [Validators.required, Validators.pattern(/^(\+|0)[0-9\s]+$/)]]
  });

  protected readonly vehicleForm = this.fb.group({
    // License plate: only blank / whitespace-only is invalid (trim, then must not be empty);
    // any other value is accepted. Angular's `required` alone would let a lone space through,
    // so `pattern(/\S/)` enforces "must contain at least one non-whitespace char".
    // Kennzeichen: nur leer / reines Leerzeichen ungültig, jeder andere Wert erlaubt.
    licensePlate: ['', [Validators.required, Validators.pattern(/\S/)]],
    // REQ-031 BR-6: whole number >= 10 — SoftNet treats 0 as "not set"
    mileage: [null as number | null, [Validators.required, Validators.pattern(/^[0-9]+$/), Validators.min(10)]],
    // REQ-039: VIN is fully optional — empty passes; if entered it must be 17 letters/digits.
    vin: ['', [optionalVinValidator()]]
  });

  protected readonly privacyControl = new FormControl(false, [Validators.requiredTrue]);

  private readonly privacyTouched = signal(false);

  constructor() {
    const customer = this.store.customerInfo();
    if (customer) {
      this.customerForm.patchValue(customer);
    }
    const vehicle = this.store.vehicleInfo();
    if (vehicle) {
      this.vehicleForm.patchValue(vehicle);
    }
    // REQ-041: prefill the VIN from the recall URL (editable), unless already set from stored data.
    const recallVin = this.store.recallVin();
    if (this.isRecallMode() && recallVin && !this.vehicleForm.controls.vin.value) {
      this.vehicleForm.controls.vin.setValue(recallVin);
    }
    if (this.store.privacyConsent()) {
      this.privacyControl.setValue(true);
    }

    // REQ-040/REQ-041: swap the VIN validators with the required state (stored tires OR recall).
    // Required + format when the field is shown; only the optional format check otherwise (and reset
    // so nothing stale is sent). `emitEvent: true` ticks the child events subscription so `vinErrors`
    // recomputes.
    effect(() => {
      const vin = this.vehicleForm.controls.vin;
      if (this.vinRequired()) {
        // REQ-040/REQ-041: whitespace-only input must NOT satisfy the mandatory field.
        vin.setValidators([requiredVinValidator()]);
      } else {
        vin.setValidators([optionalVinValidator()]);
        vin.reset('');
      }
      vin.updateValueAndValidity({ emitEvent: true });
    });
  }

  /** Consent value as signal — `privacyControl.invalid` alone is not tracked by `computed()`. */
  private readonly privacyChecked = toSignal(this.privacyControl.valueChanges, {
    initialValue: this.privacyControl.value
  });

  /** Error only after a submit attempt and only while the box is unchecked (clears on ticking it). */
  protected readonly privacyError = computed(() =>
    this.privacyTouched() && this.privacyChecked() !== true
  );

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.customerForm.markAllAsTouched();
    this.vehicleForm.markAllAsTouched();
    this.privacyControl.markAsTouched();
    this.privacyTouched.set(true);

    if (this.customerForm.invalid || this.vehicleForm.invalid || this.privacyControl.invalid) {
      return;
    }

    const customerValue = this.customerForm.value;
    const vehicleValue = this.vehicleForm.value;

    const customerInfo: CustomerInfo = {
      email: customerValue.email ?? '',
      salutation: customerValue.salutation ?? 'mr',
      firstName: customerValue.firstName ?? '',
      lastName: customerValue.lastName ?? '',
      street: customerValue.street ?? '',
      postalCode: customerValue.postalCode ?? '',
      city: customerValue.city ?? '',
      mobilePhone: customerValue.mobilePhone ?? ''
    };

    const vehicleInfo: VehicleInfo = {
      licensePlate: vehicleValue.licensePlate ?? '',
      mileage: vehicleValue.mileage ?? 0,
      // REQ-040/REQ-041: only send the VIN when it is actually asked for (stored tires or recall).
      vin: this.vinRequired() ? this.normalizeVin(vehicleValue.vin) : undefined
    };

    this.store.setCustomerInfo(customerInfo);
    this.store.setVehicleInfo(vehicleInfo);
    this.store.setPrivacyConsent(true);

    console.debug('[CarinformationContainer] Form submitted, navigating to booking overview');
    void this.router.navigate(['/home/booking-overview']);
  }

  protected onBack(): void {
    void this.router.navigate(['/home/appointment']);
  }

  /** REQ-039: optional VIN — trimmed and upper-cased, `undefined` when left empty. */
  private normalizeVin(vin: string | null | undefined): string | undefined {
    const trimmed = vin?.trim() ?? '';
    return trimmed.length > 0 ? trimmed.toUpperCase() : undefined;
  }
}
