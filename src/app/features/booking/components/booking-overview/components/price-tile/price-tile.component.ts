import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { i18nKeys, TranslatePipe, TranslateService } from '@core/i18n';

import type { AppointmentPreference, CallbackOption } from '../../../../models/notes-extras.model';

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

  /** REQ-039 BR-M11: rental car is included (snap reserved one) — no category promise. */
  public readonly rentalCarRequested = input<boolean>(false);
  /** REQ-039 BR-M5: optional, non-binding category wish. */
  public readonly rentCategoryWishName = input<string | null>(null);
  public readonly appointmentPreference = input.required<AppointmentPreference>();
  public readonly callbackOption = input.required<CallbackOption>();
  public readonly bookingNote = input<string>('');

  protected readonly bookingOverview = i18nKeys.booking.bookingOverview;
  private readonly notes = i18nKeys.booking.notes;

  protected readonly mobilityOptionLabel = computed(() =>
    this.translateService.instant(this.rentalCarRequested()
      ? this.bookingOverview.rentalCar.value
      : this.notes.rentCategory.none)
  );

  protected readonly rentalWishName = computed(() =>
    this.rentalCarRequested() ? this.rentCategoryWishName() : null
  );

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
      'no': this.notes.callback.no,
      'yes': this.notes.callback.yes
    } as const;
    return this.translateService.instant(map[this.callbackOption()]);
  });
}
