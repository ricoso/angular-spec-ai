import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { BerlinTimePipe } from '@shared/pipes/berlin-time.pipe';

import type { WorkshopCalendarDay, WorkshopTimeSlot } from '../../models/workshop-calendar.model';

@Component({
  selector: 'app-workshop-calendar-day',
  standalone: true,
  imports: [TranslatePipe, BerlinTimePipe],
  templateUrl: './workshop-calendar-day.component.html',
  styleUrl: './workshop-calendar-day.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkshopCalendarDayComponent {
  public readonly day = input.required<WorkshopCalendarDay>();
  public readonly selectedSlotId = input<string | null>(null);
  public readonly slotSelected = output<WorkshopTimeSlot>();

  protected readonly booking = i18nKeys.booking;

  protected onSlotClick(slot: WorkshopTimeSlot): void {
    this.slotSelected.emit(slot);
  }

  protected onSlotKeydown(event: KeyboardEvent, slot: WorkshopTimeSlot): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.slotSelected.emit(slot);
    }
  }
}
