import { TestBed } from '@angular/core/testing';

import type {
  AccessibilityState
} from '../models/accessibility.model';
import {
  ACCESSIBILITY_STORAGE_KEY
} from '../models/accessibility.model';

import { AccessibilityService } from './accessibility.service';

describe('AccessibilityService', () => {
  let service: AccessibilityService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AccessibilityService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-font-size');
    document.documentElement.removeAttribute('data-high-contrast');
    document.documentElement.classList.remove('reduce-motion');
  });

  describe('getSettings', () => {
    it('should return default values when LocalStorage is empty', () => {
      const settings = service.getSettings();

      expect(settings.fontSize).toBe('normal');
      expect(settings.highContrast).toBe(false);
    });

    it('should load saved settings from LocalStorage', () => {
      const saved: AccessibilityState = {
        fontSize: 'large',
        highContrast: true,
        reducedMotion: true
      };
      localStorage.setItem(
        ACCESSIBILITY_STORAGE_KEY,
        JSON.stringify({ ...saved, version: 1 })
      );

      const settings = service.getSettings();

      expect(settings.fontSize).toBe('large');
      expect(settings.highContrast).toBe(true);
      expect(settings.reducedMotion).toBe(true);
    });

    it('should reset invalid font size to normal', () => {
      localStorage.setItem(
        ACCESSIBILITY_STORAGE_KEY,
        JSON.stringify({ fontSize: 'invalid', highContrast: false, reducedMotion: false, version: 1 })
      );

      const settings = service.getSettings();

      expect(settings.fontSize).toBe('normal');
    });

    it('should return defaults for invalid JSON', () => {
      localStorage.setItem(ACCESSIBILITY_STORAGE_KEY, 'invalid-json');

      const settings = service.getSettings();

      expect(settings.fontSize).toBe('normal');
      expect(settings.highContrast).toBe(false);
    });
  });

  describe('saveSettings', () => {
    it('should save settings to LocalStorage', () => {
      const state: AccessibilityState = {
        fontSize: 'x-large',
        highContrast: true,
        reducedMotion: false
      };

      service.saveSettings(state);

      const saved = JSON.parse(localStorage.getItem(ACCESSIBILITY_STORAGE_KEY) ?? '{}');
      expect(saved.fontSize).toBe('x-large');
      expect(saved.highContrast).toBe(true);
      expect(saved.reducedMotion).toBe(false);
      expect(saved.version).toBe(1);
    });
  });

  describe('applyToDocument', () => {
    it('should set data-font-size attribute', () => {
      const state: AccessibilityState = {
        fontSize: 'large',
        highContrast: false,
        reducedMotion: false
      };

      service.applyToDocument(state);

      expect(document.documentElement.getAttribute('data-font-size')).toBe('large');
    });

    it('should set data-high-contrast attribute', () => {
      const state: AccessibilityState = {
        fontSize: 'normal',
        highContrast: true,
        reducedMotion: false
      };

      service.applyToDocument(state);

      expect(document.documentElement.getAttribute('data-high-contrast')).toBe('true');
    });

    it('should add reduce-motion class when enabled', () => {
      const state: AccessibilityState = {
        fontSize: 'normal',
        highContrast: false,
        reducedMotion: true
      };

      service.applyToDocument(state);

      expect(document.documentElement.classList.contains('reduce-motion')).toBe(true);
    });

    it('should remove reduce-motion class when disabled', () => {
      document.documentElement.classList.add('reduce-motion');
      const state: AccessibilityState = {
        fontSize: 'normal',
        highContrast: false,
        reducedMotion: false
      };

      service.applyToDocument(state);

      expect(document.documentElement.classList.contains('reduce-motion')).toBe(false);
    });

    it('should add high-contrast class to body when enabled', () => {
      const state: AccessibilityState = {
        fontSize: 'normal',
        highContrast: true,
        reducedMotion: false
      };

      service.applyToDocument(state);

      expect(document.body.classList.contains('high-contrast')).toBe(true);
    });

    it('should remove high-contrast class from body when disabled', () => {
      document.body.classList.add('high-contrast');
      const state: AccessibilityState = {
        fontSize: 'normal',
        highContrast: false,
        reducedMotion: false
      };

      service.applyToDocument(state);

      expect(document.body.classList.contains('high-contrast')).toBe(false);
    });
  });
});
