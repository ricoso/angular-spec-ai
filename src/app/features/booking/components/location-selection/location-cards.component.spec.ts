import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import type { BranchConfig } from '../../models/branch-config.model';

import type { BranchSelection, EnrichedBranch } from './location-cards.component';
import { LocationCardsComponent } from './location-cards.component';

const FULL_BRANCH: BranchConfig = {
  branchId: 'b1',
  name: 'Autohaus Eins',
  address: { street: 'Hauptstr. 1', zip: '80331', city: 'München' },
  brands: ['Audi', 'VW'],
  tireChangeGroupId: null
};

describe('LocationCardsComponent', () => {
  let component: LocationCardsComponent;
  let fixture: ComponentFixture<LocationCardsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LocationCardsComponent]
    })
      .overrideComponent(LocationCardsComponent, {
        set: { template: '<div class="mocked">Mocked LocationCards</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(LocationCardsComponent);
    component = fixture.componentInstance;
  });

  function enriched(branches: BranchConfig[]): EnrichedBranch[] {
    fixture.componentRef.setInput('branches', branches);
    fixture.detectChanges();
    const exposed = component as unknown as { enrichedBranches: () => EnrichedBranch[] };
    return exposed.enrichedBranches();
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('formats a full address as "street, zip city"', () => {
    const list = enriched([FULL_BRANCH]);
    expect(list).toHaveLength(1);
    expect(list[0].formattedAddress).toBe('Hauptstr. 1, 80331 München');
    expect(list[0].cardId).toBe('b1');
    expect(list[0].displayName).toBe('Autohaus Eins');
    expect(list[0].groupIdOverride).toBeNull();
    expect(list[0].brandLogos).toHaveLength(2);
  });

  it('falls back to zip + city when street is empty', () => {
    const list = enriched([
      { ...FULL_BRANCH, address: { street: '', zip: '80331', city: 'München' } }
    ]);
    expect(list[0].formattedAddress).toBe('80331 München');
  });

  it('falls back to street when zip and city are empty', () => {
    const list = enriched([
      { ...FULL_BRANCH, address: { street: 'Hauptstr. 1', zip: '', city: '' } }
    ]);
    expect(list[0].formattedAddress).toBe('Hauptstr. 1');
  });

  it('falls back to the branch name when the address is completely empty', () => {
    const list = enriched([
      { ...FULL_BRANCH, address: { street: '', zip: '', city: '' } }
    ]);
    expect(list[0].formattedAddress).toBe('Autohaus Eins');
  });

  it('adds a second tireChange (Boxenstopp) card when tireChangeGroupId is set', () => {
    const list = enriched([{ ...FULL_BRANCH, tireChangeGroupId: 'grp-99' }]);
    expect(list).toHaveLength(2);
    expect(list[1].cardId).toBe('b1__tireChange');
    expect(list[1].displayName).toContain('Radwechsel+');
    expect(list[1].groupIdOverride).toBe('grp-99');
  });

  // REQ-041: recall mode offers only real locations — no Radwechsel/Boxenstopp cards.
  it('hides the tireChange (Boxenstopp) card when hideTireChange is set', () => {
    fixture.componentRef.setInput('hideTireChange', true);
    const list = enriched([{ ...FULL_BRANCH, tireChangeGroupId: 'grp-99' }]);
    expect(list).toHaveLength(1);
    expect(list[0].cardId).toBe('b1');
    expect(list[0].groupIdOverride).toBeNull();
  });

  // Rückrufaktion-Karte (eigene Applikation) auf der globalen Test-Seite.
  it('adds a blue recall card per location when showRecall is set', () => {
    fixture.componentRef.setInput('showRecall', true);
    const list = enriched([{ ...FULL_BRANCH, tireChangeGroupId: 'grp-99' }]);
    expect(list).toHaveLength(3);
    expect(list.map(b => b.cardKind)).toEqual(['default', 'tireChange', 'recall']);
    const recall = list[2];
    expect(recall.cardId).toBe('b1__recall');
    expect(recall.displayName).toContain('Rückrufaktion');
    expect(recall.groupIdOverride).toBe('6ac7939ba4c0acee699905a8');
  });

  it('does not add a recall card by default (customer recall deep-link page)', () => {
    const list = enriched([FULL_BRANCH]);
    expect(list.every(b => b.cardKind !== 'recall')).toBe(true);
  });

  // REQ-041: alphabetical by city (German collation), then by name; the Boxenstopp card stays next to its branch.
  it('sorts locations by city, then name, without mutating the input', () => {
    const at = (branchId: string, name: string, city: string): BranchConfig => ({
      ...FULL_BRANCH, branchId, name, address: { ...FULL_BRANCH.address, city }
    });
    const input: BranchConfig[] = [
      at('mh', 'VW Mülheim', 'Mülheim an der Ruhr'),
      { ...at('e2', 'VW Essen', 'Essen'), tireChangeGroupId: 'grp-1' },
      at('noCity', 'Ohne Stadt', ''),
      at('dus', 'Audi Düsseldorf', 'Düsseldorf'),
      at('e1', 'Audi Essen', 'Essen'),
      at('dui', 'Audi Duisburg', 'Duisburg'),
      at('me', 'Audi Mettmann', 'Mettmann')
    ];

    const list = enriched(input);

    expect(list.map(b => b.cardId)).toEqual(['dui', 'dus', 'e1', 'e2', 'e2__tireChange', 'me', 'mh', 'noCity']);
    expect(input[0].branchId).toBe('mh');
  });

  it('emits a BranchSelection with the original branch shape on select', () => {
    const list = enriched([{ ...FULL_BRANCH, tireChangeGroupId: 'grp-99' }]);
    const received: BranchSelection[] = [];
    component.branchSelected.subscribe((s) => received.push(s));

    const exposed = component as unknown as { onSelect: (b: EnrichedBranch) => void };
    exposed.onSelect(list[1]);

    expect(received).toHaveLength(1);
    expect(received[0].branch.branchId).toBe('b1');
    expect(received[0].branch.name).toBe('Autohaus Eins');
    expect(received[0].groupIdOverride).toBe('grp-99');
    expect(received[0].mode).toBe('tireChange');
  });

  it('emits mode "recall" when the recall card is selected', () => {
    fixture.componentRef.setInput('showRecall', true);
    const list = enriched([FULL_BRANCH]);
    const received: BranchSelection[] = [];
    component.branchSelected.subscribe((s) => received.push(s));

    const exposed = component as unknown as { onSelect: (b: EnrichedBranch) => void };
    exposed.onSelect(list.find(b => b.cardKind === 'recall')!);

    expect(received[0].mode).toBe('recall');
    expect(received[0].groupIdOverride).toBe('6ac7939ba4c0acee699905a8');
  });
});
