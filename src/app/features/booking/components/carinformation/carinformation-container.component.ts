import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { CustomerInfo, VehicleInfo } from '../../models/customer.model';
import { BookingStore } from '../../stores/booking.store';

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
    mileage: [null as number | null, [Validators.required, Validators.pattern(/^[0-9]+$/), Validators.min(10)]]
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
    if (this.store.privacyConsent()) {
      this.privacyControl.setValue(true);
    }
  }

  protected readonly privacyError = computed(() =>
    this.privacyTouched() && this.privacyControl.invalid
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
      mileage: vehicleValue.mileage ?? 0
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
}
