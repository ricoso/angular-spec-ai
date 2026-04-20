import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';

import { PersonalDataTileComponent } from './personal-data-tile.component';

describe('PersonalDataTileComponent', () => {
  let component: PersonalDataTileComponent;
  let fixture: ComponentFixture<PersonalDataTileComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PersonalDataTileComponent]
    })
      .overrideComponent(PersonalDataTileComponent, {
        set: { template: '<div class="mocked">Mocked PersonalDataTile</div>' }
      })
      .compileComponents();

    fixture = TestBed.createComponent(PersonalDataTileComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('customerInfo', {
      email: 'max@example.com',
      salutation: 'mr',
      firstName: 'Max',
      lastName: 'Mustermann',
      street: 'Musterstr. 1',
      postalCode: '80331',
      city: 'München',
      mobilePhone: '+49 170 1234567'
    });
    fixture.componentRef.setInput('vehicleInfo', {
      licensePlate: 'M-AB 1234',
      mileage: 45000
    });
    fixture.componentRef.setInput('brandName', 'Audi');

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have customer info input', () => {
    expect(component.customerInfo()).toEqual(
      expect.objectContaining({
        firstName: 'Max',
        lastName: 'Mustermann',
        email: 'max@example.com'
      })
    );
  });

  it('should have vehicle info input', () => {
    expect(component.vehicleInfo()).toEqual(
      expect.objectContaining({
        licensePlate: 'M-AB 1234',
        mileage: 45000
      })
    );
  });

  it('should have brand name input', () => {
    expect(component.brandName()).toBe('Audi');
  });

  it('should expose i18n keys', () => {
    const exposed = component as unknown as { bookingOverview: Record<string, unknown> };
    expect(exposed.bookingOverview).toBeDefined();
  });
});
