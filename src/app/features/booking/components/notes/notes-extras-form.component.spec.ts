import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import type { NotesExtras } from '../../models/notes-extras.model';
import type { RentCategory } from '../../models/rent-category.model';

import { NotesExtrasFormComponent } from './notes-extras-form.component';

describe('NotesExtrasFormComponent', () => {
  let component: NotesExtrasFormComponent;
  let fixture: ComponentFixture<NotesExtrasFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotesExtrasFormComponent]
    })
      .overrideComponent(NotesExtrasFormComponent, {
        set: { template: '<div class="mocked">Mocked Extras Form</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(NotesExtrasFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Default State', () => {
    it('should have default appointment preference', () => {
      const exposed = component as unknown as { appointmentPreference: () => string };
      expect(exposed.appointmentPreference()).toBe('anytime');
    });

    it('should have default callback option', () => {
      const exposed = component as unknown as { callbackOption: () => string };
      expect(exposed.callbackOption()).toBe('no');
    });

    it('should have no selected rent category initially', () => {
      const exposed = component as unknown as { selectedRentCategoryId: () => string | null };
      expect(exposed.selectedRentCategoryId()).toBeNull();
    });
  });

  describe('Initial Inputs', () => {
    it('should apply initial extras when provided', () => {
      const extras: NotesExtras = {
        appointmentPreference: 'morning',
        callbackOption: 'yes'
      };
      fixture.componentRef.setInput('initialExtras', extras);
      fixture.detectChanges();

      const exposed = component as unknown as {
        appointmentPreference: () => string;
        callbackOption: () => string;
      };
      expect(exposed.appointmentPreference()).toBe('morning');
      expect(exposed.callbackOption()).toBe('yes');
    });

    it('should apply initial rent category id', () => {
      fixture.componentRef.setInput('initialRentCategoryId', 'cat-mid');
      fixture.detectChanges();
      const exposed = component as unknown as { selectedRentCategoryId: () => string | null };
      expect(exposed.selectedRentCategoryId()).toBe('cat-mid');
    });

    it('should keep defaults when initialExtras is null', () => {
      fixture.componentRef.setInput('initialExtras', null);
      fixture.detectChanges();
      const exposed = component as unknown as {
        appointmentPreference: () => string;
        callbackOption: () => string;
      };
      expect(exposed.appointmentPreference()).toBe('anytime');
      expect(exposed.callbackOption()).toBe('no');
    });
  });

  describe('onRentCategoryChange', () => {
    it('should update selected rent category and emit', () => {
      const spy = jest.fn();
      component.rentCategoryChanged.subscribe(spy);

      const exposed = component as unknown as {
        onRentCategoryChange: (value: string | null) => void;
        selectedRentCategoryId: () => string | null;
      };
      exposed.onRentCategoryChange('cat-small');

      expect(exposed.selectedRentCategoryId()).toBe('cat-small');
      expect(spy).toHaveBeenCalledWith('cat-small');
    });

    it('should allow unsetting rent category via null', () => {
      const spy = jest.fn();
      component.rentCategoryChanged.subscribe(spy);
      const exposed = component as unknown as {
        onRentCategoryChange: (value: string | null) => void;
        selectedRentCategoryId: () => string | null;
      };
      exposed.onRentCategoryChange('cat-small');
      exposed.onRentCategoryChange(null);
      expect(exposed.selectedRentCategoryId()).toBeNull();
      expect(spy).toHaveBeenLastCalledWith(null);
    });
  });

  describe('onAppointmentPreferenceChange', () => {
    it('should update appointment preference and emit extras', () => {
      const spy = jest.fn();
      component.extrasChanged.subscribe(spy);

      const exposed = component as unknown as {
        onAppointmentPreferenceChange: (value: string) => void;
        appointmentPreference: () => string;
      };
      exposed.onAppointmentPreferenceChange('afternoon');

      expect(exposed.appointmentPreference()).toBe('afternoon');
      expect(spy).toHaveBeenCalledWith({
        appointmentPreference: 'afternoon',
        callbackOption: 'no'
      });
    });
  });

  describe('onCallbackOptionChange', () => {
    it('should update callback option and emit extras with yes', () => {
      const spy = jest.fn();
      component.extrasChanged.subscribe(spy);

      const exposed = component as unknown as {
        onCallbackOptionChange: (value: string) => void;
        callbackOption: () => string;
      };
      exposed.onCallbackOptionChange('yes');

      expect(exposed.callbackOption()).toBe('yes');
      expect(spy).toHaveBeenCalledWith({
        appointmentPreference: 'anytime',
        callbackOption: 'yes'
      });
    });
  });

  describe('Rent Categories Input', () => {
    it('should accept a list of rent categories', () => {
      const cats: RentCategory[] = [
        { id: 'cat-small', groupId: 'g1', name: 'Kleinwagen', description: '', brands: [], branches: [] },
        { id: 'cat-mid',   groupId: 'g1', name: 'Mittelklasse', description: '', brands: [], branches: [] }
      ];
      fixture.componentRef.setInput('rentCategories', cats);
      fixture.detectChanges();
      expect(component.rentCategories()).toHaveLength(2);
    });
  });

  describe('Option Items', () => {
    it('should have 3 appointment option items', () => {
      const exposed = component as unknown as { appointmentOptionItems: () => { value: string; label: string }[] };
      expect(exposed.appointmentOptionItems()).toHaveLength(3);
      expect(exposed.appointmentOptionItems().map(i => i.value)).toEqual(['anytime', 'morning', 'afternoon']);
    });

    it('should have 2 callback option items', () => {
      const exposed = component as unknown as { callbackOptionItems: () => { value: string; label: string }[] };
      expect(exposed.callbackOptionItems()).toHaveLength(2);
      expect(exposed.callbackOptionItems().map(i => i.value)).toEqual(['no', 'yes']);
    });
  });
});
