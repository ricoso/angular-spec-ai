import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { BookingStore } from '@app/features/booking/stores/booking.store';
import type { Language } from '@core/i18n';
import { i18nKeys, TranslatePipe, TranslateService } from '@core/i18n';
import type { FontSize } from '@shared/models/accessibility.model';
import { AccessibilityStore } from '@shared/stores/accessibility.store';
import { CartStore } from '@shared/stores/cart.store';

import { AccessibilityMenuComponent } from '../header/components/accessibility-menu/accessibility-menu.component';

@Component({
  selector: 'app-floating-overlay',
  standalone: true,
  imports: [TranslatePipe, AccessibilityMenuComponent],
  templateUrl: './floating-overlay.component.html',
  styleUrl: './floating-overlay.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FloatingOverlayComponent {
  private readonly cartStore = inject(CartStore);
  private readonly bookingStore = inject(BookingStore);
  private readonly a11yStore = inject(AccessibilityStore);
  private readonly translateService = inject(TranslateService);

  protected readonly overlay = i18nKeys.booking.overlay;
  protected readonly isExpanded = signal(false);
  protected readonly isA11yOpen = signal(false);

  protected readonly badgeText = this.cartStore.badgeText;
  protected readonly hasItems = this.cartStore.hasItems;

  protected readonly selectedLocationName = computed(() => this.bookingStore.selectedLocation()?.formattedAddress ?? '');
  protected readonly selectedBrandName = computed(() => this.bookingStore.selectedBrand() ?? '');
  protected readonly selectedServiceCount = computed(() => this.bookingStore.selectedServiceCount());
  protected readonly selectedServiceLabels = computed(() => this.bookingStore.selectedServiceLabels());

  protected readonly fontSize = this.a11yStore.fontSize;
  protected readonly highContrast = this.a11yStore.highContrast;
  protected readonly reducedMotion = this.a11yStore.reducedMotion;
  protected readonly currentLanguage = computed(() => this.translateService.getCurrentLanguage());

  protected onToggleCart(): void {
    this.isA11yOpen.set(false);
    this.isExpanded.update(v => !v);
  }

  protected onCloseCart(): void {
    this.isExpanded.set(false);
  }

  protected onToggleAccessibility(): void {
    this.isExpanded.set(false);
    this.isA11yOpen.update(v => !v);
  }

  protected onCloseAccessibility(): void {
    this.isA11yOpen.set(false);
  }

  protected onFontSizeChange(size: FontSize): void {
    this.a11yStore.setFontSize(size);
  }

  protected onHighContrastChange(value: boolean): void {
    this.a11yStore.setHighContrast(value);
  }

  protected onReducedMotionChange(value: boolean): void {
    this.a11yStore.setReducedMotion(value);
  }

  protected onLanguageChange(language: Language): void {
    this.translateService.use(language);
  }
}
