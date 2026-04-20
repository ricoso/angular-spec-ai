import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AVAILABLE_BRANDS } from '../../models/brand.model';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStore } from '../../stores/booking.store';

import { BrandSelectionContainerComponent } from './brand-selection-container.component';

// UI rendering is verified via E2E (Playwright) — unit tests focus on logic only
describe('BrandSelectionContainerComponent', () => {
  let component: BrandSelectionContainerComponent;
  let fixture: ComponentFixture<BrandSelectionContainerComponent>;
  let router: jest.Mocked<Router>;
  let store: InstanceType<typeof BookingStore>;

  beforeEach(async () => {
    router = { navigate: jest.fn() } as unknown as jest.Mocked<Router>;

    await TestBed.configureTestingModule({
      imports: [BrandSelectionContainerComponent],
      providers: [
        BookingStore,
        {
          provide: BookingApiService,
          useValue: {
            getBrands: jest.fn().mockResolvedValue(AVAILABLE_BRANDS),
            getBrandsByBranch: jest.fn().mockResolvedValue([]),
            getBranchById: jest.fn().mockResolvedValue(null),
            getBrandLogoPath: jest.fn().mockReturnValue('assets/brands/default.svg'),
            getBranches: jest.fn().mockResolvedValue([]),
            getServices: jest.fn().mockResolvedValue([])
          }
        },
        { provide: Router, useValue: router }
      ]
    })
      .overrideComponent(BrandSelectionContainerComponent, {
        set: { template: '<div class="mocked">Mocked Brand Selection</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    fixture = TestBed.createComponent(BrandSelectionContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should inject BookingStore', () => {
    expect(store).toBeTruthy();
  });

  describe('Brand Selection', () => {
    it('should set brand in store on selection', () => {
      const exposed = component as unknown as { onBrandSelect: (b: string) => void };
      exposed.onBrandSelect('audi');

      expect(store.selectedBrand()).toBe('audi');
    });

    it('should set different brand in store', () => {
      const exposed = component as unknown as { onBrandSelect: (b: string) => void };
      exposed.onBrandSelect('bmw');

      expect(store.selectedBrand()).toBe('bmw');
    });
  });

  describe('Navigation', () => {
    it('should clear brand and navigate to /home/location on back', () => {
      const exposed = component as unknown as { onBrandSelect: (b: string) => void; onBack: () => void };
      exposed.onBrandSelect('audi');
      exposed.onBack();

      expect(store.selectedBrand()).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/home/location']);
    });
  });

  describe('Branch Address', () => {
    it('should return empty string when no branch is selected', () => {
      const exposed = component as unknown as { formattedBranchAddress: () => string };
      expect(exposed.formattedBranchAddress()).toBe('');
    });

    it('should format branch address correctly', () => {
      store.selectBranch({
        branchId: 'b1',
        name: 'Autohaus Test',
        address: { street: 'Musterstr. 1', zip: '12345', city: 'Berlin' },
        brands: ['audi']
      });
      const exposed = component as unknown as { formattedBranchAddress: () => string };
      expect(exposed.formattedBranchAddress()).toBe('Musterstr. 1');
    });

    it('should fall back to branch name when street is empty', () => {
      store.selectBranch({
        branchId: 'b2',
        name: 'Fallback Name',
        address: { street: '', zip: '00000', city: 'Nowhere' },
        brands: ['bmw']
      });
      const exposed = component as unknown as { formattedBranchAddress: () => string };
      expect(exposed.formattedBranchAddress()).toBe('Fallback Name');
    });
  });

  describe('Loading and Error States', () => {
    it('should expose isLoading signal from store', () => {
      const exposed = component as unknown as { isLoading: () => boolean };
      expect(exposed.isLoading()).toBe(false);
    });

    it('should expose error signal from store', () => {
      const exposed = component as unknown as { error: () => string | null };
      expect(exposed.error()).toBeNull();
    });

    it('should call store.loadBrands on retry when branch is selected', () => {
      store.selectBranch({
        branchId: 'b1',
        name: 'Test',
        address: { street: 'Test', zip: '12345', city: 'Berlin' },
        brands: ['audi']
      });
      const loadBrandsSpy = jest.spyOn(store, 'loadBrands');
      const exposed = component as unknown as { onRetry: () => void };
      exposed.onRetry();
      expect(loadBrandsSpy).toHaveBeenCalledWith('b1');
    });

    it('should not call store.loadBrands on retry when no branch is selected', () => {
      const loadBrandsSpy = jest.spyOn(store, 'loadBrands');
      const exposed = component as unknown as { onRetry: () => void };
      exposed.onRetry();
      expect(loadBrandsSpy).not.toHaveBeenCalled();
    });
  });
});
