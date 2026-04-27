import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { FormGroup } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { merge, startWith, switchMap } from 'rxjs';

@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe
  ],
  templateUrl: './customer-form.component.html',
  styleUrl: './customer-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CustomerFormComponent {
  public readonly form = input.required<FormGroup>();

  protected readonly carinformation = i18nKeys.booking.carinformation;

  // Reactive-Forms sind nicht Signal-basiert. Dieses Signal tickt bei jedem value-/status-Change
  // der FormGroup und fungiert als Dependency in allen Error-Computeds,
  // damit sie bei FormControl-Aenderungen neu evaluiert werden.
  private readonly formEvents = toSignal(
    toObservable(this.form).pipe(
      switchMap(f => merge(f.valueChanges, f.statusChanges).pipe(startWith(null)))
    ),
    { initialValue: null }
  );

  protected readonly emailErrors = computed(() => {
    this.formEvents();
    const ctrl = this.form().get('email');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.email.error.required; }
    if (ctrl.hasError('email')) { return this.carinformation.form.email.error.invalid; }
    return null;
  });

  protected readonly firstNameErrors = computed(() => {
    this.formEvents();
    const ctrl = this.form().get('firstName');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.firstName.error.required; }
    if (ctrl.hasError('pattern')) { return this.carinformation.form.firstName.error.lettersOnly; }
    return null;
  });

  protected readonly lastNameErrors = computed(() => {
    this.formEvents();
    const ctrl = this.form().get('lastName');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.lastName.error.required; }
    if (ctrl.hasError('pattern')) { return this.carinformation.form.lastName.error.lettersOnly; }
    return null;
  });

  protected readonly streetErrors = computed(() => {
    this.formEvents();
    const ctrl = this.form().get('street');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    return this.carinformation.form.street.error.required;
  });

  protected readonly postalCodeErrors = computed(() => {
    this.formEvents();
    const ctrl = this.form().get('postalCode');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.postalCode.error.required; }
    return this.carinformation.form.postalCode.error.digitsOnly;
  });

  protected readonly cityErrors = computed(() => {
    this.formEvents();
    const ctrl = this.form().get('city');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.city.error.required; }
    if (ctrl.hasError('pattern')) { return this.carinformation.form.city.error.lettersOnly; }
    return null;
  });

  protected readonly mobilePhoneErrors = computed(() => {
    this.formEvents();
    const ctrl = this.form().get('mobilePhone');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.mobilePhone.error.required; }
    if (ctrl.hasError('pattern')) { return this.carinformation.form.mobilePhone.error.startsWithZero; }
    return null;
  });
}
