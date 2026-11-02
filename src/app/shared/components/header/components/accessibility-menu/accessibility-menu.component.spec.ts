import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import type { FontSize } from '@shared/models/accessibility.model';

import { AccessibilityMenuComponent } from './accessibility-menu.component';

// UI rendering is verified via E2E (Playwright) — unit tests focus on logic only
describe('AccessibilityMenuComponent', () => {
  let component: AccessibilityMenuComponent;
  let fixture: ComponentFixture<AccessibilityMenuComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccessibilityMenuComponent]
    })
      .overrideComponent(AccessibilityMenuComponent, {
        set: { template: '<div class="mocked">Mocked Accessibility Menu</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(AccessibilityMenuComponent);
    component = fixture.componentInstance;

    // Set required inputs
    fixture.componentRef.setInput('fontSize', 'normal');
    fixture.componentRef.setInput('highContrast', false);
    fixture.componentRef.setInput('reducedMotion', false);
    fixture.componentRef.setInput('currentLanguage', 'de');

    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  describe('Inputs', () => {
    it('should accept fontSize input', () => {
      fixture.componentRef.setInput('fontSize', 'large');
      fixture.detectChanges();
      expect(component.fontSize()).toBe('large');
    });

    it('should accept highContrast input', () => {
      fixture.componentRef.setInput('highContrast', true);
      fixture.detectChanges();
      expect(component.highContrast()).toBe(true);
    });

    it('should accept reducedMotion input', () => {
      fixture.componentRef.setInput('reducedMotion', true);
      fixture.detectChanges();
      expect(component.reducedMotion()).toBe(true);
    });
  });

  describe('Outputs', () => {
    it('should emit fontSizeChanged', () => {
      const emitSpy = jest.fn();
      component.fontSizeChanged.subscribe(emitSpy);

      const exposed = component as unknown as { onFontSizeChange: (size: FontSize) => void };
      exposed.onFontSizeChange('x-large');

      expect(emitSpy).toHaveBeenCalledWith('x-large');
    });

    it('should emit highContrastChanged', () => {
      const emitSpy = jest.fn();
      component.highContrastChanged.subscribe(emitSpy);

      const exposed = component as unknown as { onHighContrastChange: (value: boolean) => void };
      exposed.onHighContrastChange(true);

      expect(emitSpy).toHaveBeenCalledWith(true);
    });

    it('should emit reducedMotionChanged', () => {
      const emitSpy = jest.fn();
      component.reducedMotionChanged.subscribe(emitSpy);

      const exposed = component as unknown as { onReducedMotionChange: (value: boolean) => void };
      exposed.onReducedMotionChange(true);

      expect(emitSpy).toHaveBeenCalledWith(true);
    });
  });

  describe('Font size options', () => {
    it('should offer all available font sizes', () => {
      const expectedSizes: FontSize[] = ['small', 'normal', 'large', 'x-large'];
      const exposed = component as unknown as { fontSizes: FontSize[] };
      expect(exposed.fontSizes).toEqual(expectedSizes);
    });
  });

  describe('Language', () => {
    it('should accept currentLanguage input', () => {
      fixture.componentRef.setInput('currentLanguage', 'en');
      fixture.detectChanges();
      expect(component.currentLanguage()).toBe('en');
    });

    it('should emit languageChanged', () => {
      const emitSpy = jest.fn();
      component.languageChanged.subscribe(emitSpy);

      const exposed = component as unknown as { onLanguageChange: (lang: string) => void };
      exposed.onLanguageChange('en');

      expect(emitSpy).toHaveBeenCalledWith('en');
    });

    it('should expose only DE as visible language (UI-Migration: nur Deutsch sichtbar; EN/UK/FR/AR-Translations bleiben im Code)', () => {
      const exposed = component as unknown as { languages: { code: string; label: string }[] };
      expect(exposed.languages).toHaveLength(1);
      expect(exposed.languages.map(l => l.code)).toEqual(['de']);
    });
  });
});
