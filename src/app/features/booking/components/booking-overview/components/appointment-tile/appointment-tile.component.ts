import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { AppointmentSlot } from '../../../../models/appointment.model';

@Component({
  selector: 'app-appointment-tile',
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  templateUrl: './appointment-tile.component.html',
  styleUrl: './appointment-tile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppointmentTileComponent {
  public readonly appointment = input.required<AppointmentSlot>();

  protected readonly bookingOverview = i18nKeys.booking.bookingOverview;
}
