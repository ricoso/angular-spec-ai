import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot } from '@angular/router';
import { Router } from '@angular/router';

import { AppointmentApiService } from '../services/appointment-api.service';
import { BookingApiService } from '../services/booking-api.service';
import { WorkshopCalendarApiService } from '../services/workshop-calendar-api.service';
import { BookingStore } from '../stores/booking.store';

import { deepLinkGuard } from './deep-link.guard';

describe('deepLinkGuard', () => {
  let store: InstanceType<typeof BookingStore>;
  let apiSpy: jest.Mocked<BookingApiService>;
  let router: { createUrlTree: jest.Mock; navigate: jest.Mock };

  beforeEach(() => {
    apiSpy = {
      getBrands: jest.fn().mockResolvedValue([]),
      getBrandsByBranch: jest.fn().mockResolvedValue([]),
      getLocations: jest.fn().mockResolvedValue([]),
      getBranchById: jest.fn().mockResolvedValue(null),
      getBranches: jest.fn().mockResolvedValue([]),
      getServices: jest.fn().mockResolvedValue([]),
      getBrandLogoPath: jest.fn().mockReturnValue('assets/brands/default.svg')
    } as unknown as jest.Mocked<BookingApiService>;

    router = {
      createUrlTree: jest.fn().mockReturnValue('/home/brand'),
      navigate: jest.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        BookingStore,
        { provide: BookingApiService, useValue: apiSpy },
        { provide: AppointmentApiService, useValue: { getAppointments: jest.fn().mockResolvedValue([]), createAppointment: jest.fn().mockResolvedValue({ id: "apt-1", status: "confirmed" }), getAppointment: jest.fn().mockResolvedValue({ id: "apt-1", status: "confirmed" }) } },
        { provide: WorkshopCalendarApiService, useValue: { getWorkshopCalendarDays: jest.fn().mockResolvedValue([]) } },
        { provide: Router, useValue: router }
      ]
    });

    store = TestBed.inject(BookingStore);
  });

  function createRoute(params: Record<string, string>): ActivatedRouteSnapshot {
    return {
      queryParamMap: {
        get: (key: string) => params[key] ?? null,
        has: (key: string) => key in params,
        getAll: () => [],
        keys: Object.keys(params)
      }
    } as unknown as ActivatedRouteSnapshot;
  }

  it('should allow normal navigation when no query params', async () => {
    const route = createRoute({});
    const result = await TestBed.runInInjectionContext(() => deepLinkGuard(route, {} as never));
    expect(result).toBe(true);
  });

  it('should allow normal navigation when only branchId is present', async () => {
    const route = createRoute({ branchId: 'b1' });
    const result = await TestBed.runInInjectionContext(() => deepLinkGuard(route, {} as never));
    expect(result).toBe(true);
  });

  it('should allow normal navigation when only groupId is present', async () => {
    const route = createRoute({ groupId: 'g1' });
    const result = await TestBed.runInInjectionContext(() => deepLinkGuard(route, {} as never));
    expect(result).toBe(true);
  });

  it('should redirect to brand route when valid deep-link params', async () => {
    const mockBranch = {
      branchId: 'b1',
      name: 'Test Branch',
      address: { street: 'Test St', zip: '12345', city: 'Berlin' },
      brands: ['Audi']
    };
    apiSpy.getBranchById.mockResolvedValue(mockBranch);

    const route = createRoute({ branchId: 'b1', groupId: 'g1' });
    await TestBed.runInInjectionContext(() => deepLinkGuard(route, {} as never));

    expect(apiSpy.getBranchById).toHaveBeenCalledWith('b1');
    expect(store.isDeepLinked()).toBe(true);
    expect(store.selectedBranch()).toEqual(mockBranch);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/home/brand'], { queryParams: { groupId: 'g1', branchId: 'b1' } });
  });

  it('should fall back to normal flow when branch not found', async () => {
    apiSpy.getBranchById.mockResolvedValue(null);

    const route = createRoute({ branchId: 'invalid', groupId: 'g1' });
    const result = await TestBed.runInInjectionContext(() => deepLinkGuard(route, {} as never));

    expect(result).toBe(true);
    expect(store.isDeepLinked()).toBe(false);
  });

  it('should fall back to normal flow on API error', async () => {
    apiSpy.getBranchById.mockRejectedValue(new Error('Network error'));

    const route = createRoute({ branchId: 'b1', groupId: 'g1' });
    const result = await TestBed.runInInjectionContext(() => deepLinkGuard(route, {} as never));

    expect(result).toBe(true);
    expect(store.isDeepLinked()).toBe(false);
  });
});
