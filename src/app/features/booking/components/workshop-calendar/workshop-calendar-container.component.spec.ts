import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import type { WorkshopCalendarDay, WorkshopTimeSlot } from '../../models/workshop-calendar.model';
import { AppointmentApiService } from '../../services/appointment-api.service';
import { WorkshopCalendarApiService } from '../../services/workshop-calendar-api.service';
import { BookingStore } from '../../stores/booking.store';

import { WorkshopCalendarContainerComponent } from './workshop-calendar-container.component';

const MOCK_SLOT: WorkshopTimeSlot = {
  id: '2026-03-02-09-00',
  time: '09:00',
  displayTime: '09:00 Uhr'
};

const MOCK_DAY: WorkshopCalendarDay = {
  date: '2026-03-02',
  displayDate: '02.03.2026',
  dayAbbreviation: 'Mo',
  displayHeading: 'Mo, 02.03.2026',
  slots: [MOCK_SLOT]
};

const MOCK_DAYS: WorkshopCalendarDay[] = [MOCK_DAY];

describe('WorkshopCalendarContainerComponent', () => {
  let component: WorkshopCalendarContainerComponent;
  let fixture: ComponentFixture<WorkshopCalendarContainerComponent>;
  let store: InstanceType<typeof BookingStore>;
  let router: Router;

  const mockWorkshopCalendarApiService = {
    getWorkshopCalendarDays: jest.fn().mockResolvedValue(MOCK_DAYS)
  };

  const mockAppointmentApiService = {
    getAppointments: jest.fn().mockResolvedValue([]),
    createAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' }),
    getAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' })
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkshopCalendarContainerComponent],
      providers: [
        provideRouter([]),
        { provide: WorkshopCalendarApiService, useValue: mockWorkshopCalendarApiService },
        { provide: AppointmentApiService, useValue: mockAppointmentApiService }
      ]
    })
      .overrideComponent(WorkshopCalendarContainerComponent, {
        set: { template: '<div class="mocked">Mocked WorkshopCalendarContainer</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    router = TestBed.inject(Router);

    fixture = TestBed.createComponent(WorkshopCalendarContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose workshopCalendarDays from store', () => {
    const exposed = component as unknown as { workshopCalendarDays: () => WorkshopCalendarDay[] };
    expect(exposed.workshopCalendarDays()).toBeDefined();
  });

  it('should expose workshopCalendarDate from store', () => {
    const exposed = component as unknown as { workshopCalendarDate: () => string | null };
    expect(exposed.workshopCalendarDate()).toBeNull();
  });

  it('should expose hasAppointmentSelected from store', () => {
    const exposed = component as unknown as { hasAppointmentSelected: () => boolean };
    expect(exposed.hasAppointmentSelected()).toBe(false);
  });

  it('should set workshop calendar date on date selection', () => {
    const exposed = component as unknown as { onDateSelected: (d: Date) => void };
    const testDate = new Date(2026, 2, 2); // March 2, 2026

    exposed.onDateSelected(testDate);

    expect(store.workshopCalendarDate()).toBe('2026-03-02');
  });

  it('should select appointment on slot selection', () => {
    const exposed = component as unknown as {
      onSlotSelected: (s: WorkshopTimeSlot, d: WorkshopCalendarDay) => void;
    };

    exposed.onSlotSelected(MOCK_SLOT, MOCK_DAY);

    const appointment = store.selectedAppointment();
    expect(appointment).not.toBeNull();
    expect(appointment!.id).toBe(MOCK_SLOT.id);
    expect(appointment!.date).toBe(MOCK_DAY.date);
    expect(appointment!.time).toBe(MOCK_SLOT.time);
    expect(appointment!.dayAbbreviation).toBe(MOCK_DAY.dayAbbreviation);
  });

  it('should navigate back to appointment page', () => {
    const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);

    const exposed = component as unknown as { onBack: () => void };
    exposed.onBack();

    expect(navigateSpy).toHaveBeenCalledWith(['/home/appointment']);
  });

  it('should clear workshop calendar on back navigation', () => {
    store.setWorkshopCalendarDate('2026-03-02');
    expect(store.workshopCalendarDate()).not.toBeNull();

    jest.spyOn(router, 'navigate').mockResolvedValue(true);
    const exposed = component as unknown as { onBack: () => void };
    exposed.onBack();

    expect(store.workshopCalendarDate()).toBeNull();
    expect(store.workshopCalendarDays()).toEqual([]);
  });

  it('should navigate forward on continue', () => {
    const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);

    const exposed = component as unknown as { onContinue: () => void };
    exposed.onContinue();

    expect(navigateSpy).toHaveBeenCalledWith(['/home/carinformation']);
  });

  it('should update hasAppointmentSelected after slot selection', () => {
    const exposed = component as unknown as {
      hasAppointmentSelected: () => boolean;
      onSlotSelected: (s: WorkshopTimeSlot, d: WorkshopCalendarDay) => void;
    };

    expect(exposed.hasAppointmentSelected()).toBe(false);
    exposed.onSlotSelected(MOCK_SLOT, MOCK_DAY);
    expect(exposed.hasAppointmentSelected()).toBe(true);
  });

  it('should expose isLoading and error signals', () => {
    const exposed = component as unknown as { isLoading: () => boolean; error: () => string | null };
    expect(exposed.isLoading()).toBe(false);
    expect(exposed.error()).toBeNull();
  });

  it('should reload workshop calendar days on retry when a date is set', () => {
    store.setWorkshopCalendarDate('2026-03-02');
    const loadSpy = jest.spyOn(store, 'loadWorkshopCalendarDays');
    const exposed = component as unknown as { onRetry: () => void };
    exposed.onRetry();
    expect(loadSpy).toHaveBeenCalledWith('2026-03-02');
  });

  it('should not reload on retry when no date is selected', () => {
    const loadSpy = jest.spyOn(store, 'loadWorkshopCalendarDays');
    const exposed = component as unknown as { onRetry: () => void };
    exposed.onRetry();
    expect(loadSpy).not.toHaveBeenCalled();
  });
});
