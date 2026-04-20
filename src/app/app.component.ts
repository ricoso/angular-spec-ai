import { ChangeDetectionStrategy,Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { i18nKeys,TranslatePipe } from '@core/i18n';
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
}
