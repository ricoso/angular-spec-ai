import { TestBed } from '@angular/core/testing';

import { AppErrorStore } from './app-error.store';
import type { CriticalError } from './models/critical-error.model';

describe('AppErrorStore', () => {
  let store: InstanceType<typeof AppErrorStore>;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(AppErrorStore);
  });

  it('should start with no error', () => {
    expect(store.criticalError()).toBeNull();
    expect(store.hasCriticalError()).toBe(false);
  });

  it('should set a critical error', () => {
    const err: CriticalError = {
      code: 'NETWORK',
      message: 'offline',
      requestId: 'req-1',
      timestamp: '2026-04-27T12:00:00Z',
      httpStatus: 0
    };
    store.setCriticalError(err);
    expect(store.criticalError()).toEqual(err);
    expect(store.hasCriticalError()).toBe(true);
  });

  it('should clear a critical error', () => {
    store.setCriticalError({
      code: 'SERVER_5XX',
      message: 'down',
      requestId: null,
      timestamp: '2026-04-27T12:00:00Z',
      httpStatus: 503
    });
    store.clearCriticalError();
    expect(store.criticalError()).toBeNull();
    expect(store.hasCriticalError()).toBe(false);
  });

  it('should overwrite existing error on second setCriticalError', () => {
    const first: CriticalError = { code: 'NETWORK', message: 'a', requestId: null, timestamp: 't1' };
    const second: CriticalError = { code: 'AUTH_LOOP', message: 'b', requestId: 'r2', timestamp: 't2' };
    store.setCriticalError(first);
    store.setCriticalError(second);
    expect(store.criticalError()).toEqual(second);
  });
});
