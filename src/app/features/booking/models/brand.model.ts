/**
 * Vehicle brand type union
 * BR-1: Exactly 5 brands available
 */
export type Brand = 'audi' | 'bmw' | 'mercedes' | 'mini' | 'volkswagen';

/**
 * Brand display model with id and display name
 */
export interface BrandDisplay {
  id: Brand;
  name: string;
}

/**
 * Available brand definitions
 * Order matches mockup layout
 */
export const AVAILABLE_BRANDS: BrandDisplay[] = [
  { id: 'audi', name: 'Audi' },
  { id: 'bmw', name: 'BMW' },
  { id: 'mercedes', name: 'Mercedes-Benz' },
  { id: 'mini', name: 'MINI' },
  { id: 'volkswagen', name: 'Volkswagen' }
];

/**
 * Brands available per location (reverse mapping)
 * REQ-013: Used after location is selected to show filtered brands
 */
/* eslint-disable @typescript-eslint/naming-convention */
export const BRANDS_BY_LOCATION: Record<string, BrandDisplay[]> = {
  muc: [
    { id: 'audi', name: 'Audi' },
    { id: 'bmw', name: 'BMW' },
    { id: 'mercedes', name: 'Mercedes-Benz' },
    { id: 'volkswagen', name: 'Volkswagen' }
  ],
  ham: [
    { id: 'audi', name: 'Audi' },
    { id: 'bmw', name: 'BMW' },
    { id: 'volkswagen', name: 'Volkswagen' }
  ],
  ber: [
    { id: 'audi', name: 'Audi' },
    { id: 'bmw', name: 'BMW' },
    { id: 'mercedes', name: 'Mercedes-Benz' },
    { id: 'volkswagen', name: 'Volkswagen' }
  ],
  fra: [
    { id: 'audi', name: 'Audi' },
    { id: 'mercedes', name: 'Mercedes-Benz' }
  ],
  dus: [
    { id: 'audi', name: 'Audi' },
    { id: 'mercedes', name: 'Mercedes-Benz' }
  ],
  stu: [
    { id: 'bmw', name: 'BMW' },
    { id: 'mercedes', name: 'Mercedes-Benz' }
  ],
  kol: [
    { id: 'bmw', name: 'BMW' }
  ],
  gar: [
    { id: 'mini', name: 'MINI' }
  ],
  'han-sued': [
    { id: 'mini', name: 'MINI' }
  ],
  ste: [
    { id: 'mini', name: 'MINI' }
  ],
  wob: [
    { id: 'volkswagen', name: 'Volkswagen' }
  ],
  han: [
    { id: 'volkswagen', name: 'Volkswagen' }
  ]
};
/* eslint-enable @typescript-eslint/naming-convention */