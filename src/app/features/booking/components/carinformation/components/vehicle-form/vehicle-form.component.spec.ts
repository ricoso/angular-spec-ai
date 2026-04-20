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
      mileage: [null as number | null, [Validators.required, Validators.min(0)]],
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

    it('should return mileage error key when mileage is negative', () => {
      const exposed = component as unknown as {
        mileageErrors: () => string | null;
        form: () => ReturnType<FormBuilder['group']>;
      };
      const ctrl = exposed.form().get('mileage');
      ctrl?.setValue(-1);
      ctrl?.markAsTouched();
      expect(exposed.mileageErrors()).toBe('booking.carinformation.form.mileage.error.digitsOnly');
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
