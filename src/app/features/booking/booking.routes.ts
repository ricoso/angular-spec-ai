import type { Routes } from '@angular/router';

import { bookingOverviewGuard } from './guards/booking-overview.guard';
import { brandSelectedGuard } from './guards/brand-selected.guard';
import { carInformationGuard } from './guards/car-information.guard';
import { deepLinkGuard } from './guards/deep-link.guard';
import { locationSelectedGuard } from './guards/location-selected.guard';
import { recallBlocksServicesGuard, recallGuard, recallServiceGuard } from './guards/recall.guard';
import { servicesSelectedGuard } from './guards/services-selected.guard';
import { appointmentsResolver } from './resolvers/appointments.resolver';
import { brandsResolver } from './resolvers/brands.resolver';
import { locationsResolver } from './resolvers/locations.resolver';
import { rentCategoriesResolver } from './resolvers/rent-categories.resolver';
import { servicesResolver } from './resolvers/services.resolver';

export const bookingRoutes: Routes = [
  {
    path: '',
    redirectTo: 'location',
    pathMatch: 'full'
  },
  {
    path: 'location',
    loadComponent: () => import('./components/location-selection/location-selection-container.component')
      .then(m => m.LocationSelectionContainerComponent),
    canActivate: [deepLinkGuard, recallGuard],
    resolve: { _: locationsResolver }
  },
  {
    path: 'brand',
    loadComponent: () => import('./components/brand-selection/brand-selection-container.component')
      .then(m => m.BrandSelectionContainerComponent),
    canActivate: [locationSelectedGuard],
    resolve: { _: brandsResolver }
  },
  {
    path: 'services',
    loadComponent: () => import('./components/service-selection/service-selection-container.component')
      .then(m => m.ServiceSelectionContainerComponent),
    canActivate: [recallBlocksServicesGuard, brandSelectedGuard],
    resolve: { _: servicesResolver }
  },
  {
    path: 'notes',
    loadComponent: () => import('./components/notes/notes-container.component')
      .then(m => m.NotesContainerComponent),
    canActivate: [recallServiceGuard, servicesSelectedGuard],
    resolve: { _: rentCategoriesResolver }
  },
  {
    path: 'appointment',
    loadComponent: () => import('./components/appointment-selection/appointment-selection-container.component')
      .then(m => m.AppointmentSelectionContainerComponent),
    canActivate: [recallServiceGuard, servicesSelectedGuard],
    resolve: { _: appointmentsResolver }
  },
  {
    path: 'workshop-calendar',
    loadComponent: () => import('./components/workshop-calendar/workshop-calendar-container.component')
      .then(m => m.WorkshopCalendarContainerComponent),
    canActivate: [recallServiceGuard, servicesSelectedGuard]
  },
  {
    path: 'carinformation',
    loadComponent: () => import('./components/carinformation/carinformation-container.component')
      .then(m => m.CarinformationContainerComponent),
    canActivate: [recallServiceGuard, carInformationGuard]
  },
  {
    path: 'booking-overview',
    loadComponent: () => import('./components/booking-overview/booking-overview-container.component')
      .then(m => m.BookingOverviewContainerComponent),
    canActivate: [recallServiceGuard, bookingOverviewGuard]
  }
];
