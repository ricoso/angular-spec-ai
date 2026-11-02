/**
 * Appointment preference for scheduling
 */
export type AppointmentPreference = 'anytime' | 'morning' | 'afternoon';

/**
 * Callback option (REQ-025 AC-12: nur noch 'no' | 'yes', kein 'none' mehr).
 */
export type CallbackOption = 'no' | 'yes';

/**
 * REQ-027: Notification channel for booking confirmations (E-Mail oder SMS).
 */
export type NotificationChannel = 'email' | 'sms';

/**
 * Extended notes extras — dropdown selections on notes page.
 * Mietwagen-Kategorie kommt separat als rentCategoryId im Store (dynamisch aus SoftNet).
 *
 * REQ-027: neues Feld `notificationChannel`.
 */
export interface NotesExtras {
  appointmentPreference: AppointmentPreference;
  callbackOption: CallbackOption;
  notificationChannel: NotificationChannel;
}

export const DEFAULT_NOTES_EXTRAS: NotesExtras = {
  appointmentPreference: 'anytime',
  callbackOption: 'no',
  notificationChannel: 'email'
};

/**
 * REQ-027: maps the UI-side notification channel to SoftNet's `notifications`
 * object (`api.planning.snaps.query`, `api.planning.appointments.add`).
 *
 * `email` → `{ SMS: false, SMTP: true }` (default)
 * `sms`   → `{ SMS: true,  SMTP: false }`
 */
export function mapNotificationChannelToSoftnet(
  channel: NotificationChannel
): { SMS: boolean; SMTP: boolean } {
  return channel === 'sms'
    ? { SMS: true, SMTP: false }
    : { SMS: false, SMTP: true };
}
