import type { TranslationKey } from '@core/i18n';

/**
 * Service type union — 7 service categories
 */
export type ServiceType = 'inspection' | 'tuv' | 'brake-fluid' | 'tire-change' | 'actions-checks' | 'repair' | 'bodywork';

/**
 * Service option for checkbox selection
 */
export interface ServiceOption {
  id: string;
  labelKey: TranslationKey;
}

/**
 * Service display model for UI rendering
 */
export interface ServiceDisplay {
  id: ServiceType;
  titleKey: TranslationKey;
  descriptionKey: TranslationKey;
  svgIcon: string;
  options: ServiceOption[];
}

/**
 * Selected service with chosen options
 */
export interface SelectedService {
  serviceId: ServiceType;
  selectedOptionIds: string[];
}

/**
 * Available service definitions
 */
export const AVAILABLE_SERVICES: ServiceDisplay[] = [
  {
    id: 'inspection',
    titleKey: 'booking.services.inspection.title',
    descriptionKey: 'booking.services.inspection.description',
    svgIcon: 'assets/icons/services/inspection.svg',
    options: [
      { id: 'dialog-acceptance', labelKey: 'booking.services.inspection.options.dialogAcceptance' },
      { id: 'inspection', labelKey: 'booking.services.inspection.options.inspection' },
      { id: 'oil-change', labelKey: 'booking.services.inspection.options.oilChange' },
      { id: 'brake-check', labelKey: 'booking.services.inspection.options.brakeCheck' },
      { id: 'wiper-blades', labelKey: 'booking.services.inspection.options.wiperBlades' },
      { id: 'maintenance-contract', labelKey: 'booking.services.inspection.options.maintenanceContract' },
      { id: 'emergency-oil', labelKey: 'booking.services.inspection.options.emergencyOil' }
    ]
  },
  {
    id: 'tuv',
    titleKey: 'booking.services.tuv.title',
    descriptionKey: 'booking.services.tuv.description',
    svgIcon: 'assets/icons/services/tuv.svg',
    options: [
      { id: 'tuv', labelKey: 'booking.services.tuv.options.tuv' },
      { id: 'uvv-inspection', labelKey: 'booking.services.tuv.options.uvvInspection' },
      { id: 'ac-cleaning', labelKey: 'booking.services.tuv.options.acCleaning' }
    ]
  },
  {
    id: 'brake-fluid',
    titleKey: 'booking.services.brakeFluid.title',
    descriptionKey: 'booking.services.brakeFluid.description',
    svgIcon: 'assets/icons/services/brake-fluid.svg',
    options: []
  },
  {
    id: 'tire-change',
    titleKey: 'booking.services.tireChange.title',
    descriptionKey: 'booking.services.tireChange.description',
    svgIcon: 'assets/icons/services/tire-change.svg',
    options: [
      { id: 'bring-own-tires', labelKey: 'booking.services.tireChange.options.bringOwnTires' },
      { id: 'stored-tires', labelKey: 'booking.services.tireChange.options.storedTires' },
      { id: 'pickup-delivery', labelKey: 'booking.services.tireChange.options.pickupDelivery' },
      { id: 'store-tires', labelKey: 'booking.services.tireChange.options.storeTires' }
    ]
  },
  {
    id: 'actions-checks',
    titleKey: 'booking.services.actionsChecks.title',
    descriptionKey: 'booking.services.actionsChecks.description',
    svgIcon: 'assets/icons/services/actions-checks.svg',
    options: [
      { id: 'warranty-check', labelKey: 'booking.services.actionsChecks.options.warrantyCheck' },
      { id: 'safety-check', labelKey: 'booking.services.actionsChecks.options.safetyCheck' },
      { id: 'fluid-check', labelKey: 'booking.services.actionsChecks.options.fluidCheck' },
      { id: 'battery-check', labelKey: 'booking.services.actionsChecks.options.batteryCheck' }
    ]
  },
  {
    id: 'repair',
    titleKey: 'booking.services.repair.title',
    descriptionKey: 'booking.services.repair.description',
    svgIcon: 'assets/icons/services/repair.svg',
    options: [
      { id: 'diagnostics', labelKey: 'booking.services.repair.options.diagnostics' },
      { id: 'noises', labelKey: 'booking.services.repair.options.noises' },
      { id: 'ac-malfunction', labelKey: 'booking.services.repair.options.acMalfunction' }
    ]
  },
  {
    id: 'bodywork',
    titleKey: 'booking.services.bodywork.title',
    descriptionKey: 'booking.services.bodywork.description',
    svgIcon: 'assets/icons/services/bodywork.svg',
    options: [
      { id: 'cost-estimate', labelKey: 'booking.services.bodywork.options.costEstimate' },
      { id: 'windshield-replacement', labelKey: 'booking.services.bodywork.options.windshieldReplacement' }
    ]
  }
];

// =============================================================================
// REQ-025: API-driven JobType-Modell (löst AVAILABLE_SERVICES in Phase 2 ab).
// =============================================================================

/** Ein JobType-Eintrag wie er vom Backend (`/branches/{id}/jobtypes`) kommt. */
export interface ApiJobType {
  id: string;
  name: string;
  description: string;
  /** Hint-Text fuer die Notes-Seite — null wenn nicht hinterlegt. */
  hint: string | null;
  /** Stabile Kategorie ("tireChange" | "default" | …) — null wenn nicht gemappt. */
  category: string | null;
  /** Display-Name der Kategorie. */
  categoryName: string | null;
  /** Optional: Icon-URL (sonst Material-Fallback). */
  iconUrl: string | null;
  operations: ApiJobTypeOption[];
}

/** Eine Sub-Option eines JobType (z. B. "Ölwechsel" unter "Inspektion"). */
export interface ApiJobTypeOption {
  id: string;
  name: string;
}

/** Auswahl eines JobType im Store — speichert nur IDs. */
export interface SelectedJobType {
  jobTypeId: string;
  selectedOperationIds: string[];
}

// =============================================================================
// REQ-025 (b) Iter. 3: Tags + Operations als korrektes SoftNet-Datenmodell.
// Cards = Categories (Tags), Sub-Options = Operations gefiltert nach tagIds.
// =============================================================================

/**
 * Service-Kategorie aus `/api/v1/branches/{id}/categories` (Tags aus SoftNet).
 * Cards auf der Services-Page rendern 1:1 diese Liste, sortiert nach `rank`.
 */
export interface ApiCategory {
  id: string;
  name: string;
  rank: number;
}

/** Marker für die TireChange-Kategorie — synchron mit Backend `JobTypesService.TIRE_CHANGE_TAG_RANK`. */
export const TIRE_CHANGE_TAG_RANK = 3;

/**
 * Operation = einzelner Service aus `/api/v1/branches/{id}/operations`.
 * `description` wird als kleiner Untertitel direkt unter dem Service-Namen
 * auf der Services-Page angezeigt (REQ-025 Punkt b Iter. 3).
 */
export interface ApiOperation {
  id: string;
  name: string;
  description: string | null;
  tagIds: string[];
  order: number | null;
  imageId: string | null;
  /** Marken, fuer die diese Operation in der SoftNet-Group buchbar ist.
   *  Leer/undefined = keine Filterung (Operation fuer alle Marken sichtbar). */
  brands?: string[];
}
