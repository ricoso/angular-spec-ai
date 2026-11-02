import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormBuilder, Validators } from '@angular/forms';

import { CustomerFormComponent } from './customer-form.component';

describe('CustomerFormComponent', () => {
  let component: CustomerFormComponent;
  let fixture: ComponentFixture<CustomerFormComponent>;
  let fb: FormBuilder;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CustomerFormComponent]
    })
      .overrideComponent(CustomerFormComponent, {
        set: { template: '<div class="mocked">Mocked Customer Form</div>' }
      })
      .compileComponents();

    fb = TestBed.inject(FormBuilder);
    fixture = TestBed.createComponent(CustomerFormComponent);
    component = fixture.componentInstance;

    const form = fb.group({
      email: ['', [Validators.required, Validators.email]],
      salutation: ['mr' as 'mr' | 'ms', [Validators.required]],
      firstName: ['', [Validators.required, Validators.pattern(/^[a-zA-ZÀ-ÖØ-öø-ÿ\s-]+$/)]],
      lastName: ['', [Validators.required, Validators.pattern(/^[a-zA-ZÀ-ÖØ-öø-ÿ\s-]+$/)]],
      street: ['', [Validators.required]],
      postalCode: ['', [Validators.required, Validators.pattern(/^\d{5}$/)]],
      city: ['', [Validators.required, Validators.pattern(/^[a-zA-ZÀ-ÖØ-öø-ÿ\s-]+$/)]],
      mobilePhone: ['', [Validators.required, Validators.pattern(/^(\+|0)[0-9\s]+$/)]]
    });

    fixture.componentRef.setInput('form', form);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have a form input', () => {
    const exposed = component as unknown as { form: () => unknown };
    expect(exposed.form()).toBeDefined();
  });

  describe('Error computeds', () => {
    it('should return null emailErrors when not touched', () => {
      const exposed = component as unknown as { emailErrors: () => string | null };
      expect(exposed.emailErrors()).toBeNull();
    });

    it('should return required error key when email touched and empty', () => {
      const exposed = component as unknown as {
        emailErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('email');
      ctrl?.markAsTouched();
      expect(exposed.emailErrors()).toBe('booking.carinformation.form.email.error.required');
    });

    it('should show required errors after markAllAsTouched without any input (submit case)', () => {
      const exposed = component as unknown as {
        emailErrors: () => string | null;
        firstNameErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      // Read first so the computeds are cached — they must still react to the touched change.
      expect(exposed.emailErrors()).toBeNull();
      expect(exposed.firstNameErrors()).toBeNull();
      exposed.form().markAllAsTouched();
      expect(exposed.emailErrors()).toBe('booking.carinformation.form.email.error.required');
      expect(exposed.firstNameErrors()).toBe('booking.carinformation.form.firstName.error.required');
    });

    it('should return email error key when email invalid format', () => {
      const exposed = component as unknown as {
        emailErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('email');
      ctrl?.setValue('not-an-email');
      ctrl?.markAsTouched();
      expect(exposed.emailErrors()).toBe('booking.carinformation.form.email.error.invalid');
    });

    it('should return null emailErrors when valid', () => {
      const exposed = component as unknown as {
        emailErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('email');
      ctrl?.setValue('valid@email.com');
      ctrl?.markAsTouched();
      expect(exposed.emailErrors()).toBeNull();
    });

    it('should return null firstNameErrors when not touched', () => {
      const exposed = component as unknown as { firstNameErrors: () => string | null };
      expect(exposed.firstNameErrors()).toBeNull();
    });

    it('should return required error key when firstName touched and empty', () => {
      const exposed = component as unknown as {
        firstNameErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('firstName');
      ctrl?.markAsTouched();
      expect(exposed.firstNameErrors()).toBe('booking.carinformation.form.firstName.error.required');
    });

    it('should return lettersOnly error key when firstName contains numbers', () => {
      const exposed = component as unknown as {
        firstNameErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('firstName');
      ctrl?.setValue('Max123');
      ctrl?.markAsTouched();
      expect(exposed.firstNameErrors()).toBe('booking.carinformation.form.firstName.error.lettersOnly');
    });

    it('should return null firstNameErrors when valid', () => {
      const exposed = component as unknown as {
        firstNameErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('firstName');
      ctrl?.setValue('Max');
      ctrl?.markAsTouched();
      expect(exposed.firstNameErrors()).toBeNull();
    });

    it('should return null lastNameErrors when not touched', () => {
      const exposed = component as unknown as { lastNameErrors: () => string | null };
      expect(exposed.lastNameErrors()).toBeNull();
    });

    it('should return required error key when lastName touched and empty', () => {
      const exposed = component as unknown as {
        lastNameErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('lastName');
      ctrl?.markAsTouched();
      expect(exposed.lastNameErrors()).toBe('booking.carinformation.form.lastName.error.required');
    });

    it('should return lettersOnly error key when lastName contains numbers', () => {
      const exposed = component as unknown as {
        lastNameErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('lastName');
      ctrl?.setValue('Smith99');
      ctrl?.markAsTouched();
      expect(exposed.lastNameErrors()).toBe('booking.carinformation.form.lastName.error.lettersOnly');
    });

    it('should return null streetErrors when not touched', () => {
      const exposed = component as unknown as { streetErrors: () => string | null };
      expect(exposed.streetErrors()).toBeNull();
    });

    it('should return required error key when street touched and empty', () => {
      const exposed = component as unknown as {
        streetErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('street');
      ctrl?.markAsTouched();
      expect(exposed.streetErrors()).toBe('booking.carinformation.form.street.error.required');
    });

    it('should return null for postalCode when pattern matches', () => {
      const exposed = component as unknown as {
        postalCodeErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('postalCode');
      ctrl?.setValue('12345');
      ctrl?.markAsTouched();
      expect(exposed.postalCodeErrors()).toBeNull();
    });

    it('should return postalCode error key when pattern fails', () => {
      const exposed = component as unknown as {
        postalCodeErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('postalCode');
      ctrl?.setValue('abc');
      ctrl?.markAsTouched();
      expect(exposed.postalCodeErrors()).toBe('booking.carinformation.form.postalCode.error.digitsOnly');
    });

    it('should return required error key for postalCode when empty', () => {
      const exposed = component as unknown as {
        postalCodeErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('postalCode');
      ctrl?.markAsTouched();
      expect(exposed.postalCodeErrors()).toBe('booking.carinformation.form.postalCode.error.required');
    });

    it('should return null cityErrors when not touched', () => {
      const exposed = component as unknown as { cityErrors: () => string | null };
      expect(exposed.cityErrors()).toBeNull();
    });

    it('should return required error key when city touched and empty', () => {
      const exposed = component as unknown as {
        cityErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('city');
      ctrl?.markAsTouched();
      expect(exposed.cityErrors()).toBe('booking.carinformation.form.city.error.required');
    });

    it('should return lettersOnly error key when city contains digits', () => {
      const exposed = component as unknown as {
        cityErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('city');
      ctrl?.setValue('Berlin123');
      ctrl?.markAsTouched();
      expect(exposed.cityErrors()).toBe('booking.carinformation.form.city.error.lettersOnly');
    });

    it('should return null cityErrors when valid', () => {
      const exposed = component as unknown as {
        cityErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('city');
      ctrl?.setValue('Berlin');
      ctrl?.markAsTouched();
      expect(exposed.cityErrors()).toBeNull();
    });

    it('should return null mobilePhoneErrors when not touched', () => {
      const exposed = component as unknown as { mobilePhoneErrors: () => string | null };
      expect(exposed.mobilePhoneErrors()).toBeNull();
    });

    it('should return required error key when mobilePhone touched and empty', () => {
      const exposed = component as unknown as {
        mobilePhoneErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mobilePhone');
      ctrl?.markAsTouched();
      expect(exposed.mobilePhoneErrors()).toBe('booking.carinformation.form.mobilePhone.error.required');
    });

    it('should return startsWithZero error key when mobilePhone does not start with 0', () => {
      const exposed = component as unknown as {
        mobilePhoneErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mobilePhone');
      ctrl?.setValue('1701234567');
      ctrl?.markAsTouched();
      expect(exposed.mobilePhoneErrors()).toBe('booking.carinformation.form.mobilePhone.error.startsWithZero');
    });

    it('should return null mobilePhoneErrors when valid', () => {
      const exposed = component as unknown as {
        mobilePhoneErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mobilePhone');
      ctrl?.setValue('017012345678');
      ctrl?.markAsTouched();
      expect(exposed.mobilePhoneErrors()).toBeNull();
    });
  });
});
