import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import type { BranchConfig } from '../../models/branch-config.model';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStore } from '../../stores/booking.store';

import { LocationSelectionContainerComponent } from './location-selection-container.component';

const TEST_BRANCH: BranchConfig = {
  branchId: 'test-branch-1',
  name: 'Test Branch Essen',
  address: { street: 'Teststr. 1', zip: '45127', city: 'Essen' },
  brands: ['VW', 'Audi']
};

const TEST_BRANCH_2: BranchConfig = {
  branchId: 'test-branch-2',
  name: 'Test Branch Dortmund',
  address: { street: 'Teststr. 2', zip: '44135', city: 'Dortmund' },
  brands: ['VW']
};

// UI rendering is verified via E2E (Playwright) — unit tests focus on logic only
describe('LocationSelectionContainerComponent', () => {
  let component: LocationSelectionContainerComponent;
  let fixture: ComponentFixture<LocationSelectionContainerComponent>;
  let router: jest.Mocked<Router>;
  let store: InstanceType<typeof BookingStore>;

  beforeEach(async () => {
    router = { navigate: jest.fn() } as unknown as jest.Mocked<Router>;

    await TestBed.configureTestingModule({
      imports: [LocationSelectionContainerComponent],
      providers: [
        BookingStore,
        {
          provide: BookingApiService,
          useValue: {
            getBranches: jest.fn().mockResolvedValue([TEST_BRANCH, TEST_BRANCH_2]),
            getBrands: jest.fn().mockResolvedValue([]),
            getBrandsByBranch: jest.fn().mockResolvedValue([]),
            getLocations: jest.fn().mockResolvedValue([]),
            getBranchById: jest.fn().mockResolvedValue(null),
            getBrandLogoPath: jest.fn().mockReturnValue('assets/brands/default.svg'),
            getServices: jest.fn().mockResolvedValue([])
          }
        },
        { provide: Router, useValue: router }
      ]
    })
      .overrideComponent(LocationSelectionContainerComponent, {
        set: { template: '<div class="mocked">Mocked Location Selection</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    fixture = TestBed.createComponent(LocationSelectionContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should inject BookingStore', () => {
    expect(store).toBeTruthy();
  });

  describe('Branch Selection', () => {
    it('should set branch in store on selection', () => {
      const exposed = component as unknown as {
        onBranchSelect: (branch: BranchConfig) => void;
      };
      exposed.onBranchSelect(TEST_BRANCH);

      expect(store.selectedBranch()).toEqual(TEST_BRANCH);
      expect(store.selectedLocation()).toEqual({
        id: TEST_BRANCH.branchId,
        name: TEST_BRANCH.name,
        city: TEST_BRANCH.address.street || TEST_BRANCH.name,
        formattedAddress: TEST_BRANCH.address.street || TEST_BRANCH.name
      });
    });

    it('should cascade-reset downstream state when branch changes', () => {
      const exposed = component as unknown as {
        onBranchSelect: (branch: BranchConfig) => void;
      };
      exposed.onBranchSelect(TEST_BRANCH);
      expect(store.selectedBranch()).toEqual(TEST_BRANCH);

      exposed.onBranchSelect(TEST_BRANCH_2);
      expect(store.selectedBranch()).toEqual(TEST_BRANCH_2);
      expect(store.selectedBrand()).toBeNull();
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

    it('should call store.loadBranches on retry', () => {
      const loadBranchesSpy = jest.spyOn(store, 'loadBranches');
      const exposed = component as unknown as { onRetry: () => void };
      exposed.onRetry();
      expect(loadBranchesSpy).toHaveBeenCalled();
    });
  });
});
