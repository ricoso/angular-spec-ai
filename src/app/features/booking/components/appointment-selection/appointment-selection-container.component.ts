import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';
import { convertToBerlinTime } from '@shared/pipes/berlin-time.pipe';
import { EMPTY, switchMap, timer } from 'rxjs';

import type { AppointmentSlot, DayAbbreviation } from '../../models/appointment.model';
import { BookingStore } from '../../stores/booking.store';

import { AppointmentCardComponent } from './appointment-card.component';
import { SNAP_CONFLICT_BANNER_TIMEOUT_MS, SnapConflictBannerComponent } from './snap-conflict-banner/snap-conflict-banner.component';

const DAY_FULL_NAMES = new Map<DayAbbreviation, string>([
  ['Mo', 'Montag'],
  ['Di', 'Dienstag'],
  ['Mi', 'Mittwoch'],
  ['Do', 'Donnerstag'],
  ['Fr', 'Freitag'],
  ['Sa', 'Samstag']
]);

const MONTH_NAMES_DE = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];

@Component({
  selector: 'app-appointment-selection-container',
  standalone: true,
  imports: [TranslatePipe, WizardBreadcrumbComponent, AppointmentCardComponent, SnapConflictBannerComponent],
  templateUrl: './appointment-selection-container.component.html',
  styleUrl: './appointment-selection-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppointmentSelectionContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly booking = i18nKeys.booking;
  protected readonly wizardSteps = WIZARD_STEPS;
  protected readonly activeStepIndex = 4;
  protected readonly appointments = this.store.appointments;
  protected readonly selectedAppointment = this.store.selectedAppointment;
  protected readonly hasAppointmentSelected = this.store.hasAppointmentSelected;
  protected readonly isLoading = this.store.isLoading;
  protected readonly error = this.store.error;
  /** REQ-031: steuert das Snap-Conflict-Banner. */
  protected readonly hasSnapConflict = this.store.hasSnapConflict;

  constructor() {
    // REQ-031 BR-3: banner auto-dismiss after 30s; timer restarts on every new conflict
    toObservable(this.hasSnapConflict).pipe(
      switchMap((active) => active ? timer(SNAP_CONFLICT_BANNER_TIMEOUT_MS) : EMPTY),
      takeUntilDestroyed()
    ).subscribe(() => { this.store.clearSnapConflict(); });

    // REQ-031 BR-3: flag must never survive the page — clear on container destroy
    this.destroyRef.onDestroy(() => {
      if (this.store.hasSnapConflict()) {
        this.store.clearSnapConflict();
      }
    });
  }

  /**
   * UI-only signal: which date is currently being viewed in the date-strip.
   * Falls back to selected appointment's date or first available date.
   * Does NOT touch the store.
   */
  private readonly viewedDate = signal<string | null>(null);

  /** Unique sorted list of date entries derived from appointments() */
  protected readonly availableDates = computed(() => {
    const seen = new Set<string>();
    const result: { date: string; dayAbbreviation: string; day: string; month: string }[] = [];
    for (const appt of this.appointments()) {
      if (seen.has(appt.date)) {
        continue;
      }
      seen.add(appt.date);
      const parts = appt.displayDate.split('.');
      const day = parts[0] ?? '';
      const monthIndex = Number(parts[1] ?? '0') - 1;
      const month = MONTH_NAMES_DE[monthIndex] ?? '';
      result.push({
        date: appt.date,
        dayAbbreviation: appt.dayAbbreviation,
        day,
        month
      });
    }
    return result;
  });

  /** The active date currently shown in the date-strip */
  protected readonly selectedDate = computed<string | null>(() => {
    const explicit = this.viewedDate();
    if (explicit) {
      return explicit;
    }
    const fromSelection = this.selectedAppointment()?.date;
    if (fromSelection) {
      return fromSelection;
    }
    return this.availableDates()[0]?.date ?? null;
  });

  /** Header label for the slots card (e.g. "Mittwoch, 25.02.2026") */
  protected readonly selectedDateLabel = computed<string>(() => {
    const date = this.selectedDate();
    if (!date) {
      return '';
    }
    const appt = this.appointments().find((a) => a.date === date);
    if (!appt) {
      return '';
    }
    return `${DAY_FULL_NAMES.get(appt.dayAbbreviation) ?? appt.dayAbbreviation}, ${appt.displayDate}`;
  });

  /** Month/year label shown in the strip header */
  protected readonly stripMonthLabel = computed<string>(() => {
    const dates = this.availableDates();
    if (dates.length === 0) {
      return '';
    }
    const first = dates[0];
    const appt = this.appointments().find((a) => a.date === first.date);
    if (!appt) {
      return first.month;
    }
    const year = appt.date.split('-')[0] ?? '';
    return `${first.month} ${year}`;
  });

  /** Slots for the currently viewed date, split into morning (< 12:00) and afternoon (>= 12:00) */
  protected readonly morningSlots = computed<AppointmentSlot[]>(() =>
    this.appointments().filter((a) => a.date === this.selectedDate() && this.parseHour(a.time) < 12)
  );

  protected readonly afternoonSlots = computed<AppointmentSlot[]>(() =>
    this.appointments().filter((a) => a.date === this.selectedDate() && this.parseHour(a.time) >= 12)
  );

  protected readonly selectedSummary = computed<string>(() => {
    const appt = this.selectedAppointment();
    if (!appt) {
      return '';
    }
    const time = convertToBerlinTime(appt.displayTime, appt.date);
    return `${DAY_FULL_NAMES.get(appt.dayAbbreviation) ?? appt.dayAbbreviation}, ${appt.displayDate} — ${time}`;
  });

  protected onDateSelect(date: string): void {
    this.viewedDate.set(date);
  }

  protected onAppointmentSelect(appointment: AppointmentSlot): void {
    this.store.selectAppointment(appointment);
  }

  protected onContinue(): void {
    this.store.clearSnapConflict();
    void this.router.navigate(['/home/carinformation']);
  }

  protected onSnapConflictBannerClosed(): void {
    this.store.clearSnapConflict();
  }

  protected onBack(): void {
    this.store.clearSelectedAppointment();
    void this.router.navigate(['/home/notes']);
  }

  protected onCalendarLinkClick(event: Event): void {
    event.preventDefault();
    void this.router.navigate(['/home/workshop-calendar']);
  }

  protected onRetry(): void {
    this.store.loadAppointments();
  }

  private parseHour(time: string): number {
    const [hh] = time.split(':');
    return Number(hh);
  }
}
