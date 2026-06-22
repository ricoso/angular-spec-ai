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
        callbackOption: 'yes',
        notificationChannel: 'sms'
      };
      fixture.componentRef.setInput('initialExtras', extras);
      fixture.detectChanges();

      const exposed = component as unknown as {
        appointmentPreference: () => string;
        callbackOption: () => string;
        notificationChannel: () => string;
      };
      expect(exposed.appointmentPreference()).toBe('morning');
      expect(exposed.callbackOption()).toBe('yes');
      expect(exposed.notificationChannel()).toBe('sms');
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
        notificationChannel: () => string;
      };
      expect(exposed.appointmentPreference()).toBe('anytime');
      expect(exposed.callbackOption()).toBe('no');
      expect(exposed.notificationChannel()).toBe('email');
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
        callbackOption: 'no',
        notificationChannel: 'email'
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
        callbackOption: 'yes',
        notificationChannel: 'email'
      });
    });
  });

  describe('onNotificationChannelChange (REQ-027)', () => {
    it('updates notificationChannel and emits extras with sms', () => {
      const spy = jest.fn();
      component.extrasChanged.subscribe(spy);

      const exposed = component as unknown as {
        onNotificationChannelChange: (value: 'email' | 'sms') => void;
        notificationChannel: () => string;
      };
      exposed.onNotificationChannelChange('sms');

      expect(exposed.notificationChannel()).toBe('sms');
      expect(spy).toHaveBeenCalledWith({
        appointmentPreference: 'anytime',
        callbackOption: 'no',
        notificationChannel: 'sms'
      });
    });

    it('emits extras with email after toggling back', () => {
      const spy = jest.fn();
      component.extrasChanged.subscribe(spy);

      const exposed = component as unknown as {
        onNotificationChannelChange: (value: 'email' | 'sms') => void;
      };
      exposed.onNotificationChannelChange('sms');
      exposed.onNotificationChannelChange('email');

      expect(spy).toHaveBeenLastCalledWith({
        appointmentPreference: 'anytime',
        callbackOption: 'no',
        notificationChannel: 'email'
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

    it('should have 2 notification channel items (email, sms) — REQ-027', () => {
      const exposed = component as unknown as { notificationChannelItems: () => { value: string; label: string; icon: string }[] };
      expect(exposed.notificationChannelItems()).toHaveLength(2);
      expect(exposed.notificationChannelItems().map(i => i.value)).toEqual(['email', 'sms']);
    });
  });

  describe('Switch-Card UX (Toggle-Switch fuer Mietwagen + Rueckruf)', () => {
    it('rentEnabled is false initially', () => {
      const exposed = component as unknown as { rentEnabled: () => boolean };
      expect(exposed.rentEnabled()).toBe(false);
    });

    it('callbackEnabled is false initially (Default no)', () => {
      const exposed = component as unknown as { callbackEnabled: () => boolean };
      expect(exposed.callbackEnabled()).toBe(false);
    });

    it('rentEnabled becomes true when category is set', () => {
      const exposed = component as unknown as {
        onRentCategoryChange: (id: string | null) => void;
        rentEnabled: () => boolean;
      };
      exposed.onRentCategoryChange('cat-1');
      expect(exposed.rentEnabled()).toBe(true);
    });

    it('callbackEnabled flips to true when "yes" selected', () => {
      const exposed = component as unknown as {
        onCallbackOptionChange: (v: 'yes' | 'no') => void;
        callbackEnabled: () => boolean;
      };
      exposed.onCallbackOptionChange('yes');
      expect(exposed.callbackEnabled()).toBe(true);
    });

    it('onRentSwitchChange(true) auto-selects first rent category', () => {
      const cats: RentCategory[] = [
        { id: 'cat-small', groupId: 'g1', name: 'Kleinwagen', description: '', brands: [], branches: [] },
        { id: 'cat-mid',   groupId: 'g1', name: 'Mittelklasse', description: '', brands: [], branches: [] }
      ];
      fixture.componentRef.setInput('rentCategories', cats);
      fixture.detectChanges();

      const exposed = component as unknown as {
        onRentSwitchChange: (checked: boolean) => void;
        selectedRentCategoryId: () => string | null;
      };
      exposed.onRentSwitchChange(true);
      expect(exposed.selectedRentCategoryId()).toBe('cat-small');
    });

    it('onRentSwitchChange(false) clears selected category', () => {
      const cats: RentCategory[] = [
        { id: 'cat-x', groupId: 'g1', name: 'X', description: '', brands: [], branches: [] }
      ];
      fixture.componentRef.setInput('rentCategories', cats);
      fixture.detectChanges();
      const exposed = component as unknown as {
        onRentSwitchChange: (c: boolean) => void;
        onRentCategoryChange: (id: string | null) => void;
        selectedRentCategoryId: () => string | null;
      };
      exposed.onRentCategoryChange('cat-x');
      exposed.onRentSwitchChange(false);
      expect(exposed.selectedRentCategoryId()).toBeNull();
    });

    it('onRentSwitchChange(true) keeps the existing selection instead of resetting to the first', () => {
      const cats: RentCategory[] = [
        { id: 'cat-a', groupId: 'g1', name: 'A', description: '', brands: [], branches: [] },
        { id: 'cat-b', groupId: 'g1', name: 'B', description: '', brands: [], branches: [] }
      ];
      fixture.componentRef.setInput('rentCategories', cats);
      fixture.detectChanges();
      const exposed = component as unknown as {
        onRentSwitchChange: (c: boolean) => void;
        onRentCategoryChange: (id: string | null) => void;
        selectedRentCategoryId: () => string | null;
      };
      exposed.onRentCategoryChange('cat-b');
      exposed.onRentSwitchChange(true); // already selected → keep cat-b (no reset to cat-a)
      expect(exposed.selectedRentCategoryId()).toBe('cat-b');
    });

    it('onRentSwitchChange(true) falls back to null when no categories are available', () => {
      fixture.componentRef.setInput('rentCategories', [] as RentCategory[]);
      fixture.detectChanges();
      const exposed = component as unknown as {
        onRentSwitchChange: (c: boolean) => void;
        selectedRentCategoryId: () => string | null;
      };
      exposed.onRentSwitchChange(true); // null + empty list → [0]?.id ?? null → null
      expect(exposed.selectedRentCategoryId()).toBeNull();
    });

    it('onCallbackSwitchChange maps boolean -> yes/no', () => {
      const exposed = component as unknown as {
        onCallbackSwitchChange: (b: boolean) => void;
        callbackOption: () => string;
      };
      exposed.onCallbackSwitchChange(true);
      expect(exposed.callbackOption()).toBe('yes');
      exposed.onCallbackSwitchChange(false);
      expect(exposed.callbackOption()).toBe('no');
    });

    it('onRentSwitchToggle wrapper reads event.target.checked', () => {
      const cats: RentCategory[] = [
        { id: 'cat-x', groupId: 'g1', name: 'X', description: '', brands: [], branches: [] }
      ];
      fixture.componentRef.setInput('rentCategories', cats);
      fixture.detectChanges();
      const exposed = component as unknown as {
        onRentSwitchToggle: (e: Event) => void;
        rentEnabled: () => boolean;
      };
      const fakeEvent = { target: { checked: true } } as unknown as Event;
      exposed.onRentSwitchToggle(fakeEvent);
      expect(exposed.rentEnabled()).toBe(true);
    });

    it('onCallbackSwitchToggle wrapper reads event.target.checked', () => {
      const exposed = component as unknown as {
        onCallbackSwitchToggle: (e: Event) => void;
        callbackEnabled: () => boolean;
      };
      const fakeEvent = { target: { checked: true } } as unknown as Event;
      exposed.onCallbackSwitchToggle(fakeEvent);
      expect(exposed.callbackEnabled()).toBe(true);
    });
  });

  describe('Rent Category Description', () => {
    it('returns null when no category selected', () => {
      const exposed = component as unknown as { selectedRentCategoryDescription: () => string | null };
      expect(exposed.selectedRentCategoryDescription()).toBeNull();
    });

    it('returns description when category has one', () => {
      const cats: RentCategory[] = [
        { id: 'cat-1', groupId: 'g1', name: 'Premium', description: 'Ab 99 EUR/Tag', brands: [], branches: [] }
      ];
      fixture.componentRef.setInput('rentCategories', cats);
      fixture.detectChanges();
      const exposed = component as unknown as {
        onRentCategoryChange: (id: string) => void;
        selectedRentCategoryDescription: () => string | null;
      };
      exposed.onRentCategoryChange('cat-1');
      expect(exposed.selectedRentCategoryDescription()).toBe('Ab 99 EUR/Tag');
    });

    it('returns null when category description is empty/whitespace', () => {
      const cats: RentCategory[] = [
        { id: 'cat-empty', groupId: 'g1', name: 'X', description: '   ', brands: [], branches: [] }
      ];
      fixture.componentRef.setInput('rentCategories', cats);
      fixture.detectChanges();
      const exposed = component as unknown as {
        onRentCategoryChange: (id: string) => void;
        selectedRentCategoryDescription: () => string | null;
      };
      exposed.onRentCategoryChange('cat-empty');
      expect(exposed.selectedRentCategoryDescription()).toBeNull();
    });
  });
});
