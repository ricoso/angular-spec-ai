import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { i18nKeys, TranslatePipe } from '@app/core/i18n';

/**
 * Container Component for the Application Header.
 * Cart-Icon + Accessibility-Menu sind in <app-floating-overlay /> ausgelagert
 * (FABs unten links/rechts). Hier nur noch Logo, Title, Close-Button (iframe).
 */
const IFRAME_RETURN_URL = 'https://www.gottfried-schultz.de/werkstatt-termin/';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [
    RouterLink,
    TranslatePipe
  ],
  templateUrl: './header-container.component.html',
  styleUrl: './header-container.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HeaderContainerComponent {
  protected readonly app = i18nKeys.app;
  protected readonly header = i18nKeys.header;

  protected readonly isEmbedded = signal(this.detectEmbedded());

  protected onClose(): void {
    const target = window.top ?? window;
    target.location.href = IFRAME_RETURN_URL;
  }

  private detectEmbedded(): boolean {
    try {
      return window.self !== window.top;
    } catch {
      return true;
    }
  }
}
