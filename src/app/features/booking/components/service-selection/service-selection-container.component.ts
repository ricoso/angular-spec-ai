import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { ServiceType } from '../../models/service.model';
import { BookingStore } from '../../stores/booking.store';

import { ServiceCardComponent } from './service-card.component';
import { ServiceSummaryBarComponent } from './service-summary-bar.component';

@Component({
  selector: 'app-service-selection-container',
  standalone: true,
  imports: [TranslatePipe, WizardBreadcrumbComponent, ServiceCardComponent, ServiceSummaryBarComponent],
  templateUrl: './service-selection-container.component.html',
  styleUrl: './service-selection-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServiceSelectionContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);

  protected readonly booking = i18nKeys.booking;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 2;
  protected readonly services = this.store.services;
  protected readonly selectedServices = this.store.selectedServices;
  protected readonly hasServicesSelected = this.store.hasServicesSelected;
  protected readonly selectedServiceCount = this.store.selectedServiceCount;

  protected readonly selectedServiceIds = computed(() =>
    this.store.selectedServices().map(s => s.serviceId)
  );

  protected readonly selectedOptionsMap = computed(() => {
    const map: Record<string, string[]> = {};
    for (const s of this.store.selectedServices()) {
      map[s.serviceId] = s.selectedOptionIds;
    }
    return map;
  });

  protected onServiceClick(serviceId: ServiceType): void {
    this.store.toggleService(serviceId);
  }

  protected onServiceConfirm(event: { serviceId: ServiceType; optionIds: string[] }): void {
    this.store.confirmServiceOptions(event.serviceId, event.optionIds);
  }

  protected onServiceDeselect(serviceId: ServiceType): void {
    this.store.deselectService(serviceId);
  }

  protected onContinue(): void {
    console.debug('[ServiceSelection] Continue clicked, services:', this.store.selectedServices());
    void this.router.navigate(['/home/notes']);
  }

  protected onBack(): void {
    this.store.clearSelectedServices();
    void this.router.navigate(['/home/brand']);
  }
}
