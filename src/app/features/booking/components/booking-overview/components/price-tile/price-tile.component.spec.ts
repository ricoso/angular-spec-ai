import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

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

    fixture.componentRef.setInput('rentCategoryName', null);
    fixture.componentRef.setInput('appointmentPreference', 'anytime');
    fixture.componentRef.setInput('callbackOption', 'none');
    fixture.componentRef.setInput('bookingNote', '');

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should accept rentCategoryName input', () => {
    expect(component.rentCategoryName()).toBeNull();
    fixture.componentRef.setInput('rentCategoryName', 'Kleinwagen');
    fixture.detectChanges();
    expect(component.rentCategoryName()).toBe('Kleinwagen');
  });

  it('should expose i18n keys', () => {
    const exposed = component as unknown as { bookingOverview: Record<string, unknown> };
    expect(exposed.bookingOverview).toBeDefined();
  });

  describe('mobilityOptionLabel', () => {
    const exposed = (c: PriceTileComponent): { mobilityOptionLabel: () => string } =>
      c as unknown as { mobilityOptionLabel: () => string };

    it('should fall back to "none" label when rentCategoryName is null', () => {
      fixture.componentRef.setInput('rentCategoryName', null);
      fixture.detectChanges();
      expect(exposed(component).mobilityOptionLabel()).toBeTruthy();
    });

    it('should return category name directly when provided', () => {
      fixture.componentRef.setInput('rentCategoryName', 'Mittelklasse');
      fixture.detectChanges();
      expect(exposed(component).mobilityOptionLabel()).toBe('Mittelklasse');
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
      fixture.componentRef.setInput('callbackOption', 'none');
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
