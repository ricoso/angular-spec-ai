import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import type { AppointmentSlot } from '../../models/appointment.model';
import type { CustomerInfo, VehicleInfo } from '../../models/customer.model';
import type { LocationDisplay } from '../../models/location.model';
import { AppointmentApiService } from '../../services/appointment-api.service';
import { BookingApiService } from '../../services/booking-api.service';
import { WorkshopCalendarApiService } from '../../services/workshop-calendar-api.service';
import { BookingStore } from '../../stores/booking.store';

import { BookingOverviewContainerComponent } from './booking-overview-container.component';

const MOCK_APPOINTMENT: AppointmentSlot = {
  id: '2026-03-15-10-00',
  date: '2026-03-15',
  displayDate: '15.03.2026',
  dayAbbreviation: 'Mo',
  time: '10:00',
  displayTime: '10:00 Uhr'
};

const MOCK_LOCATION: LocationDisplay = {
  id: 'loc-1',
  name: 'Autohaus München',
  city: 'München',
  formattedAddress: 'München'
};

const MOCK_CUSTOMER_INFO: CustomerInfo = {
  email: 'max@example.com',
  salutation: 'mr',
  firstName: 'Max',
  lastName: 'Mustermann',
  street: 'Musterstr. 1',
  postalCode: '80331',
  city: 'München',
  mobilePhone: '+49 170 1234567'
};

const MOCK_VEHICLE_INFO: VehicleInfo = {
  licensePlate: 'M-AB 1234',
  mileage: 45000
};

describe('BookingOverviewContainerComponent', () => {
  let component: BookingOverviewContainerComponent;
  let fixture: ComponentFixture<BookingOverviewContainerComponent>;
  let store: InstanceType<typeof BookingStore>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookingOverviewContainerComponent],
      providers: [
        provideRouter([]),
        {
          provide: BookingApiService,
          useValue: {
            getBrands: jest.fn().mockResolvedValue([]),
            getLocations: jest.fn().mockResolvedValue([]),
            getServices: jest.fn().mockResolvedValue([])
          }
        },
        {
          provide: AppointmentApiService,
          useValue: {
            getAppointments: jest.fn().mockResolvedValue([]),
            createAppointment: jest.fn().mockResolvedValue({ id: 'test-apt-1', status: 'confirmed' }),
            getAppointment: jest.fn().mockResolvedValue({ id: 'test-apt-1', status: 'confirmed' })
          }
        },
        {
          provide: WorkshopCalendarApiService,
          useValue: {
            getWorkshopSlots: jest.fn().mockResolvedValue([])
          }
        }
      ]
    })
      .overrideComponent(BookingOverviewContainerComponent, {
        set: { template: '<div class="mocked">Mocked BookingOverviewContainer</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    router = TestBed.inject(Router);

    fixture = TestBed.createComponent(BookingOverviewContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose appointment from store', () => {
    const exposed = component as unknown as { appointment: () => AppointmentSlot | null };
    expect(exposed.appointment()).toBeNull();

    store.selectAppointment(MOCK_APPOINTMENT);
    expect(exposed.appointment()).toEqual(MOCK_APPOINTMENT);
  });

  it('should expose customerInfo from store', () => {
    const exposed = component as unknown as { customerInfo: () => CustomerInfo | null };
    expect(exposed.customerInfo()).toBeNull();

    store.setCustomerInfo(MOCK_CUSTOMER_INFO);
    expect(exposed.customerInfo()).toEqual(MOCK_CUSTOMER_INFO);
  });

  it('should expose vehicleInfo from store', () => {
    const exposed = component as unknown as { vehicleInfo: () => VehicleInfo | null };
    expect(exposed.vehicleInfo()).toBeNull();

    store.setVehicleInfo(MOCK_VEHICLE_INFO);
    expect(exposed.vehicleInfo()).toEqual(MOCK_VEHICLE_INFO);
  });

  it('should resolve brand name from selectedBrand', () => {
    const exposed = component as unknown as { resolvedBrandName: () => string };
    expect(exposed.resolvedBrandName()).toBe('');

    store.setBrand('audi');
    expect(exposed.resolvedBrandName()).toBe('Audi');
  });

  it('should resolve location name from selectedLocation', () => {
    const exposed = component as unknown as { resolvedLocationName: () => string };
    expect(exposed.resolvedLocationName()).toBe('');

    store.setLocation(MOCK_LOCATION);
    expect(exposed.resolvedLocationName()).toBe('Autohaus München');
  });

  it('should compute resolved service labels', () => {
    const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
    expect(Object.keys(exposed.resolvedServiceLabels()).length).toBe(0);

    store.toggleService('tuv');
    const labels = exposed.resolvedServiceLabels();
    expect(labels.tuv).toBeDefined();
    expect(typeof labels.tuv).toBe('string');
  });

  it('should navigate back to carinformation on onBack()', () => {
    const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);

    const exposed = component as unknown as { onBack: () => void };
    exposed.onBack();

    expect(navigateSpy).toHaveBeenCalledWith(['/home/carinformation']);
  });

  it('should call submitBooking on onSubmit()', () => {
    const submitSpy = jest.spyOn(store, 'submitBooking');

    const exposed = component as unknown as { onSubmit: () => void };
    exposed.onSubmit();

    expect(submitSpy).toHaveBeenCalled();
  });

  it('should handle unknown brand gracefully', () => {
    const exposed = component as unknown as { resolvedBrandName: () => string };
    store.setBrand('unknown-brand' as never);
    expect(exposed.resolvedBrandName()).toBe('unknown-brand');
  });

  it('should handle services with variants', () => {
    const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
    store.toggleService('tire-change');
    store.confirmServiceOptions('tire-change', ['stored-tires']);

    const labels = exposed.resolvedServiceLabels();
    expect(labels['tire-change']).toBeDefined();
    expect(labels['tire-change']).toContain('—');
  });

  it('should expose isSubmitting and hasSubmitted signals', () => {
    const exposed = component as unknown as {
      isSubmitting: () => boolean;
      hasSubmitted: () => boolean;
      submitError: () => boolean;
    };
    expect(exposed.isSubmitting()).toBe(false);
    expect(exposed.hasSubmitted()).toBe(false);
    expect(exposed.submitError()).toBe(false);
  });

  it('should call resetBookingSubmission and submitBooking on retry', () => {
    const resetSpy = jest.spyOn(store, 'resetBookingSubmission');
    const submitSpy = jest.spyOn(store, 'submitBooking');
    const exposed = component as unknown as { onRetry: () => void };
    exposed.onRetry();
    expect(resetSpy).toHaveBeenCalled();
    expect(submitSpy).toHaveBeenCalled();
  });
});
