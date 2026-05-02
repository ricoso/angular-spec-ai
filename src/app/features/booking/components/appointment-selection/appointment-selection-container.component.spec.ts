import type { ComponentFixture} from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter,Router } from '@angular/router';

import type { AppointmentSlot } from '../../models/appointment.model';
import { AppointmentApiService } from '../../services/appointment-api.service';
import { BookingStore } from '../../stores/booking.store';

import { AppointmentSelectionContainerComponent } from './appointment-selection-container.component';

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

  describe('Date-Strip Computed Signals', () => {
    beforeEach(() => {
      // Push appointments into store
      MOCK_APPOINTMENTS.forEach(a => store.selectAppointment(a));
      store.clearSelectedAppointment();
    });

    it('should derive availableDates uniquely from appointments', () => {
      const exposed = component as unknown as { availableDates: () => Array<{ date: string; day: string; month: string; dayAbbreviation: string }> };
      const dates = exposed.availableDates();
      // store.appointments may be empty or hold MOCK_APPOINTMENTS — function shouldn't throw
      expect(Array.isArray(dates)).toBe(true);
    });

    it('should expose selectedDate signal', () => {
      const exposed = component as unknown as { selectedDate: () => string | null };
      const date = exposed.selectedDate();
      expect(date === null || typeof date === 'string').toBe(true);
    });

    it('should expose selectedDateLabel signal', () => {
      const exposed = component as unknown as { selectedDateLabel: () => string };
      expect(typeof exposed.selectedDateLabel()).toBe('string');
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
      // Push appointments into store via API mock — call loadAppointments
      mockAppointmentApiService.getAppointments.mockResolvedValue(MOCK_APPOINTMENTS);
      store.loadAppointments();
      // Wait a tick for async store-update
      await new Promise(r => setTimeout(r, 0));
    });

    it('should compute availableDates with month and day', () => {
      const exposed = component as unknown as {
        availableDates: () => Array<{ date: string; day: string; month: string }>;
      };
      const dates = exposed.availableDates();
      expect(dates.length).toBeGreaterThan(0);
      expect(dates[0]).toEqual(expect.objectContaining({ day: '25', month: 'Feb' }));
    });

    it('should fallback selectedDate to first availableDate when no viewedDate set', () => {
      const exposed = component as unknown as { selectedDate: () => string | null };
      expect(exposed.selectedDate()).toBe('2026-02-25');
    });

    it('should derive selectedDateLabel with full weekday name', () => {
      const exposed = component as unknown as { selectedDateLabel: () => string };
      const label = exposed.selectedDateLabel();
      expect(label).toContain('Mittwoch');
      expect(label).toContain('25.02.2026');
    });

    it('should derive stripMonthLabel with year', () => {
      const exposed = component as unknown as { stripMonthLabel: () => string };
      const label = exposed.stripMonthLabel();
      expect(label).toContain('Feb');
      expect(label).toContain('2026');
    });

    it('should split slots into morning (< 12) and afternoon (>= 12)', () => {
      const exposed = component as unknown as {
        morningSlots: () => Array<{ time: string }>;
        afternoonSlots: () => Array<{ time: string }>;
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
      expect(summary).toContain('14:00 Uhr');
    });
  });
});
