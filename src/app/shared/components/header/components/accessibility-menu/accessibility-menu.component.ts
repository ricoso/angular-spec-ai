import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

import type { Language, LanguageOption } from '@app/core/i18n';
import { AVAILABLE_LANGUAGES, i18nKeys, TranslatePipe } from '@app/core/i18n';
import type { FontSize } from '@app/shared/models/accessibility.model';

/**
 * Presentational Component for Accessibility Menu
 * Shows font-size pill-group, switches for high contrast / reduced motion,
 * and a language selector (currently only DE — translations EN/UK/FR/AR remain in code).
 */
@Component({
  selector: 'app-accessibility-menu',
  standalone: true,
  imports: [
    FormsModule,
    TranslatePipe
  ],
  templateUrl: './accessibility-menu.component.html',
  styleUrl: './accessibility-menu.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AccessibilityMenuComponent {
  protected readonly a11y = i18nKeys.header.accessibility;

  // Inputs (Presentational: input/output only, NO Store!)
  public readonly fontSize = input.required<FontSize>();
  public readonly highContrast = input.required<boolean>();
  public readonly reducedMotion = input.required<boolean>();
  public readonly currentLanguage = input.required<Language>();

  // Outputs
  public readonly fontSizeChanged = output<FontSize>();
  public readonly highContrastChanged = output<boolean>();
  public readonly reducedMotionChanged = output<boolean>();
  public readonly languageChanged = output<Language>();

  // Available options
  protected readonly fontSizes: FontSize[] = ['small', 'normal', 'large', 'x-large'];
  protected readonly languages: LanguageOption[] = AVAILABLE_LANGUAGES;

  protected onFontSizeChange(size: FontSize): void {
    this.fontSizeChanged.emit(size);
  }

  protected onHighContrastChange(value: boolean): void {
    this.highContrastChanged.emit(value);
  }

  protected onReducedMotionChange(value: boolean): void {
    this.reducedMotionChanged.emit(value);
  }

  protected onLanguageChange(language: Language): void {
    this.languageChanged.emit(language);
  }
}
