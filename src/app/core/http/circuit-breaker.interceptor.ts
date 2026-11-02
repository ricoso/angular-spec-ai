import type { HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';

import { catchError, tap, throwError } from 'rxjs';

import { CircuitBreakerService } from './circuit-breaker.service';

/**
 * HTTP Interceptor that applies circuit breaker pattern to all /api/ calls.
 * When the backend is unreachable (5 consecutive failures), the circuit opens
 * and rejects requests immediately for 30s before probing again.
 */
export const circuitBreakerInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
) => {
  const circuitBreaker = inject(CircuitBreakerService);

  // Only apply to API calls
  if (!req.url.includes('/api/')) {
    return next(req);
  }

  // Reject immediately if circuit is open
  if (circuitBreaker.isOpen()) {
    console.warn(`[CircuitBreaker] Request blocked (circuit OPEN): ${req.method} ${req.url}`);
    return throwError(() => new HttpErrorResponse({
      status: 503,
      statusText: 'Service Unavailable',
      url: req.url,
      error: { message: 'Backend temporarily unavailable. Please try again later.' },
    }));
  }

  return next(req).pipe(
    tap(() => { circuitBreaker.recordSuccess(); }),
    catchError((error: HttpErrorResponse) => {
      // Only count server errors and network failures as circuit breaker failures
      if (error.status === 0 || error.status >= 500) {
        circuitBreaker.recordFailure();
      }
      return throwError(() => error);
    }),
  );
};
