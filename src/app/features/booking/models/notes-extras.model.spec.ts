import type { AppointmentPreference, CallbackOption, NotesExtras } from './notes-extras.model';
import { DEFAULT_NOTES_EXTRAS } from './notes-extras.model';

describe('NotesExtras Model', () => {
  describe('DEFAULT_NOTES_EXTRAS', () => {
    it('should have correct default values', () => {
      expect(DEFAULT_NOTES_EXTRAS).toEqual({
        appointmentPreference: 'anytime',
        callbackOption: 'no'
      });
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

    it('should create a valid NotesExtras object', () => {
      const extras: NotesExtras = {
        appointmentPreference: 'morning',
        callbackOption: 'yes'
      };
      expect(extras.appointmentPreference).toBe('morning');
      expect(extras.callbackOption).toBe('yes');
    });
  });
});
