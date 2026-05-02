import { ChangeDetectionStrategy, Component, computed, inject, input, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';

import { TranslatePipe } from '@core/i18n';

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

  public readonly steps = input.required<WizardStep[]>();
  public readonly activeIndex = input.required<number>();

  protected readonly isDeepLinked = this.store.isDeepLinked;

  protected readonly stepsWithStatus = computed(() => {
    const active = this.activeIndex();
    const deepLinked = this.isDeepLinked();
    return this.steps()
      .filter((_, i) => !(deepLinked && i === 0))
      .map((step, i, arr) => {
        const originalIndex = deepLinked ? i + 1 : i;
        const adjustedActive = deepLinked ? active - 1 : active;
        let status: WizardStepStatus;
        if (i < adjustedActive) {
          status = 'done';
        } else if (i === adjustedActive) {
          status = 'active';
        } else {
          status = 'future';
        }
        return { ...step, status, isLast: i === arr.length - 1, originalIndex };
      });
  });

  protected onStepClick(event: Event, originalIndex: number): void {
    event.preventDefault();
    const allSteps = this.steps();
    const active = this.activeIndex();
    if (originalIndex >= active || originalIndex >= allSteps.length) { return; }

    if (this.isDeepLinked() && originalIndex === 0) { return; }

    this.store.resetFromStep(originalIndex);
    void this.router.navigate([allSteps[originalIndex].route]);
  }
}
