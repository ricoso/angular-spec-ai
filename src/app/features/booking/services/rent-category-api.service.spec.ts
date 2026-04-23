import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '@environments/environment';

import { RentCategoryApiService } from './rent-category-api.service';

describe('RentCategoryApiService', () => {
  let service: RentCategoryApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        RentCategoryApiService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(RentCategoryApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch and map rent categories from API', async () => {
    const promise = service.getRentCategories();

    const req = http.expectOne(`${environment.apiUrl}/rentcategories`);
    expect(req.request.method).toBe('GET');

    req.flush({
      data: [
        {
          id: 'rc-1',
          groupId: 'grp-1',
          name: 'Kleinwagen',
          description: 'Kompakte Autos',
          brands: ['VW'],
          branches: ['br-1']
        }
      ]
    });

    const result = await promise;
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('rc-1');
    expect(result[0].name).toBe('Kleinwagen');
    expect(result[0].brands).toEqual(['VW']);
    expect(result[0].branches).toEqual(['br-1']);
  });

  it('should return empty list when API returns empty data', async () => {
    const promise = service.getRentCategories();

    const req = http.expectOne(`${environment.apiUrl}/rentcategories`);
    req.flush({ data: [] });

    const result = await promise;
    expect(result).toEqual([]);
  });
});
