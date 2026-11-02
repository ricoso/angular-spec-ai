import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { environment } from '@environments/environment';
import { firstValueFrom } from 'rxjs';

import type { AppointmentSlot, DayAbbreviation } from '../models/appointment.model';

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

export interface AppointmentCustomer {
  email: string;
  firstName: string;
  lastName: string;
  mobile: string;
  phone?: string;
  address: string;
  zipCode: string;
  city: string;
  country?: string;
  language?: string;
  salutation?: string;
}

export interface AppointmentVehicle {
  licensePlate: string;
  mileage: number;
  vin?: string;
  model?: string;
  firstRegMonth?: string;
  firstRegYear?: string;
}

export interface CreateAppointmentRequest {
  snapId: string;
  branchId: string;
  variant?: 'default' | 'tireChange' | 'recall';
  customer: AppointmentCustomer;
  vehicle: AppointmentVehicle;
  note?: string;
  rentCategoryId?: string;
  callbackRequested?: boolean;
  /** REQ-027: SoftNet `notifications` ({ SMS, SMTP }). */
  notifications?: { SMS: boolean; SMTP: boolean };
  /** REQ-043: wheel/tire change booking — backend appends "Buchung erfolgte über OTV" to the note. */
  tireChangeBooking?: boolean;
}

export interface AppointmentResponse {
  id: string;
  status: string;
  date: string;
  time: string;
  branchName: string;
  services: string[];
  customerName: string;
  licensePlate: string;
}

@Injectable({ providedIn: 'root' })
export class AppointmentApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  public async getAppointments(
    branchId: string,
    serviceIds: string[],
    brandName?: string,
    variant?: 'default' | 'tireChange' | 'recall',
    timePreference?: 'AM' | 'PM',
    notifications?: { SMS: boolean; SMTP: boolean },
    rentalCarRequested = false,
  ): Promise<AppointmentSlot[]> {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const fromDate = this.formatIsoDate(tomorrow);

    // 28-Tage-Fenster — damit der strikte AM/PM-Filter genug Kandidaten hat,
    // auch wenn viele Tage nicht passen. Frontend zeigt weiterhin .slice(0, 4).
    const toDate = new Date(tomorrow);
    toDate.setDate(toDate.getDate() + 28);

    const response = await firstValueFrom(
      this.http.post<ApiResponse<SnapDayDto[]>>(`${this.apiUrl}/snaps`, {
        branchId,
        jobTypeIds: serviceIds,
        fromDate,
        toDate: this.formatIsoDate(toDate),
        mode: 'next-available',
        brandName: brandName ?? '',
        variant: variant ?? 'default',
        timePreference,
        notifications,
        // REQ-039 BR-M2/BR-M3: only sent with a rental car — without it the request stays as before.
        replacementCar: rentalCarRequested ? true : undefined,
      })
    );

    // Soft AM/PM preference: nimm Slot der passt, sonst fallback auf frühesten
    // verfügbaren. User sieht immer Termine, auch wenn Filter nicht greift.
    const isMorning = (time: string): boolean => parseInt(time.split(':')[0] ?? '0', 10) < 12;
    const pickSlot = (slots: SnapDayDto['slots']): SnapDayDto['slots'][number] | undefined => {
      const available = slots.filter(s => s.available);
      if (available.length === 0) return undefined;
      if (timePreference === 'AM') return available.find(s => isMorning(s.time)) ?? available[0];
      if (timePreference === 'PM') return available.find(s => !isMorning(s.time)) ?? available[0];
      return available[0];
    };

    const candidates = response.data
      .map(day => ({ day, slot: pickSlot(day.slots) }))
      .filter((entry): entry is { day: SnapDayDto; slot: NonNullable<ReturnType<typeof pickSlot>> } => entry.slot !== undefined);

    // Boxenstopp: Samstage sind fuer Radwechsel reserviert — im tireChange-Flow
    // die naechsten 3 Samstage zuerst anbieten, danach mit den fruehesten
    // Werktagen auf 4 Vorschlaege auffuellen. Default-Flow bleibt unveraendert.
    const selected = variant === 'tireChange'
      ? (() => {
          const saturdays = candidates.filter(entry => entry.day.dayAbbreviation === 'Sa').slice(0, 3);
          const weekdays = candidates.filter(entry => entry.day.dayAbbreviation !== 'Sa');
          return [...saturdays, ...weekdays].slice(0, 4);
        })()
      : candidates.slice(0, 4);

    return selected.map(({ day, slot }) => ({
      id: slot.id,
      date: day.date,
      displayDate: day.displayDate,
      dayAbbreviation: day.dayAbbreviation as DayAbbreviation,
      time: slot.time,
      displayTime: slot.displayTime,
      rentalCarIncluded: slot.rentalCarIncluded === true,
    }));
  }

  public async createAppointment(request: CreateAppointmentRequest): Promise<AppointmentResponse> {
    const response = await firstValueFrom(
      this.http.post<ApiResponse<AppointmentResponse>>(`${this.apiUrl}/appointments`, request)
    );
    return response.data;
  }

  public async getAppointment(appointmentId: string, branchId: string, variant?: 'default' | 'tireChange' | 'recall'): Promise<AppointmentResponse> {
    const params: Record<string, string> = { branchId };
    if (variant) {
      params.variant = variant;
    }
    const response = await firstValueFrom(
      this.http.get<ApiResponse<AppointmentResponse>>(`${this.apiUrl}/appointments/${appointmentId}`, { params })
    );
    return response.data;
  }

  private formatIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
