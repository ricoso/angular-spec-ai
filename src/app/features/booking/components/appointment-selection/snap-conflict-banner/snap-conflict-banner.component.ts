import { ChangeDetectionStrategy, Component, output } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

/** REQ-031: auto-dismiss timeout of the snap-conflict banner (BR-3). */
export const SNAP_CONFLICT_BANNER_TIMEOUT_MS = 30_000;

@Component({
  selector: 'app-snap-conflict-banner',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './snap-conflict-banner.component.html',
  styleUrl: './snap-conflict-banner.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SnapConflictBannerComponent {
  public readonly closed = output();

  protected readonly snapConflict = i18nKeys.booking.appointment.snapConflict;

  protected onClose(): void {
    this.closed.emit();
  }
}
