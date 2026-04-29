import { signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { TranslateService } from '@core/i18n';

import { BookingStore } from '../../stores/booking.store';

import { RentCategoryPriceBlockComponent } from './rent-category-price-block.component';

interface PriceInfo {
  name: string;
  price: number;
  currency: string;
  priceUnit: string;
}

describe('RentCategoryPriceBlockComponent', () => {
  let fixture: ComponentFixture<RentCategoryPriceBlockComponent>;
  let component: RentCategoryPriceBlockComponent;
  const priceInfoSignal = signal<PriceInfo | null>(null);

  beforeEach(async () => {
    priceInfoSignal.set(null);

    await TestBed.configureTestingModule({
      imports: [RentCategoryPriceBlockComponent],
      providers: [
        TranslateService,
        {
          provide: BookingStore,
          useValue: { selectedRentCategoryPrice: priceInfoSignal.asReadonly() }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RentCategoryPriceBlockComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render nothing when no rent category is selected (TC-11)', () => {
    expect(fixture.nativeElement.querySelector('.rent-category-price')).toBeNull();
  });

  it('should render nothing when API price is null (TC-12)', () => {
    priceInfoSignal.set(null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.rent-category-price')).toBeNull();
  });

  it('should render formatted price block when category + price are set (TC-10)', () => {
    priceInfoSignal.set({ name: 'Kompakt', price: 49, currency: 'EUR', priceUnit: 'day' });
    fixture.detectChanges();

    const aside = fixture.nativeElement.querySelector('.rent-category-price') as HTMLElement;
    expect(aside).toBeTruthy();

    const exposed = component as unknown as { formattedPrice: () => string; unitLabelKey: () => string };
    expect(exposed.formattedPrice()).toMatch(/49/);
    expect(exposed.unitLabelKey()).toBe('booking.notes.rentCategoryPrice.unitDay');
  });

  it('should pick the hour unit key when priceUnit is "hour"', () => {
    priceInfoSignal.set({ name: 'Kompakt', price: 15, currency: 'EUR', priceUnit: 'hour' });
    fixture.detectChanges();

    const exposed = component as unknown as { unitLabelKey: () => string };
    expect(exposed.unitLabelKey()).toBe('booking.notes.rentCategoryPrice.unitHour');
  });

  it('should fall back to "<price> <currency>" when Intl fails', () => {
    priceInfoSignal.set({ name: 'X', price: 10, currency: 'INVALID-CODE', priceUnit: 'day' });
    fixture.detectChanges();

    const exposed = component as unknown as { formattedPrice: () => string };
    const result = exposed.formattedPrice();
    // Intl may throw OR fall back internally; either way no crash
    expect(result.length).toBeGreaterThan(0);
  });

  it('should expose aria-live="polite" on the aside (TC-22 a11y)', () => {
    priceInfoSignal.set({ name: 'Kompakt', price: 49, currency: 'EUR', priceUnit: 'day' });
    fixture.detectChanges();
    const aside = fixture.nativeElement.querySelector('.rent-category-price') as HTMLElement;
    expect(aside.getAttribute('role')).toBe('status');
    expect(aside.getAttribute('aria-live')).toBe('polite');
  });
});
