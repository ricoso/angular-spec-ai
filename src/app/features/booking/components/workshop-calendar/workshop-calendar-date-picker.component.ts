import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

@Component({
  selector: 'app-workshop-calendar-date-picker',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './workshop-calendar-date-picker.component.html',
  styleUrl: './workshop-calendar-date-picker.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkshopCalendarDatePickerComponent {
  public readonly selectedDate = input<string | null>(null);
  public readonly dateSelected = output<Date>();

  protected readonly booking = i18nKeys.booking;

  private readonly today = new Date();
  // Native date input bounds as ISO strings (YYYY-MM-DD). The native picker
  // provides fast month/year navigation the ng-bootstrap popup did not render.
  protected readonly minIso = this.toIso(this.today);
  protected readonly maxIso = this.toIso(
    new Date(this.today.getFullYear() + 1, this.today.getMonth(), this.today.getDate())
  );

  protected onNativeDateChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value; // "YYYY-MM-DD" or ""
    if (!value) { return; }
    const [year, month, day] = value.split('-').map(Number);
    this.dateSelected.emit(new Date(year, month - 1, day));
  }

  private toIso(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
