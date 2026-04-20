import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { AppointmentSlot } from '../../models/appointment.model';
import type { WorkshopCalendarDay, WorkshopTimeSlot } from '../../models/workshop-calendar.model';
import { BookingStore } from '../../stores/booking.store';

import { WorkshopCalendarDatePickerComponent } from './workshop-calendar-date-picker.component';
import { WorkshopCalendarDayComponent } from './workshop-calendar-day.component';

@Component({
  selector: 'app-workshop-calendar-container',
  standalone: true,
  imports: [
    TranslatePipe,
    MatButtonModule,
    MatIconModule,
    WizardBreadcrumbComponent,
    WorkshopCalendarDatePickerComponent,
    WorkshopCalendarDayComponent
  ],
  templateUrl: './workshop-calendar-container.component.html',
  styleUrl: './workshop-calendar-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkshopCalendarContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);

  protected readonly booking = i18nKeys.booking;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 4;
  protected readonly workshopCalendarDays = this.store.workshopCalendarDays;
  protected readonly workshopCalendarDate = this.store.workshopCalendarDate;
  protected readonly selectedAppointment = this.store.selectedAppointment;
  protected readonly hasAppointmentSelected = this.store.hasAppointmentSelected;

  protected onDateSelected(date: Date): void {
    const isoDate = this.formatIsoDate(date);
    this.store.setWorkshopCalendarDate(isoDate);
    this.store.loadWorkshopCalendarDays(isoDate);
  }

  protected onSlotSelected(slot: WorkshopTimeSlot, day: WorkshopCalendarDay): void {
    const appointment: AppointmentSlot = {
      id: slot.id,
      date: day.date,
      displayDate: day.displayDate,
      dayAbbreviation: day.dayAbbreviation,
      time: slot.time,
      displayTime: slot.displayTime
    };
    this.store.selectAppointment(appointment);
  }

  protected onContinue(): void {
    console.debug('[WorkshopCalendarContainer] Continue clicked, appointment:', this.selectedAppointment());
    void this.router.navigate(['/home/carinformation']);
  }

  protected onBack(): void {
    this.store.clearWorkshopCalendar();
    void this.router.navigate(['/home/appointment']);
  }

  private formatIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
