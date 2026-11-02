import { ChangeDetectionStrategy, Component, computed, effect, input, linkedSignal, output, signal } from '@angular/core';

import { i18nKeys, TranslatePipe } from '@core/i18n';

import type {
  AppointmentPreference,
  CallbackOption,
  NotesExtras,
  NotificationChannel
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
  /** REQ-039 BR-M1: initial with/without rental car choice. */
  public readonly initialRentalCarRequested = input<boolean>(false);
  /** REQ-039 BR-M8: false when the snap group has no rent categories — switch is hidden. */
  public readonly rentalCarOffered = input<boolean>(true);
  /** REQ-039 AC-M9: categories are known to be empty — show the "not available" note. */
  public readonly rentalCarUnavailable = input<boolean>(false);
  public readonly extrasChanged = output<NotesExtras>();
  /** REQ-039 BR-M5: optional category wish (`null` = no preference). */
  public readonly rentCategoryChanged = output<string | null>();
  public readonly rentalCarChanged = output<boolean>();

  protected readonly notes = i18nKeys.booking.notes;
  /** REQ-039 BR-M5: category wish, `null` = no preference — follows the input, writable by the user. */
  protected readonly selectedRentCategoryId = linkedSignal<string | null>(() => this.initialRentCategoryId());
  /** REQ-039 BR-M1: with/without rental car — follows the input, writable by the user. */
  protected readonly rentalCarRequested = linkedSignal<boolean>(() => this.initialRentalCarRequested());

  protected readonly appointmentPreference = signal<AppointmentPreference>(DEFAULT_NOTES_EXTRAS.appointmentPreference);
  protected readonly callbackOption = signal<CallbackOption>(DEFAULT_NOTES_EXTRAS.callbackOption);
  /** REQ-027: chosen notification channel (default 'email'). */
  protected readonly notificationChannel = signal<NotificationChannel>(DEFAULT_NOTES_EXTRAS.notificationChannel);

  /** Toggle-Switch State — abgeleitet vom callbackOption ('yes' = on, 'no' = off). */
  protected readonly callbackEnabled = computed<boolean>(() => this.callbackOption() === 'yes');

  protected readonly appointmentOptionItems = computed(() => [
    { value: 'anytime' as AppointmentPreference, label: this.notes.appointmentPreference.anytime },
    { value: 'morning' as AppointmentPreference, label: this.notes.appointmentPreference.morning },
    { value: 'afternoon' as AppointmentPreference, label: this.notes.appointmentPreference.afternoon }
  ]);

  protected readonly callbackOptionItems = computed(() => [
    { value: 'no' as CallbackOption, label: this.notes.callback.no },
    { value: 'yes' as CallbackOption, label: this.notes.callback.yes }
  ]);

  /** REQ-027: pill items for notification channel toggle. */
  protected readonly notificationChannelItems = computed(() => [
    { value: 'email' as NotificationChannel, label: this.notes.notification.email },
    { value: 'sms'   as NotificationChannel, label: this.notes.notification.sms }
  ]);

  constructor() {
    effect(() => {
      const extras = this.initialExtras();
      if (extras) {
        this.appointmentPreference.set(extras.appointmentPreference);
        this.callbackOption.set(extras.callbackOption);
        this.notificationChannel.set(extras.notificationChannel);
      }
    });
  }

  protected onRentCategoryChange(value: string | null): void {
    this.selectedRentCategoryId.set(value);
    this.rentCategoryChanged.emit(value);
  }

  /**
   * REQ-039 BR-M1: the switch only says "with rental car" — no category is picked automatically.
   * Switching off also drops the category wish.
   */
  protected onRentSwitchChange(checked: boolean): void {
    this.rentalCarRequested.set(checked);
    this.rentalCarChanged.emit(checked);
    if (!checked && this.selectedRentCategoryId() !== null) {
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

  /** REQ-027: user toggled notification channel (email <-> sms). */
  protected onNotificationChannelChange(value: NotificationChannel): void {
    this.notificationChannel.set(value);
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
      callbackOption: this.callbackOption(),
      notificationChannel: this.notificationChannel()
    });
  }
}
