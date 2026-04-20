import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AVAILABLE_BRANDS } from '../../models/brand.model';
import { LOCATIONS_BY_BRAND } from '../../models/location.model';
import { AVAILABLE_SERVICES } from '../../models/service.model';
import { AppointmentApiService } from '../../services/appointment-api.service';
import { BookingApiService } from '../../services/booking-api.service';
import { WorkshopCalendarApiService } from '../../services/workshop-calendar-api.service';
import { BookingStore } from '../../stores/booking.store';

import { CarinformationContainerComponent } from './carinformation-container.component';

describe('CarinformationContainerComponent', () => {
  let component: CarinformationContainerComponent;
  let fixture: ComponentFixture<CarinformationContainerComponent>;
  let router: jest.Mocked<Router>;
  let store: InstanceType<typeof BookingStore>;

  beforeEach(async () => {
    router = { navigate: jest.fn() } as unknown as jest.Mocked<Router>;

    await TestBed.configureTestingModule({
      imports: [CarinformationContainerComponent],
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
        {
          provide: AppointmentApiService,
          useValue: { getAppointments: jest.fn().mockResolvedValue([]), createAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' }), getAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' }) }
        },
        {
          provide: WorkshopCalendarApiService,
          useValue: { getWorkshopCalendarDays: jest.fn().mockResolvedValue([]) }
        },
        { provide: Router, useValue: router }
      ]
    })
      .overrideComponent(CarinformationContainerComponent, {
        set: { template: '<div class="mocked">Mocked CarInformation Container</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    fixture = TestBed.createComponent(CarinformationContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should inject BookingStore', () => {
    expect(store).toBeTruthy();
  });

  describe('Forms', () => {
    it('should initialize customerForm', () => {
      const exposed = component as unknown as { customerForm: { valid: boolean } };
      expect(exposed.customerForm).toBeDefined();
    });

    it('should initialize vehicleForm', () => {
      const exposed = component as unknown as { vehicleForm: { valid: boolean } };
      expect(exposed.vehicleForm).toBeDefined();
    });

    it('should initialize privacyControl as false', () => {
      const exposed = component as unknown as { privacyControl: { value: boolean } };
      expect(exposed.privacyControl.value).toBe(false);
    });

    it('should have invalid customerForm initially', () => {
      const exposed = component as unknown as { customerForm: { valid: boolean } };
      expect(exposed.customerForm.valid).toBe(false);
    });
  });

  describe('onBack', () => {
    it('should clear car information and navigate to appointment', () => {
      const exposed = component as unknown as { onBack: () => void };
      exposed.onBack();
      expect(store.customerInfo()).toBeNull();
      expect(store.vehicleInfo()).toBeNull();
      expect(store.privacyConsent()).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/home/appointment']);
    });
  });

  describe('onSubmit', () => {
    it('should not save to store when forms are invalid', () => {
      const exposed = component as unknown as { onSubmit: (e: Event) => void };
      exposed.onSubmit({ preventDefault: jest.fn() } as unknown as Event);
      expect(store.customerInfo()).toBeNull();
      expect(store.vehicleInfo()).toBeNull();
    });

    it('should save to store and navigate when forms are valid', () => {
      const exposed = component as unknown as {
        onSubmit: (e: Event) => void;
        customerForm: { setValue: (v: unknown) => void };
        vehicleForm: { setValue: (v: unknown) => void };
        privacyControl: { setValue: (v: unknown) => void };
      };

      exposed.customerForm.setValue({
        email: 'test@example.com',
        salutation: 'mr',
        firstName: 'Max',
        lastName: 'Mustermann',
        street: 'Musterstraße 1',
        postalCode: '12345',
        city: 'Musterstadt',
        mobilePhone: '017012345678'
      });

      exposed.vehicleForm.setValue({
        licensePlate: 'B-AB 1234',
        mileage: 50000
      });

      exposed.privacyControl.setValue(true);
      exposed.onSubmit({ preventDefault: jest.fn() } as unknown as Event);

      expect(store.customerInfo()).not.toBeNull();
      expect(store.customerInfo()?.email).toBe('test@example.com');
      expect(store.vehicleInfo()).not.toBeNull();
      expect(store.vehicleInfo()?.licensePlate).toBe('B-AB 1234');
      expect(store.privacyConsent()).toBe(true);
      expect(router.navigate).toHaveBeenCalledWith(['/home/booking-overview']);
    });
  });

  describe('onReturningCustomer', () => {
    it('should be callable without throwing', () => {
      const exposed = component as unknown as { onReturningCustomer: () => void };
      expect(() => { exposed.onReturningCustomer(); }).not.toThrow();
    });
  });
});
