import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { Brand } from '../../models/brand.model';
import { BookingStore } from '../../stores/booking.store';

import { BrandCardsComponent } from './brand-cards.component';

@Component({
  selector: 'app-brand-selection-container',
  standalone: true,
  imports: [BrandCardsComponent, WizardBreadcrumbComponent, TranslatePipe],
  templateUrl: './brand-selection-container.component.html',
  styleUrl: './brand-selection-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandSelectionContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);

  protected readonly booking = i18nKeys.booking;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 1;
  protected readonly brands = this.store.brands;
  protected readonly selectedBrand = this.store.selectedBrand;
  protected readonly selectedBranch = this.store.selectedBranch;
  protected readonly isLoading = this.store.isLoading;
  protected readonly error = this.store.error;
  /** REQ-039: in the deep link there is no location step to go back to. */
  protected readonly isDeepLinked = this.store.isDeepLinked;

  protected readonly formattedBranchAddress = computed(() => {
    const branch = this.selectedBranch();
    if (!branch) return '';
    const { street, zip, city } = branch.address;
    const zipCity = [zip, city].filter(part => !!part && part.trim().length > 0).join(' ').trim();
    if (street && zipCity) {
      return `${street}, ${zipCity}`;
    }
    return street || zipCity || branch.name;
  });

  protected async onBrandSelect(brand: Brand): Promise<void> {
    this.store.setBrand(brand);
    // REQ-041: recall mode skips the service step — select the single recall service up front (before
    // navigating), so the services-selected guard on /home/notes passes. Angular runs a route's
    // canActivate guards in parallel, so the async recallServiceGuard alone cannot win the race
    // against the synchronous servicesSelectedGuard; selecting here makes it deterministic.
    if (this.store.isRecallMode()) {
      await this.store.applyRecallService();
      void this.router.navigate(['/home/notes']);
      return;
    }
    void this.router.navigate(['/home/services']);
  }

  protected onBack(): void {
    this.store.clearSelectedBrand();
    void this.router.navigate(['/home/location']);
  }

  protected onRetry(): void {
    const branch = this.selectedBranch();
    if (branch) {
      this.store.loadBrands(branch.branchId);
    }
  }
}
