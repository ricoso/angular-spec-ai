import { HttpErrorResponse, type HttpHandlerFn, type HttpInterceptorFn, type HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';

import { catchError, throwError } from 'rxjs';

import { AppErrorStore } from './app-error.store';
import type { CriticalError, CriticalErrorCode } from './models/critical-error.model';

const AUTH_LOOP_THRESHOLD = 3;
let consecutive401Count = 0;

/**
 * App-wide error escalation interceptor.
 *
 * Watches every HTTP error and decides whether it is a *critical* failure that
 * should take over the entire UI (fallback screen) — or just a per-request
 * problem that the calling component handles itself (REQ-017 inline retry).
 *
 * Promotes to {@link AppErrorStore} for:
 * - 5xx server errors (after circuit-breaker has stopped retrying)
 * - 3+ consecutive 401 responses (token-refresh dead-end → AUTH_LOOP)
 * - Network / DNS / timeout errors (`status === 0` → NETWORK)
 *
 * Auth-loop counter resets on every successful (non-401) response.
 */
export const errorFallbackInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const store = inject(AppErrorStore);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        const promoted = classify(err);
        if (promoted) {
          store.setCriticalError(promoted);
        }
        if (err.status !== 401) {
          consecutive401Count = 0;
        }
      }
      return throwError(() => err);
    })
  );
};

function classify(err: HttpErrorResponse): CriticalError | null {
  const requestId = extractRequestId(err);
  const timestamp = new Date().toISOString();

  if (err.status === 0) {
    return build('NETWORK', 'Network unreachable or request timed out', requestId, timestamp, err.status);
  }

  if (err.status === 401) {
    consecutive401Count++;
    if (consecutive401Count >= AUTH_LOOP_THRESHOLD) {
      return build('AUTH_LOOP', 'Repeated authentication failure', requestId, timestamp, err.status);
    }
    return null;
  }

  if (err.status >= 500 && err.status < 600) {
    return build('SERVER_5XX', `Server error ${err.status}`, requestId, timestamp, err.status);
  }

  return null;
}

function build(
  code: CriticalErrorCode,
  message: string,
  requestId: string | null,
  timestamp: string,
  httpStatus: number
): CriticalError {
  return { code, message, requestId, timestamp, httpStatus };
}

function extractRequestId(err: HttpErrorResponse): string | null {
  const header = err.headers?.get('X-Request-Id') ?? err.headers?.get('x-request-id');
  if (header && header.trim().length > 0) {
    return header;
  }
  const body = err.error;
  if (body && typeof body === 'object' && 'requestId' in body && typeof body.requestId === 'string') {
    return body.requestId;
  }
  return null;
}

// exported for tests only — resets the auth-loop counter between specs
export function _resetAuthLoopCounter(): void {
  consecutive401Count = 0;
}
