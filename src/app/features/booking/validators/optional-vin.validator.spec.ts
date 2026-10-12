import { FormControl } from '@angular/forms';

import { optionalVinValidator } from './optional-vin.validator';

describe('optionalVinValidator (REQ-039)', () => {
  const validate = (value: unknown): ReturnType<ReturnType<typeof optionalVinValidator>> =>
    optionalVinValidator()(new FormControl(value));

  it('accepts empty, whitespace-only and null as "not given"', () => {
    expect(validate('')).toBeNull();
    expect(validate('   ')).toBeNull();
    expect(validate(null)).toBeNull();
  });

  it('accepts a 17-char alphanumeric VIN, also with surrounding whitespace', () => {
    expect(validate('WVWZZZ1JZ3W386752')).toBeNull();
    expect(validate('  wvwzzz1jz3w386752 ')).toBeNull();
  });

  it('flags non-alphanumeric characters as vinFormat', () => {
    expect(validate('WVW-ZZZ1JZ3W38675')).toEqual({ vinFormat: true });
  });

  it('flags a wrong length as vinLength', () => {
    expect(validate('WVWZZZ1JZ3W3867')).toEqual({ vinLength: true });
    expect(validate('WVWZZZ1JZ3W3867521')).toEqual({ vinLength: true });
  });
});
