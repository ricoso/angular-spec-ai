import type { ApiCategory } from '../models/service.model';

/** Bootstrap icon per category tag rank — SoftNet delivers no icon for tags. */
const ICON_BY_RANK: ReadonlyMap<number, string> = new Map([
  [0, 'bi-tools'],
  [1, 'bi-clipboard-check'],
  [2, 'bi-droplet-half'],
  [3, 'bi-record-circle'],
  [4, 'bi-check2-circle'],
  [5, 'bi-search'],
  [6, 'bi-car-front'],
  [99, 'bi-arrow-left-right']
]);

const DEFAULT_ICON = 'bi-tools';
const PICKUP_DELIVERY_ICON = 'bi-arrow-left-right';

/**
 * Icon class for a service category (pill and operations panel share it).
 * The virtual pick-up & delivery category keeps its icon regardless of its rank.
 */
export function categoryIconClass(category: ApiCategory | null | undefined): string {
  if (!category) {
    return DEFAULT_ICON;
  }
  if (category.id === 'virtual-pickup-delivery' || category.name === 'Hol & Bringservice') {
    return PICKUP_DELIVERY_ICON;
  }
  return ICON_BY_RANK.get(category.rank) ?? DEFAULT_ICON;
}
