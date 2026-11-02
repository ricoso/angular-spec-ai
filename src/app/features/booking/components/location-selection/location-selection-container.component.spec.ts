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

const TEST_BRANCH_LEV: BranchConfig = {
  branchId: 'test-branch-lev',
  name: 'Volkswagen Zentrum Leverkusen',
  address: { street: 'Robert-Blum-Str. 71', zip: '51379', city: 'Leverkusen' },
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
        onBranchSelect: (selection: { branch: BranchConfig; groupIdOverride: string | null }) => void;
      };
      exposed.onBranchSelect({ branch: TEST_BRANCH, groupIdOverride: null });

      expect(store.selectedBranch()).toEqual(TEST_BRANCH);
      expect(store.selectedLocation()).toEqual({
        id: TEST_BRANCH.branchId,
        name: TEST_BRANCH.name,
        city: TEST_BRANCH.address.city,
        formattedAddress: `${TEST_BRANCH.address.street}, ${TEST_BRANCH.address.zip} ${TEST_BRANCH.address.city}`
      });
    });

    it('should cascade-reset downstream state when branch changes', () => {
      const exposed = component as unknown as {
        onBranchSelect: (selection: { branch: BranchConfig; groupIdOverride: string | null; mode?: string }) => void;
      };
      exposed.onBranchSelect({ branch: TEST_BRANCH, groupIdOverride: null, mode: 'default' });
      expect(store.selectedBranch()).toEqual(TEST_BRANCH);

      exposed.onBranchSelect({ branch: TEST_BRANCH_2, groupIdOverride: null, mode: 'default' });
      expect(store.selectedBranch()).toEqual(TEST_BRANCH_2);
      expect(store.selectedBrand()).toBeNull();
    });

    // Rückruf-Karte auf der Test-Seite aktiviert den vollen Rückruf-Modus.
    it('activates recall mode when the recall card is selected', () => {
      const exposed = component as unknown as {
        onBranchSelect: (selection: { branch: BranchConfig; groupIdOverride: string | null; mode?: string }) => void;
      };
      exposed.onBranchSelect({ branch: TEST_BRANCH, groupIdOverride: '6ac7939ba4c0acee699905a8', mode: 'recall' });

      expect(store.isRecallMode()).toBe(true);
      expect(store.deepLinkGroupId()).toBe('6ac7939ba4c0acee699905a8');
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

  // Städtesuche + Leverkusen ausblenden.
  describe('Location filter (city search + hidden cities)', () => {
    async function loadBranches(branches: BranchConfig[]): Promise<void> {
      (TestBed.inject(BookingApiService).getBranches as jest.Mock).mockResolvedValue(branches);
      store.loadBranches();
      await new Promise(resolve => setTimeout(resolve, 0));
    }

    interface Exposed {
      branches: () => BranchConfig[];
      noSearchResults: () => boolean;
      onCitySearch: (event: Event) => void;
    }
    function exposed(): Exposed {
      return component as unknown as Exposed;
    }
    const search = (value: string): void => {
      exposed().onCitySearch({ target: { value } } as unknown as Event);
    };

    it('hides Leverkusen from the location list', async () => {
      await loadBranches([TEST_BRANCH, TEST_BRANCH_2, TEST_BRANCH_LEV]);
      const cities = exposed().branches().map(b => b.address.city);
      expect(cities).toEqual(['Essen', 'Dortmund']);
      expect(cities).not.toContain('Leverkusen');
    });

    it('filters locations by the city search term (case-insensitive)', async () => {
      await loadBranches([TEST_BRANCH, TEST_BRANCH_2, TEST_BRANCH_LEV]);
      search('dort');
      expect(exposed().branches().map(b => b.address.city)).toEqual(['Dortmund']);
    });

    it('also matches the branch name, and reports no results for an unknown term', async () => {
      await loadBranches([TEST_BRANCH, TEST_BRANCH_2]);
      search('Dortmund');
      expect(exposed().branches()).toHaveLength(1);

      search('xyz-nonexistent');
      expect(exposed().branches()).toHaveLength(0);
      expect(exposed().noSearchResults()).toBe(true);
    });
  });

  // REQ-030 + REQ-041: the internal report button is hidden in an iframe and in recall mode.
  describe('Report Button', () => {
    it('should show the report button in the normal flow', () => {
      const exposed = component as unknown as { showReportButton: () => boolean };
      expect(exposed.showReportButton()).toBe(true);
    });

    it('should hide the report button in recall mode', () => {
      store.activateRecallMode('WVWZZZ1KZAW000123');
      const exposed = component as unknown as { showReportButton: () => boolean };
      expect(exposed.showReportButton()).toBe(false);
    });
  });
});
