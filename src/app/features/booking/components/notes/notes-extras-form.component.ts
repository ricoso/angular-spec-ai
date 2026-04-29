import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type {
  AppointmentPreference,
  CallbackOption,
  NotesExtras
} from '../../models/notes-extras.model';
import { DEFAULT_NOTES_EXTRAS } from '../../models/notes-extras.model';
import type { RentCategory } from '../../models/rent-category.model';

@Component({
  selector: 'app-notes-extras-form',
  standalone: true,
  imports: [MatFormFieldModule, MatSelectModule, MatIconModule, TranslatePipe],
  templateUrl: './notes-extras-form.component.html',
  styleUrl: './notes-extras-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotesExtrasFormComponent {
  public readonly initialExtras = input<NotesExtras | null>(null);
  public readonly rentCategories = input<RentCategory[]>([]);
  public readonly initialRentCategoryId = input<string | null>(null);
  public readonly extrasChanged = output<NotesExtras>();
  public readonly rentCategoryChanged = output<string | null>();

  protected readonly notes = i18nKeys.booking.notes;
  protected readonly selectedRentCategoryId = signal<string | null>(null);

  protected readonly appointmentPreference = signal<AppointmentPreference>(DEFAULT_NOTES_EXTRAS.appointmentPreference);
  protected readonly callbackOption = signal<CallbackOption>(DEFAULT_NOTES_EXTRAS.callbackOption);

  protected readonly appointmentOptionItems = computed(() => [
    { value: 'anytime' as AppointmentPreference, label: this.notes.appointmentPreference.anytime },
    { value: 'morning' as AppointmentPreference, label: this.notes.appointmentPreference.morning },
    { value: 'afternoon' as AppointmentPreference, label: this.notes.appointmentPreference.afternoon }
  ]);

  protected readonly callbackOptionItems = computed(() => [
    { value: 'no' as CallbackOption, label: this.notes.callback.no },
    { value: 'yes' as CallbackOption, label: this.notes.callback.yes }
  ]);

  /** REQ-025: Description (inkl. Preis) der gewählten Mietwagen-Kategorie aus SoftNet. */
  protected readonly selectedRentCategoryDescription = computed<string | null>(() => {
    const id = this.selectedRentCategoryId();
    if (!id) return null;
    const cat = this.rentCategories().find(c => c.id === id);
    return cat?.description?.trim() ? cat.description : null;
  });

  constructor() {
    effect(() => {
      const extras = this.initialExtras();
      if (extras) {
        this.appointmentPreference.set(extras.appointmentPreference);
        this.callbackOption.set(extras.callbackOption);
      }
    });

    effect(() => {
      this.selectedRentCategoryId.set(this.initialRentCategoryId());
    });
  }

  protected onRentCategoryChange(value: string | null): void {
    this.selectedRentCategoryId.set(value);
    this.rentCategoryChanged.emit(value);
  }

  protected onAppointmentPreferenceChange(value: AppointmentPreference): void {
    this.appointmentPreference.set(value);
    this.emitExtras();
  }

  protected onCallbackOptionChange(value: CallbackOption): void {
    this.callbackOption.set(value);
    this.emitExtras();
  }

  private emitExtras(): void {
    this.extrasChanged.emit({
      appointmentPreference: this.appointmentPreference(),
      callbackOption: this.callbackOption()
    });
  }
}
