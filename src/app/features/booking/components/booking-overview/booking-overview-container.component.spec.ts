import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import type { AppointmentSlot } from '../../models/appointment.model';
import type { CustomerInfo, VehicleInfo } from '../../models/customer.model';
import type { LocationDisplay } from '../../models/location.model';
import { AppointmentApiService } from '../../services/appointment-api.service';
import { BookingApiService } from '../../services/booking-api.service';
import { WorkshopCalendarApiService } from '../../services/workshop-calendar-api.service';
import { BookingStore } from '../../stores/booking.store';

import { BookingOverviewContainerComponent } from './booking-overview-container.component';

const MOCK_APPOINTMENT: AppointmentSlot = {
  id: '2026-03-15-10-00',
  date: '2026-03-15',
  displayDate: '15.03.2026',
  dayAbbreviation: 'Mo',
  time: '10:00',
  displayTime: '10:00 Uhr'
};

const MOCK_LOCATION: LocationDisplay = {
  id: 'loc-1',
  name: 'Autohaus München',
  city: 'München',
  formattedAddress: 'München'
};

const MOCK_CUSTOMER_INFO: CustomerInfo = {
  email: 'max@example.com',
  salutation: 'mr',
  firstName: 'Max',
  lastName: 'Mustermann',
  street: 'Musterstr. 1',
  postalCode: '80331',
  city: 'München',
  mobilePhone: '+49 170 1234567'
};

const MOCK_VEHICLE_INFO: VehicleInfo = {
  licensePlate: 'M-AB 1234',
  mileage: 45000
};

