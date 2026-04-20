import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { AppointmentSlot } from '../../models/appointment.model';
import { BookingStore } from '../../stores/booking.store';

import { AppointmentCardComponent } from './appointment-card.component';

@Component({
  selector: 'app-appointment-selection-container',
  standalone: true,
  imports: [TranslatePipe, MatButtonModule, MatIconModule, WizardBreadcrumbComponent, AppointmentCardComponent],
  templateUrl: './appointment-selection-container.component.html',
  styleUrl: './appointment-selection-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppointmentSelectionContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);

  protected readonly booking = i18nKeys.booking;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 4;
  protected readonly appointments = this.store.appointments;
  protected readonly selectedAppointment = this.store.selectedAppointment;
  protected readonly hasAppointmentSelected = this.store.hasAppointmentSelected;

  protected onAppointmentSelect(appointment: AppointmentSlot): void {
    this.store.selectAppointment(appointment);
  }

  protected onContinue(): void {
    console.debug('[AppointmentSelectionContainer] Continue clicked, appointment:', this.selectedAppointment());
    void this.router.navigate(['/home/carinformation']);
  }

  protected onBack(): void {
    this.store.clearSelectedAppointment();
    void this.router.navigate(['/home/notes']);
  }

  protected onCalendarLinkClick(event: Event): void {
    event.preventDefault();
    void this.router.navigate(['/home/workshop-calendar']);
  }
}
