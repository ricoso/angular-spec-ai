import type { PipeTransform } from '@angular/core';
import { Pipe } from '@angular/core';

/**
 * Wandelt eine UTC-Stunden:Minuten-Zeit zusammen mit dem ISO-Datum optisch in
 * Berliner Zeit um (Europe/Berlin, DST-aware via Intl.DateTimeFormat).
 *
 * Rein optisch — Original-Datenobjekte bleiben unverändert; die Buchung wird
 * weiterhin mit dem ursprünglichen UTC-Wert ans Backend gesendet.
 *
 * @param utcTime  z. B. "08:00" oder "08:00 Uhr"
 * @param isoDate  z. B. "2026-05-15" (für DST-Berücksichtigung)
 */
export function convertToBerlinTime(
  utcTime: string | null | undefined,
  isoDate: string | null | undefined,
): string {
  if (!utcTime || !isoDate) { return utcTime ?? ''; }
  const m = /(\d{2}):(\d{2})/.exec(utcTime);
  if (!m) { return utcTime; }
  const date = new Date(`${isoDate}T${m[1]}:${m[2]}:00Z`);
  if (isNaN(date.getTime())) { return utcTime; }
  const hh = new Intl.DateTimeFormat('de-DE', {
    timeZone: 'Europe/Berlin',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
  return utcTime.includes('Uhr') ? `${hh} Uhr` : hh;
}

@Pipe({ name: 'berlinTime', standalone: true, pure: true })
export class BerlinTimePipe implements PipeTransform {
  public transform(utcTime: string | null | undefined, isoDate: string | null | undefined): string {
    return convertToBerlinTime(utcTime, isoDate);
  }
}
