/**
 * Appointment preference for scheduling
 */
export type AppointmentPreference = 'anytime' | 'morning' | 'afternoon';

/**
 * Callback option (REQ-025 AC-12: nur noch 'no' | 'yes', kein 'none' mehr).
 */
export type CallbackOption = 'no' | 'yes';

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
  callbackOption: 'no'
};
