import { i18nKeys } from '@core/i18n';

import type { WizardStep } from './wizard-breadcrumb.model';

// Bootstrap-Icons (bi-*) — siehe Migration: Material → Bootstrap
export const WIZARD_STEPS: WizardStep[] = [
  { label: i18nKeys.booking.wizard.location,    icon: 'bi-geo-alt-fill',      route: '/home/location' },
  { label: i18nKeys.booking.wizard.brand,       icon: 'bi-tag-fill',          route: '/home/brand' },
  { label: i18nKeys.booking.wizard.service,     icon: 'bi-tools',             route: '/home/services' },
  { label: i18nKeys.booking.wizard.notes,       icon: 'bi-info-circle-fill',  route: '/home/notes' },
  { label: i18nKeys.booking.wizard.appointment, icon: 'bi-calendar-event',    route: '/home/appointment' },
  { label: i18nKeys.booking.wizard.vehicle,     icon: 'bi-car-front-fill',    route: '/home/carinformation' },
  { label: i18nKeys.booking.wizard.overview,    icon: 'bi-check-circle-fill', route: '/home/booking-overview' }
];
