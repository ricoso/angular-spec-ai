import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormBuilder, Validators } from '@angular/forms';

import { VehicleFormComponent } from './vehicle-form.component';

describe('VehicleFormComponent', () => {
  let component: VehicleFormComponent;
  let fixture: ComponentFixture<VehicleFormComponent>;
  let fb: FormBuilder;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VehicleFormComponent]
    })
      .overrideComponent(VehicleFormComponent, {
        set: { template: '<div class="mocked">Mocked Vehicle Form</div>' }
      })
      .compileComponents();

    fb = TestBed.inject(FormBuilder);
    fixture = TestBed.createComponent(VehicleFormComponent);
    component = fixture.componentInstance;

    const form = fb.group({
      licensePlate: ['', [Validators.required, Validators.pattern(/^[A-ZÄÖÜ]{1,3}-[A-Z]{1,2}\s?\d{1,4}$/i)]],
      // REQ-031 BR-6: spiegelt die echten Container-Validators (ganze Zahl >= 10)
      mileage: [null as number | null, [Validators.required, Validators.pattern(/^[0-9]+$/), Validators.min(10)]],
      vin: ['', [Validators.minLength(17), Validators.maxLength(17)]]
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
    it('should return null licensePlateErrors when not touched', () => {
      const exposed = component as unknown as { licensePlateErrors: () => string | null };
      expect(exposed.licensePlateErrors()).toBeNull();
    });

    it('should return required error key when licensePlate touched and empty', () => {
      const exposed = component as unknown as {
        licensePlateErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('licensePlate');
      ctrl?.markAsTouched();
      expect(exposed.licensePlateErrors()).toBe('booking.carinformation.form.licensePlate.error.required');
    });

    it('should return licensePlate error key when pattern fails', () => {
      const exposed = component as unknown as {
        licensePlateErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('licensePlate');
      ctrl?.setValue('INVALID-PLATE-!!!');
      ctrl?.markAsTouched();
      expect(exposed.licensePlateErrors()).toBe('booking.carinformation.form.licensePlate.error.invalidFormat');
    });

    it('should return null licensePlateErrors when valid', () => {
      const exposed = component as unknown as {
        licensePlateErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('licensePlate');
      ctrl?.setValue('B-AB 1234');
      ctrl?.markAsTouched();
      expect(exposed.licensePlateErrors()).toBeNull();
    });

    it('should return null mileageErrors when not touched', () => {
      const exposed = component as unknown as { mileageErrors: () => string | null };
      expect(exposed.mileageErrors()).toBeNull();
    });

    it('should return required error key when mileage touched and null', () => {
      const exposed = component as unknown as {
        mileageErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mileage');
      ctrl?.markAsTouched();
      expect(exposed.mileageErrors()).toBe('booking.carinformation.form.mileage.error.required');
    });

    it('should return digitsOnly error key when mileage is negative (pattern fails)', () => {
      const exposed = component as unknown as {
        mileageErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mileage');
      ctrl?.setValue(-1);
      ctrl?.markAsTouched();
      expect(exposed.mileageErrors()).toBe('booking.carinformation.form.mileage.error.digitsOnly');
    });

    it('should return digitsOnly error key for decimal mileage (REQ-031)', () => {
      const exposed = component as unknown as {
        mileageErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mileage');
      ctrl?.setValue(10.5);
      ctrl?.markAsTouched();
      expect(exposed.mileageErrors()).toBe('booking.carinformation.form.mileage.error.digitsOnly');
    });

    it('should return digitsOnly error key for non-numeric mileage (REQ-031)', () => {
      const exposed = component as unknown as {
        mileageErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mileage');
      ctrl?.setValue('abc');
      ctrl?.markAsTouched();
      expect(exposed.mileageErrors()).toBe('booking.carinformation.form.mileage.error.digitsOnly');
    });

    it('should return min error key when mileage is 0 (REQ-031 BR-6)', () => {
      const exposed = component as unknown as {
        mileageErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mileage');
      ctrl?.setValue(0);
      ctrl?.markAsTouched();
      expect(exposed.mileageErrors()).toBe('booking.carinformation.form.mileage.error.min');
    });

    it('should return min error key when mileage is 9 (REQ-031 BR-6)', () => {
      const exposed = component as unknown as {
        mileageErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mileage');
      ctrl?.setValue(9);
      ctrl?.markAsTouched();
      expect(exposed.mileageErrors()).toBe('booking.carinformation.form.mileage.error.min');
    });

    it('should return null mileageErrors when mileage is 10 (lower boundary)', () => {
      const exposed = component as unknown as {
        mileageErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mileage');
      ctrl?.setValue(10);
      ctrl?.markAsTouched();
      expect(exposed.mileageErrors()).toBeNull();
    });

    it('should return null mileageErrors when valid', () => {
      const exposed = component as unknown as {
        mileageErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mileage');
      ctrl?.setValue(50000);
      ctrl?.markAsTouched();
      expect(exposed.mileageErrors()).toBeNull();
    });
  });
});
