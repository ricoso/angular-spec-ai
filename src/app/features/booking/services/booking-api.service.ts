import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { environment } from '@environments/environment';
import { firstValueFrom } from 'rxjs';

import type { BranchConfig } from '../models/branch-config.model';
import { FALLBACK_ADDRESS, getBrandLogoPath } from '../models/branch-config.model';
import type { Brand, BrandDisplay } from '../models/brand.model';
import type { LocationDisplay } from '../models/location.model';
import type { ApiJobType, ApiJobTypeOption, ServiceDisplay } from '../models/service.model';
import { AVAILABLE_SERVICES } from '../models/service.model';

interface JobTypeDto {
  id: string;
  name: string;
  description: string;
  hint: string | null;
  category: string | null;
  categoryName: string | null;
  iconUrl: string | null;
  operations: ApiJobTypeOption[];
}

interface ApiResponse<T> {
  data: T;
}

interface BranchAddressDto {
  street: string | null;
  zip: string | null;
  city: string | null;
  country: string | null;
}

interface BranchDto {
  id: string;
  name: string;
  description: string | null;
  address: BranchAddressDto | null;
  brands: string[];
  /** SoftNet-Group-ID der tireChange-Variante (Boxenstopp), null wenn keine konfiguriert. */
  tireChangeGroupId?: string | null;
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

  /** REQ-025: Lädt JobTypes der gewählten Branch über das Backend (mit hint/category/iconUrl). */
  public async getJobTypesByBranch(branchId: string): Promise<ApiJobType[]> {
    const response = await firstValueFrom(
      this.http.get<ApiResponse<JobTypeDto[]>>(`${this.apiUrl}/branches/${branchId}/jobtypes`)
    );
    return response.data.map(dto => ({
      id: dto.id,
      name: dto.name,
      description: dto.description,
      hint: dto.hint,
      category: dto.category,
      categoryName: dto.categoryName,
      iconUrl: dto.iconUrl,
      operations: dto.operations,
    }));
  }

  /** REQ-025 (b) Iter. 3: Service-Kategorien (Tags aus SoftNet) — sortiert nach rank.
   *  Optional `groupId` (Deep-Link, z. B. tireChange-Group) wird ans Backend durchgereicht. */
  public async getCategoriesByBranch(
    branchId: string,
    groupId?: string | null
  ): Promise<import('../models/service.model').ApiCategory[]> {
    const params = groupId ? new HttpParams().set('groupId', groupId) : undefined;
    const response = await firstValueFrom(
      this.http.get<ApiResponse<import('../models/service.model').ApiCategory[]>>(
        `${this.apiUrl}/branches/${branchId}/categories`,
        { params }
      )
    );
    return response.data;
  }

  /** REQ-025 (b) Iter. 3: Operations (Services) inkl. description, tagIds, order, imageId.
   *  Optional `groupId` wird ans Backend durchgereicht (Boxenstopp-Modus). */
  public async getOperationsByBranch(
    branchId: string,
    groupId?: string | null
  ): Promise<import('../models/service.model').ApiOperation[]> {
    const params = groupId ? new HttpParams().set('groupId', groupId) : undefined;
    const response = await firstValueFrom(
      this.http.get<ApiResponse<import('../models/service.model').ApiOperation[]>>(
        `${this.apiUrl}/branches/${branchId}/operations`,
        { params }
      )
    );
    return response.data;
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
      city: dto.address?.city ?? address,
      formattedAddress: address,
    };
  }

  private mapBranchToBranchConfig(dto: BranchDto): BranchConfig {
    // REQ-025 (i): Strukturierte Adresse direkt aus Backend-DTO durchreichen.
    const trimmed = dto.description?.trim();
    const fallback = trimmed && trimmed.length > 0 ? trimmed : FALLBACK_ADDRESS;
    return {
      branchId: dto.id,
      name: dto.name,
      address: {
        street: dto.address?.street ?? fallback,
        zip: dto.address?.zip ?? '',
        city: dto.address?.city ?? '',
        country: dto.address?.country ?? undefined,
      },
      brands: dto.brands,
      tireChangeGroupId: dto.tireChangeGroupId ?? null,
    };
  }
}
