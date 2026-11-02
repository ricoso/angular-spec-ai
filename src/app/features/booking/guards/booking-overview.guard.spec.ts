import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, GuardResult, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Router } from '@angular/router';

import type { LocationDisplay } from '../models/location.model';
import { AppointmentApiService } from '../services/appointment-api.service';
import { BookingApiService } from '../services/booking-api.service';
import { WorkshopCalendarApiService } from '../services/workshop-calendar-api.service';
import { BookingStore } from '../stores/booking.store';

import { bookingOverviewGuard } from './booking-overview.guard';

const MOCK_LOCATION: LocationDisplay = {
  id: 'loc-1',
  name: 'Autohaus München',
  city: 'München',
  formattedAddress: 'München'
};

describe('bookingOverviewGuard', () => {
  let store: InstanceType<typeof BookingStore>;
  let router: Router;

  beforeEach(() => {
    const mockUrlTree = { toString: () => '/mock' } as unknown as UrlTree;

    TestBed.configureTestingModule({
      providers: [
        BookingStore,
        {
          provide: BookingApiService,
          useValue: {
            getBrands: jest.fn().mockResolvedValue([]),
            getBrandsByBranch: jest.fn().mockResolvedValue([]),
            getBranchById: jest.fn().mockResolvedValue(null),
            getBrandLogoPath: jest.fn().mockReturnValue('assets/brands/default.svg'),
            getBranches: jest.fn().mockResolvedValue([]),
            getServices: jest.fn().mockResolvedValue([])
          }
        },
        {
          provide: AppointmentApiService,
          useValue: {
            getAppointments: jest.fn().mockResolvedValue([]),
            createAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' }),
            getAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' })
          }
        },
        {
          provide: WorkshopCalendarApiService,
          useValue: {
            getWorkshopCalendarDays: jest.fn().mockResolvedValue([])
          }
        },
        {
          provide: Router,
          useValue: {
            createUrlTree: jest.fn().mockReturnValue(mockUrlTree)
          }
        }
      ]
    });

    store = TestBed.inject(BookingStore);
    router = TestBed.inject(Router);
  });

  function runGuard(): GuardResult {
    return TestBed.runInInjectionContext(() =>
      bookingOverviewGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    ) as GuardResult;
  }

  function setupCompleteBooking(): void {
    store.setLocation(MOCK_LOCATION);
    store.setBrand('audi');
    store.toggleService('tuv');
    store.selectAppointment({
      id: '2026-03-15-10-00',
      date: '2026-03-15',
      displayDate: '15.03.2026',
      dayAbbreviation: 'Mo',
      time: '10:00',
      displayTime: '10:00 Uhr'
    });
    store.setCustomerInfo({
      email: 'max@example.com',
      salutation: 'mr',
      firstName: 'Max',
      lastName: 'Mustermann',
      street: 'Musterstr. 1',
      postalCode: '80331',
      city: 'München',
      mobilePhone: '+49 170 1234567'
    });
    store.setVehicleInfo({
      licensePlate: 'M-AB 1234',
      mileage: 45000,

    });
    store.setPrivacyConsent(true);
  }

  it('should redirect to /home when no data is selected', () => {
    const result = runGuard();
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/home']);
  });

  it('should redirect when only brand is selected', () => {
    store.setBrand('audi');
    const result = runGuard();
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/home']);
  });

  it('should redirect when privacy consent is missing', () => {
    store.setLocation(MOCK_LOCATION);
    store.setBrand('audi');
    store.toggleService('tuv');
    store.selectAppointment({
      id: '2026-03-15-10-00',
      date: '2026-03-15',
      displayDate: '15.03.2026',
      dayAbbreviation: 'Mo',
      time: '10:00',
      displayTime: '10:00 Uhr'
    });
    store.setCustomerInfo({
      email: 'max@example.com',
      salutation: 'mr',
      firstName: 'Max',
      lastName: 'Mustermann',
      street: 'Musterstr. 1',
      postalCode: '80331',
      city: 'München',
      mobilePhone: '+49 170 1234567'
    });
    store.setVehicleInfo({
      licensePlate: 'M-AB 1234',
      mileage: 45000,

    });

    const result = runGuard();
    expect(result).not.toBe(true);
  });

  it('should allow access when all booking data is complete', () => {
    setupCompleteBooking();

    const result = runGuard();
    expect(result).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('should redirect when customer info is missing', () => {
    store.setLocation(MOCK_LOCATION);
    store.setBrand('audi');
    store.toggleService('tuv');
    store.selectAppointment({
      id: '2026-03-15-10-00',
      date: '2026-03-15',
      displayDate: '15.03.2026',
      dayAbbreviation: 'Mo',
      time: '10:00',
      displayTime: '10:00 Uhr'
    });
    store.setVehicleInfo({
      licensePlate: 'M-AB 1234',
      mileage: 45000,

    });
    store.setPrivacyConsent(true);

    const result = runGuard();
    expect(result).not.toBe(true);
  });

  it('should redirect when services are empty', () => {
    store.setLocation(MOCK_LOCATION);
    store.setBrand('audi');
    store.selectAppointment({
      id: '2026-03-15-10-00',
      date: '2026-03-15',
      displayDate: '15.03.2026',
      dayAbbreviation: 'Mo',
      time: '10:00',
      displayTime: '10:00 Uhr'
    });
    store.setCustomerInfo({
      email: 'max@example.com',
      salutation: 'mr',
      firstName: 'Max',
      lastName: 'Mustermann',
      street: 'Musterstr. 1',
      postalCode: '80331',
      city: 'München',
      mobilePhone: '+49 170 1234567'
    });
    store.setVehicleInfo({
      licensePlate: 'M-AB 1234',
      mileage: 45000,

    });
    store.setPrivacyConsent(true);

    const result = runGuard();
    expect(result).not.toBe(true);
  });
});
