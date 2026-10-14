import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { BranchConfig } from '../../models/branch-config.model';
import { getBrandLogoPath } from '../../models/branch-config.model';
import { RECALL_GROUP_ID } from '../../models/recall.model';

/** Which application/variant a card leads into. */
export type LocationCardKind = 'default' | 'tireChange' | 'recall';

export interface EnrichedBranch extends BranchConfig {
  formattedAddress: string;
  brandLogos: { name: string; path: string }[];
  /** Zusammengesetzte Card-ID (eindeutig für default vs. tireChange vs. recall). */
  cardId: string;
  /** Anzeige-Name inkl. evtl. Varianten-Suffix (für aria-label). */
  displayName: string;
  /** Optionaler groupId-Override für tireChange-Boxenstopp- bzw. Rückruf-Karten. */
  groupIdOverride: string | null;
  /** Welche Applikation/Variante die Karte öffnet. */
  cardKind: LocationCardKind;
}

export interface BranchSelection {
  branch: BranchConfig;
  groupIdOverride: string | null;
  mode: LocationCardKind;
}

/** German collation for city/branch names (Ü, Š sort like their base letter). */
const LOCATION_COLLATOR = new Intl.Collator('de', { sensitivity: 'base' });

/**
 * REQ-041: locations alphabetically by city (the card shows the address, so customers scan by
 * town), then by branch name. Branches without a city go last.
 */
function compareByCityThenName(a: BranchConfig, b: BranchConfig): number {
  const cityA = a.address.city.trim();
  const cityB = b.address.city.trim();
  if (!cityA !== !cityB) {
    return cityA ? -1 : 1;
  }
  return LOCATION_COLLATOR.compare(cityA, cityB) || LOCATION_COLLATOR.compare(a.name, b.name);
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
  /** REQ-041: in recall mode the Radwechsel/Boxenstopp cards are not offered — only real locations. */
  public readonly hideTireChange = input<boolean>(false);
  /**
   * When true, each location also gets a blue "Rückrufaktion" shortcut card (own SoftNet group, one
   * operation per branch) next to its default and Boxenstopp cards — so the global test page shows
   * every variant. The customer-facing recall deep-link page sets this false (it is already recall).
   */
  public readonly showRecall = input<boolean>(false);
  public readonly branchSelected = output<BranchSelection>();

  protected readonly booking = i18nKeys.booking;

  protected readonly enrichedBranches = computed<EnrichedBranch[]>(() => {
    const result: EnrichedBranch[] = [];
    const hideTireChange = this.hideTireChange();
    const showRecall = this.showRecall();
    // Copy before sorting — the input array belongs to the store.
    for (const branch of [...this.branches()].sort(compareByCityThenName)) {
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
        groupIdOverride: null,
        cardKind: 'default'
      });

      // Zusatz-Karte für tireChange-Boxenstopp (lokal testbar — gleicher Effekt wie Deep-Link).
      // REQ-041: im Rückruf-Modus nicht anbieten (Rückruf ist kein Radwechsel).
      if (branch.tireChangeGroupId && !hideTireChange) {
        result.push({
          ...branch,
          formattedAddress: formatted,
          brandLogos,
          cardId: `${branch.branchId}__tireChange`,
          displayName: `${branch.name} — Radwechsel+ (Boxenstopp)`,
          groupIdOverride: branch.tireChangeGroupId,
          cardKind: 'tireChange'
        });
      }

      // Zusatz-Karte für die Rückrufaktion (eigene Applikation/Group, eine Operation pro Standort).
      // Nur auf der globalen Test-Seite; die Kunden-Rückrufseite (Deep-Link) setzt showRecall=false.
      if (showRecall) {
        result.push({
          ...branch,
          formattedAddress: formatted,
          brandLogos,
          cardId: `${branch.branchId}__recall`,
          displayName: `${branch.name} — Rückrufaktion`,
          groupIdOverride: RECALL_GROUP_ID,
          cardKind: 'recall'
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
      groupIdOverride: branch.groupIdOverride,
      mode: branch.cardKind
    });
  }
}
