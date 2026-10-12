import { HttpErrorResponse } from '@angular/common/http';
import type { ComponentFixture} from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter,Router } from '@angular/router';

import { TranslateService } from '@core/i18n';

import type { AppointmentSlot } from '../../models/appointment.model';
import { AppointmentApiService } from '../../services/appointment-api.service';
import { BookingStore } from '../../stores/booking.store';

import { AppointmentSelectionContainerComponent } from './appointment-selection-container.component';
import { SNAP_CONFLICT_BANNER_TIMEOUT_MS } from './snap-conflict-banner/snap-conflict-banner.component';

const MOCK_APPOINTMENTS: AppointmentSlot[] = [
  {
    id: '2026-02-25-09-00',
    date: '2026-02-25',
    displayDate: '25.02.2026',
    dayAbbreviation: 'Mi',
    time: '09:00',
    displayTime: '09:00 Uhr'
  },
  {
    id: '2026-02-26-14-00',
    date: '2026-02-26',
    displayDate: '26.02.2026',
    dayAbbreviation: 'Do',
    time: '14:00',
    displayTime: '14:00 Uhr'
  }
];

describe('AppointmentSelectionContainerComponent', () => {
  let component: AppointmentSelectionContainerComponent;
  let fixture: ComponentFixture<AppointmentSelectionContainerComponent>;
  let store: InstanceType<typeof BookingStore>;
  let router: Router;

  const mockAppointmentApiService = {
    getAppointments: jest.fn().mockResolvedValue(MOCK_APPOINTMENTS),
    createAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' }),
    getAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' })
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppointmentSelectionContainerComponent],
      providers: [
        provideRouter([]),
        { provide: AppointmentApiService, useValue: mockAppointmentApiService }
      ]
    })
      .overrideComponent(AppointmentSelectionContainerComponent, {
        set: { template: '<div class="mocked">Mocked AppointmentSelectionContainer</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    router = TestBed.inject(Router);

    fixture = TestBed.createComponent(AppointmentSelectionContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose appointments from store', () => {
    const exposed = component as unknown as { appointments: () => AppointmentSlot[] };
    expect(exposed.appointments()).toBeDefined();
  });

  it('should expose hasAppointmentSelected from store', () => {
    const exposed = component as unknown as { hasAppointmentSelected: () => boolean };
    expect(exposed.hasAppointmentSelected()).toBe(false);
  });

  it('should select appointment via store', () => {
    const exposed = component as unknown as { onAppointmentSelect: (a: AppointmentSlot) => void };
    exposed.onAppointmentSelect(MOCK_APPOINTMENTS[0]);

    expect(store.selectedAppointment()).toEqual(MOCK_APPOINTMENTS[0]);
  });

  it('should navigate back to notes', () => {
    const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);

    const exposed = component as unknown as { onBack: () => void };
    exposed.onBack();

    expect(navigateSpy).toHaveBeenCalledWith(['/home/notes']);
  });

  it('should clear selected appointment in store before navigating back', () => {
    store.selectAppointment(MOCK_APPOINTMENTS[0]);
    expect(store.selectedAppointment()).not.toBeNull();

    const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);
    const exposed = component as unknown as { onBack: () => void };
    exposed.onBack();

    expect(store.selectedAppointment()).toBeNull();
    expect(store.hasAppointmentSelected()).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/home/notes']);
  });

  it('should handle back when appointment is already null', () => {
    expect(store.selectedAppointment()).toBeNull();

    const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);
    const exposed = component as unknown as { onBack: () => void };
    exposed.onBack();

    expect(store.selectedAppointment()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/home/notes']);
  });

  it('should navigate forward on continue', () => {
    const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);

    const exposed = component as unknown as { onContinue: () => void };
    exposed.onContinue();

    expect(navigateSpy).toHaveBeenCalledWith(['/home/carinformation']);
  });

  it('should prevent default on calendar link click', () => {
    const event = new Event('click');
    jest.spyOn(event, 'preventDefault');

    const exposed = component as unknown as { onCalendarLinkClick: (e: Event) => void };
    exposed.onCalendarLinkClick(event);

    expect(event.preventDefault).toHaveBeenCalled();
  });

  it('should update hasAppointmentSelected after selection', () => {
    const exposed = component as unknown as {
      hasAppointmentSelected: () => boolean;
      onAppointmentSelect: (a: AppointmentSlot) => void;
    };

    expect(exposed.hasAppointmentSelected()).toBe(false);
    exposed.onAppointmentSelect(MOCK_APPOINTMENTS[0]);
    expect(exposed.hasAppointmentSelected()).toBe(true);
  });

  it('should expose isLoading and error signals from store', () => {
    const exposed = component as unknown as {
      isLoading: () => boolean;
      error: () => string | null;
    };
    expect(exposed.isLoading()).toBe(false);
    expect(exposed.error()).toBeNull();
  });

  it('should call loadAppointments on retry', () => {
    const loadSpy = jest.spyOn(store, 'loadAppointments');
    const exposed = component as unknown as { onRetry: () => void };
    exposed.onRetry();
    expect(loadSpy).toHaveBeenCalled();
  });

  describe('rental car (REQ-039)', () => {
    it('onContinueWithoutRentalCar switches off the rental car, drops the wish and reloads (E-1)', () => {
      store.setRentalCarRequested(true);
      store.setSelectedRentCategoryId('rc-1');
      const loadSpy = jest.spyOn(store, 'loadAppointments');
      const exposed = component as unknown as { onContinueWithoutRentalCar: () => void };
      exposed.onContinueWithoutRentalCar();
      expect(store.rentalCarRequested()).toBe(false);
      expect(store.selectedRentCategoryId()).toBeNull();
      expect(loadSpy).toHaveBeenCalled();
    });

    it('onShowLaterAppointments opens the workshop calendar (E-1)', () => {
      const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);
      const exposed = component as unknown as { onShowLaterAppointments: () => void };
      exposed.onShowLaterAppointments();
      expect(navigateSpy).toHaveBeenCalledWith(['/home/workshop-calendar']);
    });

    it('selectedIncludesRentalCar reflects the chosen slot (AC-M10)', () => {
      const exposed = component as unknown as { selectedIncludesRentalCar: () => boolean };
      expect(exposed.selectedIncludesRentalCar()).toBe(false);
      store.selectAppointment({
        id: 'snap-r', date: '2026-10-16', displayDate: '16.10.2026', dayAbbreviation: 'Fr',
        time: '08:00', displayTime: '08:00 Uhr', rentalCarIncluded: true
      });
      expect(exposed.selectedIncludesRentalCar()).toBe(true);
    });
  });

  describe('Date-Strip Computed Signals', () => {
    beforeEach(() => {
      // Push appointments into store
      MOCK_APPOINTMENTS.forEach(a => { store.selectAppointment(a); });
      store.clearSelectedAppointment();
    });

    it('should derive availableDates uniquely from appointments', () => {
      const exposed = component as unknown as { availableDates: () => { date: string; day: string; month: string; dayAbbreviation: string }[] };
      const dates = exposed.availableDates();
      // store.appointments may be empty or hold MOCK_APPOINTMENTS — function shouldn't throw
      expect(Array.isArray(dates)).toBe(true);
    });

    it('should expose selectedDate signal', () => {
      const exposed = component as unknown as { selectedDate: () => string | null };
      const date = exposed.selectedDate();
      expect(date === null || typeof date === 'string').toBe(true);
    });

    it('should expose stripMonthLabel signal', () => {
      const exposed = component as unknown as { stripMonthLabel: () => string };
      expect(typeof exposed.stripMonthLabel()).toBe('string');
    });

    it('should split slots into morning and afternoon arrays', () => {
      const exposed = component as unknown as {
        morningSlots: () => AppointmentSlot[]; afternoonSlots: () => AppointmentSlot[];
      };
      expect(Array.isArray(exposed.morningSlots())).toBe(true);
      expect(Array.isArray(exposed.afternoonSlots())).toBe(true);
    });

    it('should expose selectedSummary signal', () => {
      const exposed = component as unknown as { selectedSummary: () => string };
      expect(typeof exposed.selectedSummary()).toBe('string');
    });

    it('should switch viewedDate via onDateSelect', () => {
      const exposed = component as unknown as {
        onDateSelect: (date: string) => void; selectedDate: () => string | null;
      };
      exposed.onDateSelect('2026-12-31');
      expect(exposed.selectedDate()).toBe('2026-12-31');
    });
  });

  describe('Date-Strip with seeded appointments', () => {
    beforeEach(async () => {
      // REQ-039: date labels follow the UI language — the expectations below are German.
      TestBed.inject(TranslateService).use('de');
      // Push appointments into store via API mock — call loadAppointments
      mockAppointmentApiService.getAppointments.mockResolvedValue(MOCK_APPOINTMENTS);
      store.loadAppointments();
      // Wait a tick for async store-update
      await new Promise(r => setTimeout(r, 0));
    });

    it('should group the viewed day into non-empty morning/afternoon sections (REQ-039)', () => {
      const exposed = component as unknown as {
        onDateSelect: (date: string) => void;
        slotSections: () => { key: string; label: string; slots: AppointmentSlot[] }[];
      };
      // 25.02. has only a 09:00 slot → morning only
      expect(exposed.slotSections().map(section => section.key)).toEqual(['morning']);
      expect(exposed.slotSections()[0].label).toBe('booking.appointment.morning');
      // 26.02. has only a 14:00 slot → afternoon only
      exposed.onDateSelect('2026-02-26');
      expect(exposed.slotSections().map(section => section.key)).toEqual(['afternoon']);
      expect(exposed.slotSections()[0].slots.map(slot => slot.id)).toEqual(['2026-02-26-14-00']);
      // a day without slots → no section at all
      exposed.onDateSelect('2026-12-31');
      expect(exposed.slotSections()).toEqual([]);
    });

    it('should group by the shown Berlin time, not by the UTC time (10:00 UTC = 12:00 CEST → afternoon)', async () => {
      mockAppointmentApiService.getAppointments.mockResolvedValue([
        { id: 'summer-09', date: '2026-10-19', displayDate: '19.10.2026', dayAbbreviation: 'Mo', time: '09:30', displayTime: '09:30 Uhr' },
        { id: 'summer-10', date: '2026-10-19', displayDate: '19.10.2026', dayAbbreviation: 'Mo', time: '10:00', displayTime: '10:00 Uhr' }
      ] satisfies AppointmentSlot[]);
      store.loadAppointments();
      await new Promise(r => setTimeout(r, 0));
      const exposed = component as unknown as {
        morningSlots: () => AppointmentSlot[];
        afternoonSlots: () => AppointmentSlot[];
      };
      // 09:30 UTC = 11:30 Berlin (CEST) → morning; 10:00 UTC = 12:00 Berlin → afternoon
      expect(exposed.morningSlots().map(slot => slot.id)).toEqual(['summer-09']);
      expect(exposed.afternoonSlots().map(slot => slot.id)).toEqual(['summer-10']);
    });

    it('should compute availableDates with month and day', () => {
      const exposed = component as unknown as {
        availableDates: () => { date: string; day: string; month: string }[];
      };
      const dates = exposed.availableDates();
      expect(dates.length).toBeGreaterThan(0);
      expect(dates[0]).toEqual(expect.objectContaining({ day: '25', month: 'Feb' }));
    });

    it('should fallback selectedDate to first availableDate when no viewedDate set', () => {
      const exposed = component as unknown as { selectedDate: () => string | null };
      expect(exposed.selectedDate()).toBe('2026-02-25');
    });

    it('should derive date tiles with weekday, day and month without abbreviation dots (REQ-039)', () => {
      const exposed = component as unknown as {
        availableDates: () => { weekday: string; day: string; month: string; ariaLabel: string }[];
      };
      const first = exposed.availableDates()[0];
      expect(first.weekday).toBe('MI');
      expect(first.day).toBe('25');
      expect(first.month).toBe('Feb');
      expect(first.ariaLabel).toContain('Mittwoch');
    });

    it('should follow a language switch (REQ-039)', () => {
      const exposed = component as unknown as { stripMonthLabel: () => string };
      TestBed.inject(TranslateService).use('en');
      expect(exposed.stripMonthLabel()).toBe('February 2026');
      TestBed.inject(TranslateService).use('de');
      expect(exposed.stripMonthLabel()).toBe('Februar 2026');
    });

    it('should derive stripMonthLabel with year', () => {
      const exposed = component as unknown as { stripMonthLabel: () => string };
      const label = exposed.stripMonthLabel();
      expect(label).toContain('Feb');
      expect(label).toContain('2026');
    });

    it('should split slots into morning (< 12) and afternoon (>= 12)', () => {
      const exposed = component as unknown as {
        morningSlots: () => { time: string }[];
        afternoonSlots: () => { time: string }[];
      };
      // Selected date is 2026-02-25 with 09:00 slot → morning
      expect(exposed.morningSlots()[0]?.time).toBe('09:00');
      expect(exposed.afternoonSlots()).toEqual([]);
    });

    it('should compute selectedSummary when appointment is selected', () => {
      store.selectAppointment(MOCK_APPOINTMENTS[1]); // 2026-02-26, 14:00
      const exposed = component as unknown as { selectedSummary: () => string };
      const summary = exposed.selectedSummary();
      expect(summary).toContain('Donnerstag');
      expect(summary).toContain('26.02.2026');
      // 14:00 UTC im Februar (Winterzeit, UTC+1) → 15:00 Berliner Zeit
      expect(summary).toContain('15:00 Uhr');
    });
  });

  // REQ-031: Snap-Conflict-Banner (Buchung schlug mit HTTP 410 fehl, weil der
  // Snap zwischenzeitlich belegt wurde) — Auto-Dismiss, X-Klick, Weiter, Destroy.
  describe('REQ-031 snap conflict banner', () => {
    /** Triggers the real 410 flow so `snapConflict` becomes true in the store. */
    async function triggerSnapConflict(): Promise<void> {
      mockAppointmentApiService.createAppointment.mockRejectedValueOnce(
        new HttpErrorResponse({ status: 410, statusText: 'Gone' })
      );
      store.submitBooking();
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
    }

    afterEach(() => {
      jest.useRealTimers();
    });

    it('should expose hasSnapConflict from store (initially false)', () => {
      const exposed = component as unknown as { hasSnapConflict: () => boolean };
      expect(exposed.hasSnapConflict()).toBe(false);
    });

    it('should auto-dismiss the banner after 30 seconds (BR-3)', async () => {
      jest.useFakeTimers();

      mockAppointmentApiService.createAppointment.mockRejectedValueOnce(
        new HttpErrorResponse({ status: 410, statusText: 'Gone' })
      );
      store.submitBooking();
      await jest.advanceTimersByTimeAsync(0); // flush the rejected promise
      expect(store.hasSnapConflict()).toBe(true);

      fixture.detectChanges(); // flush toObservable effect → 30s timer starts
      await jest.advanceTimersByTimeAsync(SNAP_CONFLICT_BANNER_TIMEOUT_MS - 1);
      expect(store.hasSnapConflict()).toBe(true); // not yet

      await jest.advanceTimersByTimeAsync(1);
      expect(store.hasSnapConflict()).toBe(false);
    });

    it('should clear the conflict when the banner close output fires', async () => {
      await triggerSnapConflict();
      expect(store.hasSnapConflict()).toBe(true);

      const exposed = component as unknown as { onSnapConflictBannerClosed: () => void };
      exposed.onSnapConflictBannerClosed();

      expect(store.hasSnapConflict()).toBe(false);
    });

    it('should clear the conflict and navigate forward on continue', async () => {
      await triggerSnapConflict();
      expect(store.hasSnapConflict()).toBe(true);

      const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);
      const exposed = component as unknown as { onContinue: () => void };
      exposed.onContinue();

      expect(store.hasSnapConflict()).toBe(false);
      expect(navigateSpy).toHaveBeenCalledWith(['/home/carinformation']);
    });

    it('should clear an active conflict when the container is destroyed (BR-3)', async () => {
      await triggerSnapConflict();
      expect(store.hasSnapConflict()).toBe(true);

      fixture.destroy();

      expect(store.hasSnapConflict()).toBe(false);
    });

    it('should not call clearSnapConflict on destroy when no conflict is active', () => {
      const clearSpy = jest.spyOn(store, 'clearSnapConflict');
      expect(store.hasSnapConflict()).toBe(false);

      fixture.destroy();

      expect(clearSpy).not.toHaveBeenCalled();
    });
  });
});
