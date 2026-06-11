import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AVAILABLE_BRANDS } from '../../models/brand.model';
import { LOCATIONS_BY_BRAND } from '../../models/location.model';
import type { NotesExtras } from '../../models/notes-extras.model';
import { AVAILABLE_SERVICES } from '../../models/service.model';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStore } from '../../stores/booking.store';

import { NotesContainerComponent } from './notes-container.component';

describe('NotesContainerComponent', () => {
  let component: NotesContainerComponent;
  let fixture: ComponentFixture<NotesContainerComponent>;
  let router: jest.Mocked<Router>;
  let store: InstanceType<typeof BookingStore>;

  beforeEach(async () => {
    router = { navigate: jest.fn() } as unknown as jest.Mocked<Router>;

    await TestBed.configureTestingModule({
      imports: [NotesContainerComponent],
      providers: [
        BookingStore,
        {
          provide: BookingApiService,
          useValue: {
            getBrands: jest.fn().mockResolvedValue(AVAILABLE_BRANDS),
            getLocations: jest.fn().mockResolvedValue(LOCATIONS_BY_BRAND.audi),
            getServices: jest.fn().mockResolvedValue(AVAILABLE_SERVICES)
          }
        },
        { provide: Router, useValue: router }
      ]
    })
      .overrideComponent(NotesContainerComponent, {
        set: { template: '<div class="mocked">Mocked Notes Container</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    fixture = TestBed.createComponent(NotesContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should inject BookingStore', () => {
    expect(store).toBeTruthy();
  });

  describe('Store Signals', () => {
    it('should expose selectedServices from store', () => {
      const exposed = component as unknown as { selectedServices: () => unknown[] };
      expect(exposed.selectedServices()).toEqual([]);
    });

    it('should expose initialNote from store', () => {
      const exposed = component as unknown as { initialNote: () => string | null };
      expect(exposed.initialNote()).toBeNull();
    });

    it('should expose currentNote as null initially', () => {
      const exposed = component as unknown as { currentNote: () => string | null };
      expect(exposed.currentNote()).toBeNull();
    });

    it('should expose initialExtras from store', () => {
      const exposed = component as unknown as { initialExtras: () => NotesExtras | null };
      expect(exposed.initialExtras()).toBeNull();
    });

    it('should expose currentExtras with defaults initially', () => {
      const exposed = component as unknown as { currentExtras: () => NotesExtras };
      expect(exposed.currentExtras()).toEqual({
        appointmentPreference: 'anytime',
        callbackOption: 'no',
        notificationChannel: 'email'
      });
    });
  });

  describe('onNoteChanged', () => {
    it('should update currentNote when note changes', () => {
      const exposed = component as unknown as {
        onNoteChanged: (note: string | null) => void;
        currentNote: () => string | null;
      };
      exposed.onNoteChanged('Test note');
      expect(exposed.currentNote()).toBe('Test note');
    });

    it('should handle null note', () => {
      const exposed = component as unknown as {
        onNoteChanged: (note: string | null) => void;
        currentNote: () => string | null;
      };
      exposed.onNoteChanged(null);
      expect(exposed.currentNote()).toBeNull();
    });
  });

  describe('onExtrasChanged', () => {
    it('should update currentExtras when extras change', () => {
      const extras: NotesExtras = {
        appointmentPreference: 'afternoon',
        callbackOption: 'yes',
        notificationChannel: 'sms'
      };
      const exposed = component as unknown as {
        onExtrasChanged: (extras: NotesExtras) => void;
        currentExtras: () => NotesExtras;
      };
      exposed.onExtrasChanged(extras);
      expect(exposed.currentExtras()).toEqual(extras);
    });
  });

  describe('onContinue', () => {
    it('should save note and extras to store and navigate', () => {
      const extras: NotesExtras = {
        appointmentPreference: 'morning',
        callbackOption: 'yes',
        notificationChannel: 'sms'
      };
      const exposed = component as unknown as {
        onNoteChanged: (note: string | null) => void;
        onExtrasChanged: (extras: NotesExtras) => void;
        onContinue: () => void;
      };
      exposed.onNoteChanged('Bitte Öl prüfen.');
      exposed.onExtrasChanged(extras);
      exposed.onContinue();
      expect(store.bookingNote()).toBe('Bitte Öl prüfen.');
      expect(store.notesExtras()).toEqual(extras);
      expect(router.navigate).toHaveBeenCalledWith(['/home/appointment']);
    });

    it('should save null when note is empty', () => {
      const exposed = component as unknown as {
        onNoteChanged: (note: string | null) => void;
        onContinue: () => void;
      };
      exposed.onNoteChanged('');
      exposed.onContinue();
      expect(store.bookingNote()).toBeNull();
    });

    it('should save null when note is whitespace only', () => {
      const exposed = component as unknown as {
        onNoteChanged: (note: string | null) => void;
        onContinue: () => void;
      };
      exposed.onNoteChanged('   ');
      exposed.onContinue();
      expect(store.bookingNote()).toBeNull();
    });

    it('should trim note before saving', () => {
      const exposed = component as unknown as {
        onNoteChanged: (note: string | null) => void;
        onContinue: () => void;
      };
      exposed.onNoteChanged('  Test note  ');
      exposed.onContinue();
      expect(store.bookingNote()).toBe('Test note');
    });

    it('should save default extras when no changes made', () => {
      const exposed = component as unknown as { onContinue: () => void };
      exposed.onContinue();
      expect(store.notesExtras()).toEqual({
        appointmentPreference: 'anytime',
        callbackOption: 'no',
        notificationChannel: 'email'
      });
      expect(router.navigate).toHaveBeenCalledWith(['/home/appointment']);
    });
  });

  describe('onBack', () => {
    it('should navigate to services page', () => {
      const exposed = component as unknown as { onBack: () => void };
      exposed.onBack();
      expect(router.navigate).toHaveBeenCalledWith(['/home/services']);
    });

    it('should clear booking note and notesExtras in store before navigating back', () => {
      store.setBookingNote('Test note');
      store.setNotesExtras({ appointmentPreference: 'afternoon', callbackOption: 'yes', notificationChannel: 'email' });

      const exposed = component as unknown as { onBack: () => void };
      exposed.onBack();

      expect(store.bookingNote()).toBeNull();
      expect(store.notesExtras()).toBeNull();
      expect(store.hasBookingNote()).toBe(false);
      expect(store.hasNotesExtras()).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/home/services']);
    });

    it('should handle back when values are already null', () => {
      expect(store.bookingNote()).toBeNull();
      expect(store.notesExtras()).toBeNull();

      const exposed = component as unknown as { onBack: () => void };
      exposed.onBack();

      expect(store.bookingNote()).toBeNull();
      expect(store.notesExtras()).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/home/services']);
    });
  });

  describe('Store Integration', () => {
    it('should reflect selectedServices after store changes', () => {
      const exposed = component as unknown as { selectedServices: () => unknown[] };
      store.toggleService('tuv');
      expect(exposed.selectedServices()).toHaveLength(1);
    });

    it('should reflect bookingNote from store', () => {
      const exposed = component as unknown as { initialNote: () => string | null };
      store.setBookingNote('Previous note');
      expect(exposed.initialNote()).toBe('Previous note');
    });

    it('should reflect notesExtras from store', () => {
      const extras: NotesExtras = {
        appointmentPreference: 'morning',
        callbackOption: 'yes',
        notificationChannel: 'email'
      };
      const exposed = component as unknown as { initialExtras: () => NotesExtras | null };
      store.setNotesExtras(extras);
      expect(exposed.initialExtras()).toEqual(extras);
    });
  });

  describe('Rent Category', () => {
    it('should update currentRentCategoryId on change', () => {
      const exposed = component as unknown as {
        onRentCategoryChanged: (id: string | null) => void;
        currentRentCategoryId: () => string | null;
      };
      exposed.onRentCategoryChanged('rc-1');
      expect(exposed.currentRentCategoryId()).toBe('rc-1');
    });

    it('should persist rentCategoryId to store on continue', () => {
      const exposed = component as unknown as {
        onRentCategoryChanged: (id: string | null) => void;
        onContinue: () => void;
      };
      exposed.onRentCategoryChanged('rc-42');
      exposed.onContinue();
      expect(store.selectedRentCategoryId()).toBe('rc-42');
    });

    it('should clear rentCategoryId in store on back', () => {
      store.setSelectedRentCategoryId('rc-1');
      const exposed = component as unknown as { onBack: () => void };
      exposed.onBack();
      expect(store.selectedRentCategoryId()).toBeNull();
    });
  });
});
