import type {
  AppointmentPreference,
  CallbackOption,
  NotesExtras,
  NotificationChannel
} from './notes-extras.model';
import { DEFAULT_NOTES_EXTRAS, mapNotificationChannelToSoftnet } from './notes-extras.model';

describe('NotesExtras Model', () => {
  describe('DEFAULT_NOTES_EXTRAS', () => {
    it('should have correct default values', () => {
      expect(DEFAULT_NOTES_EXTRAS).toEqual({
        appointmentPreference: 'anytime',
        callbackOption: 'no',
        notificationChannel: 'email'
      });
    });

    it('should default notificationChannel to email (REQ-027)', () => {
      expect(DEFAULT_NOTES_EXTRAS.notificationChannel).toBe('email');
    });
  });

  describe('Type Safety', () => {
    it('should allow valid AppointmentPreference values', () => {
      const values: AppointmentPreference[] = ['anytime', 'morning', 'afternoon'];
      expect(values).toHaveLength(3);
    });

    it('should allow valid CallbackOption values', () => {
      const values: CallbackOption[] = ['no', 'yes'];
      expect(values).toHaveLength(2);
    });

    it('should allow valid NotificationChannel values (REQ-027)', () => {
      const values: NotificationChannel[] = ['email', 'sms'];
      expect(values).toHaveLength(2);
    });

    it('should create a valid NotesExtras object', () => {
      const extras: NotesExtras = {
        appointmentPreference: 'morning',
        callbackOption: 'yes',
        notificationChannel: 'sms'
      };
      expect(extras.appointmentPreference).toBe('morning');
      expect(extras.callbackOption).toBe('yes');
      expect(extras.notificationChannel).toBe('sms');
    });
  });

  describe('mapNotificationChannelToSoftnet (REQ-027)', () => {
    it('should map "email" to { SMS: false, SMTP: true }', () => {
      expect(mapNotificationChannelToSoftnet('email')).toEqual({ SMS: false, SMTP: true });
    });

    it('should map "sms" to { SMS: true, SMTP: false }', () => {
      expect(mapNotificationChannelToSoftnet('sms')).toEqual({ SMS: true, SMTP: false });
    });
  });
});
