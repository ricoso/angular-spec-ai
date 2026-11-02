import { BerlinTimePipe, convertToBerlinTime } from './berlin-time.pipe';

describe('convertToBerlinTime', () => {
  it('returns an empty string when utcTime is null or undefined', () => {
    expect(convertToBerlinTime(null, '2026-05-15')).toBe('');
    expect(convertToBerlinTime(undefined, '2026-05-15')).toBe('');
  });

  it('returns the raw time when isoDate is missing', () => {
    expect(convertToBerlinTime('08:00', null)).toBe('08:00');
    expect(convertToBerlinTime('08:00', undefined)).toBe('08:00');
  });

  it('returns the raw value when no HH:MM pattern is found', () => {
    expect(convertToBerlinTime('no-time', '2026-05-15')).toBe('no-time');
  });

  it('returns the raw value when the constructed date is invalid', () => {
    // Matches the regex but is not a valid wall-clock time → Invalid Date → NaN.
    expect(convertToBerlinTime('25:99', '2026-05-15')).toBe('25:99');
  });

  it('converts UTC to Berlin summer time (CEST, +2)', () => {
    expect(convertToBerlinTime('08:00', '2026-05-15')).toBe('10:00');
  });

  it('converts UTC to Berlin winter time (CET, +1)', () => {
    expect(convertToBerlinTime('08:00', '2026-01-15')).toBe('09:00');
  });

  it('preserves the "Uhr" suffix when present', () => {
    expect(convertToBerlinTime('08:00 Uhr', '2026-05-15')).toBe('10:00 Uhr');
  });
});

describe('BerlinTimePipe', () => {
  const pipe = new BerlinTimePipe();

  it('delegates to convertToBerlinTime', () => {
    expect(pipe.transform('08:00', '2026-05-15')).toBe('10:00');
    expect(pipe.transform(null, null)).toBe('');
  });
});
