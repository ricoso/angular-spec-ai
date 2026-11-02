import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '@environments/environment';

import { FALLBACK_ADDRESS } from '../models/branch-config.model';
import { AVAILABLE_SERVICES } from '../models/service.model';

import { BookingApiService } from './booking-api.service';

const mockBranchesResponse = {
  data: [
    { id: 'branch-1', name: 'Filiale Mitte', description: 'Mitte', brands: ['BMW', 'Audi'] },
    { id: 'branch-2', name: 'Filiale Nord', description: 'Nord', brands: ['BMW'] },
  ],
};

describe('BookingApiService', () => {
  let service: BookingApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookingApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getBrands', () => {
    it('should return unique brands from all branches', async () => {
      const promise = service.getBrands();
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush(mockBranchesResponse);

      const result = await promise;
      const names = result.map(b => b.name);
      expect(names).toContain('BMW');
      expect(names).toContain('Audi');
    });

    it('should deduplicate brands across branches', async () => {
      const promise = service.getBrands();
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush(mockBranchesResponse);

      const result = await promise;
      // BMW appears in both branches but should only be listed once
      const bmwCount = result.filter(b => b.name === 'BMW').length;
      expect(bmwCount).toBe(1);
    });

    it('should map brand names to lowercase ids', async () => {
      const promise = service.getBrands();
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush(mockBranchesResponse);

      const result = await promise;
      const bmw = result.find(b => b.name === 'BMW');
      expect(bmw?.id).toBe('bmw');
    });
  });

  describe('getLocations', () => {
    it('should return locations filtered by brand', async () => {
      const promise = service.getLocations('bmw');
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush(mockBranchesResponse);

      const result = await promise;
      // Both branches have BMW
      expect(result).toHaveLength(2);
    });

    it('should filter to only branches with matching brand', async () => {
      const promise = service.getLocations('audi');
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush(mockBranchesResponse);

      const result = await promise;
      // Only branch-1 has Audi
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('branch-1');
    });

    it('should return locations with id, name, and city', async () => {
      const promise = service.getLocations('bmw');
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush(mockBranchesResponse);

      const result = await promise;
      for (const location of result) {
        expect(location.id).toBeDefined();
        expect(location.name).toBeDefined();
        expect(location.city).toBeDefined();
      }
    });
  });

  describe('getServices', () => {
    it('should return available services', async () => {
      const result = await service.getServices();
      expect(result).toEqual(AVAILABLE_SERVICES);
    });

    it('should return 7 services', async () => {
      const result = await service.getServices();
      expect(result).toHaveLength(7);
    });

    it('should include tire-change with options', async () => {
      const result = await service.getServices();
      const tireChange = result.find(s => s.id === 'tire-change');
      expect(tireChange?.options).toHaveLength(4);
    });
  });

  describe('getBranchById', () => {
    it('should return branch config for valid id', async () => {
      const promise = service.getBranchById('branch-1');
      const req = httpMock.expectOne(`${environment.apiUrl}/branches/branch-1`);
      req.flush({ data: { id: 'branch-1', name: 'Test', description: 'TestAddr', brands: ['BMW'] } });

      const result = await promise;
      expect(result).not.toBeNull();
      expect(result!.branchId).toBe('branch-1');
      expect(result!.name).toBe('Test');
      expect(result!.brands).toEqual(['BMW']);
    });

    it('should return null when branch not found', async () => {
      const promise = service.getBranchById('invalid');
      const req = httpMock.expectOne(`${environment.apiUrl}/branches/invalid`);
      req.flush('Not Found', { status: 404, statusText: 'Not Found' });

      const result = await promise;
      expect(result).toBeNull();
    });

    it('should use fallback address when description is empty', async () => {
      const promise = service.getBranchById('branch-1');
      const req = httpMock.expectOne(`${environment.apiUrl}/branches/branch-1`);
      req.flush({ data: { id: 'branch-1', name: 'Test', description: '', brands: [] } });

      const result = await promise;
      expect(result!.address.street).toBe(FALLBACK_ADDRESS);
    });
  });

  describe('getBrandsByBranch', () => {
    it('should map brand names to BrandDisplay', async () => {
      const result = await service.getBrandsByBranch(['BMW', 'Audi']);
      expect(result).toHaveLength(2);
      expect(result[0].name).toBe('BMW');
      expect(result[0].id).toBe('bmw');
      expect(result[1].name).toBe('Audi');
      expect(result[1].id).toBe('audi');
    });

    it('should return empty array for empty input', async () => {
      const result = await service.getBrandsByBranch([]);
      expect(result).toHaveLength(0);
    });
  });

  describe('getBranches', () => {
    it('should return branch configs from API', async () => {
      const promise = service.getBranches();
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush(mockBranchesResponse);

      const result = await promise;
      expect(result).toHaveLength(2);
      expect(result[0].branchId).toBe('branch-1');
      expect(result[0].name).toBe('Filiale Mitte');
      expect(result[0].brands).toEqual(['BMW', 'Audi']);
    });
  });

  describe('getBrandLogoPath', () => {
    it('should return correct path for known brand', () => {
      expect(service.getBrandLogoPath('BMW')).toBe('assets/brands/bmw.svg');
    });

    it('should return default.svg path for unknown brand', () => {
      expect(service.getBrandLogoPath('Unknown')).toBe('assets/brands/default.svg');
    });
  });

  describe('fallback address', () => {
    it('should use fallback when description is null', async () => {
      const promise = service.getLocations();
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush({ data: [{ id: 'b1', name: 'Test', description: null, brands: [] }] });

      const result = await promise;
      expect(result[0].formattedAddress).toBe(FALLBACK_ADDRESS);
    });

    it('should use fallback when description is empty string', async () => {
      const promise = service.getLocations();
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush({ data: [{ id: 'b1', name: 'Test', description: '', brands: [] }] });

      const result = await promise;
      expect(result[0].formattedAddress).toBe(FALLBACK_ADDRESS);
    });

    it('should use description when present', async () => {
      const promise = service.getLocations();
      const req = httpMock.expectOne(`${environment.apiUrl}/branches`);
      req.flush({ data: [{ id: 'b1', name: 'Test', description: 'Real Address', brands: [] }] });

      const result = await promise;
      expect(result[0].formattedAddress).toBe('Real Address');
    });
  });

  describe('getCategoriesByBranch / getOperationsByBranch — Deep-Link groupId', () => {
    it('should NOT add groupId param when not provided', async () => {
      const promise = service.getCategoriesByBranch('branch-1');
      const req = httpMock.expectOne(`${environment.apiUrl}/branches/branch-1/categories`);
      expect(req.request.params.has('groupId')).toBe(false);
      req.flush({ data: [] });
      await promise;
    });

    it('should add ?groupId=… when provided (categories)', async () => {
      const promise = service.getCategoriesByBranch('branch-1', 'tireChange-grp-1');
      const req = httpMock.expectOne(
        r => r.url === `${environment.apiUrl}/branches/branch-1/categories` && r.params.get('groupId') === 'tireChange-grp-1'
      );
      req.flush({ data: [] });
      await promise;
    });

    it('should add ?groupId=… when provided (operations)', async () => {
      const promise = service.getOperationsByBranch('branch-1', 'tireChange-grp-1');
      const req = httpMock.expectOne(
        r => r.url === `${environment.apiUrl}/branches/branch-1/operations` && r.params.get('groupId') === 'tireChange-grp-1'
      );
      req.flush({ data: [] });
      await promise;
    });

    it('should NOT add groupId param when null', async () => {
      const promise = service.getOperationsByBranch('branch-1', null);
      const req = httpMock.expectOne(`${environment.apiUrl}/branches/branch-1/operations`);
      expect(req.request.params.has('groupId')).toBe(false);
      req.flush({ data: [] });
      await promise;
    });
  });
});
