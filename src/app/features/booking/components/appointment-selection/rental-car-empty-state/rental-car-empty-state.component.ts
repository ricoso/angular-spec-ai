import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

/**
 * REQ-039 E-1/E-2: no appointment with a rental car — shared by the appointment page and the
 * workshop calendar (mockups/04-termin.html `.rental-empty`). No silent fallback to all slots:
 * the customer decides via the actions.
 */
@Component({
  selector: 'app-rental-car-empty-state',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './rental-car-empty-state.component.html',
  styleUrl: './rental-car-empty-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RentalCarEmptyStateComponent {
  /** "Spätere Termine anzeigen" — only where a later view exists (the calendar is that view). */
  public readonly showLaterAvailable = input<boolean>(false);
  public readonly showLater = output();
  public readonly continueWithout = output();

  protected readonly rentalCar = i18nKeys.booking.appointment.rentalCar;

  protected onShowLater(): void {
    this.showLater.emit();
  }

  protected onContinueWithout(): void {
    this.continueWithout.emit();
  }
}
