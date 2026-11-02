import type { Brand } from './brand.model';

/**
 * Location display model for UI rendering.
 * Maps from API BranchDto → UI display.
 */
export interface LocationDisplay {
  id: string;
  name: string;
  city: string;
  formattedAddress: string;
}

/**
 * All available locations without brand filter
 * REQ-013: Standort is now Step 1, shows ALL locations
 * @deprecated Use API data via BookingApiService.getLocations()
 */
export const ALL_LOCATIONS: LocationDisplay[] = [
  { id: 'muc', name: 'München', city: 'München', formattedAddress: 'München' },
  { id: 'ham', name: 'Hamburg', city: 'Hamburg', formattedAddress: 'Hamburg' },
  { id: 'ber', name: 'Berlin', city: 'Berlin', formattedAddress: 'Berlin' },
  { id: 'fra', name: 'Frankfurt', city: 'Frankfurt', formattedAddress: 'Frankfurt' },
  { id: 'dus', name: 'Düsseldorf', city: 'Düsseldorf', formattedAddress: 'Düsseldorf' },
  { id: 'stu', name: 'Stuttgart', city: 'Stuttgart', formattedAddress: 'Stuttgart' },
  { id: 'kol', name: 'Köln', city: 'Köln', formattedAddress: 'Köln' },
  { id: 'gar', name: 'Garbsen', city: 'Garbsen', formattedAddress: 'Garbsen' },
  { id: 'han-sued', name: 'Hannover Südstadt', city: 'Hannover', formattedAddress: 'Hannover' },
  { id: 'ste', name: 'Steinhude', city: 'Steinhude', formattedAddress: 'Steinhude' },
  { id: 'wob', name: 'Wolfsburg', city: 'Wolfsburg', formattedAddress: 'Wolfsburg' },
  { id: 'han', name: 'Hannover', city: 'Hannover', formattedAddress: 'Hannover' }
];

/**
 * Location data grouped by brand
 * Locations filtered by brand as specified in REQ-003
 * @deprecated Use API data via BookingApiService.getLocations(brand)
 */
export const LOCATIONS_BY_BRAND: Record<Brand, LocationDisplay[]> = {
  audi: [
    { id: 'muc', name: 'München', city: 'München', formattedAddress: 'München' },
    { id: 'ham', name: 'Hamburg', city: 'Hamburg', formattedAddress: 'Hamburg' },
    { id: 'ber', name: 'Berlin', city: 'Berlin', formattedAddress: 'Berlin' },
    { id: 'fra', name: 'Frankfurt', city: 'Frankfurt', formattedAddress: 'Frankfurt' },
    { id: 'dus', name: 'Düsseldorf', city: 'Düsseldorf', formattedAddress: 'Düsseldorf' }
  ],
  bmw: [
    { id: 'stu', name: 'Stuttgart', city: 'Stuttgart', formattedAddress: 'Stuttgart' },
    { id: 'kol', name: 'Köln', city: 'Köln', formattedAddress: 'Köln' },
    { id: 'muc', name: 'München', city: 'München', formattedAddress: 'München' },
    { id: 'ber', name: 'Berlin', city: 'Berlin', formattedAddress: 'Berlin' },
    { id: 'ham', name: 'Hamburg', city: 'Hamburg', formattedAddress: 'Hamburg' }
  ],
  mercedes: [
    { id: 'stu', name: 'Stuttgart', city: 'Stuttgart', formattedAddress: 'Stuttgart' },
    { id: 'muc', name: 'München', city: 'München', formattedAddress: 'München' },
    { id: 'fra', name: 'Frankfurt', city: 'Frankfurt', formattedAddress: 'Frankfurt' },
    { id: 'dus', name: 'Düsseldorf', city: 'Düsseldorf', formattedAddress: 'Düsseldorf' },
    { id: 'ber', name: 'Berlin', city: 'Berlin', formattedAddress: 'Berlin' }
  ],
  mini: [
    { id: 'gar', name: 'Garbsen', city: 'Garbsen', formattedAddress: 'Garbsen' },
    { id: 'han-sued', name: 'Hannover Südstadt', city: 'Hannover', formattedAddress: 'Hannover' },
    { id: 'ste', name: 'Steinhude', city: 'Steinhude', formattedAddress: 'Steinhude' }
  ],
  volkswagen: [
    { id: 'wob', name: 'Wolfsburg', city: 'Wolfsburg', formattedAddress: 'Wolfsburg' },
    { id: 'han', name: 'Hannover', city: 'Hannover', formattedAddress: 'Hannover' },
    { id: 'ber', name: 'Berlin', city: 'Berlin', formattedAddress: 'Berlin' },
    { id: 'muc', name: 'München', city: 'München', formattedAddress: 'München' },
    { id: 'ham', name: 'Hamburg', city: 'Hamburg', formattedAddress: 'Hamburg' }
  ]
};
