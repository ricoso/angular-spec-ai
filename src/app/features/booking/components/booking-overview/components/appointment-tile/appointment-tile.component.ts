import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { BerlinTimePipe } from '@shared/pipes/berlin-time.pipe';

import type { AppointmentSlot } from '../../../../models/appointment.model';

@Component({
  selector: 'app-appointment-tile',
  standalone: true,
  imports: [TranslatePipe, BerlinTimePipe],
  templateUrl: './appointment-tile.component.html',
  styleUrl: './appointment-tile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppointmentTileComponent {
  public readonly appointment = input.required<AppointmentSlot>();

  protected readonly bookingOverview = i18nKeys.booking.bookingOverview;
}
