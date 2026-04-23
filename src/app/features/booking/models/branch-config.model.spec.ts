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

    it('should resolve VW Nutzfahrzeuge spellings to vw_nutzfahrzeuge.svg', () => {
      expect(getBrandLogoPath('VW Nutzfahrzeuge')).toBe('assets/brands/vw_nutzfahrzeuge.svg');
      expect(getBrandLogoPath('VW-Nutzfahrzeuge')).toBe('assets/brands/vw_nutzfahrzeuge.svg');
      expect(getBrandLogoPath('Volkswagen Nutzfahrzeuge')).toBe('assets/brands/vw_nutzfahrzeuge.svg');
      expect(getBrandLogoPath('VWN')).toBe('assets/brands/vw_nutzfahrzeuge.svg');
    });

    it('should resolve brand names case-insensitive and whitespace/hyphen-agnostic', () => {
      expect(getBrandLogoPath('volkswagen nutzfahrzeuge')).toBe('assets/brands/vw_nutzfahrzeuge.svg');
      expect(getBrandLogoPath('VOLKSWAGEN-NUTZFAHRZEUGE')).toBe('assets/brands/vw_nutzfahrzeuge.svg');
      expect(getBrandLogoPath('mercedes benz')).toBe('assets/brands/mercedes.svg');
    });

    it('should resolve ŠKODA and Skoda to skoda.svg', () => {
      expect(getBrandLogoPath('ŠKODA')).toBe('assets/brands/skoda.svg');
      expect(getBrandLogoPath('Skoda')).toBe('assets/brands/skoda.svg');
      expect(getBrandLogoPath('skoda')).toBe('assets/brands/skoda.svg');
    });
  });
});
