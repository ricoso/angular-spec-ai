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
export class AppComponent {
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
}
