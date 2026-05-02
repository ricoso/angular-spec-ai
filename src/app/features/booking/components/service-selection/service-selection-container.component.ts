import type { ElementRef} from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';

import type { ApiCategory, ApiOperation, ServiceType } from '../../models/service.model';
import { BookingStore } from '../../stores/booking.store';

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

  protected readonly booking = i18nKeys.booking;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 2;
  /** REQ-025 (b) Iter. 3: Tags + virtuelle „Hol & Bringservice"-Card als Cards. */
  protected readonly categories = this.store.categoriesWithVirtual;
  /** REQ-025 (b) Iter. 3: Operations gruppiert nach Tag-ID. */
  protected readonly operationsByCategory = this.store.operationsByCategory;
  protected readonly hasServicesSelected = this.store.hasServicesSelected;
  protected readonly selectedServiceCount = this.store.selectedServiceCount;
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

  // --- Karussell-State (Detail-Panel + Scroll) ---------------------------------

  /** Aktive Kategorie im Karussell — initial die erste Kategorie. */
  protected readonly activeCategoryId = signal<string | null>(null);

  /** Referenz auf den Track-Container fuer programmatisches Scrollen. */
  private readonly trackEl = viewChild<ElementRef<HTMLDivElement>>('carTrack');

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

  /** Anzahl Slides fuer Indikator-Dots (4 Mini-Cards pro Slide laut Mockup). */
  protected readonly slideCount = computed<number>(() => {
    const total = this.categories().length;
    return total === 0 ? 0 : Math.ceil(total / 4);
  });

  /** Aktiver Indikator-Dot — abgeleitet vom Scroll-Offset. */
  protected readonly activeSlideIndex = signal<number>(0);

  /** Bootstrap-Icon-Klasse fuer den Detail-Panel-Header (gemappt auf Tag-Rank). */
  protected readonly activeIconClass = computed<string>(() => {
    const cat = this.activeCategory();
    if (!cat) return 'bi-tools';
    switch (cat.rank) {
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

  /** Komma-getrennte Liste der Namen aller aktuell selektierten Operations (alle Kategorien). */
  protected readonly selectedNamesList = computed<string>(() => {
    const selectedIds = new Set<string>(this.selectedServiceIds() as string[]);
    const opsByCat = this.operationsByCategory();
    const names: string[] = [];
    for (const catId of Object.keys(opsByCat)) {
      for (const op of opsByCat[catId]) {
        if (selectedIds.has(op.id)) {
          names.push(op.name);
        }
      }
    }
    return names.join(' · ');
  });

  constructor() {
    // REQ-025 (b) Iter. 3: Categories + Operations parallel laden, sobald Branch da ist.
    effect(() => {
      const branch = this.store.selectedBranch();
      const cats = this.store.categories();
      const ops = this.store.operations();
      if (branch && cats.length === 0) {
        this.store.loadCategoriesForBranch(branch.branchId);
      }
      if (branch && ops.length === 0) {
        this.store.loadOperationsForBranch(branch.branchId);
      }
    });

    // Auto-Auswahl: bevorzugt Inspektion (rank=0), Fallback erste echte Kategorie.
    // Hol & Bringservice (rank=99, virtual) wird NIE auto-aktiviert.
    // Wichtig: erst feuern wenn echte (nicht-virtuelle) Categories da sind —
    // sonst gewinnt das virtuelle Pickup-Tile bei Race (Operations vor Categories).
    effect(() => {
      const cats = this.categories();
      const realCats = cats.filter(c => c.rank !== 99);
      if (realCats.length === 0) { return; }
      const current = this.activeCategoryId();
      if (current !== null && current !== 'virtual-pickup-delivery') { return; }
      const inspektion = realCats.find(c => c.rank === 0);
      const target = inspektion ?? realCats[0];
      this.activeCategoryId.set(target.id);
    });
  }

  protected onServiceClick(operationId: string): void {
    this.store.toggleService(operationId as ServiceType);
  }

  protected onServiceConfirm(event: { serviceId: string; optionIds: string[] }): void {
    // event.serviceId ist die Category-ID (Tag), optionIds sind Operation-IDs.
    // Erst alle Operations dieser Kategorie raus, dann nur die confirmed wieder rein.
    const opsInCategory = this.operationsByCategory()[event.serviceId] ?? [];
    for (const op of opsInCategory) {
      this.store.deselectService(op.id as ServiceType);
    }
    for (const opId of event.optionIds) {
      this.store.confirmServiceOptions(opId as ServiceType, []);
    }
  }

  protected onServiceDeselect(categoryId: string): void {
    // Alle Operations dieser Kategorie deselektieren.
    const opsToRemove = this.operationsByCategory()[categoryId] ?? [];
    for (const op of opsToRemove) {
      this.store.deselectService(op.id as ServiceType);
    }
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

  // --- Karussell Scroll --------------------------------------------------------

  protected onCarouselPrev(): void {
    const el = this.trackEl()?.nativeElement;
    if (!el) return;
    el.scrollBy({ left: -(el.clientWidth - 16), behavior: 'smooth' });
  }

  protected onCarouselNext(): void {
    const el = this.trackEl()?.nativeElement;
    if (!el) return;
    el.scrollBy({ left: el.clientWidth - 16, behavior: 'smooth' });
  }

  protected onTrackScroll(): void {
    const el = this.trackEl()?.nativeElement;
    if (!el) return;
    const denom = Math.max(el.clientWidth - 16, 1);
    const idx = Math.round(el.scrollLeft / denom);
    this.activeSlideIndex.set(idx);
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
