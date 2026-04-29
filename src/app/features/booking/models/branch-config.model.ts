export interface BranchAddress {
  street: string;
  zip: string;
  city: string;
  /** REQ-025 (c): optional, falls API ein Land mitliefert. */
  country?: string;
}

export interface BranchConfig {
  branchId: string;
  name: string;
  address: BranchAddress;
  brands: string[];
}

export interface BranchConfigFile {
  branches: BranchConfig[];
  defaultBranchId: string;
}

/** Map brand display name to SVG filename in assets/brands/ */
/* eslint-disable @typescript-eslint/naming-convention */
export const BRAND_LOGO_MAP: Record<string, string> = {
  'VW': 'volkswagen.svg',
  'Volkswagen': 'volkswagen.svg',
  'VW Nutzfahrzeuge': 'vw_nutzfahrzeuge.svg',
  'VW-Nutzfahrzeuge': 'vw_nutzfahrzeuge.svg',
  'Volkswagen Nutzfahrzeuge': 'vw_nutzfahrzeuge.svg',
  'Volkswagen-Nutzfahrzeuge': 'vw_nutzfahrzeuge.svg',
  'VWN': 'vw_nutzfahrzeuge.svg',
  'Audi': 'audi.svg',
  'SEAT': 'seat.svg',
  'CUPRA': 'cupra.svg',
  'ŠKODA': 'skoda.svg',
  'Skoda': 'skoda.svg',
  'Hyundai': 'hyundai.svg',
  'Porsche': 'porsche.svg',
  'Bentley': 'bentley.svg',
  'Bugatti': 'bugatti.svg',
  'Rimac': 'rimac.svg',
  'BMW': 'bmw.svg',
  'BMWi': 'bmw.svg',
  'Mercedes-Benz': 'mercedes.svg',
  'Mercedes': 'mercedes.svg',
  'Opel': 'opel.svg',
};
/* eslint-enable @typescript-eslint/naming-convention */

/** Default brand logo fallback when brand is not in BRAND_LOGO_MAP */
export const DEFAULT_BRAND_LOGO = 'default.svg';

/** Fallback address when API description is empty/null */
export const FALLBACK_ADDRESS = 'Musterstraße 1, 00000 Musterhausen';

/** Get brand logo path with fallback to default.svg.
 *  Lookup is case-insensitive and whitespace/hyphen-normalised — so
 *  "VW Nutzfahrzeuge", "vw-nutzfahrzeuge" and "Volkswagen Nutzfahrzeuge"
 *  all resolve to the same logo. */
export function getBrandLogoPath(brandName: string): string {
  if (!brandName) {
    return `assets/brands/${DEFAULT_BRAND_LOGO}`;
  }
  const direct = BRAND_LOGO_MAP[brandName];
  if (direct) {
    return `assets/brands/${direct}`;
  }
  const normalised = brandName.toLowerCase().replace(/[\s_-]+/g, '');
  for (const [key, file] of Object.entries(BRAND_LOGO_MAP)) {
    if (key.toLowerCase().replace(/[\s_-]+/g, '') === normalised) {
      return `assets/brands/${file}`;
    }
  }
  return `assets/brands/${DEFAULT_BRAND_LOGO}`;
}
