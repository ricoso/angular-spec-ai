import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

import { TraceService } from '@core/http/trace.service';

import { BookingApiService } from '../services/booking-api.service';
import { BookingStore } from '../stores/booking.store';

import { locationsResolver } from './locations.resolver';

describe('locationsResolver', () => {
  let store: InstanceType<typeof BookingStore>;
  let traceMock: jest.Mocked<TraceService>;

  beforeEach(() => {
    traceMock = { log: jest.fn(), error: jest.fn(), createCorrelationId: jest.fn() } as unknown as jest.Mocked<TraceService>;
    TestBed.configureTestingModule({
      providers: [
        BookingStore,
        {
          provide: BookingApiService,
          useValue: {
            getBranches: jest.fn().mockResolvedValue([]),
            getLocations: jest.fn().mockResolvedValue([])
          }
        },
        { provide: TraceService, useValue: traceMock }
      ]
    });
    store = TestBed.inject(BookingStore);
  });

  it('should call loadBranches and loadLocations', () => {
    const branchSpy = jest.spyOn(store, 'loadBranches');
    const locSpy = jest.spyOn(store, 'loadLocations');
    TestBed.runInInjectionContext(() =>
      locationsResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(branchSpy).toHaveBeenCalled();
    expect(locSpy).toHaveBeenCalled();
  });

  it('should log via TraceService', () => {
    TestBed.runInInjectionContext(() =>
      locationsResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(traceMock.log).toHaveBeenCalled();
  });

  it('should return void', () => {
    const result = TestBed.runInInjectionContext(() =>
      locationsResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).toBeUndefined();
  });
});
