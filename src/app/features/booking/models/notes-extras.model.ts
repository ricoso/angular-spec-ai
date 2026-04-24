/**
 * Appointment preference for scheduling
 */
export type AppointmentPreference = 'anytime' | 'morning' | 'afternoon';

/**
 * Callback option
 */
export type CallbackOption = 'none' | 'yes';

/**
 * Extended notes extras — dropdown selections on notes page.
 * Mietwagen-Kategorie kommt separat als rentCategoryId im Store (dynamisch aus SoftNet).
 */
export interface NotesExtras {
  appointmentPreference: AppointmentPreference;
  callbackOption: CallbackOption;
}

export const DEFAULT_NOTES_EXTRAS: NotesExtras = {
  appointmentPreference: 'anytime',
  callbackOption: 'none'
};
