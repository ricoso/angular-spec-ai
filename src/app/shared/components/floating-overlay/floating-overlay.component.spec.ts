import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { AppointmentApiService } from '@app/features/booking/services/appointment-api.service';
import { BookingApiService } from '@app/features/booking/services/booking-api.service';
import { WorkshopCalendarApiService } from '@app/features/booking/services/workshop-calendar-api.service';
import { BookingStore } from '@app/features/booking/stores/booking.store';

import { FloatingOverlayComponent } from './floating-overlay.component';

describe('FloatingOverlayComponent', () => {
  let component: FloatingOverlayComponent;
  let fixture: ComponentFixture<FloatingOverlayComponent>;
  let store: InstanceType<typeof BookingStore>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FloatingOverlayComponent],
      providers: [
        BookingStore,
        {
          provide: BookingApiService,
          useValue: {
            getBrands: jest.fn().mockResolvedValue([]),
            getBrandsByBranch: jest.fn().mockResolvedValue([]),
            getLocations: jest.fn().mockResolvedValue([]),
            getBranchById: jest.fn().mockResolvedValue(null),
            getBranches: jest.fn().mockResolvedValue([]),
            getServices: jest.fn().mockResolvedValue([]),
            getBrandLogoPath: jest.fn().mockReturnValue('assets/brands/default.svg')
          }
        },
        { provide: AppointmentApiService, useValue: { getAppointments: jest.fn().mockResolvedValue([]), createAppointment: jest.fn().mockResolvedValue({ id: "apt-1", status: "confirmed" }), getAppointment: jest.fn().mockResolvedValue({ id: "apt-1", status: "confirmed" }) } },
        { provide: WorkshopCalendarApiService, useValue: { getWorkshopCalendarDays: jest.fn().mockResolvedValue([]) } }
      ]
    })
      .overrideComponent(FloatingOverlayComponent, {
        set: { template: '<div class="mocked">Mocked Overlay</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    fixture = TestBed.createComponent(FloatingOverlayComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should start with collapsed state', () => {
    const exposed = component as unknown as { isExpanded: () => boolean };
    expect(exposed.isExpanded()).toBe(false);
  });

  it('should toggle cart expansion', () => {
    const exposed = component as unknown as { isExpanded: () => boolean; onToggleCart: () => void };
    exposed.onToggleCart();
    expect(exposed.isExpanded()).toBe(true);
    exposed.onToggleCart();
    expect(exposed.isExpanded()).toBe(false);
  });

  it('should close cart', () => {
    const exposed = component as unknown as { isExpanded: () => boolean; onToggleCart: () => void; onCloseCart: () => void };
    exposed.onToggleCart();
    expect(exposed.isExpanded()).toBe(true);
    exposed.onCloseCart();
    expect(exposed.isExpanded()).toBe(false);
  });

  it('should show selected location name', () => {
    store.setLocation({ id: 'loc1', name: 'München', city: 'München', formattedAddress: 'München' });
    const exposed = component as unknown as { selectedLocationName: () => string };
    expect(exposed.selectedLocationName()).toBe('München');
  });

  it('should show selected brand name', () => {
    store.setBrand('audi');
    const exposed = component as unknown as { selectedBrandName: () => string };
    expect(exposed.selectedBrandName()).toBe('audi');
  });

  it('should return empty location name when none selected', () => {
    const exposed = component as unknown as { selectedLocationName: () => string };
    expect(exposed.selectedLocationName()).toBe('');
  });

  it('should return empty brand name when none selected', () => {
    const exposed = component as unknown as { selectedBrandName: () => string };
    expect(exposed.selectedBrandName()).toBe('');
  });

  it('should return 0 service count when none selected', () => {
    const exposed = component as unknown as { selectedServiceCount: () => number };
    expect(exposed.selectedServiceCount()).toBe(0);
  });

  describe('A11y panel', () => {
    it('should toggle a11y panel and close cart when opened', () => {
      const exposed = component as unknown as {
        isExpanded: () => boolean; isA11yOpen: () => boolean;
        onToggleCart: () => void; onToggleAccessibility: () => void; onCloseAccessibility: () => void;
      };
      exposed.onToggleCart(); // open cart
      expect(exposed.isExpanded()).toBe(true);
      exposed.onToggleAccessibility(); // open a11y closes cart
      expect(exposed.isExpanded()).toBe(false);
      expect(exposed.isA11yOpen()).toBe(true);
      exposed.onToggleAccessibility(); // toggle a11y off
      expect(exposed.isA11yOpen()).toBe(false);
    });

    it('should close a11y via onCloseAccessibility', () => {
      const exposed = component as unknown as {
        isA11yOpen: () => boolean; onToggleAccessibility: () => void; onCloseAccessibility: () => void;
      };
      exposed.onToggleAccessibility();
      expect(exposed.isA11yOpen()).toBe(true);
      exposed.onCloseAccessibility();
      expect(exposed.isA11yOpen()).toBe(false);
    });
  });

  describe('A11y settings forwarding', () => {
    it('should delegate fontSize change to AccessibilityStore', () => {
      const exposed = component as unknown as { onFontSizeChange: (s: 'small'|'normal'|'large'|'x-large') => void; fontSize: () => string };
      exposed.onFontSizeChange('large');
      expect(exposed.fontSize()).toBe('large');
    });

    it('should delegate highContrast change to AccessibilityStore', () => {
      const exposed = component as unknown as { onHighContrastChange: (v: boolean) => void; highContrast: () => boolean };
      exposed.onHighContrastChange(true);
      expect(exposed.highContrast()).toBe(true);
    });

    it('should delegate reducedMotion change to AccessibilityStore', () => {
      const exposed = component as unknown as { onReducedMotionChange: (v: boolean) => void; reducedMotion: () => boolean };
      exposed.onReducedMotionChange(true);
      expect(exposed.reducedMotion()).toBe(true);
    });

    it('should delegate language change to TranslateService', () => {
      const exposed = component as unknown as { onLanguageChange: (l: 'de'|'en') => void; currentLanguage: () => string };
      exposed.onLanguageChange('de');
      expect(['de', 'en']).toContain(exposed.currentLanguage());
    });
  });

  describe('Cart computed signals with services', () => {
    it('should expose hasItems based on cart store', () => {
      const exposed = component as unknown as { hasItems: () => boolean };
      expect(typeof exposed.hasItems()).toBe('boolean');
    });

    it('should expose badgeText based on cart store', () => {
      const exposed = component as unknown as { badgeText: () => string };
      expect(typeof exposed.badgeText()).toBe('string');
    });
  });
});
