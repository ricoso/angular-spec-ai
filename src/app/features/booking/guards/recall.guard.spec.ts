import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot } from '@angular/router';
import { Router } from '@angular/router';

import type { BranchConfig } from '../models/branch-config.model';
import { AppointmentApiService } from '../services/appointment-api.service';
import { BookingApiService } from '../services/booking-api.service';
import { WorkshopCalendarApiService } from '../services/workshop-calendar-api.service';
import { BookingStore } from '../stores/booking.store';

import { recallBlocksServicesGuard, recallGuard, recallServiceGuard } from './recall.guard';

const RECALL_GROUP_ID = '6ac7939ba4c0acee699905a8';
const RECALL_OP = { id: 'op-recall', name: 'Rückrufaktion Lenkungsgetriebe Schraube', description: null, tagIds: ['c1'], order: 1, imageId: null };
const BRANCH: BranchConfig = { branchId: 'b-recall', name: 'Test', address: { street: 'S', zip: '1', city: 'C' }, brands: [] };

describe('recallGuard / recallServiceGuard (REQ-041)', () => {
  let store: InstanceType<typeof BookingStore>;
  let apiSpy: jest.Mocked<BookingApiService>;

  beforeEach(() => {
    apiSpy = {
      getBrands: jest.fn().mockResolvedValue([]),
      getLocations: jest.fn().mockResolvedValue([]),
      getBranches: jest.fn().mockResolvedValue([]),
      getServices: jest.fn().mockResolvedValue([]),
      getOperationsByBranch: jest.fn().mockResolvedValue([RECALL_OP]),
      getBrandLogoPath: jest.fn().mockReturnValue('assets/brands/default.svg')
    } as unknown as jest.Mocked<BookingApiService>;

    TestBed.configureTestingModule({
      providers: [
        BookingStore,
        { provide: BookingApiService, useValue: apiSpy },
        { provide: AppointmentApiService, useValue: { getAppointments: jest.fn().mockResolvedValue([]), createAppointment: jest.fn(), getAppointment: jest.fn() } },
        { provide: WorkshopCalendarApiService, useValue: { getWorkshopCalendarDays: jest.fn().mockResolvedValue([]) } },
        { provide: Router, useValue: { navigate: jest.fn(), createUrlTree: jest.fn() } }
      ]
    });

    store = TestBed.inject(BookingStore);
  });

  function route(params: Record<string, string>): ActivatedRouteSnapshot {
    return {
      queryParamMap: { get: (k: string) => params[k] ?? null, has: (k: string) => k in params, getAll: () => [], keys: Object.keys(params) }
    } as unknown as ActivatedRouteSnapshot;
  }

  describe('recallGuard', () => {
    it('activates recall mode with the VIN when recall=true', async () => {
      const r = await TestBed.runInInjectionContext(() => recallGuard(route({ recall: 'true', vin: 'wvwzzz1jz3w386752' }), {} as never));
      expect(r).toBe(true);
      expect(store.isRecallMode()).toBe(true);
      expect(store.recallVin()).toBe('WVWZZZ1JZ3W386752');
      expect(store.deepLinkGroupId()).toBe(RECALL_GROUP_ID);
    });

    it('is a no-op without the recall flag (normal flow preserved)', async () => {
      const r = await TestBed.runInInjectionContext(() => recallGuard(route({}), {} as never));
      expect(r).toBe(true);
      expect(store.isRecallMode()).toBe(false);
    });
  });

  describe('recallServiceGuard', () => {
    it('auto-selects the recall service in recall mode', async () => {
      store.selectBranch(BRANCH);
      store.activateRecallMode('WVWZZZ1JZ3W386752');
      const r = await TestBed.runInInjectionContext(() => recallServiceGuard(route({}), {} as never));
      expect(r).toBe(true);
      expect(apiSpy.getOperationsByBranch).toHaveBeenCalledWith('b-recall', RECALL_GROUP_ID);
      expect(store.selectedServices()).toEqual([{ serviceId: 'op-recall', selectedOptionIds: [] }]);
    });

    it('is a no-op outside recall mode', async () => {
      store.selectBranch(BRANCH);
      const r = await TestBed.runInInjectionContext(() => recallServiceGuard(route({}), {} as never));
      expect(r).toBe(true);
      expect(apiSpy.getOperationsByBranch).not.toHaveBeenCalled();
    });

    it('does not re-apply when a service is already selected', async () => {
      store.selectBranch(BRANCH);
      store.activateRecallMode('WVWZZZ1JZ3W386752');
      await TestBed.runInInjectionContext(() => recallServiceGuard(route({}), {} as never));
      apiSpy.getOperationsByBranch.mockClear();
      await TestBed.runInInjectionContext(() => recallServiceGuard(route({}), {} as never));
      expect(apiSpy.getOperationsByBranch).not.toHaveBeenCalled();
    });
  });

  describe('recallBlocksServicesGuard', () => {
    it('redirects to the brand step in recall mode (service step stays unreachable)', () => {
      const router = TestBed.inject(Router);
      const tree = { isUrlTree: true };
      (router.createUrlTree as jest.Mock).mockReturnValue(tree);
      store.activateRecallMode('WVWZZZ1JZ3W386752');

      const r = TestBed.runInInjectionContext(() => recallBlocksServicesGuard(route({}), {} as never));

      expect(router.createUrlTree).toHaveBeenCalledWith(['/home/brand']);
      expect(r).toBe(tree);
    });

    it('allows the service step in the normal flow', () => {
      const r = TestBed.runInInjectionContext(() => recallBlocksServicesGuard(route({}), {} as never));
      expect(r).toBe(true);
    });
  });
});
