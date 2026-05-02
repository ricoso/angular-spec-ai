import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';

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
  imports: [TranslatePipe],
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

  /** Toggle-Switch State — abgeleitet vom rentCategoryId. */
  protected readonly rentEnabled = computed<boolean>(() => this.selectedRentCategoryId() !== null);

  /** Toggle-Switch State — abgeleitet vom callbackOption ('yes' = on, 'no' = off). */
  protected readonly callbackEnabled = computed<boolean>(() => this.callbackOption() === 'yes');

  protected readonly appointmentOptionItems = computed(() => [
    { value: 'anytime' as AppointmentPreference, label: this.notes.appointmentPreference.anytime, icon: null as string | null },
    { value: 'morning' as AppointmentPreference, label: this.notes.appointmentPreference.morning, icon: 'bi-sunrise' as string | null },
    { value: 'afternoon' as AppointmentPreference, label: this.notes.appointmentPreference.afternoon, icon: 'bi-sunset' as string | null }
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

  /**
   * Toggle-Switch fuer Mietwagen: ON = automatisch erste Kategorie waehlen,
   * OFF = Auswahl loeschen. Bewahrt Output-Vertrag (string | null).
   */
  protected onRentSwitchChange(checked: boolean): void {
    if (checked) {
      // Falls bereits eine Auswahl existiert, behalten — sonst erste Kategorie waehlen
      if (this.selectedRentCategoryId() === null) {
        const first = this.rentCategories()[0]?.id ?? null;
        this.onRentCategoryChange(first);
      }
    } else {
      this.onRentCategoryChange(null);
    }
  }

  /** Wrapper: Event-Target -> boolean (vermeidet $any im Template). */
  protected onRentSwitchToggle(event: Event): void {
    const target = event.target as HTMLInputElement;
    this.onRentSwitchChange(target.checked);
  }

  protected onAppointmentPreferenceChange(value: AppointmentPreference): void {
    this.appointmentPreference.set(value);
    this.emitExtras();
  }

  protected onCallbackOptionChange(value: CallbackOption): void {
    this.callbackOption.set(value);
    this.emitExtras();
  }

  /**
   * Toggle-Switch fuer Rueckruf: mappt boolean -> 'yes'/'no' (Output-Vertrag).
   */
  protected onCallbackSwitchChange(checked: boolean): void {
    this.onCallbackOptionChange(checked ? 'yes' : 'no');
  }

  /** Wrapper: Event-Target -> boolean (vermeidet $any im Template). */
  protected onCallbackSwitchToggle(event: Event): void {
    const target = event.target as HTMLInputElement;
    this.onCallbackSwitchChange(target.checked);
  }

  private emitExtras(): void {
    this.extrasChanged.emit({
      appointmentPreference: this.appointmentPreference(),
      callbackOption: this.callbackOption()
    });
  }
}
