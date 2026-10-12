import type { Language } from '@core/i18n';

/** REQ-039: BCP 47 locale per UI language for `Intl.DateTimeFormat` (date tiles, headings). */
export const LOCALE_BY_LANGUAGE: Record<Language, string> = {
  de: 'de-DE',
  en: 'en-GB',
  uk: 'uk-UA',
  fr: 'fr-FR',
  ar: 'ar'
};

/** Short weekday/month as in the mockup tiles: "DI" / "Sep" — no trailing abbreviation dot. */
export function withoutDot(value: string): string {
  return value.replace(/\.$/, '');
}

/** Local calendar date from an ISO "YYYY-MM-DD" string (no UTC shift). */
export function toLocalDate(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(year, month - 1, day);
}
