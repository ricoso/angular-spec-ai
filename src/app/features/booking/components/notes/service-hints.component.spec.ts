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
});
