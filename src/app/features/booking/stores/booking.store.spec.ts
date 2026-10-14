import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import type { AppointmentSlot } from '../models/appointment.model';
import type { BranchConfig } from '../models/branch-config.model';
import { AVAILABLE_BRANDS, type Brand } from '../models/brand.model';
import { ALL_LOCATIONS,LOCATIONS_BY_BRAND } from '../models/location.model';
import { AVAILABLE_SERVICES, type ServiceType } from '../models/service.model';
import { AppointmentApiService } from '../services/appointment-api.service';
import { BookingApiService } from '../services/booking-api.service';
import { RentCategoryApiService } from '../services/rent-category-api.service';
import { WorkshopCalendarApiService } from '../services/workshop-calendar-api.service';

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
        { provide: RentCategoryApiService, useValue: { getRentCategories: jest.fn().mockResolvedValue([]) } },
        { provide: WorkshopCalendarApiService, useValue: { getWorkshopCalendarDays: jest.fn().mockResolvedValue([]) } }
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

    it('should have no snap conflict (REQ-031)', () => {
      expect(store.snapConflict()).toBe(false);
      expect(store.hasSnapConflict()).toBe(false);
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

  // REQ-040: FIN nur bei "Räder sind eingelagert" — Erkennung über den Operations-Namen
  // (serviceId trägt im Live-Flow die Operation-ID, nicht das ServiceType-Enum).
  describe('hasStoredTiresService (REQ-040)', () => {
    const STORED_TIRES_DEFAULT = { id: 'op-stored-default', name: 'Räderwechsel - meine Räder sind eingelagert', description: null, tagIds: ['c1'], order: 1, imageId: null };
    const STORED_TIRES_BOXENSTOPP = { id: 'op-stored-box', name: 'Radwechsel mit Einlagerung', description: null, tagIds: ['c1'], order: 2, imageId: null };
    const STORE_TIRES = { id: 'op-store-tires', name: 'Die angelieferten Räder sollen bitte eingelagert werden', description: null, tagIds: ['c1'], order: 3, imageId: null };
    const INSPECTION = { id: 'op-inspection', name: 'Inspektion', description: null, tagIds: ['c1'], order: 4, imageId: null };

    async function loadOperations(): Promise<void> {
      apiSpy.getOperationsByBranch = jest.fn().mockResolvedValue([
        STORED_TIRES_DEFAULT, STORED_TIRES_BOXENSTOPP, STORE_TIRES, INSPECTION
      ]);
      store.loadOperationsForBranch('b1');
      await new Promise(r => setTimeout(r, 0));
    }

    function select(operationId: string): void {
      store.confirmServiceOptions(operationId as ServiceType, []);
    }

    it('is false when nothing is selected', () => {
      expect(store.hasStoredTiresService()).toBe(false);
    });

    it('is true for the default "Räder sind eingelagert" operation', async () => {
      await loadOperations();
      select(STORED_TIRES_DEFAULT.id);
      expect(store.hasStoredTiresService()).toBe(true);
    });

    it('is true for the Boxenstopp "Radwechsel mit Einlagerung" operation', async () => {
      await loadOperations();
      select(STORED_TIRES_BOXENSTOPP.id);
      expect(store.hasStoredTiresService()).toBe(true);
    });

    it('is false for the store-tires operation (angelieferte Räder einlagern)', async () => {
      await loadOperations();
      select(STORE_TIRES.id);
      expect(store.hasStoredTiresService()).toBe(false);
    });

    it('is false for an unrelated service (Inspektion)', async () => {
      await loadOperations();
      select(INSPECTION.id);
      expect(store.hasStoredTiresService()).toBe(false);
    });

    it('is false while operations are not loaded yet (name cannot be resolved)', () => {
      select(STORED_TIRES_DEFAULT.id);
      expect(store.hasStoredTiresService()).toBe(false);
    });
  });

  // REQ-041: recall campaign mode — own SoftNet group/variant, single auto-selected operation.
  describe('recall mode (REQ-041)', () => {
    const RECALL_GROUP_ID = '6ac7939ba4c0acee699905a8';
    const BRANCH: BranchConfig = {
      branchId: 'b-recall', name: 'Test Branch', address: { street: 'S', zip: '1', city: 'C' }, brands: []
    };
    const RECALL_OP = { id: 'op-recall', name: 'Rückrufaktion Lenkungsgetriebe Schraube', description: null, tagIds: ['c1'], order: 1, imageId: null };

    it('activateRecallMode sets mode, normalized VIN, the recall group and variant', () => {
      store.activateRecallMode('wvwzzz1jz3w386752');
      expect(store.isRecallMode()).toBe(true);
      expect(store.recallVin()).toBe('WVWZZZ1JZ3W386752');
      expect(store.deepLinkGroupId()).toBe(RECALL_GROUP_ID);
      expect(store.selectedVariant()).toBe('recall');
    });

    it('activateRecallMode rejects a malformed URL VIN (recallVin = null)', () => {
      store.activateRecallMode('too-short');
      expect(store.isRecallMode()).toBe(true);
      expect(store.recallVin()).toBeNull();
    });

    it('activateRecallMode without a VIN keeps recallVin null', () => {
      store.activateRecallMode(null);
      expect(store.recallVin()).toBeNull();
    });

    it('applyRecallService loads the recall group operation and auto-selects it', async () => {
      apiSpy.getOperationsByBranch = jest.fn().mockResolvedValue([RECALL_OP]);
      store.selectBranch(BRANCH);
      store.activateRecallMode('WVWZZZ1JZ3W386752');
      await store.applyRecallService();
      expect(apiSpy.getOperationsByBranch).toHaveBeenCalledWith('b-recall', RECALL_GROUP_ID);
      expect(store.selectedServices()).toEqual([{ serviceId: 'op-recall', selectedOptionIds: [] }]);
      expect(store.hasServicesSelected()).toBe(true);
    });

    it('applyRecallService is a no-op outside recall mode', async () => {
      apiSpy.getOperationsByBranch = jest.fn();
      store.selectBranch(BRANCH);
      await store.applyRecallService();
      expect(apiSpy.getOperationsByBranch).not.toHaveBeenCalled();
      expect(store.hasServicesSelected()).toBe(false);
    });

    it('clearRecallMode resets mode, VIN and the recall group override', () => {
      store.activateRecallMode('WVWZZZ1JZ3W386752');
      store.clearRecallMode();
      expect(store.isRecallMode()).toBe(false);
      expect(store.recallVin()).toBeNull();
      expect(store.deepLinkGroupId()).toBeNull();
      expect(store.selectedVariant()).toBe('default');
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

  describe('submitBooking — REQ-031 snap conflict (HTTP 410)', () => {
    const MOCK_APPOINTMENT: AppointmentSlot = {
      id: '2026-02-25-09-00',
      date: '2026-02-25',
      displayDate: '25.02.2026',
      dayAbbreviation: 'Mi',
      time: '09:00',
      displayTime: '09:00 Uhr'
    };

    let appointmentApi: { createAppointment: jest.Mock };

    beforeEach(() => {
      appointmentApi = TestBed.inject(AppointmentApiService) as unknown as {
        createAppointment: jest.Mock;
      };
    });

    /** Seeds a fully filled wizard so AC-8 (state must survive a 410) can be asserted. */
    function seedWizardState(): void {
      // setLocation resets brand/services/appointment/notesExtras → must run first
      store.setLocation(LOCATIONS_BY_BRAND.audi[0]);
      store.setBrand('audi');
      store.toggleService('tuv');
      store.setBookingNote('Bitte Winterreifen mitbringen');
      store.setNotesExtras({ appointmentPreference: 'morning', callbackOption: 'yes', notificationChannel: 'email' });
      store.selectAppointment(MOCK_APPOINTMENT);
      store.setCustomerInfo({
        email: 'max@example.com',
        salutation: 'mr',
        firstName: 'Max',
        lastName: 'Mustermann',
        street: 'Musterstraße 1',
        postalCode: '80331',
        city: 'München',
        mobilePhone: '017012345678'
      });
      store.setVehicleInfo({ licensePlate: 'M-AB 1234', mileage: 45000 });
      store.setPrivacyConsent(true);
      store.setSelectedRentCategoryId('rc-1');
    }

    async function submitWithRejection(err: unknown): Promise<void> {
      appointmentApi.createAppointment.mockRejectedValueOnce(err);
      store.submitBooking();
      await new Promise<void>(resolve => setTimeout(resolve, 0));
    }

    it('sets snapConflict and clears selectedAppointment on HTTP 410', async () => {
      seedWizardState();
      await submitWithRejection(new HttpErrorResponse({ status: 410, statusText: 'Gone' }));

      expect(store.snapConflict()).toBe(true);
      expect(store.hasSnapConflict()).toBe(true);
      expect(store.selectedAppointment()).toBeNull();
      expect(store.bookingSubmitting()).toBe(false);
      expect(store.bookingSubmitted()).toBe(false);
    });

    it('does NOT set bookingError on HTTP 410 (banner instead of inline error)', async () => {
      seedWizardState();
      await submitWithRejection(new HttpErrorResponse({ status: 410, statusText: 'Gone' }));

      expect(store.bookingError()).toBeNull();
      expect(store.hasBookingError()).toBe(false);
    });

    it('keeps all remaining wizard state untouched on HTTP 410 (AC-8)', async () => {
      seedWizardState();
      await submitWithRejection(new HttpErrorResponse({ status: 410, statusText: 'Gone' }));

      expect(store.customerInfo()?.email).toBe('max@example.com');
      expect(store.vehicleInfo()).toEqual({ licensePlate: 'M-AB 1234', mileage: 45000 });
      expect(store.selectedServices()).toEqual([{ serviceId: 'tuv', selectedOptionIds: [] }]);
      expect(store.selectedBrand()).toBe('audi');
      expect(store.selectedLocation()).toEqual(LOCATIONS_BY_BRAND.audi[0]);
      expect(store.bookingNote()).toBe('Bitte Winterreifen mitbringen');
      expect(store.notesExtras()).toEqual({ appointmentPreference: 'morning', callbackOption: 'yes', notificationChannel: 'email' });
      expect(store.privacyConsent()).toBe(true);
      expect(store.selectedRentCategoryId()).toBe('rc-1');
    });

    it('treats a non-410 HttpErrorResponse as a regular booking error', async () => {
      seedWizardState();
      await submitWithRejection(new HttpErrorResponse({ status: 502, statusText: 'Bad Gateway' }));

      expect(store.snapConflict()).toBe(false);
      expect(store.hasSnapConflict()).toBe(false);
      // HttpErrorResponse implements (but does not extend) Error → generic fallback message
      expect(store.bookingError()).toBe('Booking submission failed');
      expect(store.hasBookingError()).toBe(true);
      expect(store.bookingSubmitting()).toBe(false);
      // non-410 must NOT clear the selected appointment
      expect(store.selectedAppointment()).toEqual(MOCK_APPOINTMENT);
    });

    it('sets bookingError from a generic Error rejection without snap conflict', async () => {
      seedWizardState();
      await submitWithRejection(new Error('server down'));

      expect(store.snapConflict()).toBe(false);
      expect(store.bookingError()).toBe('server down');
      expect(store.bookingSubmitting()).toBe(false);
      expect(store.selectedAppointment()).toEqual(MOCK_APPOINTMENT);
    });

    it('marks bookingSubmitted on success and never flags a snap conflict', async () => {
      seedWizardState();
      store.submitBooking();
      await new Promise<void>(resolve => setTimeout(resolve, 0));

      expect(store.bookingSubmitted()).toBe(true);
      expect(store.appointmentId()).toBe('test-apt-1');
      expect(store.snapConflict()).toBe(false);
      expect(store.bookingError()).toBeNull();
    });

    it('clearSnapConflict resets the flag', async () => {
      seedWizardState();
      await submitWithRejection(new HttpErrorResponse({ status: 410, statusText: 'Gone' }));
      expect(store.snapConflict()).toBe(true);

      store.clearSnapConflict();

      expect(store.snapConflict()).toBe(false);
      expect(store.hasSnapConflict()).toBe(false);
    });

    it('clearSnapConflict is idempotent when no conflict is active', () => {
      expect(store.snapConflict()).toBe(false);
      store.clearSnapConflict();
      expect(store.snapConflict()).toBe(false);
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


  // ─── REQ-039 Abschnitt 18: rental car availability per appointment ───
  describe('rental car (REQ-039)', () => {
    const SLOT: AppointmentSlot = {
      id: 'snap-1', date: '2026-10-16', displayDate: '16.10.2026', dayAbbreviation: 'Fr',
      time: '08:00', displayTime: '08:00 Uhr', rentalCarIncluded: true
    };
    const flush = (): Promise<void> => new Promise<void>(resolve => setTimeout(resolve, 0));

    let appointmentApi: { getAppointments: jest.Mock; createAppointment: jest.Mock };
    let calendarApi: { getWorkshopCalendarDays: jest.Mock };
    let rentCategoryApi: { getRentCategories: jest.Mock };

    beforeEach(() => {
      appointmentApi = TestBed.inject(AppointmentApiService) as unknown as typeof appointmentApi;
      calendarApi = TestBed.inject(WorkshopCalendarApiService) as unknown as typeof calendarApi;
      rentCategoryApi = TestBed.inject(RentCategoryApiService) as unknown as typeof rentCategoryApi;
      store.setLocation(LOCATIONS_BY_BRAND.audi[0]);
      store.setBrand('audi');
    });

    it('defaults to "without rental car"', () => {
      expect(store.rentalCarRequested()).toBe(false);
      expect(store.hasNoRentalCarAppointments()).toBe(false);
    });

    it('setRentalCarRequested drops slots, calendar and selection (AC-M8)', () => {
      store.selectAppointment(SLOT);
      store.setWorkshopCalendarDate('2026-10-16');

      store.setRentalCarRequested(true);

      expect(store.rentalCarRequested()).toBe(true);
      expect(store.selectedAppointment()).toBeNull();
      expect(store.workshopCalendarDate()).toBeNull();
      expect(store.workshopCalendarDays()).toEqual([]);
    });

    it('setRentalCarRequested with the same value keeps the selection', () => {
      store.selectAppointment(SLOT);
      store.setRentalCarRequested(false);
      expect(store.selectedAppointment()).toEqual(SLOT);
    });

    it('loadAppointments passes the rental flag to the API (AC-M1)', async () => {
      store.setRentalCarRequested(true);
      store.loadAppointments();
      await flush();
      expect(appointmentApi.getAppointments.mock.calls.at(-1)?.[6]).toBe(true);
    });

    it('loadAppointments without rental car sends false (AC-M3)', async () => {
      store.loadAppointments();
      await flush();
      expect(appointmentApi.getAppointments.mock.calls.at(-1)?.[6]).toBe(false);
    });

    it('a failed rental call shows the rental empty state, not the generic error (E-2)', async () => {
      store.setRentalCarRequested(true);
      appointmentApi.getAppointments.mockRejectedValueOnce(new Error('500'));
      store.loadAppointments();
      await flush();
      expect(store.error()).toBeNull();
      expect(store.hasNoRentalCarAppointments()).toBe(true);
    });

    it('a failed call without rental car keeps the generic error', async () => {
      appointmentApi.getAppointments.mockRejectedValueOnce(new Error('boom'));
      store.loadAppointments();
      await flush();
      expect(store.error()).toBe('boom');
      expect(store.hasNoRentalCarAppointments()).toBe(false);
    });

    it('loadWorkshopCalendarDays passes the rental flag and maps an empty result (A-2)', async () => {
      store.setRentalCarRequested(true);
      store.loadWorkshopCalendarDays('2026-10-16');
      await flush();
      expect(calendarApi.getWorkshopCalendarDays.mock.calls.at(-1)?.[5]).toBe(true);
      expect(store.hasNoRentalCarCalendarDays()).toBe(true);
    });

    it('a failed rental calendar call shows the rental empty state (E-2)', async () => {
      store.setRentalCarRequested(true);
      calendarApi.getWorkshopCalendarDays.mockRejectedValueOnce(new Error('500'));
      store.loadWorkshopCalendarDays('2026-10-16');
      await flush();
      expect(store.error()).toBeNull();
      expect(store.hasNoRentalCarCalendarDays()).toBe(true);
    });

    it('a failed calendar call without rental car keeps the generic error', async () => {
      calendarApi.getWorkshopCalendarDays.mockRejectedValueOnce(new Error('kaputt'));
      store.loadWorkshopCalendarDays('2026-10-16');
      await flush();
      expect(store.error()).toBe('kaputt');
    });

    it('continueWithoutRentalCar drops wish and slots but keeps the calendar date (E-1)', () => {
      store.setRentalCarRequested(true);
      store.setSelectedRentCategoryId('rc-1');
      store.setWorkshopCalendarDate('2026-10-16');
      store.selectAppointment(SLOT);

      store.continueWithoutRentalCar();

      expect(store.rentalCarRequested()).toBe(false);
      expect(store.selectedRentCategoryId()).toBeNull();
      expect(store.selectedAppointment()).toBeNull();
      expect(store.workshopCalendarDate()).toBe('2026-10-16');
    });

    it('hasNoRentalCarOffer is only true once empty categories are known (AC-M9, no flicker)', async () => {
      expect(store.hasNoRentalCarOffer()).toBe(false);
      store.loadRentCategories();
      await flush();
      expect(store.hasNoRentalCarOffer()).toBe(true);
    });

    it('hasRentalCarOffer follows the loaded categories (BR-M8)', async () => {
      expect(store.hasRentalCarOffer()).toBe(false);
      rentCategoryApi.getRentCategories.mockResolvedValueOnce([
        { id: 'rc-1', groupId: 'g', name: 'Kleinwagen', description: '', brands: [], branches: [] }
      ]);
      store.loadRentCategories();
      await flush();
      expect(store.hasRentalCarOffer()).toBe(true);
      expect(rentCategoryApi.getRentCategories.mock.calls.at(-1)?.[1]).toBe('default');
    });

    it('hasRentalCarOffer is LOCATION-based, not brand-based (fix 2026-10-13)', async () => {
      // A location whose rent category lists a specific brand still offers rental for ALL brands —
      // the backend substitutes a location-valid brand on the SoftNet call. REQ-039 BR-M8 (per-brand
      // gating) was wrong: the dealer lends a brand-neutral pool car.
      rentCategoryApi.getRentCategories.mockResolvedValueOnce([
        { id: 'rc-vw', groupId: 'g', name: 'Kleinwagen', description: '', brands: ['Volkswagen'], branches: [] }
      ]);
      store.loadRentCategories();
      await flush();

      store.setBrand('volkswagen');
      expect(store.hasRentalCarOffer()).toBe(true);
      expect(store.hasNoRentalCarOffer()).toBe(false);
      expect(store.rentCategoriesForBrand().map(category => category.id)).toEqual(['rc-vw']);

      // A brand that is NOT on the category (the SEAT/Nutzfahrzeuge case) at the same location:
      // switch stays available, categories stay listed — the backend substitutes the brand.
      store.setBrand('bmw');
      expect(store.hasRentalCarOffer()).toBe(true);
      expect(store.hasNoRentalCarOffer()).toBe(false);
      expect(store.rentCategoriesForBrand().map(category => category.id)).toEqual(['rc-vw']);
    });

    it('offers rental for any brand once the location has a category', async () => {
      rentCategoryApi.getRentCategories.mockResolvedValueOnce([
        { id: 'rc-all', groupId: 'g', name: 'Kleinwagen', description: '', brands: [], branches: [] }
      ]);
      store.loadRentCategories();
      await flush();
      store.setBrand('audi');
      expect(store.hasRentalCarOffer()).toBe(true);
      expect(store.rentCategoriesForBrand().map(category => category.id)).toEqual(['rc-all']);
    });

    it('failing categories hide the switch instead of raising an error (E-5)', async () => {
      rentCategoryApi.getRentCategories.mockRejectedValueOnce(new Error('403'));
      store.loadRentCategories();
      await flush();
      expect(store.hasRentalCarOffer()).toBe(false);
      expect(store.error()).toBeNull();
    });

    it('resetFromStep(<= 3) resets the rental choice (7.2 addition)', () => {
      store.setRentalCarRequested(true);
      store.resetFromStep(3);
      expect(store.rentalCarRequested()).toBe(false);
    });

    it('resetFromStep(4) keeps the rental choice', () => {
      store.setRentalCarRequested(true);
      store.resetFromStep(4);
      expect(store.rentalCarRequested()).toBe(true);
    });

    it('submitBooking sends the category wish only with a rental car (BR-M5)', async () => {
      store.selectAppointment(SLOT);
      store.setSelectedRentCategoryId('rc-1');
      store.submitBooking();
      await flush();
      expect(appointmentApi.createAppointment.mock.calls.at(-1)?.[0].rentCategoryId).toBeUndefined();

      store.setRentalCarRequested(true);
      store.selectAppointment(SLOT);
      store.submitBooking();
      await flush();
      expect(appointmentApi.createAppointment.mock.calls.at(-1)?.[0].rentCategoryId).toBe('rc-1');
    });
  });

  // Bugfix: multi-word brands (e.g. "Volkswagen Nutzfahrzeuge") must reach SoftNet as the real
  // display name, not the hyphenated slug — otherwise snaps return HTTP 400 (zero appointments).
  describe('loadAppointments brand name', () => {
    it('sends the real brand display name, not the slug, to the snaps API', async () => {
      const appointmentApi = TestBed.inject(AppointmentApiService) as jest.Mocked<AppointmentApiService>;
      apiSpy.getBrands.mockResolvedValueOnce([
        { id: 'volkswagen-nutzfahrzeuge' as unknown as Brand, name: 'Volkswagen Nutzfahrzeuge' }
      ]);
      store.loadBrands();
      await new Promise(resolve => setTimeout(resolve, 0));

      store.setBrand('volkswagen-nutzfahrzeuge' as unknown as Brand);
      store.loadAppointments();
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(appointmentApi.getAppointments).toHaveBeenCalled();
      const brandArg = (appointmentApi.getAppointments as jest.Mock).mock.calls.at(-1)?.[2];
      expect(brandArg).toBe('Volkswagen Nutzfahrzeuge');
    });
  });
});
