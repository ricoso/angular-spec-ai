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

/**
 * REQ-040/REQ-041: validator for the case where the VIN is mandatory (stored tires or recall).
 * Empty or whitespace-only input yields `{ required: true }` (so the existing "required" error
 * message still applies) instead of silently passing; a given value must be a valid 17-character
 * alphanumeric VIN. Guards against a whitespace-only FIN defeating the mandatory field.
 */
export function requiredVinValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = typeof control.value === 'string' ? control.value.trim() : '';
    if (value.length === 0) {
      return { required: true };
    }
    if (!/^[A-Za-z0-9]+$/.test(value)) {
      return { vinFormat: true };
    }
    return value.length === VIN_LENGTH ? null : { vinLength: true };
  };
}

/**
 * REQ-041: normalize a VIN coming from the recall URL — trimmed + upper-cased. Returns the
 * normalized value only when it is a valid 17-character alphanumeric VIN, otherwise `null`
 * (E-2: a malformed URL VIN is never accepted and must be re-entered manually).
 */
export function normalizeRecallVin(vin: string | null | undefined): string | null {
  const trimmed = (vin ?? '').trim().toUpperCase();
  if (trimmed.length === 0) {
    return null;
  }
  return new RegExp(`^[A-Z0-9]{${VIN_LENGTH}}$`).test(trimmed) ? trimmed : null;
}
