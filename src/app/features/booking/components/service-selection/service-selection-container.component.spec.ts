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
            getServices: jest.fn().mockResolvedValue(AVAILABLE_SERVICES)
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

    it('should compute selectedServiceIds after selecting services', () => {
      store.toggleService('inspection');
      store.toggleService('tuv');
      const exposed = component as unknown as { selectedServiceIds: () => string[] };
      expect(exposed.selectedServiceIds()).toEqual(['inspection', 'tuv']);
    });

    it('should compute empty selectedOptionsMap initially', () => {
      const exposed = component as unknown as { selectedOptionsMap: () => Record<string, string[]> };
      expect(Object.keys(exposed.selectedOptionsMap()).length).toBe(0);
    });

    it('should compute selectedOptionsMap after confirming service options', () => {
      store.confirmServiceOptions('tire-change', ['bring-own-tires']);
      const exposed = component as unknown as { selectedOptionsMap: () => Record<string, string[]> };
      expect(exposed.selectedOptionsMap()['tire-change']).toEqual(['bring-own-tires']);
    });

    it('should return empty options when service has no options selected', () => {
      store.toggleService('inspection');
      const exposed = component as unknown as { selectedOptionsMap: () => Record<string, string[]> };
      expect(exposed.selectedOptionsMap().inspection).toEqual([]);
    });
  });

  describe('Service Click', () => {
    it('should toggle service in store on click', () => {
      const exposed = component as unknown as {
        onServiceClick: (serviceId: 'inspection' | 'tuv' | 'tire-change') => void;
      };
      exposed.onServiceClick('inspection');
      expect(store.selectedServices()).toEqual([{ serviceId: 'inspection', selectedOptionIds: [] }]);
    });

    it('should deselect service on second click', () => {
      const exposed = component as unknown as {
        onServiceClick: (serviceId: 'inspection' | 'tuv' | 'tire-change') => void;
      };
      exposed.onServiceClick('inspection');
      exposed.onServiceClick('inspection');
      expect(store.selectedServices()).toEqual([]);
    });

    it('should support multi-select', () => {
      const exposed = component as unknown as {
        onServiceClick: (serviceId: 'inspection' | 'tuv' | 'tire-change') => void;
      };
      exposed.onServiceClick('inspection');
      exposed.onServiceClick('tuv');
      expect(store.selectedServices()).toHaveLength(2);
    });
  });

  describe('Service Confirm', () => {
    it('should confirm service with options', () => {
      const exposed = component as unknown as {
        onServiceConfirm: (event: { serviceId: 'tire-change'; optionIds: string[] }) => void;
      };
      exposed.onServiceConfirm({ serviceId: 'tire-change', optionIds: ['bring-own-tires'] });
      expect(store.selectedServices()).toEqual([
        { serviceId: 'tire-change', selectedOptionIds: ['bring-own-tires'] }
      ]);
    });

    it('should deselect service', () => {
      const exposed = component as unknown as {
        onServiceConfirm: (event: { serviceId: 'tire-change'; optionIds: string[] }) => void;
        onServiceDeselect: (serviceId: 'tire-change') => void;
      };
      exposed.onServiceConfirm({ serviceId: 'tire-change', optionIds: ['bring-own-tires'] });
      exposed.onServiceDeselect('tire-change');
      expect(store.selectedServices()).toEqual([]);
    });

    it('should switch options on re-confirm', () => {
      const exposed = component as unknown as {
        onServiceConfirm: (event: { serviceId: 'tire-change'; optionIds: string[] }) => void;
      };
      exposed.onServiceConfirm({ serviceId: 'tire-change', optionIds: ['bring-own-tires'] });
      exposed.onServiceConfirm({ serviceId: 'tire-change', optionIds: ['stored-tires'] });
      expect(store.selectedServices()).toEqual([
        { serviceId: 'tire-change', selectedOptionIds: ['stored-tires'] }
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
        onServiceClick: (serviceId: 'inspection' | 'tuv' | 'tire-change') => void;
        onBack: () => void;
      };
      exposed.onServiceClick('inspection');
      exposed.onBack();
      expect(store.selectedServices()).toEqual([]);
      expect(router.navigate).toHaveBeenCalledWith(['/home/brand']);
    });

    it('should call loadServices on retry', () => {
      const loadSpy = jest.spyOn(store, 'loadServices');
      const exposed = component as unknown as { onRetry: () => void };
      exposed.onRetry();
      expect(loadSpy).toHaveBeenCalled();
    });

    it('should expose isLoading and error signals', () => {
      const exposed = component as unknown as { isLoading: () => boolean; error: () => string | null };
      expect(exposed.isLoading()).toBe(false);
      expect(exposed.error()).toBeNull();
    });
  });
});
