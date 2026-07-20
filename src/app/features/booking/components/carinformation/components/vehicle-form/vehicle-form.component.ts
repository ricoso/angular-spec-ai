import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import type { FormGroup } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';

import { i18nKeys, TranslatePipe } from '@core/i18n';

@Component({
  selector: 'app-vehicle-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TranslatePipe
  ],
  templateUrl: './vehicle-form.component.html',
  styleUrl: './vehicle-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VehicleFormComponent {
  public readonly form = input.required<FormGroup>();

  protected readonly carinformation = i18nKeys.booking.carinformation;

  // Synchronous form-state ticker driving the error computeds. We subscribe to
  // each CHILD control's events (incl. TouchedChangeEvent) — NOT the group's —
  // and NOT via toObservable():
  //  - Group events miss a single field's touched change once the group is
  //    already touched (e.g. after another field blurred). On submit the
  //    group's markAllAsTouched() then emits no group-level event, so the error
  //    would never show. Child-level events fire per control every time.
  //  - toObservable() emits on the async effect scheduler, landing after the
  //    submit's change-detection pass; a direct subscription ticks synchronously
  //    within the same pass.
  private readonly formStateTick = signal(0);

  constructor() {
    effect((onCleanup) => {
      const subs = Object.values(this.form().controls)
        .map(control => control.events.subscribe(() => { this.formStateTick.update(v => v + 1); }));
      onCleanup(() => { subs.forEach(s => { s.unsubscribe(); }); });
    });
  }

  protected readonly licensePlateErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('licensePlate');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.licensePlate.error.required; }
    return this.carinformation.form.licensePlate.error.invalidFormat;
  });

  protected readonly mileageErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('mileage');
    if (!ctrl || !ctrl.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.mileage.error.required; }
    if (ctrl.hasError('pattern')) { return this.carinformation.form.mileage.error.digitsOnly; }
    if (ctrl.hasError('min')) { return this.carinformation.form.mileage.error.min; }
    return this.carinformation.form.mileage.error.digitsOnly;
  });

}
