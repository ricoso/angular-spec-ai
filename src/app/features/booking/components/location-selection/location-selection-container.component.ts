import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import { BookingStore } from '../../stores/booking.store';

import type { BranchSelection } from './location-cards.component';
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
  protected readonly selectedGroupIdOverride = this.store.deepLinkGroupId;
  protected readonly isLoading = this.store.isLoading;
  protected readonly error = this.store.error;
  /** REQ-030: hide the internal report button when embedded in a customer iframe. */
  protected readonly isEmbedded = signal(this.detectEmbedded());

  protected onBranchSelect(selection: BranchSelection): void {
    this.store.selectBranch(selection.branch);
    this.store.setDeepLinkGroupId(selection.groupIdOverride);
    void this.router.navigate(['/home/brand']);
  }

  protected onRetry(): void {
    this.store.loadBranches();
  }

  /** REQ-030: open the internal error report (Basic Auth) in a new tab. */
  protected onOpenReport(): void {
    window.open('/internal/report', '_blank', 'noopener');
  }

  private detectEmbedded(): boolean {
    try {
      return window.self !== window.top;
    } catch {
      return true;
    }
  }
}
