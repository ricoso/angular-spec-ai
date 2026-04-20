import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import type { BranchConfig } from '../../models/branch-config.model';

import { LocationCardsComponent } from './location-cards.component';

const TEST_BRANCHES: BranchConfig[] = [
  {
    branchId: 'branch-1',
    name: 'Test Branch 1',
    address: { street: 'Str. 1', zip: '45127', city: 'Essen' },
    brands: ['VW', 'Audi', 'UnknownBrand']
  },
  {
    branchId: 'branch-2',
    name: 'Test Branch 2',
    address: { street: 'Str. 2', zip: '44135', city: 'Dortmund' },
    brands: ['VW']
  },
  {
    branchId: 'branch-3',
    name: 'Test Branch 3',
    address: { street: 'Str. 3', zip: '40210', city: 'Düsseldorf' },
    brands: ['Audi', 'SEAT']
  }
];

// UI rendering is verified via E2E (Playwright) — unit tests focus on logic only
describe('LocationCardsComponent', () => {
  let component: LocationCardsComponent;
  let fixture: ComponentFixture<LocationCardsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LocationCardsComponent]
    })
      .overrideComponent(LocationCardsComponent, {
        set: { template: '<div class="mocked">Mocked Location Cards</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(LocationCardsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('branches', TEST_BRANCHES);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Inputs', () => {
    it('should accept branches input', () => {
      expect(component.branches()).toEqual(TEST_BRANCHES);
    });

    it('should accept selectedBranch input', () => {
      fixture.componentRef.setInput('selectedBranch', TEST_BRANCHES[0]);
      fixture.detectChanges();
      expect(component.selectedBranch()).toEqual(TEST_BRANCHES[0]);
    });

    it('should default selectedBranch to null', () => {
      expect(component.selectedBranch()).toBeNull();
    });
  });

  describe('Outputs', () => {
    it('should emit branchSelected with full branch object', () => {
      const spy = jest.fn();
      component.branchSelected.subscribe(spy);

      const exposed = component as unknown as { onSelect: (branch: BranchConfig) => void };
      exposed.onSelect(TEST_BRANCHES[0]);

      expect(spy).toHaveBeenCalledWith(TEST_BRANCHES[0]);
    });

    it('should emit correct branch for each selection', () => {
      const spy = jest.fn();
      component.branchSelected.subscribe(spy);

      const exposed = component as unknown as { onSelect: (branch: BranchConfig) => void };
      exposed.onSelect(TEST_BRANCHES[2]);

      expect(spy).toHaveBeenCalledWith(TEST_BRANCHES[2]);
    });
  });

  describe('Computed signals', () => {
    it('should compute enriched branches with formatted address', () => {
      const exposed = component as unknown as { enrichedBranches: () => unknown[] };
      const enriched = exposed.enrichedBranches() as { formattedAddress: string }[];
      expect(enriched[0].formattedAddress).toBe('Str. 1');
    });

    it('should compute enriched branches with brand logos for known brands', () => {
      const exposed = component as unknown as { enrichedBranches: () => unknown[] };
      const enriched = exposed.enrichedBranches() as { brandLogos: { name: string; path: string }[] }[];
      const audiLogo = enriched[0].brandLogos.find(b => b.name === 'Audi');
      expect(audiLogo?.path).toBe('assets/brands/audi.svg');
    });

    it('should use default.svg for unknown brands', () => {
      const exposed = component as unknown as { enrichedBranches: () => unknown[] };
      const enriched = exposed.enrichedBranches() as { brandLogos: { name: string; path: string }[] }[];
      const unknownLogo = enriched[0].brandLogos.find(b => b.name === 'UnknownBrand');
      expect(unknownLogo?.path).toBe('assets/brands/default.svg');
    });
  });
});
