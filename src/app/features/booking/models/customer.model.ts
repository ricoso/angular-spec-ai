export type Salutation = 'mr' | 'ms';

/**
 * REQ-043 B-1: SoftNet stores the plate as `car.licenseNr` with at most 20 chars and answers a
 * longer value with HTTP 500 — mirrors `CreateAppointmentRequest.LICENSE_PLATE_MAX_LENGTH`.
 */
export const LICENSE_PLATE_MAX_LENGTH = 20;

export interface CustomerInfo {
  email: string;
  salutation: Salutation;
  firstName: string;
  lastName: string;
  street: string;
  postalCode: string;
  city: string;
  mobilePhone: string;
  phone?: string;
  country?: string;
  language?: string;
}

export interface VehicleInfo {
  licensePlate: string;
  mileage: number;
  vin?: string;
  model?: string;
  firstRegMonth?: string;
  firstRegYear?: string;
}
