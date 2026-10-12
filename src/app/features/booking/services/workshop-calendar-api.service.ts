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
  /** REQ-039: set by the backend when SoftNet reserved a rental car for the slot. */
  rentalCarIncluded?: boolean;
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

  public async getWorkshopCalendarDays(fromDate: string, branchId: string, serviceIds: string[], brandName?: string, variant?: 'default' | 'tireChange', rentalCarRequested = false): Promise<WorkshopCalendarDay[]> {
    const toDate = new Date(`${fromDate}T00:00:00`);
    toDate.setDate(toDate.getDate() + 14);
    const toDateStr = this.formatIsoDate(toDate);

    const response = await firstValueFrom(
      this.http.post<ApiResponse<SnapDayDto[]>>(`${this.apiUrl}/snaps`, {
        branchId,
        jobTypeIds: serviceIds,
        fromDate,
        toDate: toDateStr,
        // 'next-available' liefert erfahrungsgemaess mehr Slots als 'date-range'
        // (SoftNet `asSoonAsPossible` scannt breiter).
        mode: 'next-available',
        brandName: brandName ?? '',
        // Ohne variant loest das Backend die Default-Group auf — im tireChange-Flow
        // lehnt SoftNet die Kombination aus Default-Group + Radwechsel-Operation ab (400).
        variant: variant ?? 'default',
        // KEIN timePreference → alle Slots (vor- und nachmittags)
        // REQ-039 A-2: same rental filter as the quick selection; free days come from filtered slots.
        replacementCar: rentalCarRequested ? true : undefined,
      })
    );

    // Genau 2 Tage zeigen: ausgewaehlter Tag + naechster Business-Tag (Mo-Fr).
    // Jeder Tag bekommt ALLE verfuegbaren Slots (kein AM/PM-Filter, keine Slot-Limitierung).
    const daysFromResponse = response.data
      .filter(day => day.date >= fromDate)
      .slice()
      .sort((a, b) => a.date.localeCompare(b.date));

    const firstDay = daysFromResponse[0];
    // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
    if (!firstDay) return [];

    const nextBusinessDay = daysFromResponse
      .slice(1)
      .find(day => this.isBusinessDay(day.date));

    const daysToShow = nextBusinessDay ? [firstDay, nextBusinessDay] : [firstDay];
    return daysToShow.map(day => this.mapToCalendarDay(day));
  }

  private isBusinessDay(dateStr: string): boolean {
    const d = new Date(`${dateStr}T00:00:00`);
    const dow = d.getDay(); // 0=So, 1=Mo, ..., 6=Sa
    return dow !== 0 && dow !== 6;
  }

  private mapToCalendarDay(day: SnapDayDto): WorkshopCalendarDay {
    const slots: WorkshopTimeSlot[] = day.slots
      .filter(s => s.available)
      .map(s => ({
        id: s.id,
        time: s.time,
        displayTime: s.displayTime,
        rentalCarIncluded: s.rentalCarIncluded === true,
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
