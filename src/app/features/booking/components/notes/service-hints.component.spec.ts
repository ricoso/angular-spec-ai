import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import type { ApiJobType } from '../../models/service.model';

import { ServiceHintsComponent } from './service-hints.component';

describe('ServiceHintsComponent', () => {
  let component: ServiceHintsComponent;
  let fixture: ComponentFixture<ServiceHintsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ServiceHintsComponent]
    })
      .overrideComponent(ServiceHintsComponent, {
        set: { template: '<div class="mocked">Mocked Service Hints</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(ServiceHintsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('selectedServices', []);
    fixture.componentRef.setInput('jobTypes', []);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should return empty array when no jobTypes are loaded', () => {
    const exposed = component as unknown as { visibleHints: () => unknown[] };
    expect(exposed.visibleHints()).toEqual([]);
  });

  it('renders raw API hint strings (TC-8)', () => {
    const jobTypes: ApiJobType[] = [
      { id: 'jt-1', name: 'Inspektion', description: 'desc', hint: 'Bitte bringen Sie ihr Serviceheft mit.', category: null, categoryName: null, iconUrl: null, operations: [] }
    ];
    fixture.componentRef.setInput('jobTypes', jobTypes);
    fixture.detectChanges();

    const exposed = component as unknown as { visibleHints: () => { key: string; hint: string }[] };
    expect(exposed.visibleHints()).toHaveLength(1);
    expect(exposed.visibleHints()[0].hint).toBe('Bitte bringen Sie ihr Serviceheft mit.');
  });

  it('hides cards for jobTypes with empty hint (TC-9)', () => {
    const jobTypes: ApiJobType[] = [
      { id: 'jt-1', name: 'A', description: '', hint: '   ', category: null, categoryName: null, iconUrl: null, operations: [] },
      { id: 'jt-2', name: 'B', description: '', hint: 'Real hint', category: null, categoryName: null, iconUrl: null, operations: [] },
      { id: 'jt-3', name: 'C', description: '', hint: null, category: null, categoryName: null, iconUrl: null, operations: [] }
    ];
    fixture.componentRef.setInput('jobTypes', jobTypes);
    fixture.detectChanges();

    const exposed = component as unknown as { visibleHints: () => { key: string }[] };
    expect(exposed.visibleHints()).toHaveLength(1);
    expect(exposed.visibleHints()[0].key).toBe('jt-2');
  });

  it('exposes iconUrl from jobType when present', () => {
    const jobTypes: ApiJobType[] = [
      { id: 'jt-1', name: 'A', description: '', hint: 'h', category: null, categoryName: null, iconUrl: '/assets/api/x.svg', operations: [] }
    ];
    fixture.componentRef.setInput('jobTypes', jobTypes);
    fixture.detectChanges();

    const exposed = component as unknown as { visibleHints: () => { iconUrl: string | null }[] };
    expect(exposed.visibleHints()[0].iconUrl).toBe('/assets/api/x.svg');
  });

  it('passes null iconUrl through when API delivers no icon', () => {
    const jobTypes: ApiJobType[] = [
      { id: 'jt-1', name: 'A', description: '', hint: 'h', category: null, categoryName: null, iconUrl: null, operations: [] }
    ];
    fixture.componentRef.setInput('jobTypes', jobTypes);
    fixture.detectChanges();

    const exposed = component as unknown as { visibleHints: () => { iconUrl: string | null }[] };
    expect(exposed.visibleHints()[0].iconUrl).toBeNull();
  });

  describe('Operations-based hints (REQ-025 b Iter. 3)', () => {
    it('renders Operation description as hint when selected', () => {
      fixture.componentRef.setInput('selectedServices', [{ serviceId: 'op-1', selectedOptionIds: [] }]);
      fixture.componentRef.setInput('categories', [{ id: 'c1', name: 'Inspektion', rank: 0 }]);
      fixture.componentRef.setInput('operations', [
        { id: 'op-1', name: 'Inspektion', description: 'Bitte bringen Sie Serviceheft', tagIds: ['c1'], order: 1, imageId: null }
      ]);
      fixture.detectChanges();
      const exposed = component as unknown as { visibleHints: () => Array<{ hint: string; iconUrl: string | null }> };
      const hints = exposed.visibleHints();
      expect(hints).toHaveLength(1);
      expect(hints[0].hint).toContain('Serviceheft');
      expect(hints[0].iconUrl).toBe('assets/icons/services/inspection.svg');
    });

    it('uses Hol & Bringservice icon (rank 99) for that specific operation', () => {
      fixture.componentRef.setInput('selectedServices', [{ serviceId: 'op-pickup', selectedOptionIds: [] }]);
      fixture.componentRef.setInput('categories', [{ id: 'c1', name: 'Reparatur', rank: 5 }]);
      fixture.componentRef.setInput('operations', [
        { id: 'op-pickup', name: 'Hol & Bringservice', description: 'Wir holen ab.', tagIds: ['c1'], order: 99, imageId: null }
      ]);
      fixture.detectChanges();
      const exposed = component as unknown as { visibleHints: () => Array<{ iconUrl: string | null }> };
      expect(exposed.visibleHints()[0].iconUrl).toBe('assets/icons/services/pickup-delivery.svg');
    });

    it('treats operation description=null as empty string', () => {
      fixture.componentRef.setInput('selectedServices', [{ serviceId: 'op-1', selectedOptionIds: [] }]);
      fixture.componentRef.setInput('categories', [{ id: 'c1', name: 'X', rank: 0 }]);
      fixture.componentRef.setInput('operations', [
        { id: 'op-1', name: 'X', description: null, tagIds: ['c1'], order: 1, imageId: null }
      ]);
      fixture.detectChanges();
      const exposed = component as unknown as { visibleHints: () => Array<{ hint: string }> };
      expect(exposed.visibleHints()[0].hint).toBe('');
    });

    it('falls back to jobTypes when operations array is empty', () => {
      fixture.componentRef.setInput('selectedServices', []);
      fixture.componentRef.setInput('categories', []);
      fixture.componentRef.setInput('operations', []);
      fixture.componentRef.setInput('jobTypes', [
        { id: 'jt-x', name: 'X', description: '', hint: 'hint', category: null, categoryName: null, iconUrl: null, operations: [] }
      ]);
      fixture.detectChanges();
      const exposed = component as unknown as { visibleHints: () => Array<{ hint: string }> };
      expect(exposed.visibleHints()).toHaveLength(1);
      expect(exposed.visibleHints()[0].hint).toBe('hint');
    });

    it('handles operation with no matching category (uses default rank 0 → inspection icon)', () => {
      fixture.componentRef.setInput('selectedServices', [{ serviceId: 'op-orphan', selectedOptionIds: [] }]);
      fixture.componentRef.setInput('categories', [{ id: 'c-other', name: 'X', rank: 5 }]);
      fixture.componentRef.setInput('operations', [
        { id: 'op-orphan', name: 'Y', description: '', tagIds: [], order: 1, imageId: null }
      ]);
      fixture.detectChanges();
      const exposed = component as unknown as { visibleHints: () => Array<{ iconUrl: string | null }> };
      expect(exposed.visibleHints()[0].iconUrl).toBe('assets/icons/services/inspection.svg');
    });
  });
});
