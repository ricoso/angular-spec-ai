import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { i18nKeys, TranslatePipe, TranslateService } from '@core/i18n';

import { BookingStore } from '../../stores/booking.store';

@Component({
  selector: 'app-rent-category-price-block',
  standalone: true,
  imports: [TranslatePipe, MatIconModule],
  templateUrl: './rent-category-price-block.component.html',
  styleUrl: './rent-category-price-block.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RentCategoryPriceBlockComponent {
  private readonly store = inject(BookingStore);
  private readonly translate = inject(TranslateService);

  protected readonly booking = i18nKeys.booking;
  protected readonly priceInfo = this.store.selectedRentCategoryPrice;

  protected readonly formattedPrice = computed(() => {
    const info = this.priceInfo();
    if (!info) return '';
    const locale = this.translate.getCurrentLanguage();
    try {
      return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: info.currency
      }).format(info.price);
    } catch {
      return `${info.price} ${info.currency}`;
    }
  });

  protected readonly unitLabelKey = computed(() => {
    const unit = this.priceInfo()?.priceUnit;
    return unit === 'hour'
      ? this.booking.notes.rentCategoryPrice.unitHour
      : this.booking.notes.rentCategoryPrice.unitDay;
  });
}
