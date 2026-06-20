import { computed, inject } from '@angular/core';

import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';

import type { TranslationKey } from '@core/i18n';
import { catchError, EMPTY, from, pipe, switchMap, tap } from 'rxjs';

import type { AppointmentSlot } from '../models/appointment.model';
import type { BranchConfig } from '../models/branch-config.model';
import type { Brand, BrandDisplay } from '../models/brand.model';
import type { CustomerInfo, VehicleInfo } from '../models/customer.model';
import type { LocationDisplay } from '../models/location.model';
import type { AppointmentPreference, NotesExtras, NotificationChannel } from '../models/notes-extras.model';
import { mapNotificationChannelToSoftnet } from '../models/notes-extras.model';
import type { RentCategory } from '../models/rent-category.model';
import type { ApiCategory, ApiJobType, ApiOperation, SelectedService, ServiceDisplay, ServiceType } from '../models/service.model';
import { AVAILABLE_SERVICES } from '../models/service.model';
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
  /** REQ-025: API-driven JobType-Liste für die gewählte Branch. */
  jobTypes: ApiJobType[];
  /** REQ-025 (b) Iter. 3: Service-Kategorien (Tags) für die gewählte Branch. */
  categories: ApiCategory[];
  /** REQ-025 (b) Iter. 3: Operations (Services) für die gewählte Branch. */
  operations: ApiOperation[];
  isLoading: boolean;
  error: string | null;
  isDeepLinked: boolean;
  /** Optionaler Deep-Link-Override: SoftNet-`groupId` aus der URL (z. B. tireChange-Group). */
  deepLinkGroupId: string | null;
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
  jobTypes: [],
  categories: [],
  operations: [],
  isLoading: false,
  error: null,
  isDeepLinked: false,
  deepLinkGroupId: null
};

/**
 * Maps the UI-side appointment preference (`anytime|morning|afternoon`) to
 * SoftNet's `checkInTimePreference` filter (`AM|PM`). `anytime` yields
 * `undefined` so the filter is omitted and all slots are returned.
 */
export function mapAppointmentPreferenceToSoftnet(
  pref: AppointmentPreference,
): 'AM' | 'PM' | undefined {
  switch (pref) {
    case 'morning':   return 'AM';
    case 'afternoon': return 'PM';
    case 'anytime':
    default:          return undefined;
  }
}

