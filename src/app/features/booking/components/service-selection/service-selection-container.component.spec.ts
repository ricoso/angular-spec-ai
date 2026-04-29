import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AVAILABLE_BRANDS } from '../../models/brand.model';
import { AVAILABLE_SERVICES } from '../../models/service.model';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStore } from '../../stores/booking.store';

import { ServiceSelectionContainerComponent } from './service-selection-container.component';

describe('ServiceSelectionContainerComponent', () => {
  let component: ServiceSelectionContainerComponent;
  let fixture: ComponentFixture<ServiceSelectionContainerComponent>;
  let router: jest.Mocked<Router>;
  let store: InstanceType<typeof BookingStore>;

  beforeEach(async () => {
    router = { navigate: jest.fn() } as unknown as jest.Mocked<Router>;

    await TestBed.configureTestingModule({
      imports: [ServiceSelectionContainerComponent],
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
            getServices: jest.fn().mockResolvedValue(AVAILABLE_SERVICES),
            getJobTypesByBranch: jest.fn().mockResolvedValue([]),
            getCategoriesByBranch: jest.fn().mockResolvedValue([]),
            getOperationsByBranch: jest.fn().mockResolvedValue([])
          }
        },
        { provide: Router, useValue: router }
      ]
    })
      .overrideComponent(ServiceSelectionContainerComponent, {
        set: { template: '<div class="mocked">Mocked Service Selection</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    fixture = TestBed.createComponent(ServiceSelectionContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should inject BookingStore', () => {
    expect(store).toBeTruthy();
  });

  describe('Computed Signals', () => {
    it('should compute empty selectedServiceIds initially', () => {
      const exposed = component as unknown as { selectedServiceIds: () => string[] };
      expect(exposed.selectedServiceIds()).toEqual([]);
    });

    it('should expose categories and operationsByCategory', () => {
      const exposed = component as unknown as {
        categories: () => unknown[];
        operationsByCategory: () => Record<string, unknown[]>;
      };
      expect(exposed.categories()).toEqual([]);
      expect(exposed.operationsByCategory()).toEqual({});
    });
  });

  describe('Service Click (no operations under category)', () => {
    it('should toggle service in store on click', () => {
      const exposed = component as unknown as {
        onServiceClick: (serviceId: string) => void;
      };
      exposed.onServiceClick('inspection');
      expect(store.selectedServices()).toEqual([{ serviceId: 'inspection', selectedOptionIds: [] }]);
    });

    it('should support multi-select', () => {
      const exposed = component as unknown as {
        onServiceClick: (serviceId: string) => void;
      };
      exposed.onServiceClick('inspection');
      exposed.onServiceClick('tuv');
      expect(store.selectedServices()).toHaveLength(2);
    });
  });

  describe('Service Confirm (REQ-025 b Iter. 3 — Category contains Operations)', () => {
    it('should add Operation-IDs as selectedServices on confirm', () => {
      const exposed = component as unknown as {
        onServiceConfirm: (event: { serviceId: string; optionIds: string[] }) => void;
      };
      exposed.onServiceConfirm({ serviceId: 'cat-inspektion', optionIds: ['op-1', 'op-2'] });
      expect(store.selectedServices()).toEqual([
        { serviceId: 'op-1', selectedOptionIds: [] },
        { serviceId: 'op-2', selectedOptionIds: [] }
      ]);
    });
  });

  describe('Navigation', () => {
    it('should navigate forward on continue', () => {
      const exposed = component as unknown as { onContinue: () => void };
      exposed.onContinue();
      expect(router.navigate).toHaveBeenCalledWith(['/home/notes']);
    });

    it('should navigate back and clear services on back', () => {
      const exposed = component as unknown as {
        onServiceClick: (serviceId: string) => void;
        onBack: () => void;
      };
      exposed.onServiceClick('inspection');
      exposed.onBack();
      expect(store.selectedServices()).toEqual([]);
      expect(router.navigate).toHaveBeenCalledWith(['/home/brand']);
    });

    it('should expose isLoading and error signals', () => {
      const exposed = component as unknown as { isLoading: () => boolean; error: () => string | null };
      expect(exposed.isLoading()).toBe(false);
      expect(exposed.error()).toBeNull();
    });
  });
});
