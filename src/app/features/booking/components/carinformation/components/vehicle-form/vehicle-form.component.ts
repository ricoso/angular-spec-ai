import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import type { FormGroup } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';

import type { TranslationKey } from '@core/i18n';
import { i18nKeys, TranslatePipe } from '@core/i18n';

import { LICENSE_PLATE_MAX_LENGTH } from '../../../../models/customer.model';

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

  /**
   * REQ-040/REQ-041: when the VIN is required (stored-tires service or recall campaign) the field is
   * shown and mandatory; otherwise it is hidden and not asked. The container owns the matching
   * validator swap on the `vin` control and picks the explanatory hint.
   */
  public readonly vinRequired = input<boolean>(false);
  /** REQ-041: context hint under the required VIN field (stored tires vs. recall assignment). */
  public readonly vinHintKey = input<TranslationKey>(i18nKeys.booking.carinformation.form.vin.requiredHint);

  protected readonly carinformation = i18nKeys.booking.carinformation;

  /** REQ-043 B-1: native `maxlength` of the plate input — typing stops at the SoftNet limit. */
  protected readonly licensePlateMaxLength = LICENSE_PLATE_MAX_LENGTH;

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
    if (ctrl.hasError('maxlength')) { return this.carinformation.form.licensePlate.error.tooLong; }
    return this.carinformation.form.licensePlate.error.invalidFormat;
  });

  /** REQ-039: drives `is-invalid` / `aria-invalid` of the optional VIN field. */
  protected readonly vinInvalid = computed(() => this.vinErrors() !== null);

  /**
   * REQ-039/REQ-040: VIN errors. When the field is required (stored tires) an empty value yields
   * the `required` message; otherwise only a non-empty, malformed value errors (format/length).
   */
  protected readonly vinErrors = computed(() => {
    this.formStateTick();
    const ctrl = this.form().get('vin');
    if (!ctrl?.touched || ctrl.valid) { return null; }
    if (ctrl.hasError('required')) { return this.carinformation.form.vin.error.required; }
    if (ctrl.hasError('vinFormat')) { return this.carinformation.form.vin.error.invalidFormat; }
    return this.carinformation.form.vin.error.invalidLength;
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
