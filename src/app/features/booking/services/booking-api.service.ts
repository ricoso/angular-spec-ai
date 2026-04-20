import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { environment } from '@environments/environment';
import { firstValueFrom } from 'rxjs';

import type { BranchConfig } from '../models/branch-config.model';
import { FALLBACK_ADDRESS, getBrandLogoPath } from '../models/branch-config.model';
import type { Brand, BrandDisplay } from '../models/brand.model';
import type { LocationDisplay } from '../models/location.model';
import type { ServiceDisplay } from '../models/service.model';
import { AVAILABLE_SERVICES } from '../models/service.model';

interface ApiResponse<T> {
  data: T;
}

interface BranchDto {
  id: string;
  name: string;
  description: string | null;
  brands: string[];
}

@Injectable({ providedIn: 'root' })
export class BookingApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  /** Load all unique brands from all branches */
  public async getBrands(): Promise<BrandDisplay[]> {
    const response = await firstValueFrom(
      this.http.get<ApiResponse<BranchDto[]>>(`${this.apiUrl}/branches`)
    );

    const brandNames = new Set<string>();
    for (const branch of response.data) {
      for (const name of branch.brands) {
        brandNames.add(name);
      }
    }

    return Array.from(brandNames).map(name => ({
      id: name.toLowerCase().replace(/\s+/g, '-') as Brand,
      name,
    }));
  }

  /** Load brands available at a specific branch (from API data) */
  public getBrandsByBranch(brandNames: string[]): Promise<BrandDisplay[]> {
    console.debug('[BookingApiService] getBrandsByBranch() called for brands:', brandNames);
    const brands: BrandDisplay[] = brandNames.map(name => ({
      id: name.toLowerCase().replace(/\s+/g, '-') as Brand,
      name
    }));
    return Promise.resolve(brands);
  }

  /** Load locations from API, optionally filtered by brand */
  public async getLocations(brand?: Brand): Promise<LocationDisplay[]> {
    const response = await firstValueFrom(
      this.http.get<ApiResponse<BranchDto[]>>(`${this.apiUrl}/branches`)
    );

    const branches = brand
      ? response.data.filter(b => b.brands.some(name => name.toLowerCase().replace(/\s+/g, '-') === brand))
      : response.data;

    return branches.map(b => this.mapBranchToLocation(b));
  }

  /** Fetch a single branch by ID (for deep-link validation) */
  public async getBranchById(branchId: string): Promise<BranchConfig | null> {
    try {
      const response = await firstValueFrom(
        this.http.get<ApiResponse<BranchDto>>(`${this.apiUrl}/branches/${branchId}`)
      );
      return this.mapBranchToBranchConfig(response.data);
    } catch {
      return null;
    }
  }

  /** Load branches from API as BranchConfig[] */
  async getBranches(): Promise<BranchConfig[]> {
    console.debug('[BookingApiService] getBranches() called');
    const response = await firstValueFrom(
      this.http.get<ApiResponse<BranchDto[]>>(`${this.apiUrl}/branches`)
    );
    return response.data.map(b => this.mapBranchToBranchConfig(b));
  }

  public async getServices(): Promise<ServiceDisplay[]> {
    console.debug('[BookingApiService] getServices() called');
    return Promise.resolve(AVAILABLE_SERVICES);
  }

  /** Get brand logo path with default.svg fallback */
  public getBrandLogoPath(brandName: string): string {
    return getBrandLogoPath(brandName);
  }

  private mapBranchToLocation(dto: BranchDto): LocationDisplay {
    const trimmed = dto.description?.trim();
    const address = trimmed && trimmed.length > 0 ? trimmed : FALLBACK_ADDRESS;
    return {
      id: dto.id,
      name: dto.name,
      city: address,
      formattedAddress: address,
    };
  }

  private mapBranchToBranchConfig(dto: BranchDto): BranchConfig {
    const trimmed = dto.description?.trim();
    const address = trimmed && trimmed.length > 0 ? trimmed : FALLBACK_ADDRESS;
    return {
      branchId: dto.id,
      name: dto.name,
      address: {
        street: address,
        zip: '',
        city: '',
      },
      brands: dto.brands,
    };
  }
}
