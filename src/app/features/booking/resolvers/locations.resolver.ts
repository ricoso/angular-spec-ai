import { inject } from '@angular/core';
import type { ResolveFn } from '@angular/router';

import { TraceService } from '@core/http/trace.service';

import { BookingStore } from '../stores/booking.store';

export const locationsResolver: ResolveFn<void> = () => {
  const store = inject(BookingStore);
  const trace = inject(TraceService);
  trace.log('LocationsResolver', 'firing loadBranches() + loadLocations()');
  store.loadBranches();
  store.loadLocations();
  return;
};
