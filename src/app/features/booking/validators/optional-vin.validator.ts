import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** A VIN (FIN) has exactly 17 characters. */
export const VIN_LENGTH = 17;

/**
 * REQ-039: the VIN is fully optional. Empty or whitespace-only input is valid ("not given");
 * a given value must consist of letters/digits only (`vinFormat`) and have exactly 17
 * characters (`vinLength`). Surrounding whitespace is ignored.
 */
export function optionalVinValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = typeof control.value === 'string' ? control.value.trim() : '';
    if (value.length === 0) {
      return null;
    }
    if (!/^[A-Za-z0-9]+$/.test(value)) {
      return { vinFormat: true };
    }
    return value.length === VIN_LENGTH ? null : { vinLength: true };
  };
}