export const BookingStore = signalStore(
  { providedIn: 'root' },

  withState<BookingState>(INITIAL_STATE),

  withComputed(({ selectedBranch, selectedBrand, selectedLocation, selectedServices, bookingNote, selectedAppointment, workshopCalendarDate, customerInfo, vehicleInfo, privacyConsent, bookingSubmitted, bookingSubmitting, bookingError, appointmentId, notesExtras, rentCategories, selectedRentCategoryId, categories, operations, deepLinkGroupId }) => ({
    hasBranchSelected: computed(() => selectedBranch() !== null),
    selectedBranchBrands: computed(() => {
      const branch = selectedBranch();
      return branch ? branch.brands : [];
    }),
    hasBrandSelected: computed(() => selectedBrand() !== null),
    hasLocationSelected: computed(() => selectedLocation() !== null),
    selectedServiceCount: computed(() => selectedServices().length),
    hasServicesSelected: computed(() => selectedServices().length > 0),
    /** Flat list of user-selected operation names (REQ-025 b Iter. 3) bzw. Legacy-Labels. */
    selectedServiceLabels: computed<TranslationKey[]>(() => {
      const labels: TranslationKey[] = [];
      const ops = operations();
      for (const sel of selectedServices()) {
        // REQ-025 (b) Iter. 3: Operations primär — name als Label.
        const op = ops.find(o => o.id === sel.serviceId);
        if (op) {
          labels.push(op.name as TranslationKey);
          continue;
        }
        // Legacy-Fallback: AVAILABLE_SERVICES
        const service = AVAILABLE_SERVICES.find(s => s.id === sel.serviceId);
        if (!service) continue;
        if (sel.selectedOptionIds.length === 0) {
          labels.push(service.titleKey);
        } else {
          for (const optId of sel.selectedOptionIds) {
            const opt = service.options.find(o => o.id === optId);
            if (opt) labels.push(opt.labelKey);
          }
        }
      }
      return labels;
    }),
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
    selectedVariant: computed<'default' | 'tireChange'>(() => {
      // Variant = `'tireChange'` NUR wenn Boxenstopp-Deep-Link aktiv ist (deepLinkGroupId
      // entspricht der tireChange-Group der Branch). Sonst immer `'default'`.
      //
      // Hintergrund: Im default-Modus sind Inspektion (rank=0), TÜV (rank=1) UND Räderwechsel
      // (rank=3) alle in derselben SoftNet-default-Group registriert. Räderwechsel-Auswahl
      // soll daher NICHT zur tireChange-SoftNet-Group umgeschaltet werden — das war der alte
      // REQ-025-AC-7-Fehler, der Mischauswahl (Inspektion + Räderwechsel) blockiert hat.
      const groupId = deepLinkGroupId();
      const branch = selectedBranch();
      if (groupId && branch?.tireChangeGroupId && groupId === branch.tireChangeGroupId) {
        return 'tireChange';
      }
      return 'default';
    }),
    /**
     * REQ-025 (b) Iter. 3: Operations gruppiert nach Tag-IDs.
     * Pro Card-Tag → Liste aller Operations mit diesem Tag, sortiert nach `order`.
     *
     * Sonderregeln (UX-Wünsche, Front-End-only):
     * 1. Operations mit allen 7 Tags (Multi-Pflege in SoftNet) erscheinen NUR unter
     *    "Inspektion" (rank=0) statt unter jeder Kategorie doppelt.
     * 2. "Hol & Bringservice" bekommt eine eigene virtuelle Kategorie (rank=99,
     *    siehe `categoriesWithVirtual` Computed).
     */
    operationsByCategory: computed<Record<string, ApiOperation[]>>(() => {
      const INSPEKTION_ONLY_NAMES = new Set([
        'Dialogannahme',
        '1 Liter Notfallöl',
        'Wischerblätter erneuern',
        'Klimaanlagenreinigung (149.00 € inkl. MwSt.)'
      ]);
      const PICKUP_DELIVERY_NAME = 'Hol & Bringservice';
      const PICKUP_VIRTUAL_TAG_ID = 'virtual-pickup-delivery';
      const inspektionTagId = categories().find(c => c.rank === 0)?.id ?? null;
      const result: Record<string, ApiOperation[]> = {};

      // Marken-Filter: SoftNet-Operations koennen ein "brand"-Feld haben (Liste);
      // Operations sollen nur erscheinen, wenn die ausgewaehlte Marke (brand) dort
      // aufgefuehrt ist. Operationen ohne Brand-Info → immer sichtbar (Fallback).
      // Solange noch keine Marke gewaehlt ist → ebenfalls keine Filterung.
      const brand = selectedBrand();
      const brandLower = brand ? brand.toLowerCase() : null;
      const matchesBrand = (op: ApiOperation): boolean => {
        const opBrands = (op.brands ?? []);
        if (opBrands.length === 0) { return true; }
        if (brandLower === null) { return true; }
        return opBrands.some((x: string) => x.toLowerCase() === brandLower);
      };

      // De-duplizierte Pickup-Operations (eindeutig per ID) — ausschließlich Operations
      // mit Name "Hol & Bringservice", egal welche tagIds sie haben.
      const seenPickupIds = new Set<string>();
      const pickupOps = operations()
        .filter(op => op.name === PICKUP_DELIVERY_NAME)
        .filter(matchesBrand)
        .filter(op => { if (seenPickupIds.has(op.id)) return false; seenPickupIds.add(op.id); return true; });

      // Reguläre Categories
      for (const cat of categories()) {
        // Falls SoftNet eine Tag namens "Hol & Bringservice" mitliefert, soll diese
        // Karte exakt dieselben Pickup-Operations zeigen — kein Inspektion-/TÜV-Inhalt.
        if (cat.name === PICKUP_DELIVERY_NAME) {
          result[cat.id] = pickupOps;
          continue;
        }
        result[cat.id] = operations()
          .filter(op => {
            if (!op.tagIds.includes(cat.id)) return false;
            if (op.name === PICKUP_DELIVERY_NAME) return false; // hat eigene Pickup-Card
            if (INSPEKTION_ONLY_NAMES.has(op.name) && cat.id !== inspektionTagId) {
              return false;
            }
            if (!matchesBrand(op)) return false;
            return true;
          })
          .sort((a, b) => (a.order ?? Number.MAX_SAFE_INTEGER) - (b.order ?? Number.MAX_SAFE_INTEGER));
      }

      // Virtuelle Pickup-Delivery-Kategorie (immer nur Pickup-Operations)
      result[PICKUP_VIRTUAL_TAG_ID] = pickupOps;

      // Virtuelle Räderwechsel-Card für tireChange-Group (Boxenstopp), wenn SoftNet
      // dort keine Tags konfiguriert hat — alle Operations dieser Group landen darunter.
      result['virtual-tire-change'] = operations()
        .filter(matchesBrand)
        .sort((a, b) => (a.order ?? Number.MAX_SAFE_INTEGER) - (b.order ?? Number.MAX_SAFE_INTEGER));

      return result;
    }),

    /**
     * REQ-025 (b) Iter. 3: Categories inkl. der virtuellen "Hol & Bringservice"-Card
     * (rank=99 — wird ans Ende der Card-Liste sortiert).
     */
    categoriesWithVirtual: computed<ApiCategory[]>(() => {
      const ops = operations();
      const cats = categories();
      const hasPickupOp = ops.some(op => op.name === 'Hol & Bringservice');
      // Wenn SoftNet schon eine echte Tag namens "Hol & Bringservice" liefert, keine
      // virtuelle Doppelung anhängen — die echte Tag wird in operationsByCategory
      // ohnehin auf Pickup-Operations gefiltert.
      const hasRealPickupCat = cats.some(c => c.name === 'Hol & Bringservice');
      if (!hasPickupOp || hasRealPickupCat) return cats;
      return [
        ...cats,
        { id: 'virtual-pickup-delivery', name: 'Hol & Bringservice', rank: 99 }
      ];
    }),
    /**
     * `true`, wenn die App per Deep-Link mit der `tireChange`-Group-ID der
     * aktuellen Branch geöffnet wurde (Boxenstopp-Tag).
     */
    isTireChangeMode: computed<boolean>(() => {
      const groupId = deepLinkGroupId();
      const branch = selectedBranch();
      if (!groupId || !branch?.tireChangeGroupId) return false;
      return groupId === branch.tireChangeGroupId;
    }),
    /**
     * Im tireChange-Modus (Boxenstopp): zeigt was SoftNet für die tireChange-Group
     * ausliefert. Falls SoftNet dort keine Tags konfiguriert hat (häufig der Fall —
     * Boxenstopp-Group ist auf Operations-Ebene konfiguriert), wird eine virtuelle
     * "Räderwechsel"-Card erzeugt, unter der alle Operations dieser Group landen.
     * Sonst: alle Kategorien inkl. virtueller Hol-und-Bring-Card.
     */
    visibleCategories: computed<ApiCategory[]>(() => {
      const groupId = deepLinkGroupId();
      const branch = selectedBranch();
      const tireChangeMode = !!groupId && !!branch?.tireChangeGroupId && groupId === branch.tireChangeGroupId;
      const cats = categories();
      const ops = operations();
      if (tireChangeMode) {
        if (cats.length > 0) return cats;
        // Fallback: SoftNet liefert keine Tags für die tireChange-Group → virtuelle Card.
        if (ops.length === 0) return [];
        return [{ id: 'virtual-tire-change', name: 'Räderwechsel+', rank: 0 }];
      }
      // Default-Mode: cats + virtuelle Pickup-Card (falls passende Operations).
      const hasPickupOp = ops.some(op => op.name === 'Hol & Bringservice');
      const hasRealPickupCat = cats.some(c => c.name === 'Hol & Bringservice');
      if (!hasPickupOp || hasRealPickupCat) return cats;
      return [
        ...cats,
        { id: 'virtual-pickup-delivery', name: 'Hol & Bringservice', rank: 99 }
      ];
    }),
    hasNotesExtras: computed(() => notesExtras() !== null),
    selectedAppointmentPreference: computed(() => notesExtras()?.appointmentPreference ?? 'anytime'),
    selectedCallbackOption: computed(() => notesExtras()?.callbackOption ?? 'no'),
    callbackRequested: computed(() => notesExtras()?.callbackOption === 'yes'),
    /** REQ-027: selected notification channel (default 'email'). */
    selectedNotificationChannel: computed<NotificationChannel>(
      () => notesExtras()?.notificationChannel ?? 'email'
    ),
    selectedRentCategoryName: computed<string | null>(() => {
      const id = selectedRentCategoryId();
      if (!id) return null;
      return rentCategories().find(c => c.id === id)?.name ?? null;
    }),
    /** REQ-025 AC-15: Mietwagen-Kategorie + Preis-Info für den Notes-Preisblock. */
    selectedRentCategoryPrice: computed<{ name: string; price: number; currency: string; priceUnit: string } | null>(() => {
      const id = selectedRentCategoryId();
      if (!id) return null;
      const rc = rentCategories().find(c => c.id === id);
      if (!rc?.price || !rc.currency || !rc.priceUnit) {
        return null;
      }
      return { name: rc.name, price: rc.price, currency: rc.currency, priceUnit: rc.priceUnit };
    }),
  })),

  withMethods((store, api = inject(BookingApiService), appointmentApi = inject(AppointmentApiService), workshopCalendarApi = inject(WorkshopCalendarApiService), rentCategoryApi = inject(RentCategoryApiService)) => ({
    /**
     * REQ-025 AC-6: lädt JobTypes der gewählten Branch einmal und cached sie im Store.
     * Frontend-Components lesen aus `store.jobTypes()`.
     */
    loadJobTypesForBranch: rxMethod<string>(
      pipe(
        tap(() => { patchState(store, { isLoading: true, error: null }); }),
        switchMap((branchId) =>
          from(api.getJobTypesByBranch(branchId)).pipe(
            tap((jobTypes) => { patchState(store, { jobTypes, isLoading: false }); }),
            catchError((err: unknown) => {
              const message = err instanceof Error ? err.message : 'Unknown error';
              patchState(store, { error: message, isLoading: false });
              return EMPTY;
            })
          )
        )
      )
    ),

    /**
     * REQ-025 (b) Iter. 3: Lädt Service-Kategorien (Tags) für die gewählte Branch.
     * Cards auf der Services-Page rendern aus diesem State.
     */
    loadCategoriesForBranch: rxMethod<string>(
      pipe(
        switchMap((branchId) =>
          from(api.getCategoriesByBranch(branchId, store.deepLinkGroupId())).pipe(
            tap((categories) => { patchState(store, { categories }); }),
            catchError((err: unknown) => {
              const message = err instanceof Error ? err.message : 'Unknown error';
              patchState(store, { error: message });
              return EMPTY;
            })
          )
        )
      )
    ),

    /**
     * REQ-025 (b) Iter. 3: Lädt Operations (Services mit description) für die Branch.
     * Sub-Options einer Card werden aus diesem State gefiltert (per `tagIds`).
     */
    loadOperationsForBranch: rxMethod<string>(
      pipe(
        switchMap((branchId) =>
          from(api.getOperationsByBranch(branchId, store.deepLinkGroupId())).pipe(
            tap((operations) => { patchState(store, { operations }); }),
            catchError((err: unknown) => {
              const message = err instanceof Error ? err.message : 'Unknown error';
              patchState(store, { error: message });
              return EMPTY;
            })
          )
        )
      )
    ),

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
      const street = branch.address.street;
      const zipCity = [branch.address.zip, branch.address.city].filter(Boolean).join(' ');
      const formattedAddress = [street, zipCity].filter(Boolean).join(', ') || branch.name;
      patchState(store, {
        selectedBranch: branch,
        selectedLocation: { id: branch.branchId, name: branch.name, city: branch.address.city, formattedAddress }
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
          const timePreference = mapAppointmentPreferenceToSoftnet(store.selectedAppointmentPreference());
          const notifications = mapNotificationChannelToSoftnet(store.selectedNotificationChannel());
          return from(appointmentApi.getAppointments(branchId, serviceIds, brandName, variant, timePreference, notifications)).pipe(
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
            rentCategoryId: store.selectedRentCategoryId() ?? undefined,
            callbackRequested: extras?.callbackOption === 'yes',
            notifications: mapNotificationChannelToSoftnet(extras?.notificationChannel ?? 'email'),
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

    setDeepLinkGroupId(deepLinkGroupId: string | null): void {
      patchState(store, { deepLinkGroupId });
    },

    setDeepLinked(isDeepLinked: boolean): void {
      patchState(store, { isDeepLinked });
    }
  }))
);
