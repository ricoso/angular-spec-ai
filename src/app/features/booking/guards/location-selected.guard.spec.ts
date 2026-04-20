import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Router } from '@angular/router';

import { ALL_LOCATIONS } from '../models/location.model';
import { BookingApiService } from '../services/booking-api.service';
import { BookingStore } from '../stores/booking.store';

import { locationSelectedGuard } from './location-selected.guard';

describe('locationSelectedGuard', () => {
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

  it('should redirect to /home/location when no location selected', () => {
    const result = TestBed.runInInjectionContext(() =>
      locationSelectedGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/home/location']);
  });

  it('should allow access when location is selected', () => {
    store.setLocation(ALL_LOCATIONS[0]);
    const result = TestBed.runInInjectionContext(() =>
      locationSelectedGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });
});
