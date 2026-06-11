import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { ApiCategory, ApiJobType, ApiOperation, SelectedService } from '../../models/service.model';

interface RenderedHint {
  key: string;
  iconUrl: string | null;
  serviceName: string;
  hint: string;
}

/** Mapping rank → SVG-Icon (synchron mit ServiceCardComponent.ICON_BY_RANK). */
const ICON_BY_RANK: Record<number, string> = {
  0: 'assets/icons/services/inspection.svg',
  1: 'assets/icons/services/tuv.svg',
  2: 'assets/icons/services/brake-fluid.svg',
  3: 'assets/icons/services/tire-change.svg',
  4: 'assets/icons/services/actions-checks.svg',
  5: 'assets/icons/services/repair.svg',
  6: 'assets/icons/services/bodywork.svg',
  99: 'assets/icons/services/pickup-delivery.svg'
};

/**
 * REQ-025 (e): Service-Hints werden ausschliesslich aus der SoftNet-API gerendert
 * (`ApiJobType.hint`). Das Frontend bindet die rohen Strings via Template ein —
 * kein i18n-Lookup, kein lokales Mapping. Cards mit leerem hint sind hidden.
 */
@Component({
  selector: 'app-service-hints',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './service-hints.component.html',
  styleUrl: './service-hints.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServiceHintsComponent {
  /** Vom Notes-Container weitergereicht — die selektierten Operations. */
  public readonly selectedServices = input.required<SelectedService[]>();
  /** REQ-025 (b) Iter. 3: Operations der Branch — Description als Hint. */
  public readonly operations = input<ApiOperation[]>([]);
  /** REQ-025 (b) Iter. 3: Categories — für Icon-Lookup pro Operation (über Tag-Rank). */
  public readonly categories = input<ApiCategory[]>([]);
  /** Legacy: JobTypes — bleibt als Fallback wenn Operations leer sind. */
  public readonly jobTypes = input<ApiJobType[]>([]);

  protected readonly booking = i18nKeys.booking;

  /**
   * REQ-025 (e) Iter. 3: Hints pro selektierter Operation — Icon der Kategorie + Service-Name + Description.
   */
  protected readonly visibleHints = computed<RenderedHint[]>(() => {
    const selectedIds = new Set(this.selectedServices().map(s => s.serviceId as string));
    const cats = this.categories();
    const opsHints = this.operations()
      .filter(op => selectedIds.has(op.id))
      .map(op => {
        // Icon: rank des ersten zugehörigen Tags (= Kategorie der Operation)
        const primaryRank = cats
          .filter(c => op.tagIds.includes(c.id))
          .map(c => c.rank)
          .sort((a, b) => a - b)[0] ?? 0;
        // Spezialfall: Hol & Bringservice → eigenes Icon (rank=99)
        const rank = op.name === 'Hol & Bringservice' ? 99 : primaryRank;
        return {
          key: op.id,
          iconUrl: ICON_BY_RANK[rank] ?? null,
          serviceName: op.name,
          hint: op.description ?? ''
        };
      });
    if (opsHints.length > 0) return opsHints;
    // Fallback Legacy
    return this.jobTypes()
      .filter(jt => !!jt.hint && jt.hint.trim().length > 0)
      .map(jt => ({ key: jt.id, iconUrl: jt.iconUrl, serviceName: jt.name, hint: jt.hint! }));
  });
}
