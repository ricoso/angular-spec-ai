import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { BranchConfig } from '../../models/branch-config.model';
import { BookingStore } from '../../stores/booking.store';

import { LocationCardsComponent } from './location-cards.component';

@Component({
  selector: 'app-location-selection-container',
  standalone: true,
  imports: [LocationCardsComponent, WizardBreadcrumbComponent, TranslatePipe],
  templateUrl: './location-selection-container.component.html',
  styleUrl: './location-selection-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LocationSelectionContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);

  protected readonly booking = i18nKeys.booking;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 0;
  protected readonly branches = this.store.branches;
  protected readonly selectedBranch = this.store.selectedBranch;
  protected readonly isLoading = this.store.isLoading;
  protected readonly error = this.store.error;

  protected onBranchSelect(branch: BranchConfig): void {
    this.store.selectBranch(branch);
    void this.router.navigate(['/home/brand']);
  }

  protected onRetry(): void {
    this.store.loadBranches();
  }
}
