import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import type { BrandDisplay } from '../../models/brand.model';
import { AVAILABLE_BRANDS } from '../../models/brand.model';

import { BrandCardsComponent } from './brand-cards.component';

describe('BrandCardsComponent', () => {
  let component: BrandCardsComponent;
  let fixture: ComponentFixture<BrandCardsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BrandCardsComponent]
    })
      .overrideComponent(BrandCardsComponent, {
        set: { template: '<div class="mocked">Mocked</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(BrandCardsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('brands', AVAILABLE_BRANDS);
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should compute enrichedBrands with logoPath for known brands', () => {
    const exposed = component as unknown as { enrichedBrands: () => { logoPath: string }[] };
    const enriched = exposed.enrichedBrands();
    expect(enriched.length).toBe(AVAILABLE_BRANDS.length);
    enriched.forEach(b => {
      expect(typeof b.logoPath).toBe('string');
    });
  });

  it('should set empty logoPath for unknown brand names', () => {
    fixture.componentRef.setInput('brands', [
      { id: 'audi', name: 'UnknownBrand' } as BrandDisplay
    ]);
    fixture.detectChanges();
    const exposed = component as unknown as { enrichedBrands: () => { logoPath: string }[] };
    expect(exposed.enrichedBrands()[0].logoPath).toBe('');
  });

  it('should emit brandSelected on onSelect', () => {
    const spy = jest.fn();
    component.brandSelected.subscribe(spy);
    const exposed = component as unknown as { onSelect: (b: BrandDisplay) => void };
    exposed.onSelect(AVAILABLE_BRANDS[0]);
    expect(spy).toHaveBeenCalledWith(AVAILABLE_BRANDS[0].id);
  });

  it('should accept selectedBrand input', () => {
    fixture.componentRef.setInput('selectedBrand', 'audi');
    fixture.detectChanges();
    expect(component.selectedBrand()).toBe('audi');
  });
});
