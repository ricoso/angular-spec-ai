import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe, TranslateService } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { ApiCategory, ApiOperation, ServiceSelectionSummary, ServiceType } from '../../models/service.model';
import { BookingStore } from '../../stores/booking.store';
import { categoryIconClass } from '../../utils/category-icon.util';

import { ServiceCardComponent } from './service-card.component';

@Component({
  selector: 'app-service-selection-container',
  standalone: true,
  imports: [TranslatePipe, WizardBreadcrumbComponent, ServiceCardComponent],
  templateUrl: './service-selection-container.component.html',
  styleUrl: './service-selection-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServiceSelectionContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);
  private readonly translateService = inject(TranslateService);

  protected readonly booking = i18nKeys.booking;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 2;
  /** REQ-025 (b) Iter. 3: Tags + virtuelle „Hol & Bringservice"-Card als Cards.
   *  Im tireChange-Boxenstopp-Modus (Deep-Link mit Radwechsel-groupId) zeigt der
   *  Store nur noch die "Räderwechsel"-Kategorie. */
  protected readonly categories = this.store.visibleCategories;
  /** REQ-025 (b) Iter. 3: Operations gruppiert nach Tag-ID. */
  protected readonly operationsByCategory = this.store.operationsByCategory;
  protected readonly hasServicesSelected = this.store.hasServicesSelected;
  protected readonly isLoading = this.store.isLoading;
  protected readonly error = this.store.error;

  protected readonly selectedServiceIds = computed(() =>
    this.store.selectedServices().map(s => s.serviceId)
  );

  /** Map Category-ID → selectedOperationIds. */
  protected readonly selectedOptionsMap = computed((): Record<string, string[]> => {
    const map: Record<string, string[]> = {};
    const selected = this.store.selectedServices();
    const opsByCat = this.store.operationsByCategory();
    // operationsByCategory enthält bereits die virtuelle Pickup-Card + alle echten Tags
    for (const catId of Object.keys(opsByCat)) {
      const ids: string[] = [];
      const opsInCat = opsByCat[catId];
      for (const sel of selected) {
        if (opsInCat.some(o => o.id === sel.serviceId)) {
          ids.push(sel.serviceId);
        }
      }
      map[catId] = ids;
    }
    return map;
  });

  /** Pro Card: ist mindestens eine Operation aus dieser Kategorie selektiert? */
  protected readonly categorySelectedMap = computed((): Record<string, boolean> => {
    const result: Record<string, boolean> = {};
    const map = this.selectedOptionsMap();
    for (const catId of Object.keys(map)) {
      result[catId] = map[catId].length > 0;
    }
    return result;
  });

  // --- Aktive Kategorie (Pill + Arbeiten-Panel) --------------------------------

  /** Aktive Kategorie — initial Inspektion bzw. die erste Kategorie. */
  protected readonly activeCategoryId = signal<string | null>(null);

  protected readonly activeCategory = computed<ApiCategory | null>(() => {
    const id = this.activeCategoryId();
    if (!id) return null;
    return this.categories().find(c => c.id === id) ?? null;
  });

  protected readonly activeOperations = computed<ApiOperation[]>(() => {
    const id = this.activeCategoryId();
    if (!id) return [];
    return this.operationsByCategory()[id] ?? [];
  });

  protected readonly activeSelectedIds = computed<string[]>(() => {
    const id = this.activeCategoryId();
    if (!id) return [];
    return this.selectedOptionsMap()[id] ?? [];
  });

  protected readonly activeSelectedSet = computed<Set<string>>(() =>
    new Set(this.activeSelectedIds())
  );

  /** REQ-039: panel heading "Verfügbare Arbeiten im Bereich {category}". */
  protected readonly panelHeading = computed<string>(() =>
    this.translateService.instant(this.booking.services.panelHeading)
      .replace('{category}', this.activeCategory()?.name ?? '')
  );

  /** Bootstrap-Icon-Klasse fuer den Detail-Panel-Header (gemappt auf Tag-Rank). */
  protected readonly activeIconClass = computed<string>(() => categoryIconClass(this.activeCategory()));

  /** REQ-039: green summary — selected operations grouped by category (mockup 02-service). */
  protected readonly selectionSummary = computed<ServiceSelectionSummary[]>(() => {
    const selectedIds = new Set<string>(this.selectedServiceIds() as string[]);
    const opsByCat = this.operationsByCategory();
    return this.categories()
      .map(cat => ({
        id: cat.id,
        name: cat.name,
        operations: (opsByCat[cat.id] ?? [])
          .filter(op => selectedIds.has(op.id))
          .map(op => op.name)
          .join('; ')
      }))
      .filter(group => group.operations.length > 0);
  });

  /** Tracked Kombination aus branchId + deepLinkGroupId, um Categories+Operations
   *  nur einmal pro (Branch × Group) zu laden — verhindert Endlos-Loop, wenn das
   *  Backend ein leeres Array zurückgibt (z. B. tireChange-Group ohne Tags). */
  private loadedKey: string | null = null;

  constructor() {
    // REQ-025 (b) Iter. 3: Categories + Operations laden, sobald Branch gewählt ist;
    // Re-Load nur bei Wechsel von Branch ODER tireChange-Variante.
    effect(() => {
      const branch = this.store.selectedBranch();
      const groupId = this.store.deepLinkGroupId();
      if (!branch) { return; }
      const key = `${branch.branchId}#${groupId ?? ''}`;
      if (this.loadedKey === key) { return; }
      this.loadedKey = key;
      this.store.loadCategoriesForBranch(branch.branchId);
      this.store.loadOperationsForBranch(branch.branchId);
    });

    // Default-Auswahl: bevorzugt Inspektion (rank=0), Fallback erste echte Kategorie.
    // Greift NUR bei initialem Laden oder nach Branch-Wechsel mit ungültiger alter ID.
    // Wenn der User aktiv eine Kategorie (auch Hol & Bring, rank=99) angeklickt hat,
    // bleibt seine Auswahl erhalten — vorher wurde sie hier fälschlich überschrieben.
    effect(() => {
      const cats = this.categories();
      const realCats = cats.filter(c => c.rank !== 99);
      if (realCats.length === 0) { return; }
      const current = this.activeCategoryId();
      // current ist eine gültige Kategorie der aktuellen Liste → User-Auswahl behalten.
      if (current !== null && cats.some(c => c.id === current)) { return; }
      const inspektion = realCats.find(c => c.rank === 0);
      const target = inspektion ?? realCats[0];
      this.activeCategoryId.set(target.id);
    });
  }

  /** Aktiviert die Kategorie im Detail-Panel — keine Store-Mutation. */
  protected onMiniCardClick(categoryId: string): void {
    this.activeCategoryId.set(categoryId);
  }

  /** Toggle einer Operation im Detail-Panel. */
  protected onOperationToggle(operationId: string, checked: boolean): void {
    if (checked) {
      this.store.confirmServiceOptions(operationId as ServiceType, []);
    } else {
      this.store.deselectService(operationId as ServiceType);
    }
  }

  protected onContinue(): void {
    void this.router.navigate(['/home/notes']);
  }

  protected onBack(): void {
    this.store.clearSelectedServices();
    void this.router.navigate(['/home/brand']);
  }

  protected onRetry(): void {
    const branch = this.store.selectedBranch();
    if (branch) {
      this.store.loadCategoriesForBranch(branch.branchId);
      this.store.loadOperationsForBranch(branch.branchId);
    }
  }
}
