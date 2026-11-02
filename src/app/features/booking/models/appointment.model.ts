import type { TranslationKey } from '@core/i18n';

/**
 * Day abbreviation type for German weekdays (Monday–Saturday, no Sunday)
 * DE: Wochentag-Abkürzung / EN: Day abbreviation
 */
export type DayAbbreviation = 'Mo' | 'Di' | 'Mi' | 'Do' | 'Fr' | 'Sa';

/**
 * Single appointment slot proposal
 * DE: Terminvorschlag / EN: Appointment slot
 */
export interface AppointmentSlot {
  id: string;
  date: string;
  displayDate: string;
  dayAbbreviation: DayAbbreviation;
  time: string;
  displayTime: string;
  /** REQ-039 AC-M10: SoftNet reserved a rental car for this slot. */
  rentalCarIncluded?: boolean;
}

/**
 * Date tile of the appointment date strip (mockup 04-termin), labels localized via Intl.
 * DE: Datumskachel / EN: Date tile
 */
export interface DateTile {
  /** ISO date "YYYY-MM-DD" */
  date: string;
  weekday: string;
  day: string;
  month: string;
  /** Full localized date for screen readers */
  ariaLabel: string;
}

/**
 * Morning or afternoon slot group of the viewed date (mockup 04-termin, "Vormittag"/"Nachmittag").
 * DE: Slot-Gruppe / EN: Slot section
 */
export interface SlotSection {
  key: 'morning' | 'afternoon';
  label: TranslationKey;
  slots: AppointmentSlot[];
}

/**
 * i18n keys of the empty date strip (no appointment found). The recall campaign has its own wording.
 * DE: Texte des Leerzustands / EN: Empty-state texts
 */
export interface NoAppointmentsKeys {
  title: TranslationKey;
  hint: TranslationKey;
}
