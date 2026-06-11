import { TestBed } from '@angular/core/testing';

import type { AppointmentSlot } from '../models/appointment.model';
import { AVAILABLE_BRANDS } from '../models/brand.model';
import { ALL_LOCATIONS,LOCATIONS_BY_BRAND } from '../models/location.model';
import { AVAILABLE_SERVICES } from '../models/service.model';
import { AppointmentApiService } from '../services/appointment-api.service';
import { BookingApiService } from '../services/booking-api.service';
import { RentCategoryApiService } from '../services/rent-category-api.service';

import { BookingStore, mapAppointmentPreferenceToSoftnet } from './booking.store';

describe('BookingStore', () => {
  let store: InstanceType<typeof BookingStore>;
  let apiSpy: jest.Mocked<BookingApiService>;

  beforeEach(() => {
    apiSpy = {
      getBrands: jest.fn().mockResolvedValue(AVAILABLE_BRANDS),
      getBrandsByBranch: jest.fn().mockResolvedValue(AVAILABLE_BRANDS),
      getLocations: jest.fn().mockResolvedValue(ALL_LOCATIONS),
      getBranchById: jest.fn().mockResolvedValue(null),
      getBranches: jest.fn().mockResolvedValue([]),
      getServices: jest.fn().mockResolvedValue(AVAILABLE_SERVICES),
      getBrandLogoPath: jest.fn().mockReturnValue('assets/brands/default.svg')
    } as unknown as jest.Mocked<BookingApiService>;

    TestBed.configureTestingModule({
      providers: [
        BookingStore,
        { provide: BookingApiService, useValue: apiSpy },
        { provide: AppointmentApiService, useValue: { getAppointments: jest.fn().mockResolvedValue([]), createAppointment: jest.fn().mockResolvedValue({ id: 'test-apt-1', status: 'confirmed' }), getAppointment: jest.fn().mockResolvedValue({ id: 'test-apt-1', status: 'confirmed' }) } },
        { provide: RentCategoryApiService, useValue: { getRentCategories: jest.fn().mockResolvedValue([]) } }
      ]
    });

    store = TestBed.inject(BookingStore);
  });

  describe('Initial State', () => {
    it('should have empty brands', () => {
      expect(store.brands()).toEqual([]);
    });

    it('should have no selectedBrand', () => {
      expect(store.selectedBrand()).toBeNull();
    });

    it('should have empty locations', () => {
      expect(store.locations()).toEqual([]);
    });

    it('should have no selectedLocation', () => {
      expect(store.selectedLocation()).toBeNull();
    });

    it('should not be loading', () => {
      expect(store.isLoading()).toBe(false);
    });

    it('should have no error', () => {
      expect(store.error()).toBeNull();
    });
  });

  describe('Computed', () => {
    it('should compute hasBrandSelected as false initially', () => {
      expect(store.hasBrandSelected()).toBe(false);
    });

    it('should compute hasLocationSelected as false initially', () => {
      expect(store.hasLocationSelected()).toBe(false);
    });
  });

  describe('setBrand', () => {
    it('should set selectedBrand', () => {
      store.setBrand('bmw');
      expect(store.selectedBrand()).toBe('bmw');
    });

    it('should update hasBrandSelected to true', () => {
      store.setBrand('audi');
      expect(store.hasBrandSelected()).toBe(true);
    });
  });

  describe('setLocation', () => {
    it('should set selectedLocation', () => {
      const location = LOCATIONS_BY_BRAND.audi[0];
      store.setLocation(location);
      expect(store.selectedLocation()).toEqual(location);
    });

    it('should update hasLocationSelected to true', () => {
      store.setLocation(LOCATIONS_BY_BRAND.audi[0]);
      expect(store.hasLocationSelected()).toBe(true);
    });
  });

  describe('loadBrands', () => {
    it('should call API service', () => {
      store.loadBrands();
      expect(apiSpy.getBrands).toHaveBeenCalled();
    });

    it('should load brands from API', async () => {
      store.loadBrands();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(store.brands()).toEqual(AVAILABLE_BRANDS);
      expect(store.isLoading()).toBe(false);
    });

    it('should handle API error with Error instance', async () => {
      apiSpy.getBrands.mockRejectedValueOnce(new Error('Network error'));
      store.loadBrands();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(store.error()).toBe('Network error');
      expect(store.isLoading()).toBe(false);
    });

    it('should handle API error with non-Error value', async () => {
      apiSpy.getBrands.mockRejectedValueOnce('string error');
      store.loadBrands();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(store.error()).toBe('Unknown error');
      expect(store.isLoading()).toBe(false);
    });
  });

  describe('loadLocations', () => {
    it('should call getLocations API', async () => {
      store.loadLocations();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(apiSpy.getLocations).toHaveBeenCalled();
    });

    it('should load locations from API', async () => {
      store.loadLocations();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(store.locations()).toEqual(ALL_LOCATIONS);
      expect(store.isLoading()).toBe(false);
    });

    it('should handle API error with Error instance', async () => {
      apiSpy.getLocations.mockRejectedValueOnce(new Error('Location error'));
      store.loadLocations();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(store.error()).toBe('Location error');
      expect(store.isLoading()).toBe(false);
    });

    it('should handle API error with non-Error value', async () => {
      apiSpy.getLocations.mockRejectedValueOnce(42);
      store.loadLocations();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(store.error()).toBe('Unknown error');
      expect(store.isLoading()).toBe(false);
    });
  });

  describe('Services', () => {
    it('should have empty services initially', () => {
      expect(store.services()).toEqual([]);
      expect(store.selectedServices()).toEqual([]);
    });

    it('should compute selectedServiceCount as 0 initially', () => {
      expect(store.selectedServiceCount()).toBe(0);
    });

    it('should compute hasServicesSelected as false initially', () => {
      expect(store.hasServicesSelected()).toBe(false);
    });

    it('should load services from API', async () => {
      store.loadServices();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(store.services()).toEqual(AVAILABLE_SERVICES);
      expect(apiSpy.getServices).toHaveBeenCalled();
    });

    it('should handle loadServices API error with Error instance', async () => {
      apiSpy.getServices.mockRejectedValueOnce(new Error('Service error'));
      store.loadServices();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(store.error()).toBe('Service error');
      expect(store.isLoading()).toBe(false);
    });

    it('should handle loadServices API error with non-Error value', async () => {
      apiSpy.getServices.mockRejectedValueOnce(null);
      store.loadServices();
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(store.error()).toBe('Unknown error');
      expect(store.isLoading()).toBe(false);
    });

    it('should toggle service on', () => {
      store.toggleService('tuv');
      expect(store.selectedServices()).toEqual([{ serviceId: 'tuv', selectedOptionIds: [] }]);
      expect(store.selectedServiceCount()).toBe(1);
      expect(store.hasServicesSelected()).toBe(true);
    });

    it('should toggle service off', () => {
      store.toggleService('tuv');
      store.toggleService('tuv');
      expect(store.selectedServices()).toEqual([]);
      expect(store.selectedServiceCount()).toBe(0);
    });

    it('should support multi-select', () => {
      store.toggleService('tuv');
      store.toggleService('inspection');
      expect(store.selectedServiceCount()).toBe(2);
    });

    it('should confirm service options', () => {
      store.confirmServiceOptions('tire-change', ['bring-own-tires']);
      expect(store.selectedServices()).toEqual([
        { serviceId: 'tire-change', selectedOptionIds: ['bring-own-tires'] }
      ]);
    });

    it('should switch service options', () => {
      store.confirmServiceOptions('tire-change', ['bring-own-tires']);
      store.confirmServiceOptions('tire-change', ['stored-tires']);
      expect(store.selectedServices()).toEqual([
        { serviceId: 'tire-change', selectedOptionIds: ['stored-tires'] }
      ]);
    });

    it('should deselect service', () => {
      store.confirmServiceOptions('tire-change', ['bring-own-tires']);
      store.deselectService('tire-change');
      expect(store.selectedServices()).toEqual([]);
    });

    it('should clear selected services', () => {
      store.toggleService('tuv');
      store.toggleService('inspection');
      store.clearSelectedServices();
      expect(store.selectedServices()).toEqual([]);
    });
  });

  describe('clearSelectedAppointment', () => {
    const mockAppointment: AppointmentSlot = {
      id: '2026-02-25-09-00',
      date: '2026-02-25',
      displayDate: '25.02.2026',
      dayAbbreviation: 'Mi',
      time: '09:00',
      displayTime: '09:00 Uhr'
    };

    it('should clear selected appointment', () => {
      store.selectAppointment(mockAppointment);
      expect(store.selectedAppointment()).not.toBeNull();
      store.clearSelectedAppointment();
      expect(store.selectedAppointment()).toBeNull();
    });

    it('should update hasAppointmentSelected to false', () => {
      store.selectAppointment(mockAppointment);
      expect(store.hasAppointmentSelected()).toBe(true);
      store.clearSelectedAppointment();
      expect(store.hasAppointmentSelected()).toBe(false);
    });

    it('should be idempotent when already null', () => {
      expect(store.selectedAppointment()).toBeNull();
      store.clearSelectedAppointment();
      expect(store.selectedAppointment()).toBeNull();
    });
  });

  describe('setCustomerInfo', () => {
    it('should set customerInfo', () => {
      expect(store.customerInfo()).toBeNull();
      store.setCustomerInfo({
        email: 'test@example.com',
        salutation: 'mr',
        firstName: 'Max',
        lastName: 'Mustermann',
        street: 'Musterstraße 1',
        postalCode: '12345',
        city: 'Musterstadt',
        mobilePhone: '+49151123456'
      });
      expect(store.customerInfo()?.email).toBe('test@example.com');
    });
  });

  describe('setVehicleInfo', () => {
    it('should set vehicleInfo', () => {
      expect(store.vehicleInfo()).toBeNull();
      store.setVehicleInfo({ licensePlate: 'B-AB 1234', mileage: 50000 });
      expect(store.vehicleInfo()?.licensePlate).toBe('B-AB 1234');
    });
  });

  describe('setPrivacyConsent', () => {
    it('should set privacyConsent to true', () => {
      expect(store.privacyConsent()).toBe(false);
      store.setPrivacyConsent(true);
      expect(store.privacyConsent()).toBe(true);
    });

    it('should set privacyConsent back to false', () => {
      store.setPrivacyConsent(true);
      store.setPrivacyConsent(false);
      expect(store.privacyConsent()).toBe(false);
    });
  });

  describe('notesExtras', () => {
    it('should have null notesExtras initially', () => {
      expect(store.notesExtras()).toBeNull();
    });

    it('should have hasNotesExtras as false initially', () => {
      expect(store.hasNotesExtras()).toBe(false);
    });

    it('should have default computed values initially', () => {
      expect(store.selectedAppointmentPreference()).toBe('anytime');
      expect(store.selectedCallbackOption()).toBe('no');
      expect(store.callbackRequested()).toBe(false);
      expect(store.selectedNotificationChannel()).toBe('email');
    });

    it('should set notesExtras', () => {
      const extras = {
        appointmentPreference: 'morning' as const,
        callbackOption: 'yes' as const,
        notificationChannel: 'email' as const
      };
      store.setNotesExtras(extras);
      expect(store.notesExtras()).toEqual(extras);
    });

    it('should update hasNotesExtras to true after set', () => {
      store.setNotesExtras({ appointmentPreference: 'anytime', callbackOption: 'no', notificationChannel: 'email' });
      expect(store.hasNotesExtras()).toBe(true);
    });

    it('should update computed signals after set', () => {
      store.setNotesExtras({ appointmentPreference: 'afternoon', callbackOption: 'yes', notificationChannel: 'sms' });
      expect(store.selectedAppointmentPreference()).toBe('afternoon');
      expect(store.selectedCallbackOption()).toBe('yes');
      expect(store.callbackRequested()).toBe(true);
      expect(store.selectedNotificationChannel()).toBe('sms');
    });

    it('should clear notesExtras', () => {
      store.setNotesExtras({ appointmentPreference: 'morning', callbackOption: 'yes', notificationChannel: 'email' });
      store.clearNotesExtras();
      expect(store.notesExtras()).toBeNull();
      expect(store.hasNotesExtras()).toBe(false);
    });

    it('should reset computed signals after clear', () => {
      store.setNotesExtras({ appointmentPreference: 'afternoon', callbackOption: 'yes', notificationChannel: 'sms' });
      store.clearNotesExtras();
      expect(store.selectedAppointmentPreference()).toBe('anytime');
      expect(store.selectedCallbackOption()).toBe('no');
      expect(store.callbackRequested()).toBe(false);
      expect(store.selectedNotificationChannel()).toBe('email');
    });
  });

  describe('RentCategory', () => {
    it('should start with empty rentCategories and no selection', () => {
      expect(store.rentCategories()).toEqual([]);
      expect(store.selectedRentCategoryId()).toBeNull();
    });

    it('should set and clear selectedRentCategoryId', () => {
      store.setSelectedRentCategoryId('rc-1');
      expect(store.selectedRentCategoryId()).toBe('rc-1');
      store.setSelectedRentCategoryId(null);
      expect(store.selectedRentCategoryId()).toBeNull();
    });
  });

  describe('resetBookingSubmission', () => {
    it('should clear bookingError and bookingSubmitted', () => {
      store.resetBookingSubmission();
      expect(store.hasBookingError()).toBe(false);
      expect(store.hasBookingSubmitted()).toBe(false);
    });
  });

  describe('selectedServiceLabels', () => {
    it('returns empty list when no services are selected', () => {
      expect(store.selectedServiceLabels()).toEqual([]);
    });

    it('returns each selected sub-option as a separate label', () => {
      store.confirmServiceOptions('inspection', ['inspection', 'oil-change', 'brake-check']);
      const labels = store.selectedServiceLabels();
      expect(labels).toHaveLength(3);
      expect(labels).toContain('booking.services.inspection.options.inspection');
      expect(labels).toContain('booking.services.inspection.options.oilChange');
      expect(labels).toContain('booking.services.inspection.options.brakeCheck');
    });

    it('uses the service title when no sub-options are selected', () => {
      store.toggleService('brake-fluid'); // keine options
      const labels = store.selectedServiceLabels();
      expect(labels).toHaveLength(1);
      expect(labels[0]).toBe('booking.services.brakeFluid.title');
    });
  });

  describe('mapAppointmentPreferenceToSoftnet', () => {
    it('maps morning to AM', () => {
      expect(mapAppointmentPreferenceToSoftnet('morning')).toBe('AM');
    });

    it('maps afternoon to PM', () => {
      expect(mapAppointmentPreferenceToSoftnet('afternoon')).toBe('PM');
    });

    it('maps anytime to undefined (filter omitted)', () => {
      expect(mapAppointmentPreferenceToSoftnet('anytime')).toBeUndefined();
    });
  });

  describe('selectedRentCategoryName', () => {
    it('returns null when no rent category is selected', () => {
      expect(store.selectedRentCategoryName()).toBeNull();
    });
  });

  describe('callbackRequested computed', () => {
    it('is false initially', () => {
      expect(store.callbackRequested()).toBe(false);
    });

    it('is true when callbackOption is yes', () => {
      store.setNotesExtras({ appointmentPreference: 'anytime', callbackOption: 'yes', notificationChannel: 'email' });
      expect(store.callbackRequested()).toBe(true);
    });

    it('is false when callbackOption is none', () => {
      store.setNotesExtras({ appointmentPreference: 'anytime', callbackOption: 'no', notificationChannel: 'email' });
      expect(store.callbackRequested()).toBe(false);
    });
  });

  describe('selectedNotificationChannel (REQ-027)', () => {
    it('defaults to "email" when no extras are set', () => {
      expect(store.selectedNotificationChannel()).toBe('email');
    });

    it('reflects the configured channel after setNotesExtras', () => {
      store.setNotesExtras({ appointmentPreference: 'anytime', callbackOption: 'no', notificationChannel: 'sms' });
      expect(store.selectedNotificationChannel()).toBe('sms');
    });

    it('falls back to "email" after clearNotesExtras', () => {
      store.setNotesExtras({ appointmentPreference: 'anytime', callbackOption: 'no', notificationChannel: 'sms' });
      store.clearNotesExtras();
      expect(store.selectedNotificationChannel()).toBe('email');
    });
  });

  describe('REQ-025 Tag/Operation Loaders', () => {
    it('loadJobTypesForBranch sets jobTypes on success', async () => {
      const jts = [{ id: 'jt1', name: 'Test', operations: [] }];
      apiSpy.getJobTypesByBranch = jest.fn().mockResolvedValue(jts);
      store.loadJobTypesForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
      expect(store.jobTypes()).toEqual(jts);
    });

    it('loadJobTypesForBranch handles Error rejection', async () => {
      apiSpy.getJobTypesByBranch = jest.fn().mockRejectedValue(new Error('boom'));
      store.loadJobTypesForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
      expect(store.error()).toBe('boom');
    });

    it('loadJobTypesForBranch handles non-Error rejection', async () => {
      apiSpy.getJobTypesByBranch = jest.fn().mockRejectedValue('string-error');
      store.loadJobTypesForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
      expect(store.error()).toBeTruthy();
    });

    it('loadCategoriesForBranch sets categories on success', async () => {
      const cats = [{ id: 'c1', name: 'Test', rank: 0 }];
      apiSpy.getCategoriesByBranch = jest.fn().mockResolvedValue(cats);
      store.loadCategoriesForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
      expect(store.categories()).toEqual(cats);
    });

    it('loadCategoriesForBranch handles error', async () => {
      apiSpy.getCategoriesByBranch = jest.fn().mockRejectedValue(new Error('cat-fail'));
      store.loadCategoriesForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
      expect(store.error()).toBe('cat-fail');
    });

    it('loadCategoriesForBranch handles non-Error rejection', async () => {
      apiSpy.getCategoriesByBranch = jest.fn().mockRejectedValue('boom');
      store.loadCategoriesForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
      expect(store.error()).toBeTruthy();
    });

    it('loadOperationsForBranch sets operations on success', async () => {
      const ops = [{ id: 'op1', name: 'X', description: null, tagIds: ['c1'], order: 1, imageId: null }];
      apiSpy.getOperationsByBranch = jest.fn().mockResolvedValue(ops);
      store.loadOperationsForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
      expect(store.operations()).toEqual(ops);
    });

    it('loadOperationsForBranch handles Error rejection', async () => {
      apiSpy.getOperationsByBranch = jest.fn().mockRejectedValue(new Error('op-fail'));
      store.loadOperationsForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
      expect(store.error()).toBe('op-fail');
    });

    it('loadOperationsForBranch handles non-Error rejection', async () => {
      apiSpy.getOperationsByBranch = jest.fn().mockRejectedValue(undefined);
      store.loadOperationsForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
      expect(store.error()).toBeTruthy();
    });
  });

  describe('Service Toggle / Confirm / Deselect', () => {
    it('toggleService adds and removes a service', () => {
      store.toggleService('inspection');
      expect(store.selectedServices().some(s => s.serviceId === 'inspection')).toBe(true);
      store.toggleService('inspection');
      expect(store.selectedServices().some(s => s.serviceId === 'inspection')).toBe(false);
    });

    it('confirmServiceOptions sets selectedOptionIds for a service', () => {
      store.toggleService('inspection');
      store.confirmServiceOptions('inspection', ['opt-a']);
      const entry = store.selectedServices().find(s => s.serviceId === 'inspection');
      expect(entry?.selectedOptionIds).toEqual(['opt-a']);
    });

    it('deselectService removes from selectedServices', () => {
      store.toggleService('inspection');
      store.deselectService('inspection');
      expect(store.selectedServices().some(s => s.serviceId === 'inspection')).toBe(false);
    });
  });

});
