import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type { ApiCategory, ApiOperation } from '../../models/service.model';

/**
 * REQ-025 (b) Iter. 3: ServiceCardComponent rendert eine Service-Kategorie (Tag aus SoftNet).
 * Sub-Options sind Operations mit dem entsprechenden Tag, ihre `description` wird klein
 * direkt unter dem Operation-Namen angezeigt.
 */
@Component({
  selector: 'app-service-card',
  standalone: true,
  imports: [MatCardModule, MatIconModule, MatButtonModule, MatCheckboxModule, TranslatePipe],
  templateUrl: './service-card.component.html',
  styleUrl: './service-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServiceCardComponent {
  public readonly category = input.required<ApiCategory>();
  public readonly operations = input.required<ApiOperation[]>();
  public readonly isSelected = input<boolean>(false);
  public readonly selectedOperationIds = input<string[]>([]);

  /** Emittet die Operation-ID beim Klick (wenn Card keine Operations hat). */
  public readonly serviceClicked = output<string>();
  public readonly serviceConfirmed = output<{ serviceId: string; optionIds: string[] }>();
  public readonly serviceDeselected = output<string>();

  protected readonly services = i18nKeys.booking.services;
  protected readonly isExpanded = signal<boolean>(false);
  protected readonly selectedCheckboxOptions = signal<Set<string>>(new Set());

  protected readonly hasOptions = computed(() => this.operations().length > 0);
  protected readonly operationCount = computed(() => this.operations().length);

  /**
   * REQ-025 (b) Iter. 3: SVG-Icon-Pfad pro Kategorie, gemappt auf Tag-Rank.
   * SoftNet liefert keine iconUrl für Tags → lokales Mapping als Übergangslösung.
   */
  private static readonly ICON_BY_RANK: Record<number, string> = {
    0: 'assets/icons/services/inspection.svg',
    1: 'assets/icons/services/tuv.svg',
    2: 'assets/icons/services/brake-fluid.svg',
    3: 'assets/icons/services/tire-change.svg',
    4: 'assets/icons/services/actions-checks.svg',
    5: 'assets/icons/services/repair.svg',
    6: 'assets/icons/services/bodywork.svg',
    99: 'assets/icons/services/pickup-delivery.svg'  // virtuelle Hol-&-Bringservice-Card
  };

  protected readonly iconPath = computed<string | null>(() =>
    ServiceCardComponent.ICON_BY_RANK[this.category().rank] ?? null
  );

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
      return;
    }
    // Keine Operations → Card direkt auswählen via Category-ID
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
