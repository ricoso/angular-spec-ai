import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { BranchConfig } from '../../models/branch-config.model';
import { getBrandLogoPath } from '../../models/branch-config.model';

export interface EnrichedBranch extends BranchConfig {
  formattedAddress: string;
  brandLogos: { name: string; path: string }[];
  /** Zusammengesetzte Card-ID (eindeutig für default vs. tireChange). */
  cardId: string;
  /** Anzeige-Name inkl. evtl. "(Radwechsel)"-Suffix. */
  displayName: string;
  /** Optionaler groupId-Override für tireChange-Boxenstopp-Karten. */
  groupIdOverride: string | null;
}

export interface BranchSelection {
  branch: BranchConfig;
  groupIdOverride: string | null;
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
  public readonly selectedGroupIdOverride = input<string | null>(null);
  public readonly branchSelected = output<BranchSelection>();

  protected readonly booking = i18nKeys.booking;

  protected readonly enrichedBranches = computed<EnrichedBranch[]>(() => {
    const result: EnrichedBranch[] = [];
    for (const branch of this.branches()) {
      // REQ-025 (i): Adresse aus strukturierten Feldern formatieren — `<Street>, <ZIP> <City>`.
      const { street, zip, city } = branch.address;
      const zipCity = [zip, city].filter(p => !!p && p.trim().length > 0).join(' ').trim();
      const formatted = street && zipCity
        ? `${street}, ${zipCity}`
        : (street || zipCity || branch.name);
      const brandLogos = branch.brands.map(name => ({ name, path: getBrandLogoPath(name) }));

      // Default-Karte
      result.push({
        ...branch,
        formattedAddress: formatted,
        brandLogos,
        cardId: branch.branchId,
        displayName: branch.name,
        groupIdOverride: null
      });

      // Zusatz-Karte für tireChange-Boxenstopp (lokal testbar — gleicher Effekt wie Deep-Link).
      if (branch.tireChangeGroupId) {
        result.push({
          ...branch,
          formattedAddress: formatted,
          brandLogos,
          cardId: `${branch.branchId}__tireChange`,
          displayName: `${branch.name} — Radwechsel+ (Boxenstopp)`,
          groupIdOverride: branch.tireChangeGroupId
        });
      }
    }
    return result;
  });

  protected onSelect(branch: EnrichedBranch): void {
    this.branchSelected.emit({
      branch: {
        branchId: branch.branchId,
        name: branch.name,
        address: branch.address,
        brands: branch.brands,
        tireChangeGroupId: branch.tireChangeGroupId,
      },
      groupIdOverride: branch.groupIdOverride
    });
  }
}