describe('BookingOverviewContainerComponent', () => {
  let component: BookingOverviewContainerComponent;
  let fixture: ComponentFixture<BookingOverviewContainerComponent>;
  let store: InstanceType<typeof BookingStore>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookingOverviewContainerComponent],
      providers: [
        provideRouter([]),
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
            createAppointment: jest.fn().mockResolvedValue({ id: 'test-apt-1', status: 'confirmed' }),
            getAppointment: jest.fn().mockResolvedValue({ id: 'test-apt-1', status: 'confirmed' })
          }
        },
        {
          provide: WorkshopCalendarApiService,
          useValue: {
            getWorkshopSlots: jest.fn().mockResolvedValue([])
          }
        }
      ]
    })
      .overrideComponent(BookingOverviewContainerComponent, {
        set: { template: '<div class="mocked">Mocked BookingOverviewContainer</div>' }
      })
      .compileComponents();

    store = TestBed.inject(BookingStore);
    router = TestBed.inject(Router);

    fixture = TestBed.createComponent(BookingOverviewContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose appointment from store', () => {
    const exposed = component as unknown as { appointment: () => AppointmentSlot | null };
    expect(exposed.appointment()).toBeNull();

    store.selectAppointment(MOCK_APPOINTMENT);
    expect(exposed.appointment()).toEqual(MOCK_APPOINTMENT);
  });

  it('should expose customerInfo from store', () => {
    const exposed = component as unknown as { customerInfo: () => CustomerInfo | null };
    expect(exposed.customerInfo()).toBeNull();

    store.setCustomerInfo(MOCK_CUSTOMER_INFO);
    expect(exposed.customerInfo()).toEqual(MOCK_CUSTOMER_INFO);
  });

  it('should expose vehicleInfo from store', () => {
    const exposed = component as unknown as { vehicleInfo: () => VehicleInfo | null };
    expect(exposed.vehicleInfo()).toBeNull();

    store.setVehicleInfo(MOCK_VEHICLE_INFO);
    expect(exposed.vehicleInfo()).toEqual(MOCK_VEHICLE_INFO);
  });

  it('should resolve brand name from selectedBrand', () => {
    const exposed = component as unknown as { resolvedBrandName: () => string };
    expect(exposed.resolvedBrandName()).toBe('');

    store.setBrand('audi');
    expect(exposed.resolvedBrandName()).toBe('Audi');
  });

  it('should resolve location name from selectedLocation', () => {
    const exposed = component as unknown as { resolvedLocationName: () => string };
    expect(exposed.resolvedLocationName()).toBe('');

    store.setLocation(MOCK_LOCATION);
    expect(exposed.resolvedLocationName()).toBe('München');
  });

  it('should compute resolved service labels', () => {
    const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
    expect(Object.keys(exposed.resolvedServiceLabels()).length).toBe(0);

    store.toggleService('tuv');
    const labels = exposed.resolvedServiceLabels();
    expect(labels.tuv).toBeDefined();
    expect(typeof labels.tuv).toBe('string');
  });

  it('should navigate back to carinformation on onBack()', () => {
    const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true);

    const exposed = component as unknown as { onBack: () => void };
    exposed.onBack();

    expect(navigateSpy).toHaveBeenCalledWith(['/home/carinformation']);
  });

  it('should call submitBooking on onSubmit()', () => {
    const submitSpy = jest.spyOn(store, 'submitBooking');

    const exposed = component as unknown as { onSubmit: () => void };
    exposed.onSubmit();

    expect(submitSpy).toHaveBeenCalled();
  });

  it('should handle unknown brand gracefully', () => {
    const exposed = component as unknown as { resolvedBrandName: () => string };
    store.setBrand('unknown-brand' as never);
    expect(exposed.resolvedBrandName()).toBe('unknown-brand');
  });

  it('should handle services with variants', () => {
    const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
    store.toggleService('tire-change');
    store.confirmServiceOptions('tire-change', ['stored-tires']);

    const labels = exposed.resolvedServiceLabels();
    expect(labels['tire-change']).toBeDefined();
    expect(labels['tire-change']).toContain('—');
  });

  it('should expose isSubmitting and hasSubmitted signals', () => {
    const exposed = component as unknown as {
      isSubmitting: () => boolean;
      hasSubmitted: () => boolean;
      submitError: () => boolean;
    };
    expect(exposed.isSubmitting()).toBe(false);
    expect(exposed.hasSubmitted()).toBe(false);
    expect(exposed.submitError()).toBe(false);
  });

  it('should call resetBookingSubmission and submitBooking on retry', () => {
    const resetSpy = jest.spyOn(store, 'resetBookingSubmission');
    const submitSpy = jest.spyOn(store, 'submitBooking');
    const exposed = component as unknown as { onRetry: () => void };
    exposed.onRetry();
    expect(resetSpy).toHaveBeenCalled();
    expect(submitSpy).toHaveBeenCalled();
  });

  // REQ-028: Bestätigungsnachricht unter dem Submit-Button. Die Sichtbarkeit wird
  // ausschließlich durch das bestehende hasSubmitted()-Signal gesteuert; das DOM-
  // Rendering (role/aria-live, Position) wird via Playwright E2E abgedeckt.
  describe('REQ-028 confirmation message', () => {
    it('exposes the confirmation i18n key via the bookingOverview alias', () => {
      const exposed = component as unknown as {
        bookingOverview: { confirmation: { message: string } };
      };
      expect(exposed.bookingOverview.confirmation.message).toBe(
        'booking.bookingOverview.confirmation.message'
      );
    });

    it('does not flag hasSubmitted() before submission', () => {
      const exposed = component as unknown as { hasSubmitted: () => boolean };
      expect(exposed.hasSubmitted()).toBe(false);
    });

    it('flags hasSubmitted() === true after a successful submission (drives the confirmation)', async () => {
      const exposed = component as unknown as {
        hasSubmitted: () => boolean;
        submitError: () => boolean;
        isSubmitting: () => boolean;
      };
      expect(exposed.hasSubmitted()).toBe(false);

      store.submitBooking();
      await new Promise<void>((resolve) => setTimeout(resolve, 0));

      expect(exposed.hasSubmitted()).toBe(true);
      // Bestätigung erscheint nur im reinen Erfolgs-Zustand (nicht Loading/Error).
      expect(exposed.isSubmitting()).toBe(false);
      expect(exposed.submitError()).toBe(false);
    });

    it('hides the confirmation again after resetBookingSubmission()', async () => {
      const exposed = component as unknown as { hasSubmitted: () => boolean };

      store.submitBooking();
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
      expect(exposed.hasSubmitted()).toBe(true);

      store.resetBookingSubmission();
      expect(exposed.hasSubmitted()).toBe(false);
    });
  });

  describe('resolvedServiceLabels — additional pathways', () => {
    it('uses Operation name when service is an Operation', () => {
      // simulate operations are loaded into store
      jest.spyOn(store, 'operations').mockReturnValue([
        { id: 'op-A', name: 'Inspektion (Operation)', description: 'desc', tagIds: ['c1'], order: 1, imageId: null }
      ]);
      // toggle a fake "operation" service-id
      store.toggleService('op-A' as never);
      const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
      expect(exposed.resolvedServiceLabels()['op-A']).toBe('Inspektion (Operation)');
    });

    it('uses jobType name when no Operation matches but jobType does', () => {
      jest.spyOn(store, 'operations').mockReturnValue([]);
      jest.spyOn(store, 'jobTypes').mockReturnValue([
        { id: 'jt-1', name: 'Reparatur', operations: [{ id: 'op-r1', name: 'Lichtwechsel' }], iconUrl: null }
      ] as never);
      store.toggleService('jt-1' as never);
      store.confirmServiceOptions('jt-1' as never, ['op-r1']);
      const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
      const lbl = exposed.resolvedServiceLabels()['jt-1'];
      expect(lbl).toContain('Reparatur');
      expect(lbl).toContain('Lichtwechsel');
    });

    it('renders a jobType without any selected options as just its name', () => {
      jest.spyOn(store, 'operations').mockReturnValue([]);
      jest.spyOn(store, 'jobTypes').mockReturnValue([
        { id: 'jt-plain', name: 'Inspektion', operations: [], iconUrl: null }
      ] as never);
      store.toggleService('jt-plain' as never);
      // Keine confirmServiceOptions → selectedOptionIds bleibt leer.
      const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
      expect(exposed.resolvedServiceLabels()['jt-plain']).toBe('Inspektion');
    });

    it('handles a jobType whose selected option ids do not resolve to operations', () => {
      jest.spyOn(store, 'operations').mockReturnValue([]);
      jest.spyOn(store, 'jobTypes').mockReturnValue([
        { id: 'jt-x', name: 'Karosserie', operations: [], iconUrl: null }
      ] as never);
      store.toggleService('jt-x' as never);
      store.confirmServiceOptions('jt-x' as never, ['ghost-option']);
      const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
      // Unbekannte Option-Ids werden herausgefiltert → kein " — "-Suffix, nur der Name.
      expect(exposed.resolvedServiceLabels()['jt-x']).toBe('Karosserie');
    });

    it('ignores unknown option ids in the AVAILABLE_SERVICES fallback path', () => {
      jest.spyOn(store, 'operations').mockReturnValue([]);
      jest.spyOn(store, 'jobTypes').mockReturnValue([]);
      store.toggleService('tire-change');
      store.confirmServiceOptions('tire-change', ['nonexistent-option']);
      const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
      const label = exposed.resolvedServiceLabels()['tire-change'];
      expect(label).toBeDefined();
      // Option nicht auflösbar → kein angehängter Options-Teil.
      expect(label).not.toContain('—');
    });

    it('falls back to the raw serviceId when no source matches', () => {
      // Defensive fallback (Bug-Fix): wenn weder operations, jobTypes noch
      // AVAILABLE_SERVICES die ID auflösen koennen, muss wenigstens die ID
      // selbst als Label gerendert werden — sonst wirkt die Auswahl optisch
      // verschwunden (Bug: leere Services-Kachel auf der Übersicht).
      jest.spyOn(store, 'operations').mockReturnValue([]);
      jest.spyOn(store, 'jobTypes').mockReturnValue([]);
      store.toggleService('totally-unknown-id' as never);
      const exposed = component as unknown as { resolvedServiceLabels: () => Record<string, string> };
      expect(exposed.resolvedServiceLabels()['totally-unknown-id']).toBe('totally-unknown-id');
    });
  });

  describe('resolvedLocationName edge cases', () => {
    it('returns empty string when no location selected', () => {
      const exposed = component as unknown as { resolvedLocationName: () => string };
      expect(exposed.resolvedLocationName()).toBe('');
    });
  });
});
