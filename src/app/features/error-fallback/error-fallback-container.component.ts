import type { ElementRef} from '@angular/core';
import { afterNextRender,ChangeDetectionStrategy, Component, computed, inject, viewChild  } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

import { AppErrorStore } from '@app/core/error-handling/app-error.store';
import { BookingStore } from '@app/features/booking/stores/booking.store';
import { i18nKeys, TranslatePipe } from '@core/i18n';

const FALLBACK_PHONE = '+49 211 1234567';

@Component({
  selector: 'app-error-fallback-container',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslatePipe],
  templateUrl: './error-fallback-container.component.html',
  styleUrl: './error-fallback-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ErrorFallbackContainerComponent {
  private readonly errorStore = inject(AppErrorStore);
  private readonly bookingStore = inject(BookingStore);
  private readonly router = inject(Router);

  protected readonly error = i18nKeys.app.error;
  protected readonly criticalError = this.errorStore.criticalError;
  protected readonly isOffline = computed(() => typeof navigator !== 'undefined' && !navigator.onLine);

  protected readonly phoneNumber = computed(() => {
    const branch = this.bookingStore.selectedBranch();
    const branchPhone = branch && 'phone' in branch ? (branch as { phone?: string }).phone : undefined;
    return branchPhone && branchPhone.length > 0 ? branchPhone : FALLBACK_PHONE;
  });

  protected readonly phoneTelHref = computed(() => `tel:${this.phoneNumber().replace(/\s+/g, '')}`);

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
