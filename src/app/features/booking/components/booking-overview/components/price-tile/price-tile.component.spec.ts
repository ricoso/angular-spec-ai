import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { i18nKeys, TranslateService } from '@core/i18n';

import { PriceTileComponent } from './price-tile.component';

describe('PriceTileComponent', () => {
  let component: PriceTileComponent;
  let fixture: ComponentFixture<PriceTileComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PriceTileComponent]
    })
      .overrideComponent(PriceTileComponent, {
        set: { template: '<div class="mocked">Mocked PriceTile</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(PriceTileComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('appointmentPreference', 'anytime');
    fixture.componentRef.setInput('callbackOption', 'no');
    fixture.componentRef.setInput('bookingNote', '');

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should default to "without rental car" and no wish (REQ-039)', () => {
    expect(component.rentalCarRequested()).toBe(false);
    expect(component.rentCategoryWishName()).toBeNull();
  });

  it('should expose i18n keys', () => {
    const exposed = component as unknown as { bookingOverview: Record<string, unknown> };
    expect(exposed.bookingOverview).toBeDefined();
  });

  describe('mobilityOptionLabel', () => {
    const exposed = (c: PriceTileComponent): { mobilityOptionLabel: () => string } =>
      c as unknown as { mobilityOptionLabel: () => string };

    it('should show the "no rental car" label without rental car', () => {
      const translate = TestBed.inject(TranslateService);
      expect(exposed(component).mobilityOptionLabel()).toBe(translate.instant(i18nKeys.booking.notes.rentCategory.none));
    });

    it('should show "included" — never a category — with rental car (REQ-039 BR-M11)', () => {
      fixture.componentRef.setInput('rentalCarRequested', true);
      fixture.componentRef.setInput('rentCategoryWishName', 'Mittelklasse');
      fixture.detectChanges();
      const translate = TestBed.inject(TranslateService);
      expect(exposed(component).mobilityOptionLabel()).toBe(translate.instant(i18nKeys.booking.bookingOverview.rentalCar.value));
      expect(exposed(component).mobilityOptionLabel()).not.toContain('Mittelklasse');
    });
  });

  describe('rentalWishName (REQ-039 BR-M5)', () => {
    const exposed = (c: PriceTileComponent): { rentalWishName: () => string | null } =>
      c as unknown as { rentalWishName: () => string | null };

    it('should show the wish only together with a rental car', () => {
      fixture.componentRef.setInput('rentCategoryWishName', 'Oberklasse');
      fixture.detectChanges();
      expect(exposed(component).rentalWishName()).toBeNull();

      fixture.componentRef.setInput('rentalCarRequested', true);
      fixture.detectChanges();
      expect(exposed(component).rentalWishName()).toBe('Oberklasse');
    });
  });

  describe('appointmentPreferenceLabel', () => {
    const exposed = (c: PriceTileComponent): { appointmentPreferenceLabel: () => string } =>
      c as unknown as { appointmentPreferenceLabel: () => string };

    it('should resolve label for "anytime"', () => {
      fixture.componentRef.setInput('appointmentPreference', 'anytime');
      fixture.detectChanges();
      expect(exposed(component).appointmentPreferenceLabel()).toBeTruthy();
    });

    it('should resolve label for "morning"', () => {
      fixture.componentRef.setInput('appointmentPreference', 'morning');
      fixture.detectChanges();
      expect(exposed(component).appointmentPreferenceLabel()).toBeTruthy();
    });

    it('should resolve label for "afternoon"', () => {
      fixture.componentRef.setInput('appointmentPreference', 'afternoon');
      fixture.detectChanges();
      expect(exposed(component).appointmentPreferenceLabel()).toBeTruthy();
    });
  });

  describe('callbackOptionLabel', () => {
    const exposed = (c: PriceTileComponent): { callbackOptionLabel: () => string } =>
      c as unknown as { callbackOptionLabel: () => string };

    it('should resolve label for "none"', () => {
      fixture.componentRef.setInput('callbackOption', 'no');
      fixture.detectChanges();
      expect(exposed(component).callbackOptionLabel()).toBeTruthy();
    });

    it('should resolve label for "yes"', () => {
      fixture.componentRef.setInput('callbackOption', 'yes');
      fixture.detectChanges();
      expect(exposed(component).callbackOptionLabel()).toBeTruthy();
    });
  });
});
