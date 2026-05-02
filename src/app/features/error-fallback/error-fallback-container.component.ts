import type { ElementRef} from '@angular/core';
import { afterNextRender,ChangeDetectionStrategy, Component, computed, inject, viewChild  } from '@angular/core';
import { Router } from '@angular/router';

import { AppErrorStore } from '@app/core/error-handling/app-error.store';
import { i18nKeys, TranslatePipe } from '@core/i18n';

@Component({
  selector: 'app-error-fallback-container',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './error-fallback-container.component.html',
  styleUrl: './error-fallback-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ErrorFallbackContainerComponent {
  private readonly errorStore = inject(AppErrorStore);
  private readonly router = inject(Router);

  protected readonly error = i18nKeys.app.error;
  protected readonly criticalError = this.errorStore.criticalError;
  protected readonly isOffline = computed(() => typeof navigator !== 'undefined' && !navigator.onLine);

  protected readonly retryButton = viewChild<ElementRef<HTMLButtonElement>>('retryButton');

  constructor() {
    afterNextRender(() => {
      this.retryButton()?.nativeElement.focus();
    });
  }

  protected onRetry(): void {
    this.errorStore.clearCriticalError();
    void this.router.navigate(['/home/location']);
  }
}
