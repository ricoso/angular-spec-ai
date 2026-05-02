import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { ApiCategory, ApiOperation } from '../../models/service.model';

/**
 * Mini-Card im Service-Karussell. Zeigt pro Kategorie ein Icon, Titel und einen
 * Counter (entweder selected/total oder nur total). Klick aktiviert die Kategorie.
 *
 * Public API (inputs/outputs) bleibt rueckwaertskompatibel mit der vorherigen
 * Material-basierten Implementierung — die Detail-Sicht (Operations + Confirm)
 * lebt jetzt im Container.
 */
@Component({
  selector: 'app-service-card',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './service-card.component.html',
  styleUrl: './service-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServiceCardComponent {
  public readonly category = input.required<ApiCategory>();
  public readonly operations = input.required<ApiOperation[]>();
  public readonly isSelected = input<boolean>(false);
  public readonly selectedOperationIds = input<string[]>([]);

  /** Emittet die Category-ID beim Klick auf die Mini-Card. */
  public readonly serviceClicked = output<string>();
  public readonly serviceConfirmed = output<{ serviceId: string; optionIds: string[] }>();
  public readonly serviceDeselected = output<string>();

  protected readonly services = i18nKeys.booking.services;
  protected readonly isExpanded = signal<boolean>(false);
  protected readonly selectedCheckboxOptions = signal<Set<string>>(new Set());

  protected readonly hasOptions = computed(() => this.operations().length > 0);
  protected readonly operationCount = computed(() => this.operations().length);
  protected readonly selectedCount = computed(() => this.selectedOperationIds().length);

  /**
   * Bootstrap-Icon-Klasse pro Kategorie, gemappt auf Tag-Rank.
   * SoftNet liefert keine iconUrl fuer Tags → lokales Mapping als Uebergangsloesung.
   */
  protected readonly iconClass = computed<string>(() => {
    switch (this.category().rank) {
      case 0: return 'bi-tools';
      case 1: return 'bi-clipboard-check';
      case 2: return 'bi-droplet-half';
      case 3: return 'bi-record-circle';
      case 4: return 'bi-check2-circle';
      case 5: return 'bi-search';
      case 6: return 'bi-car-front';
      case 99: return 'bi-arrow-left-right';
      default: return 'bi-tools';
    }
  });

  protected readonly hasOptionsChanged = computed(() => {
    const confirmed = this.selectedOperationIds();
    const selected = this.selectedCheckboxOptions();
    if (confirmed.length === 0 || selected.size === 0) return false;
    if (confirmed.length !== selected.size) return true;
    return !confirmed.every((id) => selected.has(id));
  });

  protected readonly showConfirmButton = computed(() => {
    if (!this.hasOptions()) return false;
    if (!this.isExpanded()) return false;
    if (!this.isSelected()) return true;
    return this.hasOptionsChanged();
  });

  protected readonly showDeselectButton = computed(() =>
    this.isSelected() && this.hasOptions() && this.isExpanded() && !this.hasOptionsChanged()
  );

  constructor() {
    effect(() => {
      const optionIds = this.selectedOperationIds();
      if (optionIds.length > 0) {
        this.selectedCheckboxOptions.set(new Set(optionIds));
      }
    });
  }

  protected onCardClick(): void {
    if (this.hasOptions()) {
      this.isExpanded.set(!this.isExpanded());
    }
    // Emittet immer die Category-ID — der Container setzt damit die aktive Kategorie.
    this.serviceClicked.emit(this.category().id);
  }

  protected onCheckboxChange(operationId: string, checked: boolean): void {
    const current = new Set(this.selectedCheckboxOptions());
    if (checked) {
      current.add(operationId);
    } else {
      current.delete(operationId);
    }
    this.selectedCheckboxOptions.set(current);
  }

  protected onConfirm(): void {
    const optionIds = Array.from(this.selectedCheckboxOptions());
    if (optionIds.length > 0) {
      this.serviceConfirmed.emit({ serviceId: this.category().id, optionIds });
    }
  }

  protected onDeselect(): void {
    this.serviceDeselected.emit(this.category().id);
    this.selectedCheckboxOptions.set(new Set());
    this.isExpanded.set(false);
  }
}
