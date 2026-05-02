import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';

import { AppErrorStore } from '@app/core/error-handling/app-error.store';
import { i18nKeys, TranslatePipe } from '@core/i18n';
import { FloatingOverlayComponent } from '@shared/components/floating-overlay/floating-overlay.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, FloatingOverlayComponent, TranslatePipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent implements OnInit {
  protected readonly app = i18nKeys.app;

  private readonly errorStore = inject(AppErrorStore);
  private readonly router = inject(Router);

  constructor() {
    effect(() => {
      if (this.errorStore.hasCriticalError() && !this.router.url.startsWith('/app-error')) {
        void this.router.navigate(['/app-error']);
      }
    });
  }

  /**
   * REQ-025: Bei jedem Page-Reload zurück zur Location-Route. Der Wizard-State ist
   * nicht persistiert. setTimeout(0) sorgt dafür, dass der Redirect NACH dem initialen
   * Routing greift (sonst überschreibt das Initial-Routing unsere Navigation).
   * Ausnahme: /app-error darf nach Reload erreichbar bleiben.
   */
  public ngOnInit(): void {
    setTimeout(() => {
      const path = window.location.pathname + window.location.hash;
      if (!path.includes('/app-error') && !path.endsWith('/home/location')) {
        void this.router.navigateByUrl('/home/location', { replaceUrl: true });
      }
    }, 0);
  }
}
