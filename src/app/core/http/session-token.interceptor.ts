import type { HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';

import { from, switchMap, tap } from 'rxjs';

import { SessionTokenService } from './session-token.service';
import { TraceService } from './trace.service';

/**
 * Attaches the X-Session-Token and X-Correlation-Id headers to all /api/ requests.
 * Automatically fetches a token on first use and refreshes when expired.
 */
export const sessionTokenInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
) => {
  const sessionTokenService = inject(SessionTokenService);
  const trace = inject(TraceService);

  if (!req.url.includes('/api/') || req.url.includes('/api/v1/session/token')) {
    return next(req);
  }

  const correlationId = trace.createCorrelationId();
  trace.log('Interceptor', `→ ${req.method} ${req.url}`, { correlationId });

  return from(sessionTokenService.ensureToken()).pipe(
    switchMap((token) => {
      trace.log('Interceptor', `Token acquired, sending ${req.method} ${req.url}`, { correlationId });
      const headers: Record<string, string> = {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'X-Session-Token': token,
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'X-Correlation-Id': correlationId,
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'X-Session-Id': trace.sessionId,
      };
      const authReq = req.clone({ setHeaders: headers });
      return next(authReq).pipe(
        tap({
          next: () => { trace.log('Interceptor', `← ${req.method} ${req.url} OK`, { correlationId }); },
          error: (err: unknown) => { trace.error('Interceptor', `← ${req.method} ${req.url} FAILED`, { correlationId, error: err }); },
        }),
      );
    }),
  );
};
