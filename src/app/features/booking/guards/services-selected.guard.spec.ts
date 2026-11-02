import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Router } from '@angular/router';

import { ALL_LOCATIONS } from '../models/location.model';
import { BookingApiService } from '../services/booking-api.service';
import { BookingStore } from '../stores/booking.store';

import { servicesSelectedGuard } from './services-selected.guard';

describe('servicesSelectedGuard', () => {
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
      servicesSelectedGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/home/location']);
  });

  it('should redirect to /home/brand when location selected but no brand', () => {
    store.setLocation(ALL_LOCATIONS[0]);
    const result = TestBed.runInInjectionContext(() =>
      servicesSelectedGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/home/brand']);
  });

  it('should redirect to /home/services when no services selected', () => {
    store.setLocation(ALL_LOCATIONS[0]);
    store.setBrand('audi');
    const result = TestBed.runInInjectionContext(() =>
      servicesSelectedGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).not.toBe(true);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/home/services']);
  });

  it('should allow access when all prerequisites are met', () => {
    store.setLocation(ALL_LOCATIONS[0]);
    store.setBrand('audi');
    store.toggleService('tuv');
    const result = TestBed.runInInjectionContext(() =>
      servicesSelectedGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).toBe(true);
  });

  it('should allow access with multiple services selected', () => {
    store.setLocation(ALL_LOCATIONS[1]);
    store.setBrand('bmw');
    store.toggleService('tuv');
    store.toggleService('inspection');
    const result = TestBed.runInInjectionContext(() =>
      servicesSelectedGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).toBe(true);
  });
});
