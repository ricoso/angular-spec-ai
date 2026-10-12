import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { CustomerInfo, VehicleInfo } from '../../../../models/customer.model';

@Component({
  selector: 'app-personal-data-tile',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './personal-data-tile.component.html',
  styleUrl: './personal-data-tile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PersonalDataTileComponent {
  public readonly customerInfo = input.required<CustomerInfo>();
  public readonly vehicleInfo = input.required<VehicleInfo>();
  public readonly brandName = input.required<string>();

  protected readonly bookingOverview = i18nKeys.booking.bookingOverview;
  /** REQ-039: label of the optional VIN (same key as in the vehicle form). */
  protected readonly carinformation = i18nKeys.booking.carinformation;
}
