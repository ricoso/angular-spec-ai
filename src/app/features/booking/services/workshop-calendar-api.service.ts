import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { environment } from '@environments/environment';
import { firstValueFrom } from 'rxjs';

import type { DayAbbreviation } from '../models/appointment.model';
import type { WorkshopCalendarDay, WorkshopTimeSlot } from '../models/workshop-calendar.model';

interface ApiResponse<T> {
  data: T;
}

interface SnapSlotDto {
  id: string;
  time: string;
  displayTime: string;
  available: boolean;
}

interface SnapDayDto {
  date: string;
  displayDate: string;
  dayAbbreviation: string;
  slots: SnapSlotDto[];
}

@Injectable({ providedIn: 'root' })
export class WorkshopCalendarApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  public async getWorkshopCalendarDays(fromDate: string, branchId: string, serviceIds: string[], brandName?: string): Promise<WorkshopCalendarDay[]> {
    const toDate = new Date(`${fromDate}T00:00:00`);
    toDate.setDate(toDate.getDate() + 7);
    const toDateStr = this.formatIsoDate(toDate);

    const response = await firstValueFrom(
      this.http.post<ApiResponse<SnapDayDto[]>>(`${this.apiUrl}/snaps`, {
        branchId,
        jobTypeIds: serviceIds,
        fromDate,
        toDate: toDateStr,
        mode: 'date-range',
        brandName: brandName ?? '',
      })
    );

    return response.data.slice(0, 3).map(day => this.mapToCalendarDay(day));
  }

  private mapToCalendarDay(day: SnapDayDto): WorkshopCalendarDay {
    const slots: WorkshopTimeSlot[] = day.slots
      .filter(s => s.available)
      .map(s => ({
        id: s.id,
        time: s.time,
        displayTime: s.displayTime,
      }));

    return {
      date: day.date,
      displayDate: day.displayDate,
      dayAbbreviation: day.dayAbbreviation as DayAbbreviation,
      displayHeading: `${day.dayAbbreviation}, ${day.displayDate}`,
      slots,
    };
  }

  private formatIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
