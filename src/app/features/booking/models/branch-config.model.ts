export interface BranchAddress {
  street: string;
  zip: string;
  city: string;
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
  'Audi': 'audi.svg',
  'SEAT': 'seat.svg',
  'CUPRA': 'cupra.svg',
  'ŠKODA': 'skoda.svg',
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

/** Get brand logo path with fallback to default.svg */
export function getBrandLogoPath(brandName: string): string {
  const filename = BRAND_LOGO_MAP[brandName] ?? DEFAULT_BRAND_LOGO;
  return `assets/brands/${filename}`;
}
