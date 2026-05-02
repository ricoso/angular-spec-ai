import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

import { BookingApiService } from '../services/booking-api.service';
import { BookingStore } from '../stores/booking.store';

import { brandsResolver } from './brands.resolver';

describe('brandsResolver', () => {
  let store: InstanceType<typeof BookingStore>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        BookingStore,
        {
          provide: BookingApiService,
          useValue: {
            getBrands: jest.fn().mockResolvedValue([]),
            getBrandsByBranch: jest.fn().mockResolvedValue([]),
            getLocations: jest.fn().mockResolvedValue([]),
            getBranches: jest.fn().mockResolvedValue([])
          }
        }
      ]
    });
    store = TestBed.inject(BookingStore);
  });

  it('should not call loadBrands when no branch is selected', () => {
    const spy = jest.spyOn(store, 'loadBrands');
    TestBed.runInInjectionContext(() =>
      brandsResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(spy).not.toHaveBeenCalled();
  });

  it('should call loadBrands when a branch is set', () => {
    store.selectBranch({
      branchId: 'b1',
      name: 'Test',
      address: { street: 'Str. 1', zip: '10000', city: 'X', country: 'DE' },
      brands: []
    });
    const spy = jest.spyOn(store, 'loadBrands');
    TestBed.runInInjectionContext(() =>
      brandsResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(spy).toHaveBeenCalledWith('b1');
  });

  it('should return void', () => {
    const result = TestBed.runInInjectionContext(() =>
      brandsResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).toBeUndefined();
  });
});
