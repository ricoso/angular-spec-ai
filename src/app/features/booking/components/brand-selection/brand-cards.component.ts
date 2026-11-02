import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import { BRAND_LOGO_MAP } from '../../models/branch-config.model';
import type { Brand, BrandDisplay } from '../../models/brand.model';

export interface EnrichedBrand extends BrandDisplay {
  logoPath: string;
}

@Component({
  selector: 'app-brand-cards',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './brand-cards.component.html',
  styleUrl: './brand-cards.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandCardsComponent {
  public readonly brands = input.required<BrandDisplay[]>();
  public readonly selectedBrand = input<Brand | null>(null);
  public readonly brandSelected = output<Brand>();

  protected readonly booking = i18nKeys.booking;

  protected readonly enrichedBrands = computed<EnrichedBrand[]>(() =>
    this.brands().map(brand => ({
      ...brand,
      logoPath: BRAND_LOGO_MAP[brand.name] ? `assets/brands/${BRAND_LOGO_MAP[brand.name]}` : ''
    }))
  );

  protected onSelect(brand: BrandDisplay): void {
    this.brandSelected.emit(brand.id);
  }
}
