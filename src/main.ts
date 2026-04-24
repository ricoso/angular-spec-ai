import { bootstrapApplication } from '@angular/platform-browser';

import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

// iframe-Einbettung detektieren fuer kontextabhaengiges CSS (siehe styles.scss .is-embedded).
try {
  if (window.self !== window.top) {
    document.documentElement.classList.add('is-embedded');
  }
} catch {
  // Cross-origin-Zugriff auf window.top wirft — dann embedded annehmen.
  document.documentElement.classList.add('is-embedded');
}

bootstrapApplication(AppComponent, appConfig)
  .catch((err: unknown) => { console.error(err); });
