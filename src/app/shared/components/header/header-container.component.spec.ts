import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TranslateService } from '@app/core/i18n';
import type { AppointmentSlot } from '@app/features/booking/models/appointment.model';
import { LOCATIONS_BY_BRAND } from '@app/features/booking/models/location.model';
import { AppointmentApiService } from '@app/features/booking/services/appointment-api.service';
import { BookingApiService } from '@app/features/booking/services/booking-api.service';
import { RentCategoryApiService } from '@app/features/booking/services/rent-category-api.service';
import { BookingStore } from '@app/features/booking/stores/booking.store';
import { ACCESSIBILITY_DEFAULTS } from '@shared/models/accessibility.model';
import { AccessibilityService } from '@shared/services/accessibility.service';
import { AccessibilityStore } from '@shared/stores/accessibility.store';
import { CartStore } from '@shared/stores/cart.store';

import { HeaderContainerComponent } from './header-container.component';

// UI rendering is verified via E2E (Playwright) — unit tests focus on logic only
describe('HeaderContainerComponent', () => {
  let component: HeaderContainerComponent;
  let fixture: ComponentFixture<HeaderContainerComponent>;
  let accessibilityStore: InstanceType<typeof AccessibilityStore>;
  let bookingStore: InstanceType<typeof BookingStore>;
  let serviceMock: jest.Mocked<AccessibilityService>;

  beforeEach(async () => {
    serviceMock = {
      getSettings: jest.fn().mockReturnValue({ ...ACCESSIBILITY_DEFAULTS }),
      saveSettings: jest.fn(),
      applyToDocument: jest.fn()
    } as unknown as jest.Mocked<AccessibilityService>;

    await TestBed.configureTestingModule({
      imports: [HeaderContainerComponent],
      providers: [
        provideRouter([]),
        AccessibilityStore,
        CartStore,
        BookingStore,
        { provide: AccessibilityService, useValue: serviceMock },
        {
          provide: BookingApiService,
          useValue: {
            getBrands: jest.fn().mockResolvedValue([]),
            getLocations: jest.fn().mockResolvedValue([]),
            getServices: jest.fn().mockResolvedValue([])
          }
        },
        {
          provide: AppointmentApiService,
          useValue: {
            getAppointments: jest.fn().mockResolvedValue([]),
            createAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' }),
            getAppointment: jest.fn().mockResolvedValue({ id: 'apt-1', status: 'confirmed' })
          }
        },
        { provide: RentCategoryApiService, useValue: { getRentCategories: jest.fn().mockResolvedValue([]) } }
      ]
    })
      .overrideComponent(HeaderContainerComponent, {
        set: { template: '<div class="mocked">Mocked Header</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(HeaderContainerComponent);
    component = fixture.componentInstance;
    accessibilityStore = TestBed.inject(AccessibilityStore);
    bookingStore = TestBed.inject(BookingStore);
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should inject AccessibilityStore', () => {
    expect(accessibilityStore).toBeTruthy();
  });

  it('should inject CartStore', () => {
    const cartStore = TestBed.inject(CartStore);
    expect(cartStore).toBeTruthy();
  });

  describe('Event Handler', () => {
    it('should delegate onFontSizeChange to store', () => {
      const setFontSizeSpy = jest.spyOn(accessibilityStore, 'setFontSize');
      const exposed = component as unknown as { onFontSizeChange: (size: string) => void };

      exposed.onFontSizeChange('large');

      expect(setFontSizeSpy).toHaveBeenCalledWith('large');
    });

    it('should delegate onHighContrastChange to store', () => {
      const setHighContrastSpy = jest.spyOn(accessibilityStore, 'setHighContrast');
      const exposed = component as unknown as { onHighContrastChange: (value: boolean) => void };

      exposed.onHighContrastChange(true);

      expect(setHighContrastSpy).toHaveBeenCalledWith(true);
    });

    it('should delegate onReducedMotionChange to store', () => {
      const setReducedMotionSpy = jest.spyOn(accessibilityStore, 'setReducedMotion');
      const exposed = component as unknown as { onReducedMotionChange: (value: boolean) => void };

      exposed.onReducedMotionChange(true);

      expect(setReducedMotionSpy).toHaveBeenCalledWith(true);
    });

    it('should delegate onLanguageChange to TranslateService', () => {
      const translateService = TestBed.inject(TranslateService);
      const useSpy = jest.spyOn(translateService, 'use');
      const exposed = component as unknown as { onLanguageChange: (lang: string) => void };

      exposed.onLanguageChange('fr');

      expect(useSpy).toHaveBeenCalledWith('fr');
    });
  });

  describe('Cart computed signals', () => {
    it('should return null for selectedBrandName when no brand selected', () => {
      const exposed = component as unknown as { selectedBrandName: () => string | null };
      expect(exposed.selectedBrandName()).toBeNull();
    });

    it('should return capitalized brand name when brand is selected', () => {
      bookingStore.setBrand('audi');
      const exposed = component as unknown as { selectedBrandName: () => string | null };
      expect(exposed.selectedBrandName()).toBe('Audi');
    });

    it('should return null for selectedLocationName when no location selected', () => {
      const exposed = component as unknown as { selectedLocationName: () => string | null };
      expect(exposed.selectedLocationName()).toBeNull();
    });

    it('should return location name when location is selected', () => {
      bookingStore.setBrand('audi');
      bookingStore.setLocation(LOCATIONS_BY_BRAND.audi[0]);
      const exposed = component as unknown as { selectedLocationName: () => string | null };
      expect(exposed.selectedLocationName()).toBe(LOCATIONS_BY_BRAND.audi[0].name);
    });

    it('should return empty array for cartServiceChips when no services selected', () => {
      const exposed = component as unknown as { cartServiceChips: () => unknown[] };
      expect(exposed.cartServiceChips()).toEqual([]);
    });

    it('should return service chips with title and svgIcon when services selected', () => {
      bookingStore.toggleService('tuv');
      const exposed = component as unknown as { cartServiceChips: () => { title: string; svgIcon: string; optionLabels: string[] }[] };
      const chips = exposed.cartServiceChips();
      expect(chips).toHaveLength(1);
      expect(chips[0].title).toBeTruthy();
      expect(chips[0].svgIcon).toBe('assets/icons/services/tuv.svg');
      expect(chips[0].optionLabels).toEqual([]);
    });

    it('should return option labels for tire change with options', () => {
      bookingStore.confirmServiceOptions('tire-change', ['stored-tires']);
      const exposed = component as unknown as { cartServiceChips: () => { title: string; svgIcon: string; optionLabels: string[] }[] };
      const chips = exposed.cartServiceChips();
      expect(chips).toHaveLength(1);
      expect(chips[0].svgIcon).toBe('assets/icons/services/tire-change.svg');
      expect(chips[0].optionLabels.length).toBeGreaterThan(0);
    });

    it('should return summary text with count', () => {
      bookingStore.toggleService('tuv');
      bookingStore.toggleService('inspection');
      const exposed = component as unknown as { cartSummaryText: () => string };
      const text = exposed.cartSummaryText();
      expect(text).toContain('2');
    });

    it('should return null for selectedAppointmentText when no appointment selected', () => {
      const exposed = component as unknown as { selectedAppointmentText: () => string | null };
      expect(exposed.selectedAppointmentText()).toBeNull();
    });

    it('should return formatted appointment text when appointment is selected', () => {
      const mockAppointment: AppointmentSlot = {
        id: '2026-02-25-09-00',
        date: '2026-02-25',
        displayDate: '25.02.2026',
        dayAbbreviation: 'Mi',
        time: '09:00',
        displayTime: '09:00 Uhr'
      };
      bookingStore.selectAppointment(mockAppointment);
      const exposed = component as unknown as { selectedAppointmentText: () => string | null };
      expect(exposed.selectedAppointmentText()).toBe('25.02.2026, 09:00 Uhr');
    });

    it('should expose isEmbedded signal', () => {
      const exposed = component as unknown as { isEmbedded: () => boolean };
      expect(typeof exposed.isEmbedded()).toBe('boolean');
    });

    it('should expose onClose handler', () => {
      const exposed = component as unknown as { onClose: () => void };
      expect(typeof exposed.onClose).toBe('function');
    });

    it('should update appointment text when appointment changes', () => {
      const appointment1: AppointmentSlot = {
        id: '2026-02-25-09-00',
        date: '2026-02-25',
        displayDate: '25.02.2026',
        dayAbbreviation: 'Mi',
        time: '09:00',
        displayTime: '09:00 Uhr'
      };
      const appointment2: AppointmentSlot = {
        id: '2026-02-26-14-00',
        date: '2026-02-26',
        displayDate: '26.02.2026',
        dayAbbreviation: 'Do',
        time: '14:00',
        displayTime: '14:00 Uhr'
      };
      const exposed = component as unknown as { selectedAppointmentText: () => string | null };

      bookingStore.selectAppointment(appointment1);
      expect(exposed.selectedAppointmentText()).toBe('25.02.2026, 09:00 Uhr');

      bookingStore.selectAppointment(appointment2);
      expect(exposed.selectedAppointmentText()).toBe('26.02.2026, 14:00 Uhr');
    });
  });
});
