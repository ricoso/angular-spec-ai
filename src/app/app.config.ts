import { provideHttpClient, withInterceptors } from '@angular/common/http';
import type { ApplicationConfig} from '@angular/core';
import { provideZoneChangeDetection } from '@angular/core';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { circuitBreakerInterceptor, sessionTokenInterceptor } from './core/http';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([sessionTokenInterceptor, circuitBreakerInterceptor])),
    provideAnimations(),
    provideNativeDateAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: 'de-DE' }
  ]
};
