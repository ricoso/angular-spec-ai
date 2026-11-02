import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

import { RentCategoryApiService } from '../services/rent-category-api.service';
import { BookingStore } from '../stores/booking.store';

import { rentCategoriesResolver } from './rent-categories.resolver';

describe('rentCategoriesResolver', () => {
  let store: InstanceType<typeof BookingStore>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: RentCategoryApiService, useValue: { getRentCategories: jest.fn().mockResolvedValue([]) } }
      ]
    });
    store = TestBed.inject(BookingStore);
  });

  it('should call store.loadRentCategories', () => {
    const loadSpy = jest.spyOn(store, 'loadRentCategories');
    TestBed.runInInjectionContext(() => {
      rentCategoriesResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);
    });
    expect(loadSpy).toHaveBeenCalled();
  });

  it('should return void', () => {
    const result = TestBed.runInInjectionContext(() =>
      rentCategoriesResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );
    expect(result).toBeUndefined();
  });
});
