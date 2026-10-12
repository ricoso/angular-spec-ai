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

  describe('Operations-based hints (REQ-025 b Iter. 3)', () => {
    it('renders Operation description as hint when selected', () => {
      fixture.componentRef.setInput('selectedServices', [{ serviceId: 'op-1', selectedOptionIds: [] }]);
      fixture.componentRef.setInput('operations', [
        { id: 'op-1', name: 'Inspektion', description: 'Bitte bringen Sie Serviceheft', tagIds: ['c1'], order: 1, imageId: null }
      ]);
      fixture.detectChanges();
      const exposed = component as unknown as { visibleHints: () => { hint: string; serviceName: string }[] };
      const hints = exposed.visibleHints();
      expect(hints).toHaveLength(1);
      expect(hints[0].hint).toContain('Serviceheft');
      expect(hints[0].serviceName).toBe('Inspektion');
    });

    it('treats operation description=null as empty string', () => {
      fixture.componentRef.setInput('selectedServices', [{ serviceId: 'op-1', selectedOptionIds: [] }]);
      fixture.componentRef.setInput('operations', [
        { id: 'op-1', name: 'X', description: null, tagIds: ['c1'], order: 1, imageId: null }
      ]);
      fixture.detectChanges();
      const exposed = component as unknown as { visibleHints: () => { hint: string }[] };
      expect(exposed.visibleHints()[0].hint).toBe('');
    });

    it('falls back to jobTypes when operations array is empty', () => {
      fixture.componentRef.setInput('selectedServices', []);
      fixture.componentRef.setInput('operations', []);
      fixture.componentRef.setInput('jobTypes', [
        { id: 'jt-x', name: 'X', description: '', hint: 'hint', category: null, categoryName: null, iconUrl: null, operations: [] }
      ]);
      fixture.detectChanges();
      const exposed = component as unknown as { visibleHints: () => { hint: string }[] };
      expect(exposed.visibleHints()).toHaveLength(1);
      expect(exposed.visibleHints()[0].hint).toBe('hint');
    });

    it('renders a hint for an operation without category (REQ-039: no per-hint icon)', () => {
      fixture.componentRef.setInput('selectedServices', [{ serviceId: 'op-orphan', selectedOptionIds: [] }]);
      fixture.componentRef.setInput('operations', [
        { id: 'op-orphan', name: 'Y', description: 'Hinweis', tagIds: [], order: 1, imageId: null }
      ]);
      fixture.detectChanges();
      const exposed = component as unknown as { visibleHints: () => { serviceName: string; hint: string }[] };
      expect(exposed.visibleHints()).toEqual([{ key: 'op-orphan', serviceName: 'Y', hint: 'Hinweis' }]);
    });
  });
});
