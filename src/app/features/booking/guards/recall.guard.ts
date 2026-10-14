import { inject } from '@angular/core';
import type { CanActivateFn, UrlTree } from '@angular/router';
import { Router } from '@angular/router';

import { BookingStore } from '../stores/booking.store';

/**
 * REQ-041: recall-campaign entry guard (on the location route). When the OTV is opened via the
 * recall URL (`?recall=true&vin=…`) it activates recall mode and remembers the (validated) VIN.
 * Without the `recall` flag it is a no-op → the normal OTV flow is fully preserved. The user still
 * picks the location here (recall does NOT skip location selection).
 */
export const recallGuard: CanActivateFn = (route) => {
  const store = inject(BookingStore);

  if (route.queryParamMap.get('recall') === 'true') {
    store.activateRecallMode(route.queryParamMap.get('vin'));
  }

  return true;
};

/**
 * REQ-041: ensures the single configured recall service is selected before the steps that follow
 * the (skipped) service step. Loads the recall group's operation(s) for the selected branch and
 * auto-selects them. No-op outside recall mode; idempotent once a service is selected.
 */
export const recallServiceGuard: CanActivateFn = async () => {
  const store = inject(BookingStore);

  if (store.isRecallMode() && !store.hasServicesSelected()) {
    await store.applyRecallService();
  }

  return true;
};

/**
 * REQ-041: the service step is skipped in recall mode. This guard keeps the service route
 * unreachable in recall mode (direct URL, browser history, a stray back navigation) by
 * redirecting to the brand step — otherwise the default-group catalogue would load and overwrite
 * the auto-selected recall operation (wrong operation booked on the recall billing client).
 */
export const recallBlocksServicesGuard: CanActivateFn = (): true | UrlTree => {
  const store = inject(BookingStore);
  const router = inject(Router);

  return store.isRecallMode() ? router.createUrlTree(['/home/brand']) : true;
};
