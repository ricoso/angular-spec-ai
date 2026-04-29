import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
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
  imports: [BrandCardsComponent, WizardBreadcrumbComponent, MatButtonModule, MatIconModule, MatProgressSpinnerModule, TranslatePipe],
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

  protected onBrandSelect(brand: Brand): void {
    this.store.setBrand(brand);
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
