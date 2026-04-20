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
});
