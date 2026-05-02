import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { BranchConfig } from '../../models/branch-config.model';
import { getBrandLogoPath } from '../../models/branch-config.model';

export interface EnrichedBranch extends BranchConfig {
  formattedAddress: string;
  brandLogos: { name: string; path: string }[];
}

@Component({
  selector: 'app-location-cards',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './location-cards.component.html',
  styleUrl: './location-cards.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LocationCardsComponent {
  public readonly branches = input.required<BranchConfig[]>();
  public readonly selectedBranch = input<BranchConfig | null>(null);
  public readonly branchSelected = output<BranchConfig>();

  protected readonly booking = i18nKeys.booking;

  protected readonly enrichedBranches = computed<EnrichedBranch[]>(() =>
    this.branches().map(branch => {
      // REQ-025 (i): Adresse aus strukturierten Feldern formatieren — `<Street>, <ZIP> <City>`.
      const { street, zip, city } = branch.address;
      const zipCity = [zip, city].filter(p => !!p && p.trim().length > 0).join(' ').trim();
      const formatted = street && zipCity
        ? `${street}, ${zipCity}`
        : (street || zipCity || branch.name);
      return {
        ...branch,
        formattedAddress: formatted,
        brandLogos: branch.brands.map(name => ({ name, path: getBrandLogoPath(name) }))
      };
    })
  );

  protected onSelect(branch: BranchConfig): void {
    this.branchSelected.emit(branch);
  }
}
