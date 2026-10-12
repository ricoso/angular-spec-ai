import { LOCALE_BY_LANGUAGE, toLocalDate, withoutDot } from './date-locale.util';

describe('date-locale.util (REQ-039)', () => {
  it('maps every UI language to a locale', () => {
    expect(LOCALE_BY_LANGUAGE).toEqual({ de: 'de-DE', en: 'en-GB', uk: 'uk-UA', fr: 'fr-FR', ar: 'ar' });
  });

  it('removes only a trailing abbreviation dot', () => {
    expect(withoutDot('Sept.')).toBe('Sept');
    expect(withoutDot('Mai')).toBe('Mai');
    expect(withoutDot('a.b')).toBe('a.b');
  });

  it('parses an ISO date as local calendar date without UTC shift', () => {
    const date = toLocalDate('2026-02-25');
    expect(date.getFullYear()).toBe(2026);
    expect(date.getMonth()).toBe(1);
    expect(date.getDate()).toBe(25);
    expect(date.getHours()).toBe(0);
  });
});
