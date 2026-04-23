import { computed, inject } from '@angular/core';

import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';

import { catchError, EMPTY, from, pipe, switchMap, tap } from 'rxjs';

import type { AppointmentSlot } from '../models/appointment.model';
import type { BranchConfig } from '../models/branch-config.model';
import type { Brand, BrandDisplay } from '../models/brand.model';
import type { CustomerInfo, VehicleInfo } from '../models/customer.model';
import type { LocationDisplay } from '../models/location.model';
import type { NotesExtras } from '../models/notes-extras.model';
import type { RentCategory } from '../models/rent-category.model';
import type { SelectedService, ServiceDisplay, ServiceType } from '../models/service.model';
import type { WorkshopCalendarDay } from '../models/workshop-calendar.model';
import { AppointmentApiService } from '../services/appointment-api.service';
import { BookingApiService } from '../services/booking-api.service';
import { RentCategoryApiService } from '../services/rent-category-api.service';
import { WorkshopCalendarApiService } from '../services/workshop-calendar-api.service';

interface BookingState {
  branches: BranchConfig[];
  selectedBranch: BranchConfig | null;
  brands: BrandDisplay[];
  selectedBrand: Brand | null;
  locations: LocationDisplay[];
  selectedLocation: LocationDisplay | null;
  services: ServiceDisplay[];
  selectedServices: SelectedService[];
  bookingNote: string | null;
  appointments: AppointmentSlot[];
  selectedAppointment: AppointmentSlot | null;
  workshopCalendarDate: string | null;
  workshopCalendarDays: WorkshopCalendarDay[];
  customerInfo: CustomerInfo | null;
  vehicleInfo: VehicleInfo | null;
  privacyConsent: boolean;
  notesExtras: NotesExtras | null;
  bookingSubmitted: boolean;
  bookingSubmitting: boolean;
  bookingError: string | null;
  appointmentId: string | null;
  rentCategories: RentCategory[];
  selectedRentCategoryId: string | null;
  isLoading: boolean;
  error: string | null;
  isDeepLinked: boolean;
}

const INITIAL_STATE: BookingState = {
  branches: [],
  selectedBranch: null,
  brands: [],
  selectedBrand: null,
  locations: [],
  selectedLocation: null,
  services: [],
  selectedServices: [],
  bookingNote: null,
  appointments: [],
  selectedAppointment: null,
  workshopCalendarDate: null,
  workshopCalendarDays: [],
  customerInfo: null,
  vehicleInfo: null,
  privacyConsent: false,
  notesExtras: null,
  bookingSubmitted: false,
  bookingSubmitting: false,
  bookingError: null,
  appointmentId: null,
  rentCategories: [],
  selectedRentCategoryId: null,
  isLoading: false,
  error: null,
  isDeepLinked: false
};

