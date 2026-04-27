import type { Routes } from '@angular/router';

export const errorFallbackRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./error-fallback-container.component')
      .then(m => m.ErrorFallbackContainerComponent)
  }
];
