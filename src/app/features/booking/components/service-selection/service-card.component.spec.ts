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

    it('toggle expand on card click', () => {
      const exposed = component as unknown as { onCardClick: () => void; isExpanded: () => boolean };
      expect(exposed.isExpanded()).toBe(false);
      exposed.onCardClick();
      expect(exposed.isExpanded()).toBe(true);
      exposed.onCardClick();
      expect(exposed.isExpanded()).toBe(false);
    });

    it('confirm emits Category-ID and selected Operation-IDs', () => {
      const spy = jest.fn();
      component.serviceConfirmed.subscribe(spy);
      const exposed = component as unknown as {
        onCheckboxChange: (id: string, checked: boolean) => void;
        onConfirm: () => void;
      };
      exposed.onCheckboxChange('op-1', true);
      exposed.onConfirm();
      expect(spy).toHaveBeenCalledWith({ serviceId: 'tag-inspektion', optionIds: ['op-1'] });
    });

    it('deselect emits Category-ID and resets state', () => {
      const spy = jest.fn();
      component.serviceDeselected.subscribe(spy);
      const exposed = component as unknown as {
        onCheckboxChange: (id: string, checked: boolean) => void;
        onDeselect: () => void;
        selectedCheckboxOptions: () => Set<string>;
      };
      exposed.onCheckboxChange('op-1', true);
      exposed.onDeselect();
      expect(spy).toHaveBeenCalledWith('tag-inspektion');
      expect(exposed.selectedCheckboxOptions().size).toBe(0);
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
  });

  describe('Confirm/Deselect button visibility logic', () => {
    it('showConfirmButton false when no options', () => {
      setInputs({ ops: [] });
      const exposed = component as unknown as { showConfirmButton: () => boolean };
      expect(exposed.showConfirmButton()).toBe(false);
    });

    it('showConfirmButton true when options present + expanded + not selected', () => {
      setInputs({ isSelected: false });
      const exposed = component as unknown as {
        onCardClick: () => void; showConfirmButton: () => boolean;
      };
      exposed.onCardClick();
      expect(exposed.showConfirmButton()).toBe(true);
    });

    it('showDeselectButton when isSelected and expanded with same options', () => {
      setInputs({ isSelected: true, selectedIds: ['op-1'] });
      const exposed = component as unknown as {
        onCardClick: () => void; showDeselectButton: () => boolean;
      };
      exposed.onCardClick();
      expect(exposed.showDeselectButton()).toBe(true);
    });
  });

  describe('Checkbox Toggle State', () => {
    it('toggles add and remove correctly', () => {
      setInputs();
      const exposed = component as unknown as {
        onCheckboxChange: (id: string, checked: boolean) => void;
        selectedCheckboxOptions: () => Set<string>;
      };
      exposed.onCheckboxChange('op-1', true);
      exposed.onCheckboxChange('op-2', true);
      expect(exposed.selectedCheckboxOptions().size).toBe(2);
      exposed.onCheckboxChange('op-1', false);
      expect(exposed.selectedCheckboxOptions().size).toBe(1);
      expect(exposed.selectedCheckboxOptions().has('op-2')).toBe(true);
    });

    it('confirm emits nothing when no options selected', () => {
      setInputs();
      const spy = jest.fn();
      component.serviceConfirmed.subscribe(spy);
      const exposed = component as unknown as { onConfirm: () => void };
      exposed.onConfirm();
      expect(spy).not.toHaveBeenCalled();
    });
  });
});
