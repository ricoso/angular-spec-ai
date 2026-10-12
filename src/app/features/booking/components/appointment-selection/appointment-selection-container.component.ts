import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe, TranslateService } from '@core/i18n';
import { WizardBreadcrumbComponent } from '@shared/components/wizard-breadcrumb/wizard-breadcrumb.component';
import { WIZARD_STEPS } from '@shared/components/wizard-breadcrumb/wizard-steps.config';
import { convertToBerlinTime } from '@shared/pipes/berlin-time.pipe';
import { LOCALE_BY_LANGUAGE, toLocalDate, withoutDot } from '@shared/utils/date-locale.util';
import { EMPTY, switchMap, timer } from 'rxjs';

import type { AppointmentSlot, DateTile, SlotSection } from '../../models/appointment.model';
import { BookingStore } from '../../stores/booking.store';

import { AppointmentCardComponent } from './appointment-card.component';
import { RentalCarEmptyStateComponent } from './rental-car-empty-state/rental-car-empty-state.component';
import { SNAP_CONFLICT_BANNER_TIMEOUT_MS, SnapConflictBannerComponent } from './snap-conflict-banner/snap-conflict-banner.component';

@Component({
  selector: 'app-appointment-selection-container',
  standalone: true,
  imports: [
    TranslatePipe,
    WizardBreadcrumbComponent,
    AppointmentCardComponent,
    RentalCarEmptyStateComponent,
    SnapConflictBannerComponent
  ],
  templateUrl: './appointment-selection-container.component.html',
  styleUrl: './appointment-selection-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppointmentSelectionContainerComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly translateService = inject(TranslateService);

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
  /** REQ-039: with/without rental car and the empty state when no slot has a car. */
  protected readonly rentalCarRequested = this.store.rentalCarRequested;
  protected readonly hasNoRentalCarAppointments = this.store.hasNoRentalCarAppointments;

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

  private readonly locale = computed(() => LOCALE_BY_LANGUAGE[this.translateService.getCurrentLanguage()]);

  /** Unique list of date tiles derived from appointments() (mockup: weekday / day / month). */
  protected readonly availableDates = computed<DateTile[]>(() => {
    const locale = this.locale();
    const weekdayShort = new Intl.DateTimeFormat(locale, { weekday: 'short' });
    const monthShort = new Intl.DateTimeFormat(locale, { month: 'short' });
    const fullDate = new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    const seen = new Set<string>();
    const result: DateTile[] = [];
    for (const appt of this.appointments()) {
      if (seen.has(appt.date)) {
        continue;
      }
      seen.add(appt.date);
      const date = toLocalDate(appt.date);
      result.push({
        date: appt.date,
        weekday: withoutDot(weekdayShort.format(date)).toUpperCase(),
        day: String(date.getDate()).padStart(2, '0'),
        month: withoutDot(monthShort.format(date)),
        ariaLabel: fullDate.format(date)
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
    return this.availableDates().at(0)?.date ?? null;
  });

  /** Month/year heading of the calendar card (e.g. "September 2026"). */
  protected readonly stripMonthLabel = computed<string>(() => {
    const first = this.availableDates().at(0);
    if (!first) {
      return '';
    }
    return new Intl.DateTimeFormat(this.locale(), { month: 'long', year: 'numeric' }).format(toLocalDate(first.date));
  });

  /**
   * Slots for the currently viewed date, split into morning (< 12:00) and afternoon (>= 12:00) by the
   * Berlin time the slot shows — `time` is UTC, so 10:00 UTC (12:00 CEST) belongs to the afternoon.
   */
  protected readonly morningSlots = computed<AppointmentSlot[]>(() =>
    this.appointments().filter((a) => a.date === this.selectedDate() && this.berlinHour(a) < 12)
  );

  protected readonly afternoonSlots = computed<AppointmentSlot[]>(() =>
    this.appointments().filter((a) => a.date === this.selectedDate() && this.berlinHour(a) >= 12)
  );

  /** Non-empty morning/afternoon groups of the viewed date (mockup 04-termin). */
  protected readonly slotSections = computed<SlotSection[]>(() =>
    ([
      { key: 'morning', label: this.booking.appointment.morning, slots: this.morningSlots() },
      { key: 'afternoon', label: this.booking.appointment.afternoon, slots: this.afternoonSlots() }
    ] satisfies SlotSection[]).filter((section) => section.slots.length > 0)
  );

  /** Text of the green confirmation (e.g. "Dienstag, 01.09.2026 — 10:00 Uhr"). */
  protected readonly selectedSummary = computed<string>(() => {
    const appt = this.selectedAppointment();
    if (!appt) {
      return '';
    }
    const weekday = new Intl.DateTimeFormat(this.locale(), { weekday: 'long' }).format(toLocalDate(appt.date));
    const time = convertToBerlinTime(appt.displayTime, appt.date);
    return `${weekday}, ${appt.displayDate} — ${time}`;
  });

  /** REQ-039 AC-M10: the chosen slot comes with a reserved rental car. */
  protected readonly selectedIncludesRentalCar = computed(() => this.selectedAppointment()?.rentalCarIncluded === true);

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

  /** REQ-039 E-1: conscious switch to "without rental car" — reload all slots. */
  protected onContinueWithoutRentalCar(): void {
    this.store.continueWithoutRentalCar();
    this.store.loadAppointments();
  }

  /** REQ-039 E-1: later dates via the existing workshop calendar (keeps the rental filter). */
  protected onShowLaterAppointments(): void {
    void this.router.navigate(['/home/workshop-calendar']);
  }

  /** Hour of the slot in Berlin time — the time the slot shows (berlinTime pipe). */
  private berlinHour(slot: AppointmentSlot): number {
    return this.parseHour(convertToBerlinTime(slot.time, slot.date));
  }

  private parseHour(time: string): number {
    const [hh] = time.split(':');
    return Number(hh);
  }
}
