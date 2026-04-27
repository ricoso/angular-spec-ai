import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { FormGroup } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { merge, startWith, switchMap } from 'rxjs';

@Component({
  selector: 'app-vehicle-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    TranslatePipe
  ],
  templateUrl: './vehicle-form.component.html',
  styleUrl: './vehicle-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VehicleFormComponent {
  public readonly form = input.required<FormGroup>();

  protected readonly carinformation = i18nKeys.booking.carinformation;

  private readonly formEvents = toSignal(
    toObservable(this.form).pipe(
      switchMap(f => merge(f.valueChanges, f.statusChanges).pipe(startWith(null)))
    ),
    { initialValue: null }
  );

  protected readonly licensePlateErrors = computed(() => {
    this.formEvents();
    const ctrl = this.form().get('licensePlate');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.licensePlate.error.required; }
    return this.carinformation.form.licensePlate.error.invalidFormat;
  });

  protected readonly mileageErrors = computed(() => {
    this.formEvents();
    const ctrl = this.form().get('mileage');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.mileage.error.required; }
    return this.carinformation.form.mileage.error.digitsOnly;
  });

}
