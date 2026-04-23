import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { environment } from '@environments/environment';
import { firstValueFrom } from 'rxjs';

import type { RentCategory } from '../models/rent-category.model';

interface ApiResponse<T> {
  data: T;
}

interface RentCategoryDto {
  id: string;
  groupId: string;
  name: string;
  description: string;
  brands: string[];
  branches: string[];
}

@Injectable({ providedIn: 'root' })
export class RentCategoryApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  public async getRentCategories(branchId: string): Promise<RentCategory[]> {
    const response = await firstValueFrom(
      this.http.get<ApiResponse<RentCategoryDto[]>>(`${this.apiUrl}/branches/${branchId}/rentcategories`)
    );
    return response.data.map(dto => ({
      id: dto.id,
      groupId: dto.groupId,
      name: dto.name,
      description: dto.description,
      brands: dto.brands,
      branches: dto.branches
    }));
  }
}
