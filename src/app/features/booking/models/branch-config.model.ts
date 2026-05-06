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
  /** SoftNet-Group-ID der tireChange-Variante (Boxenstopp), null/undefined wenn keine konfiguriert. */
  tireChangeGroupId?: string | null;
}

export interface BranchConfigFile {
  branches: BranchConfig[];
  defaultBranchId: string;
}

/** Map brand display name to SVG filename in assets/brands/ */
/* eslint-disable @typescript-eslint/naming-convention */
export const BRAND_LOGO_MAP: Record<string, string> = {
  'VW': 'volkswagen.png',
  'Volkswagen': 'volkswagen.png',
  'VW Nutzfahrzeuge': 'vw_nutzfahrzeuge.png',
  'VW-Nutzfahrzeuge': 'vw_nutzfahrzeuge.png',
  'Volkswagen Nutzfahrzeuge': 'vw_nutzfahrzeuge.png',
  'Volkswagen-Nutzfahrzeuge': 'vw_nutzfahrzeuge.png',
  'VWN': 'vw_nutzfahrzeuge.png',
  'Audi': 'audi.png',
  'SEAT': 'seat.png',
  'CUPRA': 'cupra.png',
  'ŠKODA': 'skoda.png',
  'Skoda': 'skoda.png',
  'Hyundai': 'hyundai.png',
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
