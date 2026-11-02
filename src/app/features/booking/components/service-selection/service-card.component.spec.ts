import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import type { ApiCategory, ApiOperation } from '../../models/service.model';

import { ServiceCardComponent } from './service-card.component';

describe('ServiceCardComponent', () => {
  let component: ServiceCardComponent;
  let fixture: ComponentFixture<ServiceCardComponent>;

  const inspectionCategory: ApiCategory = {
    id: 'tag-inspektion',
    name: 'Inspektion',
    rank: 0
  };

  const operations: ApiOperation[] = [
    {
      id: 'op-1',
      name: 'Inspektion',
      description: 'Servicearbeiten nach Wartungsplan. Bitte bringen Sie Ihr Serviceheft mit.',
      tagIds: ['tag-inspektion'],
      order: 1.1,
      imageId: 'img-1'
    },
    {
      id: 'op-2',
      name: 'Ölwechsel',
      description: null,
      tagIds: ['tag-inspektion'],
      order: 1.2,
      imageId: null
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ServiceCardComponent]
    })
      .overrideComponent(ServiceCardComponent, {
        set: { template: '<div class="mocked">Mocked</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(ServiceCardComponent);
    component = fixture.componentInstance;
  });

  function setInputs(opts: { ops?: ApiOperation[]; isSelected?: boolean; selectedIds?: string[] } = {}): void {
    fixture.componentRef.setInput('category', inspectionCategory);
    fixture.componentRef.setInput('operations', opts.ops ?? operations);
    fixture.componentRef.setInput('isSelected', opts.isSelected ?? false);
    fixture.componentRef.setInput('selectedOperationIds', opts.selectedIds ?? []);
    fixture.detectChanges();
  }

  describe('Category card with operations', () => {
    beforeEach(() => { setInputs(); });

    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should expose operationCount = operations length', () => {
      const exposed = component as unknown as { operationCount: () => number };
      expect(exposed.operationCount()).toBe(2);
    });

    it('hasOptions() is true when operations are present', () => {
      const exposed = component as unknown as { hasOptions: () => boolean };
      expect(exposed.hasOptions()).toBe(true);
    });

    it('emits serviceClicked with category-id on click', () => {
      const spy = jest.fn();
      component.serviceClicked.subscribe(spy);
      const exposed = component as unknown as { onCardClick: () => void };
      exposed.onCardClick();
      expect(spy).toHaveBeenCalledWith('tag-inspektion');
    });

    it('selectedCount follows selectedOperationIds', () => {
      setInputs({ isSelected: true, selectedIds: ['op-1'] });
      const exposed = component as unknown as { selectedCount: () => number };
      expect(exposed.selectedCount()).toBe(1);
    });
  });

  describe('Empty category (no operations)', () => {
    beforeEach(() => { setInputs({ ops: [] }); });

    it('hasOptions() is false', () => {
      const exposed = component as unknown as { hasOptions: () => boolean };
      expect(exposed.hasOptions()).toBe(false);
    });

    it('emits serviceClicked with category-id on click', () => {
      const spy = jest.fn();
      component.serviceClicked.subscribe(spy);
      const exposed = component as unknown as { onCardClick: () => void };
      exposed.onCardClick();
      expect(spy).toHaveBeenCalledWith('tag-inspektion');
    });
  });

  describe('REQ-025 (b) Iter. 3: Description rendering', () => {
    it('exposes description on operation with non-null value', () => {
      setInputs();
      const exposed = component as unknown as { operations: () => ApiOperation[] };
      const op = exposed.operations().find(o => o.id === 'op-1')!;
      expect(op.description).toContain('Serviceheft');
    });

    it('handles null description gracefully', () => {
      setInputs();
      const exposed = component as unknown as { operations: () => ApiOperation[] };
      const op = exposed.operations().find(o => o.id === 'op-2')!;
      expect(op.description).toBeNull();
    });
  });

  describe('Bootstrap-Icon Mapping (rank → bi-Klasse)', () => {
    it('rank 0 → bi-tools', () => {
      setInputs();
      const exposed = component as unknown as { iconClass: () => string };
      expect(exposed.iconClass()).toBe('bi-tools');
    });

    const ranks: { rank: number; cls: string }[] = [
      { rank: 1, cls: 'bi-clipboard-check' },
      { rank: 2, cls: 'bi-droplet-half' },
      { rank: 3, cls: 'bi-record-circle' },
      { rank: 4, cls: 'bi-check2-circle' },
      { rank: 5, cls: 'bi-search' },
      { rank: 6, cls: 'bi-car-front' },
      { rank: 99, cls: 'bi-arrow-left-right' }
    ];
    ranks.forEach(({ rank, cls }) => {
      it(`rank ${rank} → ${cls}`, () => {
        fixture.componentRef.setInput('category', { id: 't', name: 'X', rank });
        fixture.componentRef.setInput('operations', operations);
        fixture.componentRef.setInput('isSelected', false);
        fixture.componentRef.setInput('selectedOperationIds', []);
        fixture.detectChanges();
        const exposed = component as unknown as { iconClass: () => string };
        expect(exposed.iconClass()).toBe(cls);
      });
    });

    it('unknown rank → bi-tools (default fallback)', () => {
      fixture.componentRef.setInput('category', { id: 't', name: 'X', rank: 42 });
      fixture.componentRef.setInput('operations', operations);
      fixture.componentRef.setInput('isSelected', false);
      fixture.componentRef.setInput('selectedOperationIds', []);
      fixture.detectChanges();
      const exposed = component as unknown as { iconClass: () => string };
      expect(exposed.iconClass()).toBe('bi-tools');
    });

    it('id "virtual-pickup-delivery" → bi-arrow-left-right (egal welcher rank)', () => {
      fixture.componentRef.setInput('category', { id: 'virtual-pickup-delivery', name: 'Hol & Bringservice', rank: 0 });
      fixture.componentRef.setInput('operations', operations);
      fixture.componentRef.setInput('isSelected', false);
      fixture.componentRef.setInput('selectedOperationIds', []);
      fixture.detectChanges();
      const exposed = component as unknown as { iconClass: () => string };
      expect(exposed.iconClass()).toBe('bi-arrow-left-right');
    });

    it('name "Hol & Bringservice" → bi-arrow-left-right (egal welcher rank)', () => {
      fixture.componentRef.setInput('category', { id: 'tag-foo', name: 'Hol & Bringservice', rank: 2 });
      fixture.componentRef.setInput('operations', operations);
      fixture.componentRef.setInput('isSelected', false);
      fixture.componentRef.setInput('selectedOperationIds', []);
      fixture.detectChanges();
      const exposed = component as unknown as { iconClass: () => string };
      expect(exposed.iconClass()).toBe('bi-arrow-left-right');
    });
  });
});
