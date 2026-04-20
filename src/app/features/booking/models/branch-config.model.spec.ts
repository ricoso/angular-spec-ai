import { BRAND_LOGO_MAP, DEFAULT_BRAND_LOGO, FALLBACK_ADDRESS, getBrandLogoPath } from './branch-config.model';

describe('branch-config.model', () => {
  describe('BRAND_LOGO_MAP', () => {
    it('should map known brands to SVG filenames', () => {
      expect(BRAND_LOGO_MAP.VW).toBe('volkswagen.svg');
      expect(BRAND_LOGO_MAP.Audi).toBe('audi.svg');
      expect(BRAND_LOGO_MAP.BMW).toBe('bmw.svg');
      expect(BRAND_LOGO_MAP['Mercedes-Benz']).toBe('mercedes.svg');
      expect(BRAND_LOGO_MAP.Opel).toBe('opel.svg');
    });
  });

  describe('DEFAULT_BRAND_LOGO', () => {
    it('should be default.svg', () => {
      expect(DEFAULT_BRAND_LOGO).toBe('default.svg');
    });
  });

  describe('FALLBACK_ADDRESS', () => {
    it('should contain Musterstraße', () => {
      expect(FALLBACK_ADDRESS).toContain('Musterstraße');
    });
  });

  describe('getBrandLogoPath', () => {
    it('should return correct path for known brand', () => {
      expect(getBrandLogoPath('Audi')).toBe('assets/brands/audi.svg');
    });

    it('should return correct path for VW alias', () => {
      expect(getBrandLogoPath('VW')).toBe('assets/brands/volkswagen.svg');
      expect(getBrandLogoPath('Volkswagen')).toBe('assets/brands/volkswagen.svg');
    });

    it('should return default.svg path for unknown brand', () => {
      expect(getBrandLogoPath('UnknownBrand')).toBe('assets/brands/default.svg');
    });

    it('should return default.svg path for empty string', () => {
      expect(getBrandLogoPath('')).toBe('assets/brands/default.svg');
    });
  });
});
