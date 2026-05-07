import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { BerlinTimePipe } from '@shared/pipes/berlin-time.pipe';

import type { AppointmentSlot } from '../../models/appointment.model';

@Component({
  selector: 'app-appointment-card',
  standalone: true,
  imports: [BerlinTimePipe],
  templateUrl: './appointment-card.component.html',
  styleUrl: './appointment-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppointmentCardComponent {
  public readonly appointment = input.required<AppointmentSlot>();
  public readonly isSelected = input<boolean>(false);
  public readonly appointmentSelected = output<AppointmentSlot>();

  protected onSelect(): void {
    this.appointmentSelected.emit(this.appointment());
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.onSelect();
    }
  }
}
