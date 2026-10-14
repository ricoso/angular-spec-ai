import { ChangeDetectionStrategy, Component, computed, inject, input, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';

import { i18nKeys, TranslatePipe, TranslateService } from '@core/i18n';

import { BookingStore } from '../../../features/booking/stores/booking.store';

import type { WizardStep, WizardStepStatus } from './wizard-breadcrumb.model';

@Component({
  selector: 'app-wizard-breadcrumb',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './wizard-breadcrumb.component.html',
  styleUrl: './wizard-breadcrumb.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None
})
export class WizardBreadcrumbComponent {
  private readonly store = inject(BookingStore);
  private readonly router = inject(Router);
  private readonly translateService = inject(TranslateService);

  public readonly steps = input.required<WizardStep[]>();
  public readonly activeIndex = input.required<number>();

  protected readonly wizard = i18nKeys.booking.wizard;
  protected readonly isDeepLinked = this.store.isDeepLinked;
  /** REQ-041: recall mode hides the service step (index 2 in WIZARD_STEPS). */
  protected readonly isRecallMode = this.store.isRecallMode;

  /** Original WIZARD_STEPS index of the service step (hidden in recall mode). */
  private static readonly SERVICE_STEP_INDEX = 2;

  protected readonly stepsWithStatus = computed(() => {
    // `activeIndex` is the ORIGINAL step index (0..6). We hide whole steps (location when deep-linked,
    // the service step in recall mode) and keep each visible step's original index for status + reset.
    const active = this.activeIndex();
    const hidden = new Set<number>();
    if (this.isDeepLinked()) { hidden.add(0); }
    if (this.isRecallMode()) { hidden.add(WizardBreadcrumbComponent.SERVICE_STEP_INDEX); }

    return this.steps()
      .map((step, originalIndex) => ({ step, originalIndex }))
      .filter(({ originalIndex }) => !hidden.has(originalIndex))
      .map(({ step, originalIndex }, i, arr) => {
        let status: WizardStepStatus;
        if (originalIndex < active) {
          status = 'done';
        } else if (originalIndex === active) {
          status = 'active';
        } else {
          status = 'future';
        }
        return { ...step, status, isLast: i === arr.length - 1, originalIndex };
      });
  });

  protected readonly activeStep = computed(() =>
    this.stepsWithStatus().find(step => step.status === 'active')
  );

  /** REQ-039 mockup: "Schritt X von Y" under the compact title (visible steps, deep link aware). */
  protected readonly stepCounter = computed(() => {
    const visible = this.stepsWithStatus();
    const position = visible.findIndex(step => step.status === 'active') + 1;
    return this.translateService.instant(this.wizard.counter)
      .replace('{current}', String(position))
      .replace('{total}', String(visible.length));
  });

  protected onStepClick(event: Event, originalIndex: number): void {
    event.preventDefault();
    const allSteps = this.steps();
    const active = this.activeIndex();
    if (originalIndex < 0 || originalIndex >= active || originalIndex >= allSteps.length) { return; }

    if (this.isDeepLinked() && originalIndex === 0) { return; }
    if (this.isRecallMode() && originalIndex === WizardBreadcrumbComponent.SERVICE_STEP_INDEX) { return; }

    this.store.resetFromStep(originalIndex);
    void this.router.navigate([allSteps[originalIndex].route]);
  }
}
