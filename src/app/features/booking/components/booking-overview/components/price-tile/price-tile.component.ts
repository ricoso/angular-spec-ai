import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { i18nKeys, TranslatePipe, TranslateService } from '@core/i18n';

import type { AppointmentPreference, CallbackOption, MobilityOption } from '../../../../models/notes-extras.model';

@Component({
  selector: 'app-price-tile',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './price-tile.component.html',
  styleUrl: './price-tile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PriceTileComponent {
  private readonly translateService = inject(TranslateService);

  public readonly mobilityOption = input.required<MobilityOption>();
  public readonly appointmentPreference = input.required<AppointmentPreference>();
  public readonly callbackOption = input.required<CallbackOption>();
  public readonly bookingNote = input<string>('');

  protected readonly bookingOverview = i18nKeys.booking.bookingOverview;
  private readonly notes = i18nKeys.booking.notes;

  protected readonly mobilityOptionLabel = computed(() => {
    /* eslint-disable @typescript-eslint/naming-convention */
    const map = {
      'none': this.notes.mobilityOptions.none,
      'compact-car': this.notes.mobilityOptions.compactCar,
      'mid-range': this.notes.mobilityOptions.midRange,
      'luxury': this.notes.mobilityOptions.luxury
    } as const;
    /* eslint-enable @typescript-eslint/naming-convention */
    return this.translateService.instant(map[this.mobilityOption()]);
  });

  protected readonly appointmentPreferenceLabel = computed(() => {
    const map = {
      'anytime': this.notes.appointmentPreference.anytime,
      'morning': this.notes.appointmentPreference.morning,
      'afternoon': this.notes.appointmentPreference.afternoon
    } as const;
    return this.translateService.instant(map[this.appointmentPreference()]);
  });

  protected readonly callbackOptionLabel = computed(() => {
    const map = {
      'none': this.notes.callback.none,
      'yes': this.notes.callback.yes
    } as const;
    return this.translateService.instant(map[this.callbackOption()]);
  });
}
