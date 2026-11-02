import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import type { FormGroup } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';

import { i18nKeys, TranslatePipe } from '@core/i18n';

@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslatePipe
  ],
  templateUrl: './customer-form.component.html',
  styleUrl: './customer-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CustomerFormComponent {
  public readonly form = input.required<FormGroup>();

  protected readonly carinformation = i18nKeys.booking.carinformation;

  // Reactive forms are not signal-based. This tick is the dependency of all error computeds and
  // follows each CHILD control's events (incl. TouchedChangeEvent) — as in vehicle-form (REQ-031):
  // markAllAsTouched() on submit emits no group valueChanges/statusChanges, so errors of untouched
  // required fields would otherwise never show (only the red border did).
  private readonly formStateTick = signal(0);

  constructor() {
    effect((onCleanup) => {
      const subs = Object.values(this.form().controls)
        .map(control => control.events.subscribe(() => { this.formStateTick.update(v => v + 1); }));
      onCleanup(() => { subs.forEach(s => { s.unsubscribe(); }); });
    });
  }

  protected readonly emailErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('email');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.email.error.required; }
    if (ctrl.hasError('email')) { return this.carinformation.form.email.error.invalid; }
    return null;
  });

  protected readonly firstNameErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('firstName');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.firstName.error.required; }
    if (ctrl.hasError('pattern')) { return this.carinformation.form.firstName.error.lettersOnly; }
    return null;
  });

  protected readonly lastNameErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('lastName');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.lastName.error.required; }
    if (ctrl.hasError('pattern')) { return this.carinformation.form.lastName.error.lettersOnly; }
    return null;
  });

  protected readonly streetErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('street');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    return this.carinformation.form.street.error.required;
  });

  protected readonly postalCodeErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('postalCode');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.postalCode.error.required; }
    return this.carinformation.form.postalCode.error.digitsOnly;
  });

  protected readonly cityErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('city');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.city.error.required; }
    if (ctrl.hasError('pattern')) { return this.carinformation.form.city.error.lettersOnly; }
    return null;
  });

  protected readonly mobilePhoneErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('mobilePhone');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.mobilePhone.error.required; }
    if (ctrl.hasError('pattern')) { return this.carinformation.form.mobilePhone.error.startsWithZero; }
    return null;
  });
}
