import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { SelectedService } from '../../../../models/service.model';

@Component({
  selector: 'app-services-tile',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './services-tile.component.html',
  styleUrl: './services-tile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServicesTileComponent {
  public readonly services = input.required<SelectedService[]>();
  public readonly serviceLabels = input.required<Record<string, string>>();
  public readonly locationName = input.required<string>();

  protected readonly bookingOverview = i18nKeys.booking.bookingOverview;
}
