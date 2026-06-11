import { computed } from '@angular/core';

import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';

import type { CriticalError } from './models/critical-error.model';

interface AppErrorState {
  criticalError: CriticalError | null;
}

const INITIAL_STATE: AppErrorState = {
  criticalError: null
};

export const AppErrorStore = signalStore(
  { providedIn: 'root' },
  withState<AppErrorState>(INITIAL_STATE),
  withComputed(({ criticalError }) => ({
    hasCriticalError: computed(() => criticalError() !== null)
  })),
  withMethods((store) => ({
    setCriticalError(err: CriticalError): void {
      patchState(store, { criticalError: err });
    },
    clearCriticalError(): void {
      patchState(store, { criticalError: null });
    }
  }))
);
