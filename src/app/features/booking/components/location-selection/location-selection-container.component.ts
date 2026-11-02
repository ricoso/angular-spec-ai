import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import { BookingStore } from '../../stores/booking.store';

import type { BranchSelection } from './location-cards.component';
import { LocationCardsComponent } from './location-cards.component';

/** Cities removed from the location list — they book through a different provider (e.g. Leverkusen). */
const HIDDEN_CITIES = new Set<string>(['leverkusen']);

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
  private readonly allBranches = this.store.branches;
  /** City-search filter (live, case-insensitive) for the location list. */
  protected readonly citySearch = signal('');
  /**
   * Branches shown in the list: cities in HIDDEN_CITIES are removed entirely (they book via a
   * different provider), then the city-search term filters by city or branch name.
   */
  protected readonly branches = computed(() => {
    const term = this.citySearch().trim().toLowerCase();
    return this.allBranches()
      .filter(branch => !HIDDEN_CITIES.has(branch.address.city.trim().toLowerCase()))
      .filter(branch => term.length === 0
        || branch.address.city.toLowerCase().includes(term)
        || branch.name.toLowerCase().includes(term));
  });
  /** True when a search term is active but matches no location. */
  protected readonly noSearchResults = computed(() =>
    this.citySearch().trim().length > 0 && this.branches().length === 0
  );
  protected readonly selectedBranch = this.store.selectedBranch;
  protected readonly selectedGroupIdOverride = this.store.deepLinkGroupId;
  protected readonly isLoading = this.store.isLoading;
  protected readonly error = this.store.error;
  /** REQ-041: recall campaign entry — shows the recall banner + detected VIN on the location page. */
  protected readonly isRecallMode = this.store.isRecallMode;
  protected readonly recallVin = this.store.recallVin;
  /** REQ-030: hide the internal report button when embedded in a customer iframe. */
  protected readonly isEmbedded = signal(this.detectEmbedded());
  /** REQ-041: the internal report button is also hidden for recall-campaign customers. */
  protected readonly showReportButton = computed(() => !this.isEmbedded() && !this.isRecallMode());

  protected onBranchSelect(selection: BranchSelection): void {
    this.store.selectBranch(selection.branch);
    if (selection.mode === 'recall') {
      // Rückruf-Karte (globale Test-Seite): vollständigen Rückruf-Modus aktivieren.
      this.store.activateRecallMode(null);
    } else if (!this.store.isRecallMode()) {
      // Default / Boxenstopp im Normalmodus: Group setzen. Im Rückruf-Deep-Link-Modus bleibt die
      // Standortwahl einer echten Karte im Rückruf-Modus — die Rückruf-Group darf nicht verloren gehen.
      this.store.setDeepLinkGroupId(selection.groupIdOverride);
    }
    void this.router.navigate(['/home/brand']);
  }

  protected onCitySearch(event: Event): void {
    this.citySearch.set((event.target as HTMLInputElement).value);
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
