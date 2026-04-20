import { inject } from '@angular/core';
import type { CanActivateFn } from '@angular/router';
import { Router } from '@angular/router';

import { BookingApiService } from '../services/booking-api.service';
import { BookingStore } from '../stores/booking.store';

/**
 * Deep-link guard: checks for groupId + branchId query params.
 * If present, fetches branch from API, stores it, skips location selection.
 * Invalid params silently fall back to normal flow.
 */
export const deepLinkGuard: CanActivateFn = async (route) => {
  const store = inject(BookingStore);
  const api = inject(BookingApiService);
  const router = inject(Router);

  const branchId = route.queryParamMap.get('branchId');
  const groupId = route.queryParamMap.get('groupId');

  if (!branchId || !groupId) {
    return true;
  }

  console.debug('[deepLinkGuard] Deep-link detected — branchId:', branchId, 'groupId:', groupId);

  try {
    const branch = await api.getBranchById(branchId);
    if (!branch) {
      console.warn('[deepLinkGuard] Branch not found, falling back to normal flow');
      return true;
    }

    store.selectBranch(branch);
    store.setDeepLinked(true);
    store.loadBrands(branch.branchId);

    return router.createUrlTree(['/home/brand'], { queryParams: { groupId, branchId } });
  } catch (err) {
    console.warn('[deepLinkGuard] Error fetching branch, falling back to normal flow:', err);
    return true;
  }
};
