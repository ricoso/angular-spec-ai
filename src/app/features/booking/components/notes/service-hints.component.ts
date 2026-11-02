import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { ApiJobType, ApiOperation, SelectedService } from '../../models/service.model';

interface RenderedHint {
  key: string;
  serviceName: string;
  hint: string;
}

/**
 * REQ-025 (e): Service-Hints werden ausschliesslich aus der SoftNet-API gerendert
 * (`ApiJobType.hint`). Das Frontend bindet die rohen Strings via Template ein —
 * kein i18n-Lookup, kein lokales Mapping. Hints mit leerem Text sind hidden.
 * REQ-039: dargestellt als eine blaue Hinweisbox („<Service>: <Hinweis>“) ohne Einzel-Icons.
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
  /** Legacy: JobTypes — bleibt als Fallback wenn Operations leer sind. */
  public readonly jobTypes = input<ApiJobType[]>([]);

  protected readonly booking = i18nKeys.booking;

  /** REQ-025 (e) Iter. 3: Hints pro selektierter Operation — Service-Name + Description. */
  protected readonly visibleHints = computed<RenderedHint[]>(() => {
    const selectedIds = new Set(this.selectedServices().map(s => s.serviceId as string));
    const opsHints = this.operations()
      .filter(op => selectedIds.has(op.id))
      .map(op => ({ key: op.id, serviceName: op.name, hint: op.description ?? '' }));
    if (opsHints.length > 0) return opsHints;
    // Fallback Legacy
    return this.jobTypes()
      .filter(jt => !!jt.hint && jt.hint.trim().length > 0)
      .map(jt => ({ key: jt.id, serviceName: jt.name, hint: jt.hint! }));
  });
}
