import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { i18nKeys, TranslatePipe } from '@core/i18n';
import type { NgbDate, NgbDateStruct } from '@ng-bootstrap/ng-bootstrap';
import { NgbDateAdapter, NgbDateNativeAdapter, NgbDatepickerModule } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-workshop-calendar-date-picker',
  standalone: true,
  imports: [TranslatePipe, FormsModule, NgbDatepickerModule],
  providers: [{ provide: NgbDateAdapter, useClass: NgbDateNativeAdapter }],
  templateUrl: './workshop-calendar-date-picker.component.html',
  styleUrl: './workshop-calendar-date-picker.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkshopCalendarDatePickerComponent {
  public readonly selectedDate = input<string | null>(null);
  public readonly dateSelected = output<Date>();

  protected readonly booking = i18nKeys.booking;
  protected readonly today = new Date();
  protected readonly minDate: NgbDateStruct = {
    year: this.today.getFullYear(),
    month: this.today.getMonth() + 1,
    day: this.today.getDate()
  };

  protected onDateChange(date: Date | null): void {
    if (date) {
      this.dateSelected.emit(date);
    }
  }

  protected onNgbDateSelect(ngbDate: NgbDate): void {
    // NgbDate (year/month/day) -> nativer Date (month ist in NgbDate 1-12, in Date 0-11)
    if (ngbDate) {
      this.onDateChange(new Date(ngbDate.year, ngbDate.month - 1, ngbDate.day));
    }
  }
}
