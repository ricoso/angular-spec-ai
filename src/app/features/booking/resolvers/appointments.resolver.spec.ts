import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

import { AppointmentApiService } from '../services/appointment-api.service';
import { BookingStore } from '../stores/booking.store';

import { appointmentsResolver } from './appointments.resolver';

describe('appointmentsResolver', () => {
  let store: InstanceType<typeof BookingStore>;

  const mockAppointmentApiService = {
    getAppointments: jest.fn().mockResolvedValue([]),
    createAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' }),
    getAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' })
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AppointmentApiService, useValue: mockAppointmentApiService }
      ]
    });

    store = TestBed.inject(BookingStore);
  });

  it('should call store.loadAppointments', () => {
    const loadSpy = jest.spyOn(store, 'loadAppointments');

    TestBed.runInInjectionContext(() => {
      appointmentsResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);
    });

    expect(loadSpy).toHaveBeenCalled();
  });

  it('should return void', () => {
    const result = TestBed.runInInjectionContext(() => appointmentsResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

    expect(result).toBeUndefined();
  });
});
