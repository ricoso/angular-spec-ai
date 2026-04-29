import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
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
  imports: [TranslatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule, WizardBreadcrumbComponent, ServiceCardComponent, ServiceSummaryBarComponent],
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
  /** REQ-025 (b) Iter. 3: Tags + virtuelle „Hol & Bringservice"-Card als Cards. */
  protected readonly categories = this.store.categoriesWithVirtual;
  /** REQ-025 (b) Iter. 3: Operations gruppiert nach Tag-ID. */
  protected readonly operationsByCategory = this.store.operationsByCategory;
  protected readonly hasServicesSelected = this.store.hasServicesSelected;
  protected readonly selectedServiceCount = this.store.selectedServiceCount;
  protected readonly isLoading = this.store.isLoading;
  protected readonly error = this.store.error;

  protected readonly selectedServiceIds = computed(() =>
    this.store.selectedServices().map(s => s.serviceId)
  );

  /** Map Category-ID → selectedOperationIds. */
  protected readonly selectedOptionsMap = computed((): Record<string, string[]> => {
    const map: Record<string, string[]> = {};
    const selected = this.store.selectedServices();
    const opsByCat = this.store.operationsByCategory();
    // operationsByCategory enthält bereits die virtuelle Pickup-Card + alle echten Tags
    for (const catId of Object.keys(opsByCat)) {
      const ids: string[] = [];
      const opsInCat = opsByCat[catId];
      for (const sel of selected) {
        if (opsInCat.some(o => o.id === sel.serviceId)) {
          ids.push(sel.serviceId);
        }
      }
      map[catId] = ids;
    }
    return map;
  });

  /** Pro Card: ist mindestens eine Operation aus dieser Kategorie selektiert? */
  protected readonly categorySelectedMap = computed((): Record<string, boolean> => {
    const result: Record<string, boolean> = {};
    const map = this.selectedOptionsMap();
    for (const catId of Object.keys(map)) {
      result[catId] = map[catId].length > 0;
    }
    return result;
  });

  constructor() {
    // REQ-025 (b) Iter. 3: Categories + Operations parallel laden, sobald Branch da ist.
    effect(() => {
      const branch = this.store.selectedBranch();
      const cats = this.store.categories();
      const ops = this.store.operations();
      if (branch && cats.length === 0) {
        this.store.loadCategoriesForBranch(branch.branchId);
      }
      if (branch && ops.length === 0) {
        this.store.loadOperationsForBranch(branch.branchId);
      }
    });
  }

  protected onServiceClick(operationId: string): void {
    this.store.toggleService(operationId as ServiceType);
  }

  protected onServiceConfirm(event: { serviceId: string; optionIds: string[] }): void {
    // event.serviceId ist die Category-ID (Tag), optionIds sind Operation-IDs.
    // Erst alle Operations dieser Kategorie raus, dann nur die confirmed wieder rein.
    const opsInCategory = this.operationsByCategory()[event.serviceId] ?? [];
    for (const op of opsInCategory) {
      this.store.deselectService(op.id as ServiceType);
    }
    for (const opId of event.optionIds) {
      this.store.confirmServiceOptions(opId as ServiceType, []);
    }
  }

  protected onServiceDeselect(categoryId: string): void {
    // Alle Operations dieser Kategorie deselektieren.
    const opsToRemove = this.operationsByCategory()[categoryId] ?? [];
    for (const op of opsToRemove) {
      this.store.deselectService(op.id as ServiceType);
    }
  }

  protected onContinue(): void {
    void this.router.navigate(['/home/notes']);
  }

  protected onBack(): void {
    this.store.clearSelectedServices();
    void this.router.navigate(['/home/brand']);
  }

  protected onRetry(): void {
    const branch = this.store.selectedBranch();
    if (branch) {
      this.store.loadCategoriesForBranch(branch.branchId);
      this.store.loadOperationsForBranch(branch.branchId);
    }
  }
}
