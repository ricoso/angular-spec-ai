import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';

import type { Language } from '@app/core/i18n';
import { i18nKeys, TranslatePipe, TranslateService } from '@app/core/i18n';
import { AVAILABLE_SERVICES } from '@app/features/booking/models/service.model';
import { BookingStore } from '@app/features/booking/stores/booking.store';
import type { FontSize } from '@app/shared/models/accessibility.model';
import { AccessibilityStore } from '@app/shared/stores/accessibility.store';
import { CartStore } from '@app/shared/stores/cart.store';

import { AccessibilityMenuComponent } from './components/accessibility-menu/accessibility-menu.component';
import { CartIconComponent } from './components/cart-icon/cart-icon.component';

/**
 * Container Component for the Application Header
 * Connects AccessibilityStore, CartStore, and BookingStore
 */
const IFRAME_RETURN_URL = 'https://www.gottfried-schultz.de/werkstatt-termin/';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatChipsModule,
    TranslatePipe,
    AccessibilityMenuComponent,
    CartIconComponent
  ],
  templateUrl: './header-container.component.html',
  styleUrl: './header-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HeaderContainerComponent {
  protected readonly accessibilityStore = inject(AccessibilityStore);
  protected readonly cartStore = inject(CartStore);
  protected readonly bookingStore = inject(BookingStore);
  private readonly translateService = inject(TranslateService);

  protected readonly app = i18nKeys.app;
  protected readonly header = i18nKeys.header;
  protected readonly currentLanguage = this.translateService.getLanguageSignal();

  protected readonly isEmbedded = signal(this.detectEmbedded());

  protected readonly selectedBrandName = computed(() => {
    const brand = this.bookingStore.selectedBrand();
    return brand ? brand.charAt(0).toUpperCase() + brand.slice(1) : null;
  });

  protected readonly selectedLocationName = computed(() => {
    const location = this.bookingStore.selectedLocation();
    return location ? location.name : null;
  });

  protected readonly cartServiceChips = computed(() => {
    // REQ-025 (b) Iter. 3: Operations primär, JobTypes als Fallback, AVAILABLE_SERVICES als Legacy.
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
    const selected = this.bookingStore.selectedServices();
    const operations = this.bookingStore.operations();
    const categories = this.bookingStore.categories();
    const jobTypes = this.bookingStore.jobTypes();
    return selected.map(s => {
      const op = operations.find(o => o.id === s.serviceId);
      if (op) {
        // Icon: rank-niedrigster Tag der Operation, oder rank=99 für Hol & Bringservice
        const primaryRank = categories
          .filter(c => op.tagIds.includes(c.id))
          .map(c => c.rank)
          .sort((a, b) => a - b)[0] ?? 0;
        const rank = op.name === 'Hol & Bringservice' ? 99 : primaryRank;
        return { title: op.name, svgIcon: ICON_BY_RANK[rank] ?? ICON_BY_RANK[0], optionLabels: [] as string[] };
      }
      const jt = jobTypes.find(j => j.id === s.serviceId);
      if (jt) {
        const optionLabels: string[] = [];
        if (s.selectedOptionIds.length > 0) {
          for (const optionId of s.selectedOptionIds) {
            const op = jt.operations.find(o => o.id === optionId);
            if (op) optionLabels.push(op.name);
          }
        }
        return { title: jt.name, svgIcon: jt.iconUrl ?? 'build', optionLabels };
      }
      const serviceData = AVAILABLE_SERVICES.find(svc => svc.id === s.serviceId);
      const title = serviceData ? this.translateService.instant(serviceData.titleKey) : s.serviceId;
      const svgIcon = serviceData?.svgIcon ?? 'build';
      const optionLabels: string[] = [];
      if (s.selectedOptionIds.length > 0 && serviceData) {
        for (const optionId of s.selectedOptionIds) {
          const option = serviceData.options.find(o => o.id === optionId);
          if (option) {
            optionLabels.push(this.translateService.instant(option.labelKey));
          }
        }
      }
      return { title, svgIcon, optionLabels };
    });
  });

  protected readonly selectedAppointmentText = computed(() => {
    const appointment = this.bookingStore.selectedAppointment();
    if (!appointment) return null;
    return `${appointment.displayDate}, ${appointment.displayTime}`;
  });

  protected readonly cartSummaryText = computed(() => {
    const count = this.bookingStore.selectedServiceCount();
    const template = this.translateService.instant(this.header.cart.summary);
    return template.replace('{count}', `${count}`);
  });

  protected onFontSizeChange(fontSize: FontSize): void {
    this.accessibilityStore.setFontSize(fontSize);
  }

  protected onHighContrastChange(value: boolean): void {
    this.accessibilityStore.setHighContrast(value);
  }

  protected onReducedMotionChange(value: boolean): void {
    this.accessibilityStore.setReducedMotion(value);
  }

  protected onLanguageChange(language: Language): void {
    this.translateService.use(language);
  }

  protected onCartOpen(): void {
    // Cart menu opens via matMenuTriggerFor
  }

  protected onClose(): void {
    const target = window.top ?? window;
    target.location.href = IFRAME_RETURN_URL;
  }

  private detectEmbedded(): boolean {
    try {
      return window.self !== window.top;
    } catch {
      return true;
    }
  }
}
