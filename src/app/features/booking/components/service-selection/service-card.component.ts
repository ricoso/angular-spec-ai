import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import type { ApiCategory, ApiOperation } from '../../models/service.model';
import { categoryIconClass } from '../../utils/category-icon.util';

/**
 * REQ-039: Kategorie-Pill (vormals Karussell-Mini-Card). Zeigt pro Kategorie ein Icon, den Titel
 * und einen Counter (selected/total oder nur total). Klick aktiviert die Kategorie; die
 * Operations-Auswahl lebt im Detail-Panel des Containers.
 */
@Component({
  selector: 'app-service-card',
  standalone: true,
  templateUrl: './service-card.component.html',
  styleUrl: './service-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServiceCardComponent {
  public readonly category = input.required<ApiCategory>();
  public readonly operations = input.required<ApiOperation[]>();
  public readonly isSelected = input<boolean>(false);
  public readonly selectedOperationIds = input<string[]>([]);
  /** REQ-039: this category is the one shown in the operations panel (filled pill). */
  public readonly isActive = input<boolean>(false);

  /** Emittet die Category-ID beim Klick auf die Pill. */
  public readonly serviceClicked = output<string>();

  protected readonly hasOptions = computed(() => this.operations().length > 0);
  protected readonly operationCount = computed(() => this.operations().length);
  protected readonly selectedCount = computed(() => this.selectedOperationIds().length);

  /** Bootstrap-Icon-Klasse pro Kategorie (Tag-Rank), gemeinsam mit dem Detail-Panel. */
  protected readonly iconClass = computed<string>(() => categoryIconClass(this.category()));

  protected onCardClick(): void {
    // Der Container setzt damit die aktive Kategorie im Detail-Panel.
    this.serviceClicked.emit(this.category().id);
  }
}