export const BookingStore = signalStore(
  { providedIn: 'root' },

  withState<BookingState>(INITIAL_STATE),

  withComputed(({ selectedBranch, selectedBrand, selectedLocation, selectedServices, bookingNote, selectedAppointment, workshopCalendarDate, customerInfo, vehicleInfo, privacyConsent, bookingSubmitted, bookingSubmitting, bookingError, appointmentId, notesExtras }) => ({
    hasBranchSelected: computed(() => selectedBranch() !== null),
    selectedBranchBrands: computed(() => {
      const branch = selectedBranch();
      return branch ? branch.brands : [];
    }),
    hasBrandSelected: computed(() => selectedBrand() !== null),
    hasLocationSelected: computed(() => selectedLocation() !== null),
    selectedServiceCount: computed(() => selectedServices().length),
    hasServicesSelected: computed(() => selectedServices().length > 0),
    hasBookingNote: computed(() => bookingNote() !== null && bookingNote() !== ''),
    hasAppointmentSelected: computed(() => selectedAppointment() !== null),
    hasWorkshopSlotSelected: computed(() => {
      const appointment = selectedAppointment();
      const calendarDate = workshopCalendarDate();
      return appointment !== null && calendarDate !== null;
    }),
    isBookingComplete: computed(() =>
      selectedBrand() !== null &&
      selectedLocation() !== null &&
      selectedServices().length > 0 &&
      selectedAppointment() !== null &&
      customerInfo() !== null &&
      vehicleInfo() !== null &&
      privacyConsent()
    ),
    hasBookingSubmitted: computed(() => bookingSubmitted()),
    isBookingSubmitting: computed(() => bookingSubmitting()),
    hasBookingError: computed(() => bookingError() !== null),
    submittedAppointmentId: computed(() => appointmentId()),
    selectedVariant: computed<'default' | 'tireChange'>(() =>
      selectedServices().some(s => s.serviceId === 'tire-change') ? 'tireChange' : 'default'
    ),
    hasNotesExtras: computed(() => notesExtras() !== null),
    selectedMobilityOption: computed(() => notesExtras()?.mobilityOption ?? 'none'),
    selectedAppointmentPreference: computed(() => notesExtras()?.appointmentPreference ?? 'anytime'),
    selectedCallbackOption: computed(() => notesExtras()?.callbackOption ?? 'none')
  })),

  withMethods((store, api = inject(BookingApiService), appointmentApi = inject(AppointmentApiService), workshopCalendarApi = inject(WorkshopCalendarApiService), rentCategoryApi = inject(RentCategoryApiService)) => ({
    loadBrands: rxMethod<string | void>(
      pipe(
        tap(() => { patchState(store, { isLoading: true, error: null }); }),
        switchMap((branchId) => {
          const branch = store.selectedBranch();
          let source;
          if (branch) {
            source = from(api.getBrandsByBranch(branch.brands));
          } else if (branchId) {
            source = from(api.getBrands());
          } else {
            source = from(api.getBrands());
          }
          return source.pipe(
            tap((brands) => {
              patchState(store, { brands, isLoading: false });
            }),
            catchError((err: unknown) => {
              const message = err instanceof Error ? err.message : 'Unknown error';
              patchState(store, { error: message, isLoading: false });
              return EMPTY;
            })
          );
        })
      )
    ),

    loadLocations: rxMethod<void>(
      pipe(
        tap(() => {
          patchState(store, { isLoading: true, error: null });
        }),
        switchMap(() => {
          const brand = store.selectedBrand() ?? undefined;
          return from(api.getLocations(brand)).pipe(
            tap((locations) => {
              patchState(store, { locations, isLoading: false });
            }),
            catchError((err: unknown) => {
              const message = err instanceof Error ? err.message : 'Unknown error';
              patchState(store, { error: message, isLoading: false });
              return EMPTY;
            })
          );
        })
      )
    ),

    loadBranches: rxMethod<void>(
      pipe(
        tap(() => { patchState(store, { isLoading: true, error: null }); }),
        switchMap(() => from(api.getBranches()).pipe(
          tap((branches) => {
            patchState(store, { branches, isLoading: false });
          }),
          catchError((err: unknown) => {
            const message = err instanceof Error ? err.message : 'Unknown error';
            patchState(store, { error: message, isLoading: false });
            return EMPTY;
          })
        ))
      )
    ),

    loadServices: rxMethod<void>(
      pipe(
        tap(() => { patchState(store, { isLoading: true, error: null }); }),
        switchMap(() => from(api.getServices()).pipe(
          tap((services) => {
            patchState(store, { services, isLoading: false });
          }),
          catchError((err: unknown) => {
            const message = err instanceof Error ? err.message : 'Unknown error';
            patchState(store, { error: message, isLoading: false });
            return EMPTY;
          })
        ))
      )
    ),

    setBrand(brand: Brand): void {
      patchState(store, { selectedBrand: brand });
    },

    setLocation(location: LocationDisplay): void {
      const currentLocation = store.selectedLocation();
      const locationChanged = currentLocation?.id !== location.id;
      patchState(store, { selectedLocation: location });
      if (locationChanged) {
        patchState(store, {
          selectedBrand: null,
          selectedServices: [],
          selectedAppointment: null,
          notesExtras: null,
          bookingSubmitted: false
        });
      }
    },

    selectBranch(branch: BranchConfig): void {
      const currentBranch = store.selectedBranch();
      const branchChanged = currentBranch?.branchId !== branch.branchId;
      const address = branch.address.street || branch.name;
      patchState(store, {
        selectedBranch: branch,
        selectedLocation: { id: branch.branchId, name: branch.name, city: address, formattedAddress: address }
      });
      if (branchChanged) {
        patchState(store, {
          selectedBrand: null,
          selectedServices: [],
          selectedAppointment: null,
          notesExtras: null,
          bookingSubmitted: false
        });
      }
    },

    toggleService(serviceId: ServiceType): void {
      const current = store.selectedServices();
      const exists = current.some(s => s.serviceId === serviceId);
      if (exists) {
        patchState(store, { selectedServices: current.filter(s => s.serviceId !== serviceId) });
      } else {
        patchState(store, { selectedServices: [...current, { serviceId, selectedOptionIds: [] }] });
      }
    },

    confirmServiceOptions(serviceId: ServiceType, optionIds: string[]): void {
      const current = store.selectedServices();
      const filtered = current.filter(s => s.serviceId !== serviceId);
      patchState(store, {
        selectedServices: [...filtered, { serviceId, selectedOptionIds: optionIds }]
      });
    },

    deselectService(serviceId: ServiceType): void {
      const current = store.selectedServices();
      patchState(store, { selectedServices: current.filter(s => s.serviceId !== serviceId) });
    },

    setBookingNote(note: string | null): void {
      patchState(store, { bookingNote: note ?? null });
    },

    loadAppointments: rxMethod<void>(
      pipe(
        tap(() => {
          patchState(store, {
            appointments: [],
            selectedAppointment: null,
            isLoading: true,
            error: null
          });
        }),
        switchMap(() => {
          const branchId = store.selectedLocation()?.id ?? '';
          const serviceIds = store.selectedServices().map(s => s.serviceId);
          const brandName = store.selectedBrand() ?? '';
          const variant = store.selectedVariant();
          return from(appointmentApi.getAppointments(branchId, serviceIds, brandName, variant)).pipe(
            tap((appointments) => {
              patchState(store, { appointments, isLoading: false });
            }),
            catchError((err: unknown) => {
              const message = err instanceof Error ? err.message : 'Unknown error';
              patchState(store, { error: message, isLoading: false });
              return EMPTY;
            })
          );
        })
      )
    ),

    selectAppointment(appointment: AppointmentSlot): void {
      patchState(store, { selectedAppointment: appointment });
    },

    setWorkshopCalendarDate(date: string): void {
      patchState(store, { workshopCalendarDate: date, selectedAppointment: null });
    },

    loadWorkshopCalendarDays: rxMethod<string>(
      pipe(
        tap(() => { patchState(store, { isLoading: true, error: null }); }),
        switchMap((fromDate) => {
          const branchId = store.selectedLocation()?.id ?? '';
          const serviceIds = store.selectedServices().map(s => s.serviceId);
          const brandName = store.selectedBrand() ?? '';
          return from(workshopCalendarApi.getWorkshopCalendarDays(fromDate, branchId, serviceIds, brandName)).pipe(
            tap((workshopCalendarDays) => {
              patchState(store, { workshopCalendarDays, isLoading: false });
            }),
            catchError((err: unknown) => {
              const message = err instanceof Error ? err.message : 'Unknown error';
              patchState(store, { error: message, isLoading: false });
              return EMPTY;
            })
          );
        })
      )
    ),

    clearWorkshopCalendar(): void {
      patchState(store, { workshopCalendarDate: null, workshopCalendarDays: [], selectedAppointment: null });
    },

    clearSelectedBrand(): void {
      patchState(store, { selectedBrand: null });
    },

    setNotesExtras(extras: NotesExtras): void {
      patchState(store, { notesExtras: extras });
    },

    clearNotesExtras(): void {
      patchState(store, { notesExtras: null });
    },

    clearSelectedAppointment(): void {
      patchState(store, { selectedAppointment: null });
    },

    clearSelectedServices(): void {
      patchState(store, { selectedServices: [] });
    },

    setCustomerInfo(info: CustomerInfo): void {
      patchState(store, { customerInfo: info });
    },

    setVehicleInfo(info: VehicleInfo): void {
      patchState(store, { vehicleInfo: info });
    },

    setPrivacyConsent(consent: boolean): void {
      patchState(store, { privacyConsent: consent });
    },

    submitBooking: rxMethod<void>(
      pipe(
        tap(() => { patchState(store, { bookingSubmitting: true, bookingError: null }); }),
        switchMap(() => {
          const appointment = store.selectedAppointment();
          const customer = store.customerInfo();
          const vehicle = store.vehicleInfo();
          const extras = store.notesExtras();

          const request = {
            snapId: appointment?.id ?? '',
            branchId: store.selectedLocation()?.id ?? '',
            variant: store.selectedVariant(),
            customer: {
              email: customer?.email ?? '',
              firstName: customer?.firstName ?? '',
              lastName: customer?.lastName ?? '',
              mobile: customer?.mobilePhone ?? '',
              phone: (customer?.phone && customer.phone.length > 0 ? customer.phone : customer?.mobilePhone) ?? '',
              address: customer?.street ?? '',
              zipCode: customer?.postalCode ?? '',
              city: customer?.city ?? '',
              country: customer?.country && customer.country.length > 0 ? customer.country : 'DE',
              language: customer?.language && customer.language.length > 0 ? customer.language : 'de',
              salutation: customer?.salutation,
            },
            vehicle: {
              licensePlate: vehicle?.licensePlate ?? '',
              mileage: vehicle?.mileage ?? 0,
              vin: vehicle?.vin,
              model: vehicle?.model,
              firstRegMonth: vehicle?.firstRegMonth,
              firstRegYear: vehicle?.firstRegYear,
            },
            note: store.bookingNote() ?? undefined,
            mobilityOption: extras?.mobilityOption,
            rentCategoryId: store.selectedRentCategoryId() ?? undefined,
          };

          return from(appointmentApi.createAppointment(request)).pipe(
            tap((response) => {
              patchState(store, {
                bookingSubmitted: true,
                bookingSubmitting: false,
                appointmentId: response.id,
              });
            }),
            catchError((err: unknown) => {
              const message = err instanceof Error ? err.message : 'Booking submission failed';
              patchState(store, {
                bookingSubmitting: false,
                bookingError: message,
              });
              return EMPTY;
            })
          );
        })
      )
    ),

    resetBookingSubmission(): void {
      patchState(store, { bookingError: null, bookingSubmitted: false });
    },

    loadRentCategories: rxMethod<void>(
      pipe(
        tap(() => { patchState(store, { isLoading: true, error: null }); }),
        switchMap(() => {
          const branchId = store.selectedLocation()?.id ?? '';
          if (!branchId) {
            patchState(store, { rentCategories: [], isLoading: false });
            return EMPTY;
          }
          return from(rentCategoryApi.getRentCategories(branchId)).pipe(
            tap((rentCategories) => {
              patchState(store, { rentCategories, isLoading: false });
            }),
            catchError((err: unknown) => {
              const message = err instanceof Error ? err.message : 'Unknown error';
              patchState(store, { error: message, isLoading: false });
              return EMPTY;
            })
          );
        })
      )
    ),

    setSelectedRentCategoryId(id: string | null): void {
      patchState(store, { selectedRentCategoryId: id });
    },

    resetFromStep(targetIndex: number): void {
      if (targetIndex <= 0) { patchState(store, { selectedBranch: null, selectedLocation: null }); }
      if (targetIndex <= 1) { patchState(store, { selectedBrand: null }); }
      if (targetIndex <= 2) { patchState(store, { selectedServices: [] }); }
      if (targetIndex <= 3) { patchState(store, { notesExtras: null }); }
      if (targetIndex <= 4) { patchState(store, { selectedAppointment: null, workshopCalendarDate: null, workshopCalendarDays: [] }); }
    },

    setDeepLinked(isDeepLinked: boolean): void {
      patchState(store, { isDeepLinked });
    }
  }))
);
