import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { TranslateService } from '@core/i18n';

import { NotesFormComponent } from './notes-form.component';

describe('NotesFormComponent', () => {
  let component: NotesFormComponent;
  let fixture: ComponentFixture<NotesFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotesFormComponent]
    })
      .overrideComponent(NotesFormComponent, {
        set: { template: '<div class="mocked">Mocked Notes Form</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(NotesFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Form Control', () => {
    it('should have empty initial value', () => {
      const exposed = component as unknown as {
        noteControl: { value: string };
      };
      expect(exposed.noteControl.value).toBe('');
    });

    it('should have maxLength validator', () => {
      const exposed = component as unknown as {
        noteControl: { setValue: (v: string) => void; valid: boolean };
      };
      const longText = 'a'.repeat(1001);
      exposed.noteControl.setValue(longText);
      expect(exposed.noteControl.valid).toBe(false);
    });

    it('should accept text within maxLength', () => {
      const exposed = component as unknown as {
        noteControl: { setValue: (v: string) => void; valid: boolean };
      };
      const validText = 'a'.repeat(1000);
      exposed.noteControl.setValue(validText);
      expect(exposed.noteControl.valid).toBe(true);
    });
  });

  describe('charCountLabel (screen reader text of the counter)', () => {
    it('should follow the entered length and the UI language', () => {
      const translate = TestBed.inject(TranslateService);
      translate.use('de');
      const exposed = component as unknown as {
        noteControl: { setValue: (v: string) => void };
        charCountLabel: () => string;
      };
      exposed.noteControl.setValue('Hello');
      expect(exposed.charCountLabel()).toBe('5 von 1000 Zeichen verwendet');
      translate.use('en');
      expect(exposed.charCountLabel()).toBe('5 of 1000 characters used');
      translate.use('de');
    });
  });

  describe('currentLength', () => {
    it('should be 0 initially', () => {
      const exposed = component as unknown as { currentLength: () => number };
      expect(exposed.currentLength()).toBe(0);
    });

    it('should update when text is entered', () => {
      const exposed = component as unknown as {
        noteControl: { setValue: (v: string) => void };
        currentLength: () => number;
      };
      exposed.noteControl.setValue('Hello');
      expect(exposed.currentLength()).toBe(5);
    });
  });

  describe('noteChanged output', () => {
    it('should emit on value changes', () => {
      const spy = jest.fn();
      component.noteChanged.subscribe(spy);

      const exposed = component as unknown as {
        noteControl: { setValue: (v: string) => void };
      };
      exposed.noteControl.setValue('Test note');

      expect(spy).toHaveBeenCalledWith('Test note');
    });

    it('should emit null for empty text', () => {
      const spy = jest.fn();
      component.noteChanged.subscribe(spy);

      const exposed = component as unknown as {
        noteControl: { setValue: (v: string) => void };
      };
      exposed.noteControl.setValue('');

      expect(spy).toHaveBeenCalledWith(null);
    });

    it('should emit null for whitespace-only text', () => {
      const spy = jest.fn();
      component.noteChanged.subscribe(spy);

      const exposed = component as unknown as {
        noteControl: { setValue: (v: string) => void };
      };
      exposed.noteControl.setValue('   ');

      expect(spy).toHaveBeenCalledWith(null);
    });
  });

  describe('initialNote input', () => {
    it('should prefill form control when initialNote is set', () => {
      fixture.componentRef.setInput('initialNote', 'Prefilled note');
      fixture.detectChanges();

      const exposed = component as unknown as {
        noteControl: { value: string };
        currentLength: () => number;
      };
      expect(exposed.noteControl.value).toBe('Prefilled note');
      expect(exposed.currentLength()).toBe(14);
    });

    it('should not change form when initialNote is null', () => {
      fixture.componentRef.setInput('initialNote', null);
      fixture.detectChanges();

      const exposed = component as unknown as {
        noteControl: { value: string };
      };
      expect(exposed.noteControl.value).toBe('');
    });
  });

  describe('maxLength constant', () => {
    it('should expose maxLength as 1000', () => {
      const exposed = component as unknown as { maxLength: number };
      expect(exposed.maxLength).toBe(1000);
    });
  });
});
